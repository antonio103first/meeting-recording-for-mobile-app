package com.krunventures.meetingrecorder.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunventures.meetingrecorder.data.Meeting
import com.krunventures.meetingrecorder.ui.theme.*
import com.krunventures.meetingrecorder.viewmodel.MeetingListViewModel

@Composable
fun MeetingListScreen(viewModel: MeetingListViewModel) {
    val meetings by viewModel.meetings.collectAsState(initial = emptyList())
    var selectedMeeting by remember { mutableStateOf<Meeting?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋 저장된 회의 목록", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
            IconButton(onClick = { /* refresh is automatic with Flow */ }) {
                Icon(Icons.Filled.Refresh, "새로고침", tint = Accent)
            }
        }

        if (meetings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("저장된 회의가 없습니다.", color = TextLight, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(meetings) { meeting ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedMeeting = meeting },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedMeeting?.id == meeting.id) Accent.copy(alpha = 0.1f) else CardBg
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(meeting.fileName.ifEmpty { "제목 없음" },
                                    fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextDark)
                                Text(meeting.createdAt, fontSize = 12.sp, color = TextLight)
                                Text("${String.format("%.1f", meeting.fileSizeMb)}MB",
                                    fontSize = 11.sp, color = TextLight)
                            }
                            Row {
                                if (meeting.sttText.isNotEmpty())
                                    Text("STT ✅ ", fontSize = 11.sp, color = Success)
                                if (meeting.summaryText.isNotEmpty())
                                    Text("요약 ✅", fontSize = 11.sp, color = Success)
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
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("내용 보기", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(onClick = {
                                showDeleteDialog = true
                            }) {
                                Icon(Icons.Filled.Delete, "삭제", tint = Danger)
                            }
                        }
                        val detailScroll = androidx.compose.foundation.rememberScrollState()
                        Text(
                            text = buildString {
                                appendLine("=== STT 결과 ===")
                                appendLine(meeting.sttText.ifEmpty { "(없음)" })
                                appendLine()
                                appendLine("=== 요약 결과 ===")
                                appendLine(meeting.summaryText.ifEmpty { "(없음)" })
                            },
                            modifier = Modifier.fillMaxWidth()
                                .androidx.compose.foundation.verticalScroll(detailScroll),
                            fontSize = 12.sp,
                            color = TextDark
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && selectedMeeting != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제 확인") },
            text = { Text("선택한 회의 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedMeeting?.let { viewModel.deleteMeeting(it.id) }
                    selectedMeeting = null
                    showDeleteDialog = false
                }) { Text("삭제", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            }
        )
    }
}
