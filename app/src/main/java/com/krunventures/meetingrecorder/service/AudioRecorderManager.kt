package com.krunventures.meetingrecorder.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingState { IDLE, RECORDING, PAUSED }

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private var timerThread: Thread? = null
    @Volatile private var timerRunning = false

    fun startRecording(saveDir: File): Result<File> {
        if (_state.value != RecordingState.IDLE) {
            return Result.failure(Exception("이미 녹음 중입니다."))
        }
        return try {
            saveDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(saveDir, "${timestamp}_녹음.m4a")
            outputFile = file

            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            startTime = System.currentTimeMillis()
            pausedDuration = 0
            _state.value = RecordingState.RECORDING
            _elapsedSeconds.value = 0
            startTimer()
            Result.success(file)
        } catch (e: Exception) {
            _state.value = RecordingState.IDLE
            recorder?.release()
            recorder = null
            Result.failure(Exception("마이크 오류: ${e.message}"))
        }
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
            recorder?.stop()
            recorder?.release()
            recorder = null
            _state.value = RecordingState.IDLE
            val file = outputFile ?: return Result.failure(Exception("녹음 파일이 없습니다."))
            if (file.exists() && file.length() > 0) {
                Result.success(file)
            } else {
                Result.failure(Exception("녹음 데이터가 없습니다."))
            }
        } catch (e: Exception) {
            _state.value = RecordingState.IDLE
            recorder?.release()
            recorder = null
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
        timerRunning = false
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        _state.value = RecordingState.IDLE
    }
}
