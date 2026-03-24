package com.krunventures.meetingrecorder.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.krunventures.meetingrecorder.data.Meeting
import com.krunventures.meetingrecorder.ui.theme.*
import com.krunventures.meetingrecorder.viewmodel.MeetingListViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingListScreen(viewModel: MeetingListViewModel) {
    val meetings by viewModel.meetings.collectAsState(initial = emptyList())
    val state by viewModel.uiState.collectAsState()
    var selectedMeeting by remember { mutableStateOf<Meeting?>(null) }

    // 전체화면 다이얼로그 상태
    var showFullScreen by remember { mutableStateOf(false) }
    var fullScreenTitle by remember { mutableStateOf("") }
    var fullScreenText by remember { mutableStateOf("") }

    // 상태 메시지 스낵바
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.statusMessage) {
        if (state.statusMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(state.statusMessage, duration = SnackbarDuration.Short)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📋 저장된 회의 목록", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                Text("${meetings.size}건", fontSize = 13.sp, color = TextLight)
            }
            Text("길게 누르면 관리 메뉴가 표시됩니다", fontSize = 11.sp, color = TextLight,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

            if (meetings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(48.dp), tint = TextLight)
                        Spacer(Modifier.height(8.dp))
                        Text("저장된 회의가 없습니다.", color = TextLight, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(meetings, key = { it.id }) { meeting ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        selectedMeeting = if (selectedMeeting?.id == meeting.id) null else meeting
                                    },
                                    onLongClick = { viewModel.showActionMenu(meeting) }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedMeeting?.id == meeting.id)
                                    Accent.copy(alpha = 0.1f) else CardBg
                            ),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 파일 아이콘
                                Icon(
                                    Icons.Filled.Description, null,
                                    modifier = Modifier.size(36.dp),
                                    tint = Accent.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        meeting.fileName.ifEmpty { "제목 없음" },
                                        fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                        color = TextDark, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(meeting.createdAt, fontSize = 11.sp, color = TextLight)
                                        Text("${String.format("%.1f", meeting.fileSizeMb)}MB",
                                            fontSize = 11.sp, color = TextLight)
                                    }
                                    // 파일 존재 상태 표시
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        if (meeting.mp3LocalPath.isNotBlank()) {
                                            val exists = java.io.File(meeting.mp3LocalPath).exists()
                                            Text(
                                                if (exists) "🎵 녹음" else "🎵 ❌",
                                                fontSize = 10.sp,
                                                color = if (exists) Success else Danger
                                            )
                                        }
                                        if (meeting.sttLocalPath.isNotBlank()) {
                                            val exists = java.io.File(meeting.sttLocalPath).exists()
                                            Text(
                                                if (exists) "📝 회의록" else "📝 ❌",
                                                fontSize = 10.sp,
                                                color = if (exists) Success else Danger
                                            )
                                        }
                                    }
                                }

                                // 관리 버튼
                                IconButton(onClick = { viewModel.showActionMenu(meeting) }) {
                                    Icon(Icons.Filled.MoreVert, "관리", tint = TextLight)
                                }
                            }
                        }
                    }
                }

                // Detail view (미리보기 + 전체보기 버튼)
                selectedMeeting?.let { meeting ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("내용 보기", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row {
                                    IconButton(onClick = {
                                        viewModel.showActionMenu(meeting)
                                    }) {
                                        Icon(Icons.Filled.MoreVert, "관리", tint = TextLight, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { selectedMeeting = null }) {
                                        Icon(Icons.Filled.Close, "닫기", tint = TextLight, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            // Speaker name change + Share buttons
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.showActionMenu(meeting)
                                        viewModel.showSpeakerDialog()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text("🔄 화자이름 변경", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        viewModel.showActionMenu(meeting)
                                        viewModel.showShareSheet()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Text("📤 공유", fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 회의록 요약 미리보기
                            Text("회의록 요약", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                color = Accent, modifier = Modifier.padding(bottom = 4.dp))
                            Text(
                                text = meeting.summaryText.ifEmpty { "(없음)" },
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp, lineHeight = 18.sp, color = TextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                fullScreenTitle = "회의록 요약"
                                fullScreenText = meeting.summaryText.ifEmpty { "(없음)" }
                                showFullScreen = true
                            }) {
                                Icon(Icons.Filled.Fullscreen, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("전체보기", fontSize = 13.sp)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // STT 원문 미리보기
                            Text("STT 변환 원문", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                color = Accent, modifier = Modifier.padding(bottom = 4.dp))
                            Text(
                                text = meeting.sttText.ifEmpty { "(없음)" },
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp, lineHeight = 18.sp, color = TextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = {
                                fullScreenTitle = "STT 변환 원문"
                                fullScreenText = meeting.sttText.ifEmpty { "(없음)" }
                                showFullScreen = true
                            }) {
                                Icon(Icons.Filled.Fullscreen, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("전체보기", fontSize = 13.sp)
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            // 전체 내용 보기 (요약 + STT 합본)
                            Button(
                                onClick = {
                                    fullScreenTitle = meeting.fileName.ifEmpty { "회의 내용" }
                                    fullScreenText = buildString {
                                        appendLine("# 회의록 요약")
                                        appendLine("---")
                                        appendLine(meeting.summaryText.ifEmpty { "(없음)" })
                                        appendLine()
                                        appendLine("# STT 변환 원문")
                                        appendLine("---")
                                        appendLine(meeting.sttText.ifEmpty { "(없음)" })
                                    }
                                    showFullScreen = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Filled.OpenInFull, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("전체 내용 보기 (스크롤 + 복사 가능)", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // === 전체화면 다이얼로그 — 독립 스크롤 + 부분 선택 복사 ===
    if (showFullScreen) {
        MeetingFullScreenTextDialog(
            title = fullScreenTitle,
            text = fullScreenText,
            onDismiss = { showFullScreen = false }
        )
    }

    // === Action Menu Bottom Sheet ===
    if (state.showActionMenu && state.targetMeeting != null) {
        ActionMenuDialog(
            meeting = state.targetMeeting!!,
            onRename = { viewModel.showRenameDialog() },
            onDelete = { viewModel.showDeleteDialog() },
            onDeleteFilesOnly = { viewModel.deleteFilesOnly() },
            onShare = { viewModel.shareMeeting() },
            onDismiss = { viewModel.dismissActionMenu() }
        )
    }

    // === Delete Confirm Dialog ===
    if (state.showDeleteDialog && state.targetMeeting != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("삭제 확인", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("선택한 회의 기록을 삭제하시겠습니까?")
                    Spacer(Modifier.height(4.dp))
                    Text("• DB 기록 삭제", fontSize = 13.sp, color = TextLight)
                    Text("• 녹음 파일 삭제", fontSize = 13.sp, color = TextLight)
                    Text("• 회의록 파일 삭제", fontSize = 13.sp, color = TextLight)
                    Spacer(Modifier.height(4.dp))
                    Text("⚠ 이 작업은 되돌릴 수 없습니다.", fontSize = 12.sp, color = Danger)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteMeeting(deleteFiles = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("전체 삭제") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("취소") }
            }
        )
    }

    // === Rename Dialog ===
    if (state.showRenameDialog && state.targetMeeting != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRenameDialog() },
            title = { Text("이름 변경", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("새 파일이름을 입력하세요.", fontSize = 13.sp, color = TextLight)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.renameText,
                        onValueChange = { viewModel.updateRenameText(it) },
                        label = { Text("파일이름") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmRename() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = state.renameText.isNotBlank()
                ) { Text("변경") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRenameDialog() }) { Text("취소") }
            }
        )
    }

    // === Speaker Name Dialog ===
    if (state.showSpeakerDialog && state.targetMeeting != null) {
        SpeakerNameDialog(
            speakers = state.currentSpeakers,
            onSpeakerNameChange = { index, newName -> viewModel.updateSpeakerName(index, newName) },
            onApply = { viewModel.saveSpeakerMap(state.targetMeeting!!.id) },
            onDismiss = { viewModel.dismissSpeakerDialog() }
        )
    }

    // === Share Sheet ===
    if (state.showShareSheet && state.targetMeeting != null) {
        ShareBottomSheet(
            meeting = state.targetMeeting!!,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissShareSheet() }
        )
    }
}

/**
 * 전체화면 마크다운 렌더링 다이얼로그 — 회의목록 전용
 * - WebView로 마크다운 표/서식을 HTML로 렌더링
 * - 스크롤과 텍스트 선택(길게 눌러 복사)이 동시에 가능
 */
@Composable
private fun MeetingFullScreenTextDialog(
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
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
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
 * 파일 관리 액션 메뉴 다이얼로그
 */
@Composable
private fun ActionMenuDialog(
    meeting: Meeting,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDeleteFilesOnly: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                meeting.fileName.ifEmpty { "제목 없음" },
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(meeting.createdAt, fontSize = 12.sp, color = TextLight)
                Spacer(Modifier.height(12.dp))

                // 이름 변경
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp), tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Text("이름 변경", fontSize = 15.sp, color = TextDark)
                    }
                }

                // 공유
                TextButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(20.dp), tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Text("공유 (카카오톡, 메일 등)", fontSize = 15.sp, color = TextDark)
                    }
                }

                // 로컬 파일만 삭제
                TextButton(
                    onClick = onDeleteFilesOnly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FolderDelete, null, modifier = Modifier.size(20.dp), tint = Warning)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("로컬 파일만 삭제", fontSize = 15.sp, color = TextDark)
                            Text("DB 기록은 유지됨", fontSize = 11.sp, color = TextLight)
                        }
                    }
                }

                // 전체 삭제
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(20.dp), tint = Danger)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("전체 삭제", fontSize = 15.sp, color = Danger)
                            Text("DB + 로컬 파일 모두 삭제", fontSize = 11.sp, color = TextLight)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

/**
 * Speaker Name Change Dialog
 * Shows detected speakers with TextField for each name mapping
 */
@Composable
private fun SpeakerNameDialog(
    speakers: List<Pair<String, String>>,
    onSpeakerNameChange: (Int, String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("화자 이름 변경", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("각 화자의 실명을 입력하세요", fontSize = 13.sp, color = TextLight, modifier = Modifier.padding(bottom = 12.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(speakers.size) { index ->
                        val (label, name) = speakers[index]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$label →", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            OutlinedTextField(
                                value = name,
                                onValueChange = { onSpeakerNameChange(index, it) },
                                placeholder = { Text("이름 입력") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/**
 * Share Bottom Sheet
 * Allows selecting share target (summary, stt, all) and share method (KakaoTalk, Email, Copy)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(
    meeting: Meeting,
    viewModel: MeetingListViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var shareTarget by remember { mutableStateOf("all") }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("공유 방법 선택", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

            // Share target selection
            Text("공유 대상", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Accent, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareTargetCheckbox(
                    label = "요약",
                    selected = shareTarget == "summary",
                    onClick = { shareTarget = "summary" },
                    modifier = Modifier.weight(1f)
                )
                ShareTargetCheckbox(
                    label = "원문",
                    selected = shareTarget == "stt",
                    onClick = { shareTarget = "stt" },
                    modifier = Modifier.weight(1f)
                )
                ShareTargetCheckbox(
                    label = "전체",
                    selected = shareTarget == "all",
                    onClick = { shareTarget = "all" },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Share methods
            Text("공유 방법", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Accent, modifier = Modifier.padding(bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // KakaoTalk
                Button(
                    onClick = {
                        val text = viewModel.getShareText(meeting, shareTarget)
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            setPackage("com.kakao.talk")
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "카카오톡"))
                            onDismiss()
                        } catch (e: Exception) {
                            android.util.Log.e("ShareSheet", "KakaoTalk not installed", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE812))
                ) {
                    Text("💬 카카오톡", color = Color.Black)
                }

                // Email
                Button(
                    onClick = {
                        val text = viewModel.getShareText(meeting, shareTarget)
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_SUBJECT, meeting.fileName)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        try {
                            context.startActivity(Intent.createChooser(intent, "이메일"))
                            onDismiss()
                        } catch (e: Exception) {
                            android.util.Log.e("ShareSheet", "Email client not available", e)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("✉️ 이메일")
                }

                // Copy to clipboard
                Button(
                    onClick = {
                        val text = viewModel.getShareText(meeting, shareTarget)
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText("회의 내용", text)
                        clipboardManager.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "복사되었습니다", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("📋 복사")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Share Target Selection Checkbox
 */
@Composable
private fun ShareTargetCheckbox(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Accent else Color.LightGray
        )
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Color.White else TextDark
        )
    }
}
