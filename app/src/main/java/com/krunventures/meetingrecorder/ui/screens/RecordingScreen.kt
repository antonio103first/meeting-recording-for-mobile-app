package com.krunventures.meetingrecorder.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunventures.meetingrecorder.service.CallState
import com.krunventures.meetingrecorder.service.RecordingState
import com.krunventures.meetingrecorder.ui.theme.*
import com.krunventures.meetingrecorder.viewmodel.RecordingViewModel
import kotlin.math.sin

@Composable
fun RecordingScreen(viewModel: RecordingViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setAudioFileFromUri(it) }
    }

    // Pulsing animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Error dialog
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("오류") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("확인") }
            }
        )
    }

    // 파일이름 입력 다이얼로그
    if (state.showFileNameDialog) {
        FileNameInputDialog(
            suggestedName = state.suggestedFileName,
            onConfirm = { fileName -> viewModel.confirmFileName(fileName) },
            onCancel = { viewModel.cancelFileName() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // === Section 1: Recording ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (state.recordingState != RecordingState.IDLE) 6.dp else 2.dp
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎙 녹음", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        if (state.recordingState == RecordingState.RECORDING) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Danger.copy(alpha = pulseAlpha))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("REC", color = Danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        if (state.callAutoPaused) {
                            Spacer(Modifier.width(8.dp))
                            Text("📞 통화 중 — 일시정지", color = Warning, fontSize = 12.sp,
                                fontWeight = FontWeight.Medium)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Elapsed time - large display
                    Text(
                        text = state.elapsed,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.recordingState == RecordingState.RECORDING)
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Amplitude visualization - multi-bar waveform
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barCount = 20
                        for (i in 0 until barCount) {
                            val barHeight = if (state.recordingState == RecordingState.RECORDING) {
                                val phase = (System.currentTimeMillis() / 100.0 + i * 0.5)
                                val base = state.amplitude * 0.7f
                                val variation = (sin(phase) * 0.3f + 0.3f).toFloat()
                                ((base + variation * state.amplitude) * 40).coerceIn(4f, 40f)
                            } else {
                                4f
                            }
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (state.recordingState == RecordingState.RECORDING)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + state.amplitude * 0.5f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Recording buttons - larger touch targets for mobile
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (state.recordingState) {
                            RecordingState.IDLE -> {
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.startRecording()
                                    },
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(26.dp)
                                ) {
                                    Icon(Icons.Filled.FiberManualRecord, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("녹음 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            RecordingState.RECORDING -> {
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.pauseRecording()
                                    },
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Warning, contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(26.dp)
                                ) {
                                    Icon(Icons.Filled.Pause, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("일시정지", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.stopRecording()
                                    },
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Danger, contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(26.dp)
                                ) {
                                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("중지", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            RecordingState.PAUSED -> {
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.resumeRecording()
                                    },
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Success, contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(26.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("재개", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                FilledTonalButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.stopRecording()
                                    },
                                    modifier = Modifier.height(52.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Danger, contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(26.dp)
                                ) {
                                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("중지", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Current file + file picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("선택 파일", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.currentFile, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch("audio/*") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("파일 선택", fontSize = 13.sp)
                        }
                    }
                }
            }

            // === Section 2: STT Result ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("📝 STT 변환 결과", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Divider(modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    if (state.sttProgress > 0) {
                        LinearProgressIndicator(
                            progress = state.sttProgress / 100f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    if (state.sttStatus.isNotEmpty()) {
                        Text(state.sttStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Pipeline start button - large, prominent
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startPipeline()
                        },
                        enabled = !state.isProcessing && state.recordingState == RecordingState.IDLE,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("처리 중...", fontSize = 16.sp)
                        } else {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("▶ 변환 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.sttText.isNotEmpty()) {
                        Text("변환 결과:", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            val sttScroll = rememberScrollState()
                            SelectionContainer {
                                Text(
                                    state.sttText,
                                    modifier = Modifier.padding(12.dp).verticalScroll(sttScroll),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // === Section 3: Summary Result ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("📋 회의록 요약", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Divider(modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    if (state.summaryProgress > 0) {
                        LinearProgressIndicator(
                            progress = state.summaryProgress / 100f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    if (state.summaryStatus.isNotEmpty()) {
                        Text(state.summaryStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (state.summaryText.isNotEmpty()) {
                        Text("요약 결과:", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            val sumScroll = rememberScrollState()
                            SelectionContainer {
                                Text(
                                    state.summaryText,
                                    modifier = Modifier.padding(12.dp).verticalScroll(sumScroll),
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // === Section 4: Key Metrics ===
            if (state.metricsText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("📊 핵심 지표", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Divider(modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        SelectionContainer {
                            Text(state.metricsText, fontSize = 14.sp, lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // === Save Status ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(1.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(state.saveStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(80.dp)) // Bottom nav padding
        }

        // ── 전화 수신 오버레이 (녹음 중에만 표시) ─────────────────
        AnimatedVisibility(
            visible = state.callState == CallState.RINGING
                    && state.recordingState != RecordingState.IDLE,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            IncomingCallOverlay(
                callerNumber = state.callerNumber,
                onAccept = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.acceptCall()
                },
                onReject = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.rejectCall()
                }
            )
        }
    }
}

/**
 * 파일이름 입력 다이얼로그
 * 변환 완료 후 저장 직전에 표시되어 사용자가 파일이름을 확인/수정할 수 있게 함
 */
@Composable
private fun FileNameInputDialog(
    suggestedName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var fileName by remember { mutableStateOf(suggestedName) }

    AlertDialog(
        onDismissRequest = { /* 배경 터치로 닫히지 않도록 */ },
        title = {
            Text("회의록 파일 저장", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("파일이름을 입력하거나 수정해주세요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("파일이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text(".txt", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fileName.ifBlank { suggestedName }) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("취소") }
        }
    )
}

/**
 * 전화 수신 시 화면 상단에 표시되는 오버레이 카드
 * - 전화번호 표시
 * - 수락 버튼 (녹음 자동 일시정지 → 통화 → 통화 종료 후 자동 재개)
 * - 거절 버튼 (녹음 계속 유지)
 */
@Composable
private fun IncomingCallOverlay(
    callerNumber: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B2838).copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(12.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 전화 아이콘 + 제목
            Icon(
                Icons.Filled.Phone, contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(36.dp)
            )
            Text(
                "📞 전화 수신 중",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (callerNumber.isNotEmpty()) {
                Text(
                    callerNumber,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            }
            Text(
                "녹음 중입니다. 전화를 수락하면 녹음이 자동으로 일시정지되고,\n통화 종료 후 자동으로 재개됩니다.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(4.dp))

            // 수락/거절 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 거절 버튼
                FilledTonalButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.CallEnd, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("거절", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                // 수락 버튼
                FilledTonalButton(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Phone, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("수락", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
