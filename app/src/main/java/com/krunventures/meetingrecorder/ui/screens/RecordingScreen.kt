package com.krunventures.meetingrecorder.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    // 전체보기 다이얼로그 상태
    var fullScreenTitle by remember { mutableStateOf("") }
    var fullScreenText by remember { mutableStateOf("") }
    var showFullScreen by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setAudioFileFromUri(it) }
    }

    // STT 변환파일 선택 (재요약용)
    val sttFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectSttFileFromUri(it) }
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

    // 전체보기 다이얼로그 — 독립 스크롤 + 부분 선택 복사
    if (showFullScreen) {
        FullScreenTextDialog(
            title = fullScreenTitle,
            text = fullScreenText,
            onDismiss = { showFullScreen = false }
        )
    }

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

    // 요약방식 선택 BottomSheet (재요약)
    if (state.showResummarizeSheet) {
        SummaryModeBottomSheet(
            currentMode = "speaker",
            onDismiss = { viewModel.dismissResummarizeSheet() },
            onSelect = { mode -> viewModel.startResummarize(mode) }
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
            // SAF 저장 폴더 미설정 경고 배너
            if (state.safNotConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "저장 폴더가 지정되지 않았습니다",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                "설정 → 💾저장/Drive에서 폴더를 선택해야\n파일 관리자에서 파일을 찾을 수 있습니다.",
                                fontSize = 11.sp,
                                color = Color(0xFF795548)
                            )
                        }
                    }
                }
            }

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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
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
                            RecordingState.PAUSED, RecordingState.AUDIO_FOCUS_LOST -> {
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    if (state.sttProgress > 0) {
                        LinearProgressIndicator(
                            progress = { state.sttProgress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    if (state.sttStatus.isNotEmpty()) {
                        Text(state.sttStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // ★ v3.0: MP3 파일 선택 버튼 (STT 변환할 오디오 파일 직접 선택)
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            filePicker.launch("audio/*")
                        },
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AudioFile, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.currentFile != "(없음)") "🎵 ${state.currentFile}" else "🎵 MP3/M4A 파일 선택",
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pipeline start button (녹음 후 자동 또는 파일 선택 후 수동)
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
                            Text("▶ STT 변환 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.sttText.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("변환 결과:", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface)
                            TextButton(
                                onClick = {
                                    fullScreenTitle = "📝 STT 변환 결과"
                                    fullScreenText = state.sttText
                                    showFullScreen = true
                                }
                            ) {
                                Icon(Icons.Filled.OpenInFull, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("전체보기", fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                state.sttText,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // ★ v3.0: STT txt 파일 선택 버튼 (회의록 요약 단독 실행용)
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            sttFilePicker.launch("text/*")
                        },
                        enabled = !state.isProcessing,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.selectedSttFile.isNotEmpty()) "📄 ${state.selectedSttFile}" else "📄 STT txt 파일 선택",
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ★ v3.0: 회의록 요약만 시작 버튼 (STT txt 선택 후 또는 실패 후 재시작)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.showResummarizeOptions()
                        },
                        enabled = !state.isProcessing && (state.sttText.isNotEmpty() || state.selectedSttText.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("▶ 회의록 요약 시작", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    if (state.summaryProgress > 0) {
                        LinearProgressIndicator(
                            progress = { state.summaryProgress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    if (state.summaryStatus.isNotEmpty()) {
                        Text(state.summaryStatus, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (state.summaryText.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("요약 결과:", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface)
                            TextButton(
                                onClick = {
                                    fullScreenTitle = "📋 회의록 요약"
                                    fullScreenText = state.summaryText
                                    showFullScreen = true
                                }
                            ) {
                                Icon(Icons.Filled.OpenInFull, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("전체보기", fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                state.summaryText,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis
                            )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📊 핵심 지표", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            TextButton(
                                onClick = {
                                    fullScreenTitle = "📊 핵심 지표"
                                    fullScreenText = state.metricsText
                                    showFullScreen = true
                                }
                            ) {
                                Icon(Icons.Filled.OpenInFull, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("전체보기", fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Text(state.metricsText, fontSize = 14.sp, lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // === Section 5: 재요약 (STT 변환파일 기반) ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("🔄 STT 변환파일로 재요약", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    Text(
                        "기존 STT 변환파일을 다른 요약방식으로 다시 요약합니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // 선택된 파일명 표시
                    if (state.selectedSttFile.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Description, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(state.selectedSttFile, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.clearResummarize() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "선택 취소",
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // STT 파일 선택 버튼
                    OutlinedButton(
                        onClick = { sttFilePicker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.selectedSttFile.isEmpty()) "📄 STT 변환파일 선택"
                            else "다른 파일 선택",
                            fontSize = 14.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 재요약 진행률
                    if (state.resummarizeProgress > 0) {
                        LinearProgressIndicator(
                            progress = { state.resummarizeProgress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    if (state.resummarizeStatus.isNotEmpty()) {
                        Text(state.resummarizeStatus, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }

                    // 재요약 실행 버튼
                    Button(
                        onClick = { viewModel.showResummarizeSheet() },
                        enabled = state.selectedSttFile.isNotEmpty() && !state.isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("▶ 재요약 실행", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
 * ★ v3.3.1: 전체화면 다이얼로그 — 편집 모드 + 찾기/바꾸기 기능 포함
 */
@Composable
private fun FullScreenTextDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onTextChanged: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var currentText by remember(text) { mutableStateOf(text) }
    val htmlContent = remember(currentText) { markdownToHtml(currentText) }

    // 모드 상태: 보기 vs 편집
    var isEditMode by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // 찾기/바꾸기 상태
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCount by remember { mutableStateOf(0) }
    var replaceResultMsg by remember { mutableStateOf("") }

    LaunchedEffect(findQuery, currentText) {
        matchCount = if (findQuery.isNotEmpty()) {
            var count = 0; var start = 0
            while (true) { val idx = currentText.indexOf(findQuery, start); if (idx < 0) break; count++; start = idx + findQuery.length }
            count
        } else 0
        replaceResultMsg = ""
    }

    Dialog(
        onDismissRequest = {
            if (hasUnsavedChanges) {
                // 미저장 변경이 있어도 닫기
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(top = 32.dp),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── 헤더 ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 편집/보기 모드 전환
                    IconButton(onClick = {
                        if (!isEditMode) {
                            editText = currentText
                            isEditMode = true
                            showFindReplace = false
                            hasUnsavedChanges = false
                        } else {
                            isEditMode = false
                            hasUnsavedChanges = false
                        }
                    }) {
                        Icon(
                            if (isEditMode) Icons.Filled.Visibility else Icons.Filled.Edit,
                            if (isEditMode) "보기 모드" else "편집 모드",
                            tint = if (isEditMode) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 찾기/바꾸기 (보기 모드에서만)
                    if (!isEditMode) {
                        IconButton(onClick = { showFindReplace = !showFindReplace }) {
                            Icon(Icons.Filled.FindReplace, "찾기/바꾸기",
                                tint = if (showFindReplace) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 전체복사
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("회의록", currentText))
                        Toast.makeText(context, "전체 내용 복사 완료", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, "전체복사", tint = Color(0xFF2196F3))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "닫기")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // ── 편집 모드: 저장 바 ──
                if (isEditMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2196F3).copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (hasUnsavedChanges) "수정됨 — 저장하세요" else "편집 모드",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (hasUnsavedChanges) Color(0xFFE53935) else Color(0xFF2196F3)
                            )
                            Button(
                                onClick = {
                                    currentText = editText
                                    onTextChanged?.invoke(currentText)
                                    hasUnsavedChanges = false
                                    isEditMode = false
                                    Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
                                },
                                enabled = hasUnsavedChanges,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("저장", fontSize = 13.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }

                // ── 찾기/바꾸기 바 (보기 모드에서만) ──
                if (showFindReplace && !isEditMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = findQuery,
                                    onValueChange = { findQuery = it },
                                    placeholder = { Text("찾기", fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                )
                                Text(
                                    if (findQuery.isNotEmpty()) "${matchCount}건" else "",
                                    fontSize = 12.sp,
                                    color = if (matchCount > 0) Color(0xFF2196F3) else Color.Gray,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = replaceQuery,
                                    onValueChange = { replaceQuery = it },
                                    placeholder = { Text("바꿀 내용", fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                )
                                Button(
                                    onClick = {
                                        if (findQuery.isNotEmpty() && matchCount > 0) {
                                            val replaced = matchCount
                                            currentText = currentText.replace(findQuery, replaceQuery)
                                            onTextChanged?.invoke(currentText)
                                            replaceResultMsg = "${replaced}건 변경 완료"
                                            findQuery = ""; replaceQuery = ""
                                        }
                                    },
                                    enabled = findQuery.isNotEmpty() && matchCount > 0,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("전체 바꾸기", fontSize = 12.sp)
                                }
                            }
                            if (replaceResultMsg.isNotEmpty()) {
                                Text(replaceResultMsg, fontSize = 11.sp, color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }

                // ── 본문 영역 ──
                if (isEditMode) {
                    // 편집 모드: TextField
                    OutlinedTextField(
                        value = editText,
                        onValueChange = {
                            editText = it
                            hasUnsavedChanges = true
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2196F3),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                } else {
                    // 보기 모드: 안내 + WebView
                    Text(
                        "길게 눌러 선택 복사 | ✏️ 편집 버튼으로 내용 수정",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = false
                                    defaultTextEncodingName = "UTF-8"
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                }
                                isLongClickable = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 마크다운 텍스트를 HTML로 변환
 * - 표(table), 헤더(#), 굵게(**), 리스트(-), 수평선(---) 지원
 */
private fun markdownToHtml(markdown: String): String {
    val body = StringBuilder()
    val lines = markdown.split("\n")
    var inTable = false
    var isFirstTableRow = true

    for (line in lines) {
        val trimmed = line.trim()

        // 테이블 구분선 (|---|---|) → 무시
        if (trimmed.matches(Regex("^\\|[\\s\\-:|]+\\|$"))) {
            continue
        }

        // 테이블 행
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            if (!inTable) {
                body.append("<table>")
                inTable = true
                isFirstTableRow = true
            }
            val cells = trimmed.split("|")
                .filter { it.isNotBlank() }
                .map { it.trim() }
            val tag = if (isFirstTableRow) "th" else "td"
            body.append("<tr>")
            cells.forEach { cell ->
                body.append("<$tag>${inlineMd(cell)}</$tag>")
            }
            body.append("</tr>")
            isFirstTableRow = false
            continue
        }

        // 테이블 종료
        if (inTable) {
            body.append("</table>")
            inTable = false
        }

        when {
            trimmed.startsWith("#### ") -> body.append("<h4>${inlineMd(trimmed.drop(5))}</h4>")
            trimmed.startsWith("### ") -> body.append("<h3>${inlineMd(trimmed.drop(4))}</h3>")
            trimmed.startsWith("## ") -> body.append("<h2>${inlineMd(trimmed.drop(3))}</h2>")
            trimmed.startsWith("# ") -> body.append("<h1>${inlineMd(trimmed.drop(2))}</h1>")
            trimmed == "---" || trimmed == "***" -> body.append("<hr>")
            trimmed.startsWith("- ") -> body.append("<div class='li'>• ${inlineMd(trimmed.drop(2))}</div>")
            trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                val content = trimmed.replaceFirst(Regex("^\\d+\\.\\s"), "")
                body.append("<div class='li'>${trimmed.substringBefore(" ")} ${inlineMd(content)}</div>")
            }
            trimmed.isEmpty() -> body.append("<br>")
            else -> body.append("<p>${inlineMd(trimmed)}</p>")
        }
    }

    if (inTable) body.append("</table>")

    return """<!DOCTYPE html>
<html><head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
<style>
* { box-sizing: border-box; }
body {
    font-family: 'Noto Sans KR', -apple-system, sans-serif;
    font-size: 15px;
    line-height: 1.7;
    color: #1a1a1a;
    padding: 12px 16px;
    margin: 0;
    word-break: keep-all;
}
h1 { font-size: 20px; margin: 18px 0 8px; color: #111; border-bottom: 2px solid #2196F3; padding-bottom: 4px; }
h2 { font-size: 17px; margin: 14px 0 6px; color: #1565C0; }
h3 { font-size: 15px; margin: 12px 0 4px; color: #333; }
h4 { font-size: 14px; margin: 10px 0 4px; color: #555; }
table {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    font-size: 14px;
}
th, td {
    border: 1px solid #ccc;
    padding: 8px 12px;
    text-align: left;
    vertical-align: top;
}
th { background: #e3f2fd; font-weight: bold; color: #1565C0; }
tr:nth-child(even) td { background: #fafafa; }
hr { border: none; border-top: 1px solid #ddd; margin: 16px 0; }
p { margin: 4px 0; }
.li { padding: 2px 0 2px 8px; }
strong { color: #1565C0; }
</style>
</head><body>
$body
</body></html>"""
}

/**
 * 인라인 마크다운 → HTML (굵게, 이모지 보존)
 */
private fun inlineMd(text: String): String {
    return text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("__(.+?)__"), "<strong>$1</strong>")
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        .replace(Regex("`(.+?)`"), "<code>$1</code>")
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
                    suffix = { Text(".md", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

/**
 * 요약방식 선택 BottomSheet — 재요약 실행 시 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryModeBottomSheet(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "요약 방식 선택",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "선택한 방식으로 STT 텍스트를 다시 요약합니다.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // ★ v3.0: 양식별 설명 및 샘플 제공
            val modes = listOf(
                Triple("speaker", "주간회의 (화자 중심)",
                    "파트너 주간회의 — 화자별 코드([K2],[K1]등) 구분\n주제별 재분류, 사실 중심 기록"),
                Triple("topic", "다자간 협의 (안건 중심)",
                    "기관 협의·다자간 공식회의 — 안건별 구조화\nQ&A 주석, 배경·합의 소항목 구분"),
                Triple("formal_md", "회의록(업무)",
                    "투자업체 사후관리 — 경영현황·재무·IPO·펀드\n전수 포착, Q&A 필수, 경영현황 테이블"),
                Triple("ir_md", "IR 미팅",
                    "IR 미팅 노트 — 기술해자·펀드적합성·투자매력도\n3대 분석축, 경쟁사 비교표, 스코어링"),
                Triple("phone", "전화통화 메모",
                    "전화통화 — 주제별 1~2줄 압축 요약\n[Antonio] 화자 표기, Q&A 보충 주석"),
                Triple("flow", "네트워킹(티타임)",
                    "비공식 미팅·티타임 — 주제별 압축 요약\n현황/주요내용 소항목, Q&A 주석"),
                Triple("lecture_md", "강의 요약",
                    "강의/세미나 — 상세 구조화 마크다운 노트\n이모지 금지, 핵심개념 정리 테이블")
            )

            modes.forEach { (value, label, description) ->
                val isSelected = selectedMode == value
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { selectedMode = value }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMode = value }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (value == currentMode) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "현재 설정",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        // ★ v3.0: 선택된 양식의 설명 및 샘플 표시
                        if (isSelected) {
                            Text(
                                description,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("취소", fontSize = 15.sp)
                }
                Button(
                    onClick = { onSelect(selectedMode) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("요약 실행", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp)) // Bottom safe area
        }
    }
}
