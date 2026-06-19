package com.krunventures.meetingrecorder.service

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.krunventures.meetingrecorder.data.ConfigManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingState { IDLE, RECORDING, PAUSED, AUDIO_FOCUS_LOST }

/**
 * ★ v3.5: 녹음 엔진을 MediaRecorder → AudioRecord + MediaCodec 파이프라인으로 교체.
 *
 * MediaRecorder에는 입력 음량(게인) 조절 API가 전혀 없어서 "녹음이 너무 작다"는
 * 문제를 해결할 수 없었다. 이제 AudioRecord로 16-bit PCM 원본을 직접 캡처하여
 * 샘플마다 소프트웨어 게인(ConfigManager.recordingGain, 기본 3.0배)을 곱하고
 * 클리핑(±32767) 방지 후 MediaCodec(AAC-LC)로 인코딩, MediaMuxer로 M4A(MPEG-4)에 저장한다.
 *
 * - AGC(자동 게인)·AEC(에코 제거)는 비활성화하여 수동 게인이 그대로 적용되도록 한다.
 * - NoiseSuppressor는 활성화하여 증폭 시 잡음만 같이 커지는 것을 줄인다.
 * - v3.0의 오디오 포커스 인터럽트 완전 차단 로직은 그대로 유지한다.
 */
class AudioRecorderManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioRecorderManager"
        private const val SAMPLE_RATE = 16000          // STT 표준 입력 (CLOVA/Whisper)
        private const val AAC_BIT_RATE = 32000         // 32kbps AAC — 음성 녹음 최적
        private const val DEFAULT_GAIN = 4.0f          // 기본 증폭 배수
        private const val MIN_GAIN = 1.0f
        private const val MAX_GAIN = 8.0f
    }

    private val config = ConfigManager(context)

    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var agc: AutomaticGainControl? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    private var trackIndex = -1
    private var muxerStarted = false
    @Volatile private var totalSamplesRead = 0L  // 인코더에 큐잉된 누적 프레임 수 (PTS 계산용)

    private var outputFile: File? = null
    private var gain: Float = DEFAULT_GAIN

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

    private var recordThread: Thread? = null
    @Volatile private var recordingActive = false
    @Volatile private var paused = false

    // ★ v3.0.2: 녹음 중 인터럽트 완전 차단 + 자동 포커스 재요청
    // 전화, 카메라, 다른 앱의 마이크 점유 등 모든 오디오 포커스 변경을 무시하고 녹음 계속 진행
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus lost permanently — 녹음 계속 진행 + 포커스 재요청")
                _audioFocusLost.value = true
                if (_state.value == RecordingState.RECORDING) {
                    val reacquired = requestAudioFocus()
                    Log.d(TAG, "Audio focus re-request after LOSS: $reacquired")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost temporarily — 녹음 계속 진행 + 포커스 재요청")
                _audioFocusLost.value = true
                if (_state.value == RecordingState.RECORDING) {
                    val reacquired = requestAudioFocus()
                    Log.d(TAG, "Audio focus re-request after TRANSIENT: $reacquired")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus ducked (volume reduced) — 녹음 계속 진행")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained")
                _audioFocusLost.value = false
                wasAudioFocusLost = false
            }
        }
    }

    fun startRecording(saveDir: File): Result<File> {
        if (_state.value != RecordingState.IDLE) {
            return Result.failure(Exception("이미 녹음 중입니다."))
        }
        // 마이크 권한 사전 확인
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return Result.failure(Exception("마이크 권한이 허용되지 않았습니다.\n\n설정 → 앱 → 회의녹음요약 → 권한 → 마이크 → 허용"))
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

            // ★ v3.5: 사용자 설정 게인 로드 (1.0~8.0 범위로 클램프)
            gain = config.recordingGain.coerceIn(MIN_GAIN, MAX_GAIN)

            // Step 2: AudioRecord 생성
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
                throw Exception("마이크 설정 오류: AudioRecord 버퍼 크기 계산 실패")
            }
            val bufferSizeBytes = maxOf(minBuf, 4096) * 2

            val record = AudioRecord(
                // VOICE_RECOGNITION: 시스템 우선순위가 높아 다른 앱에 마이크를 뺏기기 어려움
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw Exception("마이크 초기화 실패 (다른 앱이 마이크 사용 중?)")
            }
            audioRecord = record

            // ★ v3.5: 음성 효과 — AGC/AEC OFF (수동 게인 보존), NoiseSuppressor ON (증폭 잡음 억제)
            setupAudioEffects(record.audioSessionId)

            // Step 3: MediaCodec AAC 인코더 + MediaMuxer 구성
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AAC_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSizeBytes)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            encoder = enc

            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            trackIndex = -1
            muxerStarted = false
            totalSamplesRead = 0L

            // Step 4: 캡처 스레드 시작
            record.startRecording()
            recordingActive = true
            paused = false
            startTime = System.currentTimeMillis()
            pausedDuration = 0
            wasAudioFocusLost = false
            _state.value = RecordingState.RECORDING
            _elapsedSeconds.value = 0
            _amplitude.value = 0f
            startCaptureThread(bufferSizeBytes)
            startTimer()
            Log.d(TAG, "Recording started: ${file.absolutePath} (gain=${gain}x)")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            _state.value = RecordingState.IDLE
            releasePipeline()
            abandonAudioFocus()
            val errorMsg = when {
                e.message?.contains("EPERM") == true || e.message?.contains("Permission denied") == true ->
                    "파일 저장 경로 접근 불가 (EPERM).\n저장 폴더를 기본값으로 변경하거나 앱 전용 폴더를 사용해주세요.\n\n경로: ${saveDir.absolutePath}"
                e.message?.contains("파일 저장") == true -> e.message!!
                e.message?.contains("마이크") == true -> e.message!!
                else -> "녹음 시작 오류: ${e.message}"
            }
            Result.failure(Exception(errorMsg))
        }
    }

    private fun setupAudioEffects(sessionId: Int) {
        try {
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = false }
                Log.d(TAG, "AGC disabled (manual gain in control)")
            }
        } catch (e: Exception) { Log.w(TAG, "AGC setup failed: ${e.message}") }
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = false }
            }
        } catch (e: Exception) { Log.w(TAG, "AEC setup failed: ${e.message}") }
        try {
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                Log.d(TAG, "NoiseSuppressor enabled")
            }
        } catch (e: Exception) { Log.w(TAG, "NS setup failed: ${e.message}") }
    }

    private fun startCaptureThread(bufferSizeBytes: Int) {
        recordThread = Thread {
            val pcm = ShortArray(bufferSizeBytes / 2)
            try {
                while (recordingActive) {
                    if (paused) {
                        Thread.sleep(20)
                        continue
                    }
                    val record = audioRecord ?: break
                    val read = record.read(pcm, 0, pcm.size)
                    if (read > 0) {
                        // ★ 게인 적용 + 클리핑 방지 + 진폭 측정
                        var maxAmp = 0
                        for (i in 0 until read) {
                            var s = (pcm[i] * gain).toInt()
                            if (s > 32767) s = 32767 else if (s < -32768) s = -32768
                            pcm[i] = s.toShort()
                            val a = abs(s)
                            if (a > maxAmp) maxAmp = a
                        }
                        _amplitude.value = (maxAmp / 32768f).coerceIn(0f, 1f)
                        encodePcm(pcm, read)
                    }
                    drainEncoder(false)
                }
                // EOS 신호 후 잔여 출력 flush
                encodeEndOfStream()
                drainEncoder(true)
            } catch (e: Exception) {
                Log.e(TAG, "Capture thread error: ${e.message}", e)
            }
        }.also { it.isDaemon = true; it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    /** PCM 샘플(short[])을 바이트로 변환하여 인코더 입력 버퍼에 큐잉 */
    private fun encodePcm(pcm: ShortArray, sampleCount: Int) {
        val enc = encoder ?: return
        val byteCount = sampleCount * 2
        val bytes = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) bytes.putShort(pcm[i])
        val data = bytes.array()

        var attempts = 0
        while (attempts < 5) {
            val inIndex = enc.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val inBuf = enc.getInputBuffer(inIndex) ?: return
                inBuf.clear()
                inBuf.put(data, 0, byteCount)
                val pts = totalSamplesRead * 1_000_000L / SAMPLE_RATE
                enc.queueInputBuffer(inIndex, 0, byteCount, pts, 0)
                totalSamplesRead += sampleCount
                return
            } else {
                // 입력 버퍼가 없으면 출력을 비워 버퍼를 회수한 뒤 재시도
                drainEncoder(false)
                attempts++
            }
        }
        Log.w(TAG, "Dropped a PCM chunk: encoder input unavailable")
    }

    private fun encodeEndOfStream() {
        val enc = encoder ?: return
        var attempts = 0
        while (attempts < 100) {
            val inIndex = enc.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val pts = totalSamplesRead * 1_000_000L / SAMPLE_RATE
                enc.queueInputBuffer(inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            drainEncoder(false)
            attempts++
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val enc = encoder ?: return
        val mux = muxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = enc.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return  // 더 기다리지 않음
                    // EOS 대기 중에는 계속 폴링
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = mux.addTrack(enc.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                }
                outIndex >= 0 -> {
                    val encoded = enc.getOutputBuffer(outIndex)
                    if (encoded != null) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            mux.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                    }
                    enc.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) {
            Log.w(TAG, "AudioManager not available")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // ★ v3.0: AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE — 다른 앱의 오디오를 완전 차단
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setWillPauseWhenDucked(false)
                    .build()
                audioFocusRequest = request
                val result = audioManager.requestAudioFocus(request)
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
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

    /** 캡처 스레드 종료 대기 + AudioRecord/MediaCodec/MediaMuxer/효과 해제 */
    private fun releasePipeline() {
        recordingActive = false
        try {
            recordThread?.join(3000)
        } catch (_: Exception) {}
        recordThread = null

        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        try { encoder?.stop() } catch (_: Exception) {}
        try { encoder?.release() } catch (_: Exception) {}
        encoder = null

        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
        muxerStarted = false

        try { agc?.release() } catch (_: Exception) {}
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        agc = null; aec = null; ns = null
    }

    fun pauseRecording() {
        if (_state.value == RecordingState.RECORDING) {
            paused = true
            pauseStartTime = System.currentTimeMillis()
            _state.value = RecordingState.PAUSED
            timerRunning = false
        }
    }

    fun resumeRecording() {
        if (_state.value == RecordingState.PAUSED) {
            paused = false
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
            paused = false
            // 캡처 스레드가 EOS flush + drain 까지 마치고 종료되도록 대기 후 자원 해제
            releasePipeline()
            abandonAudioFocus()
            _state.value = RecordingState.IDLE
            _amplitude.value = 0f

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
            releasePipeline()
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
        releasePipeline()
        abandonAudioFocus()
        _state.value = RecordingState.IDLE
    }
}
