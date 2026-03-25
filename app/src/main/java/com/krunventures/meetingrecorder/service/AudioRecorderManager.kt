package com.krunventures.meetingrecorder.service

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingState { IDLE, RECORDING, PAUSED, AUDIO_FOCUS_LOST }

class AudioRecorderManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioRecorderManager"
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasAudioFocusLost = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val _audioFocusLost = MutableStateFlow(false)
    val audioFocusLost: StateFlow<Boolean> = _audioFocusLost

    private var timerThread: Thread? = null
    @Volatile private var timerRunning = false

    // 오디오 포커스 변경 리스너
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently (camera, video call, etc.)")
                wasAudioFocusLost = true
                _audioFocusLost.value = true
                // 녹음 중일 때만 자동 일시정지
                if (_state.value == RecordingState.RECORDING) {
                    pauseRecording()
                    _state.value = RecordingState.AUDIO_FOCUS_LOST
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost temporarily")
                _audioFocusLost.value = true
                if (_state.value == RecordingState.RECORDING) {
                    pauseRecording()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus ducked (volume reduced)")
                // 녹음은 계속 진행하지만 다른 앱의 음량이 줄어듦
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained")
                _audioFocusLost.value = false
                // 이전에 포커스 손실로 일시정지했다면 자동 재개
                if (wasAudioFocusLost && _state.value == RecordingState.PAUSED) {
                    resumeRecording()
                    _state.value = RecordingState.RECORDING
                    wasAudioFocusLost = false
                }
            }
        }
    }

    fun startRecording(saveDir: File): Result<File> {
        if (_state.value != RecordingState.IDLE) {
            return Result.failure(Exception("이미 녹음 중입니다."))
        }
        return try {
            // Step 1: 오디오 포커스 요청 (카메라, 비디오콜 등과 충돌 감지)
            val focusAcquired = requestAudioFocus()
            if (!focusAcquired) {
                Log.w(TAG, "Warning: Audio focus not acquired. Recording may be interrupted by other apps.")
            }

            saveDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(saveDir, "${timestamp}_녹음.m4a")
            outputFile = file

            // Step 2: MediaRecorder 생성 및 설정 (각 단계에서 예외 처리)
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(32000)   // 32kbps AAC — 음성 녹음 최적 (3시간=43MB, STT 인식률 동일)
                    setAudioSamplingRate(16000)     // 16kHz — STT 엔진 표준 입력 샘플레이트 (CLOVA/Whisper 기본값)
                    setAudioChannels(1)
                    setOutputFile(file.absolutePath)
                    prepare()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "MediaRecorder configuration error: ${e.message}", e)
                    throw Exception("마이크 설정 오류 (다른 앱이 마이크 사용 중?): ${e.message}")
                } catch (e: IOException) {
                    Log.e(TAG, "MediaRecorder IO error: ${e.message}", e)
                    throw Exception("파일 저장 오류: ${e.message}")
                }

                try {
                    start()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "MediaRecorder start error: ${e.message}", e)
                    throw Exception("녹음 시작 오류: ${e.message}")
                }
            }

            startTime = System.currentTimeMillis()
            pausedDuration = 0
            wasAudioFocusLost = false
            _state.value = RecordingState.RECORDING
            _elapsedSeconds.value = 0
            startTimer()
            Log.d(TAG, "Recording started: ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _state.value = RecordingState.IDLE
            releaseMediaRecorder()
            abandonAudioFocus()
            Result.failure(Exception("마이크 오류: ${e.message}"))
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) {
            Log.w(TAG, "AudioManager not available")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ : AudioFocusRequest 사용
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = request
                val result = audioManager.requestAudioFocus(request)
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                // Android 7.1 이하
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus: ${e.message}", e)
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusChangeListener)
            }
            Log.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus: ${e.message}", e)
        }
    }

    private fun releaseMediaRecorder() {
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }

    fun pauseRecording() {
        if (_state.value == RecordingState.RECORDING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder?.pause()
            }
            pauseStartTime = System.currentTimeMillis()
            _state.value = RecordingState.PAUSED
            timerRunning = false
        }
    }

    fun resumeRecording() {
        if (_state.value == RecordingState.PAUSED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                recorder?.resume()
            }
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            _state.value = RecordingState.RECORDING
            startTimer()
        }
    }

    fun stopRecording(): Result<File> {
        if (_state.value == RecordingState.IDLE) {
            return Result.failure(Exception("녹음 중이 아닙니다."))
        }
        return try {
            timerRunning = false

            // MediaRecorder stop/release 안전하게 처리
            try {
                recorder?.stop()
                Log.d(TAG, "MediaRecorder stopped successfully")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "MediaRecorder stop error (may indicate audio interruption): ${e.message}", e)
                // stop() 실패해도 release() 진행
            }

            releaseMediaRecorder()
            abandonAudioFocus()
            _state.value = RecordingState.IDLE

            val file = outputFile ?: return Result.failure(Exception("녹음 파일이 없습니다."))
            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "Recording stopped successfully: ${file.absolutePath}, size=${file.length()}")
                Result.success(file)
            } else {
                Log.w(TAG, "Recording file is empty or doesn't exist")
                Result.failure(Exception("녹음 데이터가 없습니다."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            _state.value = RecordingState.IDLE
            releaseMediaRecorder()
            abandonAudioFocus()
            Result.failure(Exception("중지 오류: ${e.message}"))
        }
    }

    private fun startTimer() {
        timerRunning = true
        timerThread = Thread {
            while (timerRunning) {
                Thread.sleep(500)
                if (_state.value == RecordingState.RECORDING) {
                    val elapsed = (System.currentTimeMillis() - startTime - pausedDuration) / 1000
                    _elapsedSeconds.value = elapsed
                    // Update amplitude
                    try {
                        val maxAmp = recorder?.maxAmplitude ?: 0
                        _amplitude.value = (maxAmp / 32768f).coerceIn(0f, 1f)
                    } catch (_: Exception) {}
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun getElapsedString(): String {
        val total = _elapsedSeconds.value
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    fun release() {
        Log.d(TAG, "Releasing AudioRecorderManager resources")
        timerRunning = false
        releaseMediaRecorder()
        abandonAudioFocus()
        _state.value = RecordingState.IDLE
    }
}
