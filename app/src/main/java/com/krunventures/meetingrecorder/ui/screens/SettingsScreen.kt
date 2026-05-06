package com.krunventures.meetingrecorder.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import com.krunventures.meetingrecorder.ui.theme.*
import com.krunventures.meetingrecorder.viewmodel.SettingsUiState
import com.krunventures.meetingrecorder.viewmodel.SettingsViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Google Sign-In launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // resultCode 무관하게 항상 전달 — 에러 코드 진단을 위해
        viewModel.handleSignInResult(result.data)
    }

    // SAF (Storage Access Framework) folder picker launchers
    // ★ Android 11+: takePersistableUriPermission() 필수 — 앱 재시작 후에도 접근 권한 유지
    val context = androidx.compose.ui.platform.LocalContext.current
    val safBasePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setUserSelectedBaseDir(uri.toString())
        }
    }

    val safAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setUserSelectedAudioDir(uri.toString())
        }
    }

    val safSttPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setUserSelectedSttDir(uri.toString())
        }
    }

    val safSummaryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setUserSelectedSummaryDir(uri.toString())
        }
    }

    // ★ v3.0: Obsidian vault 폴더 선택 launcher
    val obsidianPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setObsidianVaultDir(uri.toString())
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("⚙ 엔진 설정", "🔑 API 키", "💾 저장/Drive")

    Column(modifier = Modifier.fillMaxSize()) {
        // Top TabRow
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = CardBg,
            contentColor = Accent,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    height = 3.dp,
                    color = Accent
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    unselectedContentColor = TextLight
                )
            }
        }

        // Tab Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> EngineSettingsTab(viewModel, state)
                1 -> ApiKeysTab(viewModel, state)
                2 -> StorageDriveTab(viewModel, state, safBasePickerLauncher, safAudioPickerLauncher, safSttPickerLauncher, safSummaryPickerLauncher, obsidianPickerLauncher, signInLauncher)
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // === Folder Picker Dialog (Google Drive) ===
    if (state.showFolderPicker) {
        DriveFolderPickerDialog(
            folders = state.driveFolders,
            target = if (state.folderPickerTarget == "mp3") "녹음 파일" else "회의록",
            isLoading = state.folderPickerLoading,
            path = state.folderPickerPath,
            onSelect = { id, name -> viewModel.selectDriveFolder(id, name) },
            onNavigate = { id, name -> viewModel.navigateToFolder(id, name) },
            onNavigateUp = { viewModel.navigateUp() },
            onNavigateToPath = { index -> viewModel.navigateToPathIndex(index) },
            onCreateNew = { name -> viewModel.createNewDriveFolder(name) },
            onDismiss = { viewModel.dismissFolderPicker() }
        )
    }
}

@Composable
private fun EngineSettingsTab(
    viewModel: SettingsViewModel,
    state: SettingsUiState
) {
    // STT Engine
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("STT 엔진", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Spacer(Modifier.height(8.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.sttEngine == "clova", onClick = { viewModel.setSttEngine("clova") })
                Text("CLOVA", fontSize = 13.sp)
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = state.sttEngine == "gemini", onClick = { viewModel.setSttEngine("gemini") })
                Text("Gemini", fontSize = 13.sp)
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = state.sttEngine == "whisper", onClick = { viewModel.setSttEngine("whisper") })
                Text("Whisper", fontSize = 13.sp)
            }
        }
    }

    // AI Summary Engine
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("요약 엔진", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Spacer(Modifier.height(8.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.aiEngine == "gemini", onClick = { viewModel.setAiEngine("gemini") })
                Text("Gemini", fontSize = 13.sp)
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = state.aiEngine == "claude", onClick = { viewModel.setAiEngine("claude") })
                Text("Claude", fontSize = 13.sp)
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = state.aiEngine == "chatgpt", onClick = { viewModel.setAiEngine("chatgpt") })
                Text("GPT-4o", fontSize = 13.sp)
            }
        }
    }

    // Summary Mode
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("요약 방식", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Spacer(Modifier.height(8.dp))
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(4.dp))
            listOf(
                "speaker" to "주간회의 (화자 중심)",
                "topic" to "다자간 협의 (안건 중심)",
                "formal_md" to "회의록(업무)",
                "ir_md" to "IR 미팅",
                "phone" to "전화통화 메모",
                "flow" to "네트워킹(티타임)",
                "lecture_md" to "강의 요약",
                "conference" to "컨퍼런스 / 간담회"
            ).forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    RadioButton(selected = state.summaryMode == value, onClick = { viewModel.setSummaryMode(value) })
                    Text(label, fontSize = 13.sp)
                }
            }
        }
    }

    // Number of Speakers — 자동/수동 선택
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("화자 수", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // 자동/수동 토글
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.autoSpeakerDetection) "자동 감지 (AI가 화자 수를 판단)"
                    else "수동 설정",
                    fontSize = 14.sp, modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.autoSpeakerDetection,
                    onCheckedChange = { viewModel.setAutoSpeakerDetection(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Accent,
                        checkedTrackColor = Accent.copy(alpha = 0.3f)
                    )
                )
            }

            // 수동 모드일 때만 입력 필드 표시
            if (!state.autoSpeakerDetection) {
                Spacer(Modifier.height(8.dp))
                var numSpeakersText by remember { mutableStateOf(state.numSpeakers.toString()) }
                OutlinedTextField(
                    value = numSpeakersText,
                    onValueChange = { newVal ->
                        numSpeakersText = newVal
                        val num = newVal.toIntOrNull() ?: 2
                        if (num > 0 && num <= 20) {
                            viewModel.setNumSpeakers(num)
                        }
                    },
                    label = { Text("화자 수 (1-20)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeysTab(
    viewModel: SettingsViewModel,
    state: SettingsUiState
) {
    var selectedApiTab by remember { mutableStateOf(0) }
    val apiTabs = listOf("CLOVA", "Gemini", "ChatGPT", "Claude")

    ScrollableTabRow(
        selectedTabIndex = selectedApiTab,
        modifier = Modifier.fillMaxWidth(),
        containerColor = CardBg,
        contentColor = Accent,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedApiTab]),
                height = 3.dp,
                color = Accent
            )
        }
    ) {
        apiTabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedApiTab == index,
                onClick = { selectedApiTab = index },
                text = { Text(title, fontSize = 12.sp) },
                unselectedContentColor = TextLight
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    when (selectedApiTab) {
        0 -> ClovaApiCard(viewModel, state)
        1 -> GeminiApiCard(viewModel, state)
        2 -> ChatGptApiCard(viewModel, state)
        3 -> ClaudeApiCard(viewModel, state)
    }
}

@Composable
private fun ClovaApiCard(
    viewModel: SettingsViewModel,
    state: SettingsUiState
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("🎤 CLOVA Speech API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Text("한국어 특화 STT — 긴 회의 녹음도 안정적 처리", fontSize = 12.sp, color = Success)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

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
}

@Composable
private fun GeminiApiCard(
    viewModel: SettingsViewModel,
    state: SettingsUiState
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("🤖 Gemini API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

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
}

@Composable
private fun ChatGptApiCard(
    viewModel: SettingsViewModel,
    state: SettingsUiState
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("💬 ChatGPT / OpenAI API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Text("Whisper STT + GPT-4o 요약", fontSize = 12.sp, color = Success)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            var showChatGptKey by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.chatGptApiKey,
                onValueChange = { viewModel.updateChatGptKey(it) },
                label = { Text("OpenAI API 키") },
                visualTransformation = if (showChatGptKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { showChatGptKey = !showChatGptKey }) {
                        Text(if (showChatGptKey) "숨김" else "보기", fontSize = 11.sp)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveChatGptKey() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("저장")
                }
                OutlinedButton(onClick = { viewModel.testChatGpt() }) { Text("연결 테스트") }
            }
            if (state.chatGptStatus.isNotEmpty()) {
                Text(state.chatGptStatus, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ClaudeApiCard(
    viewModel: SettingsViewModel,
    state: SettingsUiState
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("🧠 Claude API", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveClaudeKey() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("저장")
                }
                OutlinedButton(onClick = { viewModel.testClaude() }) { Text("연결 테스트") }
            }
            if (state.claudeStatus.isNotEmpty()) {
                Text(state.claudeStatus, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun StorageDriveTab(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    safBasePickerLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    safAudioPickerLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    safSttPickerLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    safSummaryPickerLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    obsidianPickerLauncher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    signInLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    // Local Storage Path — Separate Folders (v2.0)
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("📁 로컬 저장소 (폴더 분리)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Audio folder
            Text("🎵 녹음파일 (MP3) 폴더", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextDark)
            if (state.safAudioDisplayPath.isNotEmpty()) {
                Text("📂 ${state.safAudioDisplayPath}", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(state.audioSaveDir, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
            Button(
                onClick = { safAudioPickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("폴더 선택", fontSize = 13.sp)
            }
            if (state.userSelectedAudioDir.isNotEmpty()) {
                Text("✅ 커스텀 경로 설정됨", fontSize = 11.sp, color = Success, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            // STT folder
            Text("📝 STT 변환 폴더", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextDark)
            if (state.safSttDisplayPath.isNotEmpty()) {
                Text("📂 ${state.safSttDisplayPath}", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(state.sttSaveDir, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
            Button(
                onClick = { safSttPickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("폴더 선택", fontSize = 13.sp)
            }
            if (state.userSelectedSttDir.isNotEmpty()) {
                Text("✅ 커스텀 경로 설정됨", fontSize = 11.sp, color = Success, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Summary folder
            Text("📋 회의록 (요약) 폴더", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextDark)
            if (state.safSummaryDisplayPath.isNotEmpty()) {
                Text("📂 ${state.safSummaryDisplayPath}", fontSize = 12.sp, color = Accent, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(state.summarySaveDir, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
            Button(
                onClick = { safSummaryPickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("폴더 선택", fontSize = 13.sp)
            }
            if (state.userSelectedSummaryDir.isNotEmpty()) {
                Text("✅ 커스텀 경로 설정됨", fontSize = 11.sp, color = Success, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // Legacy Base Directory Picker (fallback)
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("📁 기본 저장소 (레거시)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("위의 폴더 분리 설정을 사용할 것을 권장합니다.", fontSize = 12.sp, color = TextLight)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { safBasePickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("기본 폴더 선택", fontSize = 13.sp)
            }

            if (state.userSelectedBaseDir.isNotEmpty()) {
                Text("✅ 커스텀 경로 설정됨", fontSize = 12.sp, color = Success, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    // ★ v3.0: Obsidian Vault 저장 설정
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("📓 Obsidian Vault 저장", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("회의록 저장 시 Obsidian vault 폴더에 .md 파일로 자동 저장합니다.",
                fontSize = 12.sp, color = TextLight)
            Spacer(Modifier.height(8.dp))

            if (state.obsidianVaultDir.isNotEmpty()) {
                Text("✅ Obsidian 폴더 설정됨", fontSize = 12.sp, color = Success)
                Spacer(Modifier.height(4.dp))
            }

            Button(
                onClick = { obsidianPickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Obsidian vault 폴더 선택", fontSize = 13.sp)
            }

            if (state.obsidianVaultDir.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.setObsidianVaultDir("") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Obsidian 저장 해제", fontSize = 12.sp, color = Danger)
                }
            }
        }
    }

    // Google Drive Settings
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("☁ Google Drive 설정", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (state.driveSignedIn) {
                // Signed-in state
                Text("✅ ${state.driveEmail}", fontSize = 13.sp, color = Success)
                Spacer(Modifier.height(8.dp))

                // Auto-upload toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("변환 완료 후 자동 업로드", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.driveAutoUpload,
                        onCheckedChange = { viewModel.setDriveAutoUpload(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.3f))
                    )
                }
                Spacer(Modifier.height(12.dp))

                // MP3 Folder
                Text("녹음 파일 폴더:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = TextLight, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.driveMp3FolderName.ifBlank { "미설정" }, fontSize = 12.sp, color = TextLight, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.showFolderPicker("mp3") }) {
                        Text("변경", fontSize = 12.sp)
                    }
                }

                // Summary Folder
                Text("회의록 폴더:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = TextLight, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.driveTxtFolderName.ifBlank { "미설정" }, fontSize = 12.sp, color = TextLight, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.showFolderPicker("txt") }) {
                        Text("변경", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.signOutDrive() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Google 로그아웃", fontSize = 13.sp)
                }
            } else {
                // Not signed in
                Text("Google Drive에 로그인하면 녹음파일과 회의록이 자동으로 업로드됩니다.", fontSize = 12.sp, color = TextLight)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Google 로그인")
                }
            }

            if (state.driveStatus.isNotEmpty()) {
                Text(state.driveStatus, fontSize = 12.sp, color = TextLight, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun DriveFolderPickerDialog(
    folders: List<Pair<String, String>>,
    target: String,
    isLoading: Boolean,
    path: List<Pair<String, String>>,
    onSelect: (String, String) -> Unit,
    onNavigate: (String, String) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (Int) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var showNewFolder by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("$target 저장 폴더 선택", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Breadcrumb navigation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    path.forEachIndexed { index, (_, name) ->
                        if (index > 0) {
                            Text(" > ", fontSize = 12.sp, color = TextLight)
                        }
                        val isLast = index == path.size - 1
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) TextDark else Accent,
                            modifier = if (!isLast) Modifier.clickable { onNavigateToPath(index) } else Modifier
                        )
                    }
                }

                // Back button (if not at root)
                if (path.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateUp() }
                            .padding(vertical = 6.dp)
                    ) {
                        Text("⬆", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("상위 폴더로", fontSize = 13.sp, color = Accent)
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent)
                    }
                } else if (folders.isEmpty()) {
                    Text(
                        "이 폴더에 하위 폴더가 없습니다.\n이 폴더를 선택하거나 새 폴더를 만드세요.",
                        fontSize = 13.sp, color = TextLight,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 220.dp)) {
                        items(folders) { (id, name) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                // Folder icon + name — tap to navigate into
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onNavigate(id, name) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, fontSize = 14.sp)
                                }
                                // Select button
                                TextButton(
                                    onClick = { onSelect(id, name) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("선택", fontSize = 12.sp, color = Accent)
                                }
                            }
                        }
                    }
                }

                // Select current folder button (when navigated into a subfolder)
                if (path.size > 1 && !isLoading) {
                    val currentFolder = path.last()
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { onSelect(currentFolder.first, currentFolder.second) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("이 폴더 선택 (${currentFolder.second})", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (showNewFolder) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("새 폴더 이름") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    onCreateNew(newFolderName)
                                    newFolderName = ""
                                    showNewFolder = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            enabled = newFolderName.isNotBlank()
                        ) { Text("생성") }
                        TextButton(onClick = { showNewFolder = false }) { Text("취소") }
                    }
                } else {
                    OutlinedButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("새 폴더 만들기", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }
        }
    }
}
