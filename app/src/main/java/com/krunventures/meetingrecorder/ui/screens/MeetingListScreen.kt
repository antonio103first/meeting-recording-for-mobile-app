package com.krunventures.meetingrecorder.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunventures.meetingrecorder.data.Meeting
import com.krunventures.meetingrecorder.ui.theme.*
import com.krunventures.meetingrecorder.viewmodel.MeetingListViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeetingListScreen(viewModel: MeetingListViewModel) {
    val meetings by viewModel.meetings.collectAsState(initial = emptyList())
    val state by viewModel.uiState.collectAsState()
    var selectedMeeting by remember { mutableStateOf<Meeting?>(null) }

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

                // Detail view
                selectedMeeting?.let { meeting ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
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
                            val detailScroll = rememberScrollState()
                            SelectionContainer {
                                Text(
                                    text = buildString {
                                        appendLine("=".repeat(40))
                                        appendLine("【 회의록 요약 】")
                                        appendLine("=".repeat(40))
                                        appendLine(meeting.summaryText.ifEmpty { "(없음)" })
                                        appendLine()
                                        appendLine("=".repeat(40))
                                        appendLine("【 STT 변환 원문 】")
                                        appendLine("=".repeat(40))
                                        appendLine(meeting.sttText.ifEmpty { "(없음)" })
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .verticalScroll(detailScroll),
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp,
                                    color = TextDark
                                )
                            }
                        }
                    }
                }
            }
        }
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
