package com.krunventures.meetingrecorder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krunventures.meetingrecorder.data.ConfigManager
import com.krunventures.meetingrecorder.service.ClovaService
import com.krunventures.meetingrecorder.service.ClaudeService
import com.krunventures.meetingrecorder.service.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val geminiApiKey: String = "",
    val claudeApiKey: String = "",
    val clovaInvokeUrl: String = "",
    val clovaSecretKey: String = "",
    val sttEngine: String = "clova",
    val aiEngine: String = "gemini",
    val summaryMode: String = "speaker",
    val audioSaveDir: String = "",
    val summarySaveDir: String = "",
    val geminiStatus: String = "",
    val clovaStatus: String = "",
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val config = ConfigManager(app)
    private val geminiService = GeminiService()
    private val clovaService = ClovaService()
    private val claudeService = ClaudeService()

    private val _uiState = MutableStateFlow(SettingsUiState(
        geminiApiKey = config.geminiApiKey,
        claudeApiKey = config.claudeApiKey,
        clovaInvokeUrl = config.clovaInvokeUrl,
        clovaSecretKey = config.clovaSecretKey,
        sttEngine = config.sttEngine,
        aiEngine = config.aiEngine,
        summaryMode = config.summaryMode,
        audioSaveDir = config.audioSaveDir.absolutePath,
        summarySaveDir = config.summarySaveDir.absolutePath,
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun updateGeminiKey(key: String) { _uiState.value = _uiState.value.copy(geminiApiKey = key) }
    fun updateClaudeKey(key: String) { _uiState.value = _uiState.value.copy(claudeApiKey = key) }
    fun updateClovaUrl(url: String) { _uiState.value = _uiState.value.copy(clovaInvokeUrl = url) }
    fun updateClovaKey(key: String) { _uiState.value = _uiState.value.copy(clovaSecretKey = key) }

    fun saveGeminiKey() { config.geminiApiKey = _uiState.value.geminiApiKey }
    fun saveClaudeKey() { config.claudeApiKey = _uiState.value.claudeApiKey }
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

    fun testGemini() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(geminiStatus = "테스트 중...")
            val result = geminiService.testConnection(_uiState.value.geminiApiKey)
            _uiState.value = _uiState.value.copy(geminiStatus = result.text)
        }
    }

    fun testClova() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(clovaStatus = "테스트 중...")
            val result = clovaService.testConnection(_uiState.value.clovaInvokeUrl, _uiState.value.clovaSecretKey)
            _uiState.value = _uiState.value.copy(clovaStatus = if (result.success) result.text else "❌ ${result.text}")
        }
    }
}
