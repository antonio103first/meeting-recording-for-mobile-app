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
import org.json.JSONObject
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
    val callAutoPaused: Boolean = false,  // 통화로 인해 자동 일시정지된 상태
    // V2.0: 재요약 기능
    val selectedSttFile: String = "",  // selected STT file name for resummarization
    val selectedSttText: String = "",  // loaded STT text content
    val showResummarizeSheet: Boolean = false,  // show/hide summary mode BottomSheet
    val resummarizeProgress: Int = 0,  // progress for resummarize
    val resummarizeStatus: String = ""  // status for resummarize
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
    private val chatGptService = ChatGptService()
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
    // 즉시 저장된 녹음파일 (confirmFileName에서 rename 시 사용)
    private var savedRecordingFile: File? = null
    // V2.0: 재요약 기능 — 로드된 STT 파일
    private var loadedSttFile: File? = null

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
            try {
                when (intent?.action) {
                    RecordingService.ACTION_PAUSE -> pauseRecording()
                    RecordingService.ACTION_RESUME -> resumeRecording()
                    RecordingService.ACTION_STOP -> stopRecording()
                    // 사용자가 최근 앱 목록에서 앱 스와이프 → 녹음파일 즉시 저장
                    RecordingService::class.java.name + ".TASK_REMOVED" -> {
                        Log.w(TAG, "App task removed by user. Saving recording immediately.")
                        currentAudioFile?.let { saveRecordingImmediately(it) }
                    }
                    // 시스템 저메모리 상태 → 녹음 일시정지
                    RecordingService::class.java.name + ".LOW_MEMORY" -> {
                        Log.w(TAG, "System low memory. Pausing recording to free resources.")
                        if (_uiState.value.recordingState == RecordingState.RECORDING) {
                            pauseRecording()
                            _uiState.value = _uiState.value.copy(
                                error = "⚠️ 시스템 메모리 부족으로 녹음을 일시정지했습니다. 메모리 정리 후 재개해주세요."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in notificationActionReceiver: ${e.message}", e)
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

        // 오디오 포커스 손실 감시 — 카메라, 비디오콜 등이 마이크 사용 시
        viewModelScope.launch {
            recorderManager.audioFocusLost.collect { focusLost ->
                if (focusLost) {
                    updateUiState { it.copy(
                        error = "⚠️ 다른 앱이 마이크를 사용 중입니다. 카메라, 비디오콜을 종료하면 녹음을 재개할 수 있습니다."
                    ) }
                }
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
            // 서비스 자동 신호
            addAction(RecordingService::class.java.name + ".TASK_REMOVED")
            addAction(RecordingService::class.java.name + ".LOW_MEMORY")
        }
        val app = getApplication<MeetingApp>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(notificationActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
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
                saveStatus = "녹음 정지 — 파일 저장 중..."
            )
            // ★ V2.0: 녹음파일 즉시 저장 + Google Drive 업로드 (데이터 유실 방지)
            saveRecordingImmediately(file)
        }
        result.onFailure { e ->
            _uiState.value = _uiState.value.copy(error = e.message)
        }
        // 서비스 중지 및 전화 모니터링 중지
        stopService()
        callManager.stopMonitoring()
    }

    /**
     * V2.0: 녹음 정지 즉시 녹음파일을 저장 디렉토리에 복사하고 Drive에 업로드
     * 파이프라인(STT→요약)과 독립적으로 실행 — 앱 크래시/종료에도 녹음파일 보존
     */
    private fun saveRecordingImmediately(audioFile: File) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                // 1) 로컬 즉시 저장
                val saveResult = fileManager.saveRecordingImmediately(audioFile, config.audioSaveDir)
                val savedFile = saveResult.getOrNull()
                if (savedFile != null) {
                    savedRecordingFile = savedFile
                    Log.d(TAG, "Recording saved immediately: ${savedFile.absolutePath}")
                    updateUiState { it.copy(
                        saveStatus = "✅ 녹음파일 저장됨: ${savedFile.name}"
                    ) }

                    // 알림 표시
                    NotificationHelper.notifyRecordingSaved(getApplication(), savedFile.name)

                    // 2) Google Drive 업로드 (녹음파일만 먼저)
                    if (config.driveAutoUpload) {
                        try {
                            updateUiState { it.copy(saveStatus = "✅ 녹음 저장 완료 — ☁ Drive 업로드 중...") }
                            val driveService = GoogleDriveService(getApplication())
                            if (driveService.initFromLastAccount()) {
                                val mp3FolderId = config.driveMp3FolderId
                                if (mp3FolderId.isNotBlank()) {
                                    val results = driveService.uploadMeetingFiles(
                                        mp3File = savedFile,
                                        sttFile = null,
                                        summaryFile = null,
                                        mp3FolderId = mp3FolderId,
                                        txtFolderId = ""
                                    )
                                    val ok = results.values.all { it.success }
                                    updateUiState { it.copy(
                                        saveStatus = if (ok) "✅ 녹음 저장 + Drive 업로드 완료"
                                                     else "✅ 녹음 저장 완료 (Drive 일부 실패)"
                                    ) }
                                    Log.d(TAG, "Drive upload (recording): ${results.entries.joinToString { "${it.key}=${it.value.success}" }}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Drive upload (recording) failed", e)
                            updateUiState { it.copy(
                                saveStatus = "✅ 녹음 저장 완료 (Drive 업로드 실패)"
                            ) }
                        }
                    }
                } else {
                    Log.e(TAG, "saveRecordingImmediately failed: ${saveResult.exceptionOrNull()?.message}")
                    updateUiState { it.copy(
                        saveStatus = "⚠️ 녹음파일 즉시 저장 실패 — 파이프라인 완료 후 저장됩니다."
                    ) }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "saveRecordingImmediately error", e)
            }
        }
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
        when (sttEngine) {
            "clova" -> {
                if (config.clovaInvokeUrl.isBlank() || config.clovaSecretKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        error = "CLOVA Speech API 키가 설정되지 않았습니다.\n\n" +
                                "설정 탭에서 CLOVA Speech Invoke URL과 Secret Key를 입력해주세요."
                    )
                    return
                }
            }
            "whisper" -> {
                if (config.chatGptApiKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        error = "OpenAI API 키가 설정되지 않았습니다.\n\n" +
                                "설정 탭에서 OpenAI API 키를 입력해주세요."
                    )
                    return
                }
            }
            else -> { // gemini
                if (config.geminiApiKey.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        error = "Gemini API 키가 설정되지 않았습니다.\n\n" +
                                "설정 탭에서 Gemini API 키를 입력해주세요."
                    )
                    return
                }
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
        if (aiEngine == "chatgpt" && config.chatGptApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "OpenAI API 키가 설정되지 않았습니다.\n\n" +
                        "설정 탭에서 OpenAI API 키를 입력해주세요."
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

                // ★ V2.0: STT 변환 완료 알림
                NotificationHelper.notifySttComplete(getApplication(), audioFile.name)

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

                // ★ V2.0: AI 요약 완료 알림
                NotificationHelper.notifySummaryComplete(getApplication(), audioFile.name)

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
        val audioFile = pendingAudioFile  // nullable — null in resummarize mode

        _uiState.value = _uiState.value.copy(showFileNameDialog = false)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ★ V2.0: 즉시 저장된 녹음파일 이름 변경 (REC_임시이름 → 사용자 입력 이름)
                val finalAudioFile = if (audioFile != null) {
                    try {
                        val saved = savedRecordingFile
                        if (saved != null && saved.exists()) {
                            val renamed = fileManager.renameRecordingFile(saved, fileName)
                            renamed.getOrNull()?.also {
                                savedRecordingFile = it
                                Log.d(TAG, "Recording file renamed: ${saved.name} → ${it.name}")
                            } ?: saved
                        } else {
                            // 즉시 저장이 실패했던 경우 — 여기서 복사 저장
                            val copyResult = fileManager.copyAudioToSaveDir(audioFile, config.audioSaveDir, fileName)
                            copyResult.getOrNull() ?: audioFile
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Recording file rename failed", e)
                        audioFile  // 폴백: 원본 파일 사용
                    }
                } else {
                    null  // 재요약 모드: 오디오 파일 없음
                }

                // Step 5: Save files (통합 파일: 회의록요약 + STT변환)
                val combinedFile = try {
                    fileManager.saveCombinedSummary(summaryText, sttText, config.summarySaveDir, fileName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save combined summary file", e)
                    Result.failure(e)
                }
                Log.d(TAG, "Combined summary file saved: ${combinedFile.getOrNull()?.absolutePath}")

                // Step 6: Save to DB (최종 경로 반영)
                try {
                    dao.insert(Meeting(
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        fileName = finalAudioFile?.name ?: fileName,  // null check for resummarize mode
                        mp3LocalPath = finalAudioFile?.absolutePath ?: "",  // empty path for resummarize
                        sttLocalPath = combinedFile.getOrNull()?.absolutePath ?: "",
                        summaryLocalPath = combinedFile.getOrNull()?.absolutePath ?: "",
                        sttText = sttText,
                        summaryText = summaryText,
                        fileSizeMb = if (finalAudioFile != null) fileManager.getFileSizeMb(finalAudioFile) else 0.0
                    ))
                    Log.d(TAG, "Meeting saved to DB")
                } catch (e: Exception) {
                    Log.e(TAG, "DB insert failed", e)
                }

                // Step 7: Google Drive 자동 업로드 (회의록 파일만 — 녹음파일은 stopRecording에서 이미 업로드)
                if (config.driveAutoUpload) {
                    try {
                        updateUiState { it.copy(saveStatus = "☁ 회의록 Drive 업로드 중...") }
                        val driveService = GoogleDriveService(getApplication())
                        if (driveService.initFromLastAccount()) {
                            val txtFolderId = config.driveTxtFolderId
                            if (txtFolderId.isNotBlank()) {
                                val results = driveService.uploadMeetingFiles(
                                    mp3File = null,
                                    sttFile = null,
                                    summaryFile = combinedFile.getOrNull(),
                                    mp3FolderId = "",
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
                savedRecordingFile = null
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
            saveStatus = "저장 취소됨 (녹음파일은 이미 저장됨)"
        )
        pendingSttText = null
        pendingSummaryText = null
        pendingAudioFile = null
        // savedRecordingFile은 유지 — 이미 저장된 녹음파일은 삭제하지 않음
    }

    private suspend fun runStt(audioFile: File): Pair<Boolean, String> {
        val engine = config.sttEngine
        val engineLabel = when (engine) {
            "clova" -> "CLOVA Speech"
            "whisper" -> "Whisper"
            else -> "Gemini"
        }
        updateUiState { it.copy(
            sttStatus = "$engineLabel STT 시작...",
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
            when (engine) {
                "clova" -> {
                    val result = clovaService.transcribe(
                        audioFile = audioFile,
                        invokeUrl = config.clovaInvokeUrl,
                        secretKey = config.clovaSecretKey,
                        numSpeakers = config.numSpeakers,
                        onProgress = safeOnProgress,
                        onStatus = safeOnStatus
                    )
                    Pair(result.success, result.text)
                }
                "whisper" -> {
                    val result = chatGptService.transcribe(
                        audioFile = audioFile,
                        apiKey = config.chatGptApiKey,
                        numSpeakers = config.numSpeakers,
                        onProgress = safeOnProgress,
                        onStatus = safeOnStatus
                    )
                    Pair(result.success, result.text)
                }
                else -> { // gemini
                    val result = geminiService.transcribe(
                        audioFile = audioFile,
                        apiKey = config.geminiApiKey,
                        numSpeakers = config.numSpeakers,
                        onProgress = safeOnProgress,
                        onStatus = safeOnStatus
                    )
                    Pair(result.success, result.text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "STT execution failed: ${e.message}", e)
            Pair(false, "STT 변환 중 오류: ${e.message?.take(200) ?: "알 수 없는 오류"}")
        }
    }

    private suspend fun runSummary(sttText: String): Pair<Boolean, String> {
        val aiEngine = config.aiEngine
        val mode = config.summaryMode
        val engineLabel = when (aiEngine) {
            "claude" -> "Claude"
            "chatgpt" -> "GPT-4o"
            else -> "Gemini"
        }
        updateUiState { it.copy(
            summaryStatus = "$engineLabel 요약 시작...",
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
            when (aiEngine) {
                "claude" -> {
                    val result = claudeService.summarize(
                        sttText = sttText,
                        apiKey = config.claudeApiKey,
                        summaryMode = mode,
                        onProgress = safeOnProgress
                    )
                    Pair(result.success, result.text)
                }
                "chatgpt" -> {
                    val result = chatGptService.summarize(
                        sttText = sttText,
                        apiKey = config.chatGptApiKey,
                        summaryMode = mode,
                        onProgress = safeOnProgress
                    )
                    Pair(result.success, result.text)
                }
                else -> { // gemini
                    val result = geminiService.summarize(
                        sttText = sttText,
                        apiKey = config.geminiApiKey,
                        summaryMode = mode,
                        onProgress = safeOnProgress
                    )
                    Pair(result.success, result.text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Summary execution failed: ${e.message}", e)
            Pair(false, "요약 중 오류: ${e.message?.take(200) ?: "알 수 없는 오류"}")
        }
    }

    // ── V2.0: 재요약 기능 ────────────────────────────────────────

    /**
     * STT 변환파일 목록 가져오기 (요약 저장 디렉토리 내 .md/.txt 파일)
     */
    fun getSttFileList(): List<File> {
        return fileManager.listSummaryFiles(config.summarySaveDir)
    }

    /**
     * STT 변환파일 선택 및 로드
     */
    fun selectSttFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = file.readText(Charsets.UTF_8)
                loadedSttFile = file
                updateUiState { it.copy(
                    selectedSttFile = file.name,
                    selectedSttText = text,
                    resummarizeStatus = "파일 로드 완료: ${file.name} (${text.length}자)"
                ) }
            } catch (e: Exception) {
                updateUiState { it.copy(
                    error = "파일 읽기 실패: ${e.message}"
                ) }
            }
        }
    }

    /**
     * URI에서 STT 파일 선택 (파일 선택기 사용 시)
     */
    fun selectSttFileFromUri(uri: Uri) {
        val context = getApplication<MeetingApp>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
                inputStream.close()
                // 임시 파일로 저장
                val tempFile = File(config.summarySaveDir, "imported_stt_${System.currentTimeMillis()}.txt")
                tempFile.writeText(text, Charsets.UTF_8)
                loadedSttFile = tempFile
                updateUiState { it.copy(
                    selectedSttFile = uri.lastPathSegment ?: "선택된 파일",
                    selectedSttText = text,
                    resummarizeStatus = "파일 로드 완료 (${text.length}자)"
                ) }
            } catch (e: Exception) {
                updateUiState { it.copy(
                    error = "파일 읽기 실패: ${e.message}"
                ) }
            }
        }
    }

    /**
     * 재요약 BottomSheet 표시
     */
    fun showResummarizeSheet() {
        if (_uiState.value.selectedSttText.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "STT 변환파일을 먼저 선택해주세요."
            )
            return
        }
        _uiState.value = _uiState.value.copy(showResummarizeSheet = true)
    }

    fun dismissResummarizeSheet() {
        _uiState.value = _uiState.value.copy(showResummarizeSheet = false)
    }

    /**
     * V2.0: 재요약 실행 — STT 텍스트를 다른 요약방식으로 다시 요약
     * startPipeline()과 달리 STT 단계를 건너뛰고 요약만 실행
     */
    fun startResummarize(summaryMode: String) {
        val sttText = _uiState.value.selectedSttText
        if (sttText.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "STT 텍스트가 비어 있습니다.")
            return
        }

        // 요약 엔진 키 검증
        val aiEngine = config.aiEngine
        if (aiEngine == "claude" && config.claudeApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Claude API 키가 설정되지 않았습니다.")
            return
        }
        if (aiEngine == "chatgpt" && config.chatGptApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "OpenAI API 키가 설정되지 않았습니다.")
            return
        }
        if (aiEngine == "gemini" && config.geminiApiKey.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Gemini API 키가 설정되지 않았습니다.")
            return
        }

        _uiState.value = _uiState.value.copy(
            showResummarizeSheet = false,
            isProcessing = true,
            error = null,
            resummarizeProgress = 0,
            resummarizeStatus = "재요약 시작..."
        )

        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                // 요약방식을 1회성으로 오버라이드하여 실행
                val engineLabel = when (aiEngine) {
                    "claude" -> "Claude"
                    "chatgpt" -> "GPT-4o"
                    else -> "Gemini"
                }
                val modeLabel = when (summaryMode) {
                    "topic" -> "주제중심"
                    "formal_md" -> "회의양식"
                    "flow" -> "흐름중심"
                    "lecture_md" -> "강의요약"
                    else -> "화자중심"
                }
                updateUiState { it.copy(
                    resummarizeStatus = "$engineLabel ($modeLabel) 요약 중...",
                    resummarizeProgress = 10
                ) }

                val safeOnProgress: (Int) -> Unit = { p ->
                    try {
                        viewModelScope.launch(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(resummarizeProgress = p)
                        }
                    } catch (_: Exception) {}
                }

                // 요약 실행 (선택된 summaryMode로 1회성 실행)
                val result = when (aiEngine) {
                    "claude" -> claudeService.summarize(sttText, config.claudeApiKey, summaryMode, onProgress = safeOnProgress).let { Pair(it.success, it.text) }
                    "chatgpt" -> chatGptService.summarize(sttText, config.chatGptApiKey, summaryMode, onProgress = safeOnProgress).let { Pair(it.success, it.text) }
                    else -> geminiService.summarize(sttText, config.geminiApiKey, summaryMode, onProgress = safeOnProgress).let { Pair(it.success, it.text) }
                }

                if (!result.first) {
                    updateUiState { it.copy(
                        isProcessing = false,
                        error = "재요약 실패:\n${result.second}"
                    ) }
                    return@launch
                }

                // Apply speaker map if available
                var summaryText = result.second
                try {
                    val selectedFileName = _uiState.value.selectedSttFile
                    val matchingMeeting = dao.getByFileName(selectedFileName)

                    if (matchingMeeting != null && !matchingMeeting.speakerMap.isNullOrBlank()) {
                        summaryText = applySpeakerMap(summaryText, matchingMeeting.speakerMap!!)
                        Log.d(TAG, "Speaker map applied to resummarize text")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying speaker map during resummarize", e)
                    // Continue with original text if speaker map lookup fails
                }

                updateUiState { it.copy(
                    resummarizeProgress = 90,
                    resummarizeStatus = "재요약 완료 — 파일이름 입력 대기"
                ) }

                // 알림
                NotificationHelper.notifySummaryComplete(getApplication(), _uiState.value.selectedSttFile)

                // 파일이름 생성 (날짜_원본파일명_요약방식.md)
                val originalName = loadedSttFile?.name ?: "회의록"
                val suggestedName = fileManager.getResummarizeFileName(originalName, summaryMode)

                // pendingResummarize 저장
                pendingSttText = sttText
                pendingSummaryText = summaryText
                pendingAudioFile = null  // 재요약에는 오디오 파일 없음

                updateUiState { it.copy(
                    summaryText = summaryText,
                    showFileNameDialog = true,
                    suggestedFileName = suggestedName,
                    saveStatus = "파일이름을 입력해주세요..."
                ) }

            } catch (e: Throwable) {
                Log.e(TAG, "Resummarize error", e)
                updateUiState { it.copy(
                    isProcessing = false,
                    error = "재요약 오류:\n${e.message?.take(300)}"
                ) }
            }
        }
    }

    /**
     * 재요약 관련 상태 초기화
     */
    fun clearResummarize() {
        _uiState.value = _uiState.value.copy(
            selectedSttFile = "",
            selectedSttText = "",
            resummarizeProgress = 0,
            resummarizeStatus = ""
        )
        loadedSttFile = null
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

    /**
     * Apply speaker map to summary text by replacing speaker labels with mapped names
     * Handles patterns like [화자1], 화자1:, 화자1 → mapped name
     */
    private fun applySpeakerMap(text: String, speakerMapJson: String): String {
        return try {
            val json = JSONObject(speakerMapJson)
            var result = text
            json.keys().forEach { key ->
                val name = json.getString(key)
                // Replace patterns: [화자1] → [김대표], 화자1: → 김대표:, 화자1  → 김대표
                result = result.replace("[$key]", "[$name]")
                    .replace("$key:", "$name:")
                    .replace("$key ", "$name ")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply speaker map", e)
            text  // Return original text if parsing fails
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
