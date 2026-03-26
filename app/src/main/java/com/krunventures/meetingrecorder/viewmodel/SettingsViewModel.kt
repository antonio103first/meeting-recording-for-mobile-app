package com.krunventures.meetingrecorder.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.krunventures.meetingrecorder.data.ConfigManager
import com.krunventures.meetingrecorder.service.ChatGptService
import com.krunventures.meetingrecorder.service.ClovaService
import com.krunventures.meetingrecorder.service.ClaudeService
import com.krunventures.meetingrecorder.service.GeminiService
import com.krunventures.meetingrecorder.service.GoogleDriveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val geminiApiKey: String = "",
    val claudeApiKey: String = "",
    val chatGptApiKey: String = "",
    val clovaInvokeUrl: String = "",
    val clovaSecretKey: String = "",
    val sttEngine: String = "clova",
    val aiEngine: String = "gemini",
    val summaryMode: String = "speaker",
    val numSpeakers: Int = 2,
    val audioSaveDir: String = "",
    val summarySaveDir: String = "",
    val userSelectedBaseDir: String = "",
    val geminiStatus: String = "",
    val clovaStatus: String = "",
    val chatGptStatus: String = "",
    val claudeStatus: String = "",
    // Google Drive
    val driveSignedIn: Boolean = false,
    val driveEmail: String = "",
    val driveAutoUpload: Boolean = true,
    val driveMp3FolderName: String = "",
    val driveTxtFolderName: String = "",
    val driveStatus: String = "",
    val driveFolders: List<Pair<String, String>> = emptyList(), // id, name
    val showFolderPicker: Boolean = false,
    val folderPickerTarget: String = "", // "mp3" or "txt"
    val folderPickerLoading: Boolean = false,
    val folderPickerParentId: String = "root",
    val folderPickerPath: List<Pair<String, String>> = listOf("root" to "내 드라이브"), // id, name breadcrumb
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "SettingsVM"
    }

    private val config = ConfigManager(app)
    private val geminiService = GeminiService()
    private val clovaService = ClovaService()
    private val claudeService = ClaudeService()
    private val chatGptService = ChatGptService()
    val driveService = GoogleDriveService(app)

    private val _uiState = MutableStateFlow(SettingsUiState(
        geminiApiKey = config.geminiApiKey,
        claudeApiKey = config.claudeApiKey,
        chatGptApiKey = config.chatGptApiKey,
        clovaInvokeUrl = config.clovaInvokeUrl,
        clovaSecretKey = config.clovaSecretKey,
        sttEngine = config.sttEngine,
        aiEngine = config.aiEngine,
        summaryMode = config.summaryMode,
        numSpeakers = config.numSpeakers,
        audioSaveDir = config.audioSaveDir.absolutePath,
        summarySaveDir = config.summarySaveDir.absolutePath,
        userSelectedBaseDir = config.userSelectedBaseDir,
        driveSignedIn = driveService.isSignedIn(),
        driveEmail = driveService.getAccountEmail(),
        driveAutoUpload = config.driveAutoUpload,
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        // 이미 로그인 상태면 폴더명 로드
        if (driveService.isSignedIn()) {
            driveService.initFromLastAccount()
            loadDriveFolderNames()
        }
    }

    // === API Key Management ===
    fun updateGeminiKey(key: String) { _uiState.value = _uiState.value.copy(geminiApiKey = key) }
    fun updateClaudeKey(key: String) { _uiState.value = _uiState.value.copy(claudeApiKey = key) }
    fun updateClovaUrl(url: String) { _uiState.value = _uiState.value.copy(clovaInvokeUrl = url) }
    fun updateClovaKey(key: String) { _uiState.value = _uiState.value.copy(clovaSecretKey = key) }
    fun updateChatGptKey(key: String) { _uiState.value = _uiState.value.copy(chatGptApiKey = key) }

    fun saveGeminiKey() { config.geminiApiKey = _uiState.value.geminiApiKey }
    fun saveClaudeKey() { config.claudeApiKey = _uiState.value.claudeApiKey }
    fun saveChatGptKey() { config.chatGptApiKey = _uiState.value.chatGptApiKey }
    fun saveClovaKeys() {
        config.clovaInvokeUrl = _uiState.value.clovaInvokeUrl
        config.clovaSecretKey = _uiState.value.clovaSecretKey
    }

    fun setSttEngine(engine: String) {
        config.sttEngine = engine
        _uiState.value = _uiState.value.copy(sttEngine = engine)
    }
    fun setAiEngine(engine: String) {
        config.aiEngine = engine
        _uiState.value = _uiState.value.copy(aiEngine = engine)
    }
    fun setSummaryMode(mode: String) {
        config.summaryMode = mode
        _uiState.value = _uiState.value.copy(summaryMode = mode)
    }

    fun setNumSpeakers(num: Int) {
        config.numSpeakers = num
        _uiState.value = _uiState.value.copy(numSpeakers = num)
    }

    fun setUserSelectedBaseDir(uri: String) {
        config.userSelectedBaseDir = uri
        _uiState.value = _uiState.value.copy(
            userSelectedBaseDir = uri,
            audioSaveDir = config.audioSaveDir.absolutePath,
            summarySaveDir = config.summarySaveDir.absolutePath
        )
    }

    fun testGemini() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(geminiStatus = "테스트 중...")
            }
            val result = geminiService.testConnection(_uiState.value.geminiApiKey)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(geminiStatus = result.text)
            }
        }
    }

    fun testClova() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(clovaStatus = "테스트 중...")
            }
            val result = clovaService.testConnection(_uiState.value.clovaInvokeUrl, _uiState.value.clovaSecretKey)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(clovaStatus = if (result.success) result.text else "❌ ${result.text}")
            }
        }
    }

    fun testChatGpt() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(chatGptStatus = "테스트 중...")
            }
            val result = chatGptService.testConnection(_uiState.value.chatGptApiKey)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(chatGptStatus = result.text)
            }
        }
    }

    fun testClaude() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(claudeStatus = "테스트 중...")
            }
            val result = claudeService.testConnection(_uiState.value.claudeApiKey)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(claudeStatus = result.text)
            }
        }
    }

    // === Google Drive ===
    fun getSignInIntent(): Intent = driveService.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                val success = driveService.handleSignInResult(account)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        driveSignedIn = true,
                        driveEmail = account.email ?: "",
                        driveStatus = "✅ Google Drive 연결 완료"
                    )
                    // 기본 폴더 자동 생성
                    initDefaultDriveFolders()
                } else {
                    _uiState.value = _uiState.value.copy(
                        driveStatus = "❌ Drive 서비스 초기화 실패"
                    )
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed: ${e.statusCode}", e)
                _uiState.value = _uiState.value.copy(
                    driveStatus = "❌ 로그인 실패 (코드: ${e.statusCode})"
                )
            }
        }
    }

    fun signOutDrive() {
        viewModelScope.launch {
            driveService.signOut()
            config.driveMp3FolderId = ""
            config.driveTxtFolderId = ""
            _uiState.value = _uiState.value.copy(
                driveSignedIn = false,
                driveEmail = "",
                driveMp3FolderName = "",
                driveTxtFolderName = "",
                driveStatus = "로그아웃 완료"
            )
        }
    }

    fun setDriveAutoUpload(enabled: Boolean) {
        config.driveAutoUpload = enabled
        _uiState.value = _uiState.value.copy(driveAutoUpload = enabled)
    }

    private fun initDefaultDriveFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (mp3Result, txtResult) = driveService.initDriveFolders("회의녹음_음성파일", "회의녹음_회의록")
                if (mp3Result.success) {
                    config.driveMp3FolderId = mp3Result.id
                }
                if (txtResult.success) {
                    config.driveTxtFolderId = txtResult.id
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        driveMp3FolderName = if (mp3Result.success) "회의녹음_음성파일" else "설정 필요",
                        driveTxtFolderName = if (txtResult.success) "회의녹음_회의록" else "설정 필요",
                        driveStatus = "✅ Drive 폴더 준비 완료"
                    )
                }
                Log.d(TAG, "Drive folders initialized: mp3=${mp3Result.id}, txt=${txtResult.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init Drive folders", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        driveStatus = "❌ 폴더 생성 실패: ${e.message?.take(100)}"
                    )
                }
            }
        }
    }

    private fun loadDriveFolderNames() {
        viewModelScope.launch(Dispatchers.IO) {
            val mp3Id = config.driveMp3FolderId
            val txtId = config.driveTxtFolderId
            val mp3Name = if (mp3Id.isNotBlank()) driveService.getFolderName(mp3Id) else ""
            val txtName = if (txtId.isNotBlank()) driveService.getFolderName(txtId) else ""
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    driveMp3FolderName = mp3Name.ifBlank { "미설정" },
                    driveTxtFolderName = txtName.ifBlank { "미설정" }
                )
            }
        }
    }

    fun showFolderPicker(target: String) {
        _uiState.value = _uiState.value.copy(
            showFolderPicker = true,
            folderPickerTarget = target,
            folderPickerLoading = true,
            folderPickerParentId = "root",
            folderPickerPath = listOf("root" to "내 드라이브"),
            driveFolders = emptyList()
        )
        loadFoldersForPicker("root")
    }

    fun navigateToFolder(folderId: String, folderName: String) {
        val currentPath = _uiState.value.folderPickerPath.toMutableList()
        currentPath.add(folderId to folderName)
        _uiState.value = _uiState.value.copy(
            folderPickerParentId = folderId,
            folderPickerPath = currentPath,
            folderPickerLoading = true,
            driveFolders = emptyList()
        )
        loadFoldersForPicker(folderId)
    }

    fun navigateUp() {
        val currentPath = _uiState.value.folderPickerPath
        if (currentPath.size <= 1) return
        val newPath = currentPath.dropLast(1)
        val parentId = newPath.last().first
        _uiState.value = _uiState.value.copy(
            folderPickerParentId = parentId,
            folderPickerPath = newPath,
            folderPickerLoading = true,
            driveFolders = emptyList()
        )
        loadFoldersForPicker(parentId)
    }

    fun navigateToPathIndex(index: Int) {
        val currentPath = _uiState.value.folderPickerPath
        if (index >= currentPath.size - 1) return
        val newPath = currentPath.take(index + 1)
        val parentId = newPath.last().first
        _uiState.value = _uiState.value.copy(
            folderPickerParentId = parentId,
            folderPickerPath = newPath,
            folderPickerLoading = true,
            driveFolders = emptyList()
        )
        loadFoldersForPicker(parentId)
    }

    private fun loadFoldersForPicker(parentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folders = driveService.listFolders(parentId)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    driveFolders = folders,
                    folderPickerLoading = false
                )
            }
        }
    }

    fun dismissFolderPicker() {
        _uiState.value = _uiState.value.copy(showFolderPicker = false)
    }

    fun selectDriveFolder(folderId: String, folderName: String) {
        val target = _uiState.value.folderPickerTarget
        if (target == "mp3") {
            config.driveMp3FolderId = folderId
            _uiState.value = _uiState.value.copy(
                driveMp3FolderName = folderName,
                showFolderPicker = false
            )
        } else if (target == "txt") {
            config.driveTxtFolderId = folderId
            _uiState.value = _uiState.value.copy(
                driveTxtFolderName = folderName,
                showFolderPicker = false
            )
        }
    }

    fun createNewDriveFolder(folderName: String) {
        val parentId = _uiState.value.folderPickerParentId
        viewModelScope.launch(Dispatchers.IO) {
            val result = driveService.ensureFolder(folderName, parentId)
            if (result.success) {
                // Refresh folder list after creation
                val folders = driveService.listFolders(parentId)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(driveFolders = folders)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        driveStatus = "❌ 폴더 생성 실패: ${result.message}"
                    )
                }
            }
        }
    }
}
