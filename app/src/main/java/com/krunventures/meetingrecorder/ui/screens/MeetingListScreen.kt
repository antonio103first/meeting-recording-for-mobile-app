package com.krunventures.meetingrecorder.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
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
    val context = LocalContext.current

    // ★ 상단 탭 상태: 0=녹음파일MP3, 1=STT변환, 2=회의록(요약)
    var selectedTab by remember { mutableIntStateOf(2) }

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
            // ── 헤더 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("저장된 회의 목록", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                Text("${meetings.size}건", fontSize = 13.sp, color = TextLight)
            }
            Text("길게 누르면 관리 메뉴가 표시됩니다", fontSize = 11.sp, color = TextLight,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

            // ★ 상단 탭 UI: 녹음파일 MP3 | STT변환 | 회의록(요약)
            val tabTitles = listOf("녹음파일 MP3", "STT변환", "회의록(요약)")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isActive = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isActive) Accent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            title,
                            fontSize = if (isActive) 13.sp else 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color.White else TextLight,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

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
                                        // 탭 2(회의록)에서 클릭하면 전체보기 다이얼로그
                                        when (selectedTab) {
                                            1 -> if (meeting.sttText.isNotBlank()) {
                                                fullScreenTitle = "${meeting.fileName} — STT 원문"
                                                fullScreenText = meeting.sttText
                                                showFullScreen = true
                                            }
                                            2 -> if (meeting.summaryText.isNotBlank()) {
                                                fullScreenTitle = "${meeting.fileName} — 회의록"
                                                fullScreenText = meeting.summaryText
                                                showFullScreen = true
                                            }
                                            else -> { /* MP3 탭은 클릭 시 별도 동작 없음 */ }
                                        }
                                    },
                                    onLongClick = { viewModel.showActionMenu(meeting) }
                                ),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                // ── 공통 헤더: 파일명 + 날짜 ──
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when (selectedTab) {
                                            0 -> Icons.Filled.Audiotrack
                                            1 -> Icons.Filled.TextSnippet
                                            else -> Icons.Filled.Description
                                        },
                                        null, modifier = Modifier.size(32.dp),
                                        tint = Accent.copy(alpha = 0.7f)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            meeting.fileName.ifEmpty { "제목 없음" },
                                            fontWeight = FontWeight.Medium, fontSize = 14.sp,
                                            color = TextDark, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(meeting.createdAt, fontSize = 11.sp, color = TextLight)
                                    }
                                    IconButton(onClick = { viewModel.showActionMenu(meeting) }) {
                                        Icon(Icons.Filled.MoreVert, "관리", tint = TextLight)
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                // ★ 탭별 상세 내용 표시
                                when (selectedTab) {
                                    // ── 탭 0: 녹음파일 MP3/M4A ──
                                    0 -> {
                                        if (meeting.mp3LocalPath.isNotBlank()) {
                                            val mp3File = java.io.File(meeting.mp3LocalPath)
                                            val exists = mp3File.exists()
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(mp3File.name, fontSize = 12.sp, color = TextDark,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Text("${String.format("%.1f", meeting.fileSizeMb)}MB",
                                                            fontSize = 11.sp, color = TextLight)
                                                        Text(
                                                            if (exists) "파일 존재" else "파일 없음",
                                                            fontSize = 11.sp,
                                                            color = if (exists) Success else Danger
                                                        )
                                                    }
                                                }
                                                if (exists) {
                                                    Icon(Icons.Filled.CheckCircle, null,
                                                        modifier = Modifier.size(20.dp), tint = Success)
                                                } else {
                                                    Icon(Icons.Filled.Error, null,
                                                        modifier = Modifier.size(20.dp), tint = Danger)
                                                }
                                            }
                                        } else {
                                            Text("녹음 파일 없음", fontSize = 12.sp, color = TextLight)
                                        }
                                    }

                                    // ── 탭 1: STT변환 ──
                                    1 -> {
                                        if (meeting.sttText.isNotBlank()) {
                                            Text(
                                                meeting.sttText, maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp, lineHeight = 18.sp, color = TextDark
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Text("터치하면 전체보기", fontSize = 10.sp, color = Accent)
                                            }
                                        } else {
                                            Text("STT 변환 결과 없음", fontSize = 12.sp, color = TextLight)
                                        }
                                        if (meeting.sttLocalPath.isNotBlank()) {
                                            val sttFile = java.io.File(meeting.sttLocalPath)
                                            Text(
                                                "파일: ${sttFile.name} ${if (sttFile.exists()) "✓" else "✗"}",
                                                fontSize = 10.sp, color = TextLight,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    // ── 탭 2: 회의록(요약) ──
                                    2 -> {
                                        if (meeting.summaryText.isNotBlank()) {
                                            Text(
                                                meeting.summaryText, maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp, lineHeight = 18.sp, color = TextDark
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("터치하면 전체보기", fontSize = 10.sp, color = Accent)
                                                Row {
                                                    TextButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(ClipData.newPlainText("회의록", meeting.summaryText))
                                                            Toast.makeText(context, "회의록 전체 복사 완료", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(2.dp))
                                                        Text("복사", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        } else {
                                            Text("회의록 요약 없음", fontSize = 12.sp, color = TextLight)
                                        }
                                        if (meeting.summaryLocalPath.isNotBlank()) {
                                            val summaryFile = java.io.File(meeting.summaryLocalPath)
                                            Text(
                                                "파일: ${summaryFile.name} ${if (summaryFile.exists()) "✓" else "✗"}",
                                                fontSize = 10.sp, color = TextLight,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // === 전체화면 다이얼로그 — 독립 스크롤 + 부분 선택 복사 + 전체복사 버튼 ===
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
            onCopy = { viewModel.copyMeetingTextToClipboard() },
            onRename = { viewModel.showRenameDialog() },
            onSpeakerEdit = { viewModel.showSpeakerDialog() },
            onDelete = { viewModel.showDeleteDialog() },
            onDeleteFilesOnly = { viewModel.deleteFilesOnly() },
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
                    Text("DB 기록 삭제", fontSize = 13.sp, color = TextLight)
                    Text("녹음 파일 삭제", fontSize = 13.sp, color = TextLight)
                    Text("회의록 파일 삭제", fontSize = 13.sp, color = TextLight)
                    Spacer(Modifier.height(4.dp))
                    Text("이 작업은 되돌릴 수 없습니다.", fontSize = 12.sp, color = Danger)
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
 * 전체화면 마크다운 렌더링 다이얼로그 + 전체복사 버튼
 */
@Composable
private fun MeetingFullScreenTextDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
                    // ★ 전체복사 버튼
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("회의록", text))
                        Toast.makeText(context, "전체 내용 복사 완료", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, "전체복사", tint = Accent)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "닫기")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // 안내 문구
                Text(
                    "길게 눌러 선택 복사 | 우측 상단 아이콘으로 전체 복사",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // WebView — 마크다운을 HTML로 렌더링
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
 */
private fun markdownToHtml(markdown: String): String {
    val body = StringBuilder()
    val lines = markdown.split("\n")
    var inTable = false
    var isFirstTableRow = true

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.matches(Regex("^\\|[\\s\\-:|]+\\|$"))) continue

        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            if (!inTable) {
                body.append("<table>")
                inTable = true
                isFirstTableRow = true
            }
            val cells = trimmed.split("|").filter { it.isNotBlank() }.map { it.trim() }
            val tag = if (isFirstTableRow) "th" else "td"
            body.append("<tr>")
            cells.forEach { cell -> body.append("<$tag>${inlineMd(cell)}</$tag>") }
            body.append("</tr>")
            isFirstTableRow = false
            continue
        }

        if (inTable) { body.append("</table>"); inTable = false }

        when {
            trimmed.startsWith("#### ") -> body.append("<h4>${inlineMd(trimmed.drop(5))}</h4>")
            trimmed.startsWith("### ") -> body.append("<h3>${inlineMd(trimmed.drop(4))}</h3>")
            trimmed.startsWith("## ") -> body.append("<h2>${inlineMd(trimmed.drop(3))}</h2>")
            trimmed.startsWith("# ") -> body.append("<h1>${inlineMd(trimmed.drop(2))}</h1>")
            trimmed == "---" || trimmed == "***" -> body.append("<hr>")
            trimmed.startsWith("- ") -> body.append("<div class='li'>&bull; ${inlineMd(trimmed.drop(2))}</div>")
            trimmed.startsWith("> ") -> body.append("<blockquote>${inlineMd(trimmed.drop(2))}</blockquote>")
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
    font-size: 15px; line-height: 1.7; color: #1a1a1a;
    padding: 12px 16px; margin: 0; word-break: keep-all;
}
h1 { font-size: 20px; margin: 18px 0 8px; color: #111; border-bottom: 2px solid #2196F3; padding-bottom: 4px; }
h2 { font-size: 17px; margin: 14px 0 6px; color: #1565C0; }
h3 { font-size: 15px; margin: 12px 0 4px; color: #333; }
h4 { font-size: 14px; margin: 10px 0 4px; color: #555; }
table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 14px; }
th, td { border: 1px solid #ccc; padding: 8px 12px; text-align: left; vertical-align: top; }
th { background: #e3f2fd; font-weight: bold; color: #1565C0; }
tr:nth-child(even) td { background: #fafafa; }
hr { border: none; border-top: 1px solid #ddd; margin: 16px 0; }
p { margin: 4px 0; }
.li { padding: 2px 0 2px 8px; }
strong { color: #1565C0; }
blockquote { margin: 6px 0; padding: 6px 12px; border-left: 3px solid #2196F3; background: #f5f9ff; font-size: 14px; }
</style>
</head><body>
$body
</body></html>"""
}

private fun inlineMd(text: String): String {
    return text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("__(.+?)__"), "<strong>$1</strong>")
        .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        .replace(Regex("`(.+?)`"), "<code>$1</code>")
}

/**
 * Action Menu Dialog
 */
@Composable
private fun ActionMenuDialog(
    meeting: Meeting,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onSpeakerEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteFilesOnly: () -> Unit,
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

                TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(20.dp), tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("회의록 복사", fontSize = 15.sp, color = TextDark)
                            Text("요약 + STT 원문을 클립보드에 복사", fontSize = 11.sp, color = TextLight)
                        }
                    }
                }

                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp), tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Text("이름 변경", fontSize = 15.sp, color = TextDark)
                    }
                }

                // ★ v3.2: 화자 이름 수정 메뉴
                if (meeting.summaryText.isNotBlank()) {
                    TextButton(onClick = onSpeakerEdit, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, null, modifier = Modifier.size(20.dp), tint = Accent)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("화자 이름 수정", fontSize = 15.sp, color = TextDark)
                                Text("회의록 내 화자 표기를 실명으로 변경", fontSize = 11.sp, color = TextLight)
                            }
                        }
                    }
                }

                TextButton(onClick = onDeleteFilesOnly, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FolderDelete, null, modifier = Modifier.size(20.dp), tint = Warning)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("로컬 파일만 삭제", fontSize = 15.sp, color = TextDark)
                            Text("DB 기록은 유지됨", fontSize = 11.sp, color = TextLight)
                        }
                    }
                }

                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

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
            Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(
    meeting: Meeting,
    viewModel: MeetingListViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("공유할 파일 형태 선택", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp))
            Text(meeting.fileName.ifEmpty { "제목 없음" }, fontSize = 13.sp, color = TextLight,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.shareByFormat("plain_text") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Plain Text", fontSize = 14.sp) }

                Button(
                    onClick = { viewModel.shareByFormat("md_file") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("회의록 (.md 파일)", fontSize = 14.sp) }

                Button(
                    onClick = { viewModel.shareByFormat("txt_file") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("회의록 (.txt 파일)", fontSize = 14.sp) }

                Button(
                    onClick = { viewModel.shareByFormat("with_audio") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
                ) { Text("녹음포함 (회의록 + MP3)", fontSize = 14.sp) }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
