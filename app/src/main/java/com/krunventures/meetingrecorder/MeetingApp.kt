package com.krunventures.meetingrecorder

import android.app.Application
import android.os.Environment
import android.util.Log
import com.krunventures.meetingrecorder.data.AppDatabase
import com.krunventures.meetingrecorder.service.NotificationHelper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class MeetingApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        // 변환 완료 알림 채널 생성 (Android 8.0+)
        NotificationHelper.createChannels(this)
    }

    /**
     * 글로벌 예외 핸들러 — 앱 크래시 시 로그를 파일로 저장
     * 로그 위치: /Android/data/com.krunventures.meetingrecorder/files/Documents/crash_log.txt
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val logText = """
=== CRASH LOG ===
Time: $timestamp
Thread: ${thread.name}
Exception: ${throwable.javaClass.simpleName}
Message: ${throwable.message}

Stack Trace:
$stackTrace

Device: ${android.os.Build.MODEL} (${android.os.Build.MANUFACTURER})
Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
App Version: ${packageManager.getPackageInfo(packageName, 0).versionName}
=================

"""
                Log.e("MeetingApp", "FATAL CRASH: ${throwable.message}", throwable)

                // 앱 전용 디렉토리에 로그 저장 (권한 불필요)
                val logDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: filesDir
                val logFile = File(logDir, "crash_log.txt")
                logFile.appendText(logText)
                Log.e("MeetingApp", "Crash log saved to: ${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("MeetingApp", "Failed to save crash log", e)
            }

            // 기본 핸들러 호출 (시스템 크래시 다이얼로그 표시)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
