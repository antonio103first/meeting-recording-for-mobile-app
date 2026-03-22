package com.krunventures.meetingrecorder.ui.screens

import android.net.Uri
import android.webkit.WebView
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp),
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

                    // Pipeline start button
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
 * 전체화면 마크다운 렌더링 다이얼로그
 * - WebView로 마크다운 표/서식을 HTML로 렌더링
 * - 스크롤과 텍스트 선택(길게 눌러 복사)이 동시에 가능
 */
@Composable
private fun FullScreenTextDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    val htmlContent = remember(text) { markdownToHtml(text) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "닫기")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // 안내 문구
                Text(
                    "길게 눌러 원하는 부분을 선택하여 복사하세요",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // WebView — 마크다운을 HTML로 렌더링, 스크롤/복사 자유
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                )
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
