package com.krunventures.meetingrecorder.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krunventures.meetingrecorder.MeetingApp
import com.krunventures.meetingrecorder.data.ConfigManager
import com.krunventures.meetingrecorder.data.Meeting
import com.krunventures.meetingrecorder.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

data class RecordingUiState(
    val recordingState: RecordingState = RecordingState.IDLE,
    val elapsed: String = "00:00:00",
    val amplitude: Float = 0f,
    val currentFile: String = "(없음)",
    val sttProgress: Int = 0,
    val sttStatus: String = "",
    val sttText: String = "",
    val summaryProgress: Int = 0,
    val summaryStatus: String = "",
    val summaryText: String = "",
    val metricsText: String = "",
    val saveStatus: String = "파이프라인을 실행하면 자동으로 저장됩니다.",
    val isProcessing: Boolean = false,
    val error: String? = null
)

class RecordingViewModel(app: Application) : AndroidViewModel(app) {
    private val config = ConfigManager(app)
    private val recorderManager = AudioRecorderManager(app)
    private val clovaService = ClovaService()
    private val geminiService = GeminiService()
    private val claudeService = ClaudeService()
    private val fileManager = FileManager()
    private val dao = (app as MeetingApp).database.meetingDao()

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState

    private var currentAudioFile: File? = null

    init {
        config.ensureDirs()
        // Observe recorder state
        viewModelScope.launch {
            recorderManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(recordingState = state)
            }
        }
        viewModelScope.launch {
            recorderManager.elapsedSeconds.collect {
                _uiState.value = _uiState.value.copy(elapsed = recorderManager.getElapsedString())
            }
        }
        viewModelScope.launch {
            recorderManager.amplitude.collect { amp ->
                _uiState.value = _uiState.value.copy(amplitude = amp)
            }
        }
    }

    fun startRecording() {
        val result = recorderManager.startRecording(config.audioSaveDir)
        result.onSuccess { file ->
            currentAudioFile = file
            _uiState.value = _uiState.value.copy(currentFile = file.name)
        }
        result.onFailure { e ->
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun pauseRecording() = recorderManager.pauseRecording()
    fun resumeRecording() = recorderManager.resumeRecording()

    fun stopRecording() {
        val result = recorderManager.stopRecording()
        result.onSuccess { file ->
            currentAudioFile = file
            _uiState.value = _uiState.value.copy(
                currentFile = file.name,
                saveStatus = "녹음 저장됨: ${file.name}"
            )
        }
        result.onFailure { e ->
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun setAudioFile(file: File) {
        currentAudioFile = file
        _uiState.value = _uiState.value.copy(currentFile = file.name)
    }

    fun setAudioFileFromUri(uri: Uri) {
        val context = getApplication<MeetingApp>()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val tempFile = File(config.audioSaveDir, "imported_${System.currentTimeMillis()}.m4a")
        config.audioSaveDir.mkdirs()
        tempFile.outputStream().use { inputStream.copyTo(it) }
        currentAudioFile = tempFile
        _uiState.value = _uiState.value.copy(currentFile = tempFile.name)
    }

    fun startPipeline() {
        val audioFile = currentAudioFile ?: run {
            _uiState.value = _uiState.value.copy(error = "오디오 파일을 먼저 선택하거나 녹음해주세요.")
            return
        }

        _uiState.value = _uiState.value.copy(isProcessing = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: STT
                val sttResult = runStt(audioFile)
                if (!sttResult.first) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false, error = sttResult.second
                    )
                    return@launch
                }
                val sttText = sttResult.second
                _uiState.value = _uiState.value.copy(sttText = sttText)

                // Step 2: Summary
                val sumResult = runSummary(sttText)
                if (!sumResult.first) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false, error = sumResult.second
                    )
                    return@launch
                }
                val summaryText = sumResult.second
                _uiState.value = _uiState.value.copy(summaryText = summaryText)

                // Step 3: Save files
                val baseName = fileManager.getDefaultBaseName()
                val sttFile = fileManager.saveSttText(sttText, config.summarySaveDir, "${baseName}_녹음")
                val sumFile = fileManager.saveSummaryText(summaryText, config.summarySaveDir, "${baseName}_요약")

                // Step 4: Save to DB
                val meetingId = dao.insert(Meeting(
                    createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    fileName = audioFile.name,
                    mp3LocalPath = audioFile.absolutePath,
                    sttLocalPath = sttFile.getOrNull()?.absolutePath ?: "",
                    summaryLocalPath = sumFile.getOrNull()?.absolutePath ?: "",
                    sttText = sttText,
                    summaryText = summaryText,
                    fileSizeMb = fileManager.getFileSizeMb(audioFile)
                ))

                _uiState.value = _uiState.value.copy(
                    saveStatus = "✅ 저장 완료 — ${config.audioSaveDir.absolutePath}",
                    isProcessing = false
                )

                // Step 5: Extract metrics
                if (config.geminiApiKey.isNotBlank()) {
                    val metricsResult = geminiService.extractKeyMetrics(summaryText, config.geminiApiKey)
                    if (metricsResult.success) {
                        _uiState.value = _uiState.value.copy(metricsText = metricsResult.text)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false, error = "오류: ${e.message}"
                )
            }
        }
    }

    private suspend fun runStt(audioFile: File): Pair<Boolean, String> {
        val engine = config.sttEngine
        _uiState.value = _uiState.value.copy(
            sttStatus = "${if (engine == "clova") "CLOVA Speech" else "Gemini"} STT 시작...",
            sttProgress = 0
        )

        return if (engine == "clova") {
            val result = clovaService.transcribe(
                audioFile = audioFile,
                invokeUrl = config.clovaInvokeUrl,
                secretKey = config.clovaSecretKey,
                numSpeakers = config.numSpeakers,
                onProgress = { p -> _uiState.value = _uiState.value.copy(sttProgress = p) },
                onStatus = { s -> _uiState.value = _uiState.value.copy(sttStatus = s) }
            )
            Pair(result.success, result.text)
        } else {
            val result = geminiService.transcribe(
                audioFile = audioFile,
                apiKey = config.geminiApiKey,
                numSpeakers = config.numSpeakers,
                onProgress = { p -> _uiState.value = _uiState.value.copy(sttProgress = p) },
                onStatus = { s -> _uiState.value = _uiState.value.copy(sttStatus = s) }
            )
            Pair(result.success, result.text)
        }
    }

    private suspend fun runSummary(sttText: String): Pair<Boolean, String> {
        val aiEngine = config.aiEngine
        val mode = config.summaryMode
        _uiState.value = _uiState.value.copy(
            summaryStatus = "${if (aiEngine == "claude") "Claude" else "Gemini"} 요약 시작...",
            summaryProgress = 0
        )

        return if (aiEngine == "claude" && config.claudeApiKey.isNotBlank()) {
            val result = claudeService.summarize(
                sttText = sttText,
                apiKey = config.claudeApiKey,
                summaryMode = mode,
                onProgress = { p -> _uiState.value = _uiState.value.copy(summaryProgress = p) }
            )
            Pair(result.success, result.text)
        } else {
            val result = geminiService.summarize(
                sttText = sttText,
                apiKey = config.geminiApiKey,
                summaryMode = mode,
                onProgress = { p -> _uiState.value = _uiState.value.copy(summaryProgress = p) }
            )
            Pair(result.success, result.text)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        recorderManager.release()
        super.onCleared()
    }
}
