package com.krunventures.meetingrecorder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.krunventures.meetingrecorder.MainActivity
import com.krunventures.meetingrecorder.R

/**
 * 변환/요약 완료 알림 헬퍼
 *
 * STT 변환 완료, AI 요약 완료 시 시스템 Notification을 표시하여
 * 백그라운드에서도 사용자가 완료 시점을 인지할 수 있도록 한다.
 *
 * 알림 채널:
 *   - pipeline_channel (변환 완료 알림) — IMPORTANCE_HIGH → 헤드업 알림 + 소리
 *
 * 알림 ID:
 *   - 2001: STT 변환 완료
 *   - 2002: AI 요약 완료
 *   - 2003: 녹음파일 저장 완료
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    const val CHANNEL_ID_PIPELINE = "pipeline_channel"
    private const val CHANNEL_NAME = "변환 완료 알림"
    private const val CHANNEL_DESC = "STT 변환 및 AI 요약 완료 시 알림"

    // 알림 ID — RecordingService(1001)과 충돌 방지
    const val NOTIFICATION_ID_STT_DONE = 2001
    const val NOTIFICATION_ID_SUMMARY_DONE = 2002
    const val NOTIFICATION_ID_RECORDING_SAVED = 2003

    /**
     * 앱 초기화 시 호출 — 알림 채널 생성 (Android 8.0+)
     * MeetingApp.onCreate()에서 1회 호출
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PIPELINE,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
            Log.d(TAG, "Pipeline notification channel created")
        }
    }

    /**
     * STT 변환 완료 알림
     */
    fun notifySttComplete(context: Context, fileName: String) {
        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_STT_DONE,
            title = "음성인식 변환 완료",
            text = "\"$fileName\" STT 변환이 완료되었습니다. AI 요약을 시작합니다.",
            subText = "STT 완료"
        )
    }

    /**
     * AI 요약 완료 알림
     */
    fun notifySummaryComplete(context: Context, fileName: String) {
        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_SUMMARY_DONE,
            title = "회의록 요약 완료",
            text = "\"$fileName\" AI 요약이 완료되었습니다. 파일이름을 입력해주세요.",
            subText = "요약 완료"
        )
    }

    /**
     * 녹음파일 즉시 저장 완료 알림
     */
    fun notifyRecordingSaved(context: Context, fileName: String) {
        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_RECORDING_SAVED,
            title = "녹음파일 저장 완료",
            text = "\"$fileName\" 녹음파일이 저장되었습니다.",
            subText = "녹음 저장"
        )
    }

    /**
     * 공통 알림 빌드 + 표시
     */
    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        subText: String
    ) {
        try {
            val contentIntent = PendingIntent.getActivity(
                context, notificationId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_PIPELINE)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(notificationId, notification)
            Log.d(TAG, "Notification shown: id=$notificationId, title=$title")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification: $title", e)
        }
    }
}
