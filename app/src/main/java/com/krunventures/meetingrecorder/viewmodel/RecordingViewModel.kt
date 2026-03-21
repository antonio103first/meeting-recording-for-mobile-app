package com.krunventures.meetingrecorder.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krunventures.meetingrecorder.MeetingApp
import com.krunventures.meetingrecorder.data.ConfigManager
import com.krunventures.meetingrecorder.data.Meeting
import com.krunventures.meetingrecorder.service.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val error: String? = null,
    // 파일이름 입력 다이얼로그
    val showFileNameDialog: Boolean = false,
    val suggestedFileName: String = "",
    // 통화 상태
    val callState: CallState = CallState.NONE,
    val callerNumber: String = "",
    val callAutoPaused: Boolean = false  // 통화로 인해 자동 일시정지된 상태
)

class RecordingViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "RecordingVM"
    }

    private val config = ConfigManager(app)
    private val recorderManager = AudioRecorderManager(app)
    private val callManager = CallManager(app)
    private val clovaService = ClovaService()
    private val geminiService = GeminiService()
    private val claudeService = ClaudeService()
    private val fileManager = FileManager()
    private val dao = (app as MeetingApp).database.meetingDao()

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState

    private var currentAudioFile: File? = null
    private var isServiceRunning = false  // 서비스 실행 상태 추적

    // 파일이름 입력 대기를 위한 continuation
    private var pendingSttText: String? = null
    private var pendingSummaryText: String? = null
    private var pendingAudioFile: File? = null

    /**
     * UI 상태를 메인 스레드에서 안전하게 업데이트
     * Dispatchers.IO 코루틴에서 직접 _uiState.value를 변경하면
     * Compose Recomposer와 충돌하여 크래시 발생 (Android 16 확인)
     */
    private suspend fun updateUiState(update: (RecordingUiState) -> RecordingUiState) {
        withContext(Dispatchers.Main) {
            _uiState.value = update(_uiState.value)
        }
    }

    // 코루틴 예외 핸들러 — 앱 크래시 방지
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = "처리 중 오류가 발생했습니다: ${throwable.message?.take(200) ?: "알 수 없는 오류"}"
            )
        }
    }

    // 알림바 액션 버튼에서 오는 Broadcast 수신
    private val notificationActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RecordingService.ACTION_PAUSE -> pauseRecording()
                RecordingService.ACTION_RESUME -> resumeRecording()
                RecordingService.ACTION_STOP -> stopRecording()
            }
        }
    }

    init {
        config.ensureDirs()
        registerNotificationReceiver()
        checkPreviousCrashLog()

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

        // Observe call state — 녹음 중 전화가 오면 자동 일시정지/재개
        viewModelScope.launch {
            callManager.callState.collect { callState ->
                val currentRecState = _uiState.value.recordingState
                val wasAutoPaused = _uiState.value.callAutoPaused

                when (callState) {
                    CallState.RINGING -> {
                        // 녹음 중에 전화가 오면 UI에 수락/거절 표시
                        _uiState.value = _uiState.value.copy(
                            callState = callState,
                            callerNumber = callManager.callerNumber.value
                        )
                    }
                    CallState.IN_CALL -> {
                        // 통화 수락 → 녹음 자동 일시정지
                        if (currentRecState == RecordingState.RECORDING) {
                            recorderManager.pauseRecording()
                            updateServiceNotification("통화 중 — 녹음 일시정지", true)
                            _uiState.value = _uiState.value.copy(
                                callState = callState, callAutoPaused = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(callState = callState)
                        }
                    }
                    CallState.CALL_ENDED -> {
                        // 통화 종료 → 자동 일시정지였으면 녹음 재개
                        if (wasAutoPaused && currentRecState == RecordingState.PAUSED) {
                            recorderManager.resumeRecording()
                            updateServiceNotification("녹음 중...", false)
                        }
                        _uiState.value = _uiState.value.copy(
                            callState = CallState.NONE,
                            callAutoPaused = false,
                            callerNumber = ""
                        )
                        callManager.resetCallState()
                    }
                    CallState.NONE -> {
                        _uiState.value = _uiState.value.copy(
                            callState = CallState.NONE,
                            callAutoPaused = false,
                            callerNumber = ""
                        )
                    }
                }
            }
        }
    }

    private fun registerNotificationReceiver() {
        val filter = IntentFilter().apply {
            addAction(RecordingService.ACTION_PAUSE)
            addAction(RecordingService.ACTION_RESUME)
            addAction(RecordingService.ACTION_STOP)
        }
        val app = getApplication<MeetingApp>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(notificationActionReceiver, filter)
        }
    }

    // ── 녹음 제어 ────────────────────────────────────────────────

    fun startRecording() {
        val result = recorderManager.startRecording(config.audioSaveDir)
        result.onSuccess { file ->
            currentAudioFile = file
            _uiState.value = _uiState.value.copy(currentFile = file.name)
            // 포그라운드 서비스 시작
            startService()
            // 전화 상태 모니터링 시작
            callManager.startMonitoring()
        }
        result.onFailure { e ->
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun pauseRecording() {
        recorderManager.pauseRecording()
        updateServiceNotification("녹음 일시정지 중", true)
    }

    fun resumeRecording() {
        recorderManager.resumeRecording()
        updateServiceNotification("녹음 중...", false)
    }

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
        // 서비스 중지 및 전화 모니터링 중지
        stopService()
        callManager.stopMonitoring()
    }

    // ── 통화 제어 ────────────────────────────────────────────────

    fun acceptCall() {
        callManager.acceptCall()
        // IN_CALL 상태는 TelephonyCallback에서 자동 전환 → 녹음 자동 일시정지
    }

    fun rejectCall() {
        callManager.rejectCall()
        // IDLE 상태로 전환 → 녹음 계속 유지
    }

    // ── 서비스 관리 ──────────────────────────────────────────────

    private fun startService() {
        try {
            val app = getApplication<MeetingApp>()
            val intent = Intent(app, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
            isServiceRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service", e)
        }
    }

    private fun stopService() {
        try {
            val app = getApplication<MeetingApp>()
            app.stopService(Intent(app, RecordingService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop service", e)
        } finally {
            isServiceRunning = false
        }
    }

    private fun updateServiceNotification(text: String, isPaused: Boolean) {
        // 서비스가 실행 중이 아니면 호출하지 않음 (Android 12+ 크래시 방지)
        if (!isServiceRunning) {
            Log.w(TAG, "Skipping notification update — service not running")
            return
        }
        try {
            val app = getApplication<MeetingApp>()
            val intent = Intent(app, RecordingService::class.java).apply {
                action = RecordingService.ACTION_UPDATE_NOTIFICATION
                putExtra(RecordingService.EXTRA_STATUS_TEXT, text)
                putExtra(RecordingService.EXTRA_IS_PAUSED, isPaused)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update service notification", e)
        }
    }

    // ── 파일 및 파이프라인 ───────────────────────────────────────

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

        // ── API 키 사전 검증 ──────────────────────────────────────
        val sttEngine = config.sttEngine
        val aiEngine = config.aiEngine

        // STT 엔진 키 검증
        if (sttEngine == "clova") {
            if (config.clovaInvokeUrl.isBlank() || config.clovaSecretKey.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "CLOVA Speech API 키가 설정되지 않았습니다.\n\n" +
                            "설정 탭에서 CLOVA Speech Invoke URL과 Secret Key를 입력해주세요.\n" +
                            "(또는 STT 엔진을 Gemini로 변경해주세요.)"
                )
                return
            }
        } else {
            if (config.geminiApiKey.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "Gemini API 키가 설정되지 않았습니다.\n\n" +
                            "설정 탭에서 Gemini API 키를 입력해주세요."
                )
                return
            }
        }

        // 요약 엔진 키 검증
        if (aiEngine == "claude" && config.claudeApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Claude API 키가 설정되지 않았습니다.\n\n" +
                        "설정 탭에서 Claude API 키를 입력하거나,\n요약 엔진을 Gemini로 변경해주세요."
            )
            return
        }
        if (aiEngine == "gemini" && config.geminiApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Gemini API 키가 설정되지 않았습니다.\n\n" +
                        "설정 탭에서 Gemini API 키를 입력해주세요."
            )
            return
        }

        // 기본 파일 존재 확인 (메인 스레드에서 가벼운 체크만)
        if (!audioFile.exists()) {
            _uiState.value = _uiState.value.copy(
                error = "녹음 파일을 찾을 수 없습니다: ${audioFile.name}\n\n" +
                        "다시 녹음하거나 파일을 선택해주세요."
            )
            return
        }

        _uiState.value = _uiState.value.copy(isProcessing = true, error = null)
        Log.d(TAG, "Pipeline starting — file: ${audioFile.absolutePath}, size: ${audioFile.length()}")

        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                // 파일 접근성 검증 (IO 스레드에서 실행)
                if (!audioFile.canRead()) {
                    updateUiState { it.copy(
                        isProcessing = false,
                        error = "녹음 파일에 접근할 수 없습니다.\n잠시 후 다시 시도해주세요."
                    ) }
                    return@launch
                }
                try {
                    audioFile.inputStream().use { it.read() }
                } catch (e: Exception) {
                    Log.e(TAG, "File lock check failed: ${e.message}", e)
                    updateUiState { it.copy(
                        isProcessing = false,
                        error = "녹음 파일이 아직 처리 중입니다.\n2~3초 후 다시 시도해주세요.\n(${e.message})"
                    ) }
                    return@launch
                }
                Log.d(TAG, "File validation passed")
                // Step 1: STT
                Log.d(TAG, "Pipeline started — STT engine: $sttEngine, file: ${audioFile.name}")
                val sttResult = runStt(audioFile)
                if (!sttResult.first) {
                    updateUiState { it.copy(
                        isProcessing = false, error = "STT 변환 실패:\n${sttResult.second}"
                    ) }
                    return@launch
                }
                val sttText = sttResult.second
                updateUiState { it.copy(sttText = sttText) }
                Log.d(TAG, "STT completed — ${sttText.length} chars")

                // Step 2: Summary
                val sumResult = runSummary(sttText)
                if (!sumResult.first) {
                    updateUiState { it.copy(
                        isProcessing = false, error = "요약 실패:\n${sumResult.second}"
                    ) }
                    return@launch
                }
                val summaryText = sumResult.second
                updateUiState { it.copy(summaryText = summaryText) }
                Log.d(TAG, "Summary completed — ${summaryText.length} chars")

                // Step 3: Extract metrics (optional — 실패해도 무시, 저장 대기 전 실행)
                try {
                    if (config.geminiApiKey.isNotBlank()) {
                        val metricsResult = geminiService.extractKeyMetrics(summaryText, config.geminiApiKey)
                        if (metricsResult.success) {
                            updateUiState { it.copy(metricsText = metricsResult.text) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Metrics extraction failed", e)
                }

                // Step 4: 파일이름 입력 다이얼로그 표시 → 사용자 확인 후 저장
                val baseName = fileManager.getDefaultBaseName()
                pendingSttText = sttText
                pendingSummaryText = summaryText
                pendingAudioFile = audioFile
                updateUiState { it.copy(
                    showFileNameDialog = true,
                    suggestedFileName = "${baseName}_회의록",
                    saveStatus = "파일이름을 입력해주세요..."
                ) }
                // 파이프라인은 여기서 중단 — confirmFileName()에서 저장+업로드 이어짐
            } catch (e: Throwable) {
                Log.e(TAG, "Pipeline error: ${e.javaClass.simpleName}", e)
                val errorMsg = when (e) {
                    is OutOfMemoryError -> "메모리 부족 오류가 발생했습니다.\n파일이 너무 크거나 기기 메모리가 부족합니다."
                    else -> "처리 중 오류 발생:\n${e.message?.take(300) ?: "알 수 없는 오류"}"
                }
                updateUiState { it.copy(
                    isProcessing = false,
                    error = errorMsg
                ) }
            }
        }
    }

    /**
     * 사용자가 파일이름을 확인/수정하고 저장 버튼을 눌렀을 때 호출
     * 파이프라인의 저장 + Drive 업로드를 이어서 실행
     */
    fun confirmFileName(fileName: String) {
        val sttText = pendingSttText ?: return
        val summaryText = pendingSummaryText ?: return
        val audioFile = pendingAudioFile ?: return

        _uiState.value = _uiState.value.copy(showFileNameDialog = false)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 5: Save files (통합 파일: 회의록요약 + STT변환)
                val combinedFile = try {
                    fileManager.saveCombinedSummary(summaryText, sttText, config.summarySaveDir, fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save combined summary file", e)
                    Result.failure(e)
                }
                Log.d(TAG, "Combined summary file saved: ${combinedFile.getOrNull()?.absolutePath}")

                // Step 6: Save to DB
                try {
                    dao.insert(Meeting(
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        fileName = audioFile.name,
                        mp3LocalPath = audioFile.absolutePath,
                        sttLocalPath = combinedFile.getOrNull()?.absolutePath ?: "",
                        summaryLocalPath = combinedFile.getOrNull()?.absolutePath ?: "",
                        sttText = sttText,
                        summaryText = summaryText,
                        fileSizeMb = fileManager.getFileSizeMb(audioFile)
                    ))
                    Log.d(TAG, "Meeting saved to DB")
                } catch (e: Exception) {
                    Log.e(TAG, "DB insert failed", e)
                }

                // Step 7: Google Drive 자동 업로드
                if (config.driveAutoUpload) {
                    try {
                        updateUiState { it.copy(saveStatus = "☁ Google Drive 업로드 중...") }
                        val driveService = GoogleDriveService(getApplication())
                        if (driveService.initFromLastAccount()) {
                            val mp3FolderId = config.driveMp3FolderId
                            val txtFolderId = config.driveTxtFolderId
                            if (mp3FolderId.isNotBlank() || txtFolderId.isNotBlank()) {
                                val results = driveService.uploadMeetingFiles(
                                    mp3File = audioFile,
                                    sttFile = null,
                                    summaryFile = combinedFile.getOrNull(),
                                    mp3FolderId = mp3FolderId,
                                    txtFolderId = txtFolderId
                                )
                                val uploadStatus = buildString {
                                    results.forEach { (key, result) ->
                                        if (result.success) append("✅ $key ") else append("❌ $key ")
                                    }
                                }
                                Log.d(TAG, "Drive upload results: $uploadStatus")
                                updateUiState { it.copy(
                                    saveStatus = "✅ 저장 완료 + Drive 업로드 ($uploadStatus)",
                                    isProcessing = false
                                ) }
                            } else {
                                updateUiState { it.copy(
                                    saveStatus = "✅ 저장 완료 (Drive 폴더 미설정)",
                                    isProcessing = false
                                ) }
                            }
                        } else {
                            updateUiState { it.copy(
                                saveStatus = "✅ 저장 완료 (Drive 미연결)",
                                isProcessing = false
                            ) }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Drive upload failed", e)
                        updateUiState { it.copy(
                            saveStatus = "✅ 저장 완료 (Drive 업로드 실패: ${e.message?.take(100)})",
                            isProcessing = false
                        ) }
                    }
                } else {
                    updateUiState { it.copy(
                        saveStatus = "✅ 저장 완료 — ${config.summarySaveDir.absolutePath}",
                        isProcessing = false
                    ) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Save pipeline error", e)
                updateUiState { it.copy(
                    isProcessing = false,
                    error = "저장 중 오류 발생:\n${e.message?.take(300)}"
                ) }
            } finally {
                pendingSttText = null
                pendingSummaryText = null
                pendingAudioFile = null
            }
        }
    }

    /**
     * 파일이름 다이얼로그에서 취소
     */
    fun cancelFileName() {
        _uiState.value = _uiState.value.copy(
            showFileNameDialog = false,
            isProcessing = false,
            saveStatus = "저장 취소됨"
        )
        pendingSttText = null
        pendingSummaryText = null
        pendingAudioFile = null
    }

    private suspend fun runStt(audioFile: File): Pair<Boolean, String> {
        val engine = config.sttEngine
        updateUiState { it.copy(
            sttStatus = "${if (engine == "clova") "CLOVA Speech" else "Gemini"} STT 시작...",
            sttProgress = 0
        ) }

        val safeOnProgress: (Int) -> Unit = { p ->
            try {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(sttProgress = p)
                }
            } catch (e: Exception) { Log.e(TAG, "STT onProgress callback error", e) }
        }
        val safeOnStatus: (String) -> Unit = { s ->
            try {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(sttStatus = s)
                }
            } catch (e: Exception) { Log.e(TAG, "STT onStatus callback error", e) }
        }

        return try {
            if (engine == "clova") {
                val result = clovaService.transcribe(
                    audioFile = audioFile,
                    invokeUrl = config.clovaInvokeUrl,
                    secretKey = config.clovaSecretKey,
                    numSpeakers = config.numSpeakers,
                    onProgress = safeOnProgress,
                    onStatus = safeOnStatus
                )
                Pair(result.success, result.text)
            } else {
                val result = geminiService.transcribe(
                    audioFile = audioFile,
                    apiKey = config.geminiApiKey,
                    numSpeakers = config.numSpeakers,
                    onProgress = safeOnProgress,
                    onStatus = safeOnStatus
                )
                Pair(result.success, result.text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT execution failed: ${e.message}", e)
            Pair(false, "STT 변환 중 오류: ${e.message?.take(200) ?: "알 수 없는 오류"}")
        }
    }

    private suspend fun runSummary(sttText: String): Pair<Boolean, String> {
        val aiEngine = config.aiEngine
        val mode = config.summaryMode
        updateUiState { it.copy(
            summaryStatus = "${if (aiEngine == "claude") "Claude" else "Gemini"} 요약 시작...",
            summaryProgress = 0
        ) }

        val safeOnProgress: (Int) -> Unit = { p ->
            try {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(summaryProgress = p)
                }
            } catch (e: Exception) { Log.e(TAG, "Summary onProgress callback error", e) }
        }

        return try {
            if (aiEngine == "claude" && config.claudeApiKey.isNotBlank()) {
                val result = claudeService.summarize(
                    sttText = sttText,
                    apiKey = config.claudeApiKey,
                    summaryMode = mode,
                    onProgress = safeOnProgress
                )
                Pair(result.success, result.text)
            } else {
                val result = geminiService.summarize(
                    sttText = sttText,
                    apiKey = config.geminiApiKey,
                    summaryMode = mode,
                    onProgress = safeOnProgress
                )
                Pair(result.success, result.text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Summary execution failed: ${e.message}", e)
            Pair(false, "요약 중 오류: ${e.message?.take(200) ?: "알 수 없는 오류"}")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 이전 크래시 로그가 있으면 앱 실행 시 에러 다이얼로그로 표시
     * 표시 후 로그 파일을 삭제하여 다음 실행 시 중복 표시 방지
     */
    private fun checkPreviousCrashLog() {
        try {
            val app = getApplication<MeetingApp>()
            val logDir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir
            val logFile = File(logDir, "crash_log.txt")
            if (logFile.exists() && logFile.length() > 0) {
                // 앞부분(예외 종류 + 메시지)을 먼저 표시
                val logContent = logFile.readText().take(3000)
                _uiState.value = _uiState.value.copy(
                    error = "⚠️ 이전 실행에서 크래시가 발생했습니다.\n\n$logContent"
                )
                logFile.delete()
                Log.w(TAG, "Previous crash log displayed and deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check crash log", e)
        }
    }

    override fun onCleared() {
        try {
            getApplication<MeetingApp>().unregisterReceiver(notificationActionReceiver)
        } catch (_: Exception) {}
        callManager.release()
        recorderManager.release()
        super.onCleared()
    }
}
