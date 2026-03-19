package com.krunventures.meetingrecorder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krunventures.meetingrecorder.ui.theme.*
import com.krunventures.meetingrecorder.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === Gemini API ===
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("🤖 Gemini API 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var showGemKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.geminiApiKey,
                    onValueChange = { viewModel.updateGeminiKey(it) },
                    label = { Text("Gemini API 키") },
                    visualTransformation = if (showGemKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showGemKey = !showGemKey }) {
                            Text(if (showGemKey) "숨김" else "보기", fontSize = 11.sp)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.saveGeminiKey() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("저장")
                    }
                    OutlinedButton(onClick = { viewModel.testGemini() }) { Text("연결 테스트") }
                }
                if (state.geminiStatus.isNotEmpty()) {
                    Text(state.geminiStatus, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // === CLOVA Speech ===
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("🎤 CLOVA Speech API 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("한국어 특화 STT — 긴 회의 녹음도 안정적 처리", fontSize = 12.sp, color = Success)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.clovaInvokeUrl,
                    onValueChange = { viewModel.updateClovaUrl(it) },
                    label = { Text("Invoke URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                var showClovaKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.clovaSecretKey,
                    onValueChange = { viewModel.updateClovaKey(it) },
                    label = { Text("Secret Key") },
                    visualTransformation = if (showClovaKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showClovaKey = !showClovaKey }) {
                            Text(if (showClovaKey) "숨김" else "보기", fontSize = 11.sp)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.saveClovaKeys() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("저장")
                    }
                    OutlinedButton(onClick = { viewModel.testClova() }) { Text("연결 테스트") }
                }
                if (state.clovaStatus.isNotEmpty()) {
                    Text(state.clovaStatus, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // === Claude API ===
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("🧠 Claude API 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var showClaudeKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.claudeApiKey,
                    onValueChange = { viewModel.updateClaudeKey(it) },
                    label = { Text("Claude API 키") },
                    visualTransformation = if (showClaudeKey) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { showClaudeKey = !showClaudeKey }) {
                            Text(if (showClaudeKey) "숨김" else "보기", fontSize = 11.sp)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.saveClaudeKey() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("저장")
                }
            }
        }

        // === Engine Selection ===
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("⚙ 엔진 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("STT 엔진:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.sttEngine == "clova", onClick = { viewModel.setSttEngine("clova") })
                    Text("CLOVA Speech (권장)", fontSize = 13.sp)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = state.sttEngine == "gemini", onClick = { viewModel.setSttEngine("gemini") })
                    Text("Gemini", fontSize = 13.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("요약 엔진:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.aiEngine == "gemini", onClick = { viewModel.setAiEngine("gemini") })
                    Text("Gemini", fontSize = 13.sp)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = state.aiEngine == "claude", onClick = { viewModel.setAiEngine("claude") })
                    Text("Claude", fontSize = 13.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("요약 방식:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Column {
                    listOf(
                        "speaker" to "화자 중심", "topic" to "주제 중심",
                        "formal_md" to "공식 양식 (MD)", "formal_text" to "공식 양식 (텍스트)",
                        "lecture_md" to "강의 요약"
                    ).forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.summaryMode == value, onClick = { viewModel.setSummaryMode(value) })
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // === Storage Info ===
        Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("📁 저장 경로", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("녹음 파일: ${state.audioSaveDir}", fontSize = 12.sp, color = TextLight)
                Text("회의록: ${state.summarySaveDir}", fontSize = 12.sp, color = TextLight)
                Spacer(Modifier.height(4.dp))
                Text("Samsung Galaxy 내장 저장소 > Documents > Meeting recording", fontSize = 11.sp, color = TextLight)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
