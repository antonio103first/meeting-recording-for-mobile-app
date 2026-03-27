package com.krunventures.meetingrecorder.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.krunventures.meetingrecorder.MainActivity
import com.krunventures.meetingrecorder.R

/**
 * 백그라운드 녹음 포그라운드 서비스
 *
 * - WakeLock으로 화면 꺼짐/절전 모드에서도 녹음 유지
 * - 알림바에 일시정지/중지 액션 버튼 제공
 * - START_STICKY로 시스템에 의해 종료 시 자동 재시작
 * - 통화 상태 변경 시 알림 텍스트 업데이트
 */
class RecordingService : Service() {

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001

        // 알림 액션 인텐트
        const val ACTION_PAUSE = "com.krunventures.meetingrecorder.ACTION_PAUSE"
        const val ACTION_RESUME = "com.krunventures.meetingrecorder.ACTION_RESUME"
        const val ACTION_STOP = "com.krunventures.meetingrecorder.ACTION_STOP"

        // 포그라운드 서비스 준비 완료 신호 (startForeground 성공 후 발송)
        const val ACTION_FOREGROUND_READY = "com.krunventures.meetingrecorder.ACTION_FOREGROUND_READY"

        // 알림 업데이트 인텐트
        const val ACTION_UPDATE_NOTIFICATION = "com.krunventures.meetingrecorder.ACTION_UPDATE_NOTIFICATION"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_IS_PAUSED = "extra_is_paused"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification("녹음 일시정지 중", true)
                // ViewModel에서 BroadcastReceiver로 수신하여 실제 일시정지 처리
                sendBroadcast(Intent(ACTION_PAUSE).setPackage(packageName))
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification("녹음 중...", false)
                sendBroadcast(Intent(ACTION_RESUME).setPackage(packageName))
            }
            ACTION_STOP -> {
                sendBroadcast(Intent(ACTION_STOP).setPackage(packageName))
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_NOTIFICATION -> {
                val text = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "녹음 중..."
                isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                updateNotification(text, isPaused)
            }
            else -> {
                // 서비스 시작 — WakeLock 획득 및 포그라운드 알림 표시
                // ⚠️ Android 14+ 요구사항: onStartCommand 진입 후 5초 내에 startForeground() 호출 필수
                // ⚠️ Android 14+ (API 34+): startForeground()에 FOREGROUND_SERVICE_TYPE_MICROPHONE 명시 필수
                acquireWakeLock()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+: foregroundServiceType MICROPHONE 명시 (14+에서 필수)
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification("녹음 중...", false),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification("녹음 중...", false))
                    }
                    android.util.Log.d("RecordingService", "✅ startForeground(MICROPHONE) 성공")
                    // ★ 포그라운드 상태 진입 성공 → ViewModel에 녹음 시작 허가 신호
                    sendBroadcast(Intent(ACTION_FOREGROUND_READY).setPackage(packageName))
                } catch (e: Exception) {
                    android.util.Log.e("RecordingService", "❌ startForeground 실패: ${e.message}", e)
                    // 실패 시에도 신호 전송 (ViewModel에서 에러 처리)
                    sendBroadcast(Intent(ACTION_FOREGROUND_READY).apply {
                        setPackage(packageName)
                        putExtra("error", e.message)
                    })
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * 사용자가 최근 앱 목록에서 앱을 스와이프하여 종료했을 때 호출
     * START_STICKY를 반환하면 시스템이 서비스를 자동 재시작하지만,
     * 녹음 중인 상태를 ViewModel에 알려야 함
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 녹음 중이면 Broadcast로 ViewModel에 알리기
        sendBroadcast(Intent(RecordingService::class.java.name + ".TASK_REMOVED").setPackage(packageName))
        // WakeLock은 아직 유지 (서비스가 다시 시작될 때까지)
    }

    /**
     * 시스템 저메모리 상태 — 녹음 중이면 일시정지하여 메모리 확보
     */
    override fun onLowMemory() {
        super.onLowMemory()
        // 녹음 중이면 일시정지 신호 전송
        sendBroadcast(Intent(RecordingService::class.java.name + ".LOW_MEMORY").setPackage(packageName))
    }

    // ── WakeLock 관리 ────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MeetingRecorder::RecordingWakeLock"
            ).apply {
                acquire(4 * 60 * 60 * 1000L)  // 최대 4시간 (장시간 회의 대비)
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    // ── 알림 관리 ────────────────────────────────────────────────

    private fun buildNotification(statusText: String, paused: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("회의녹음요약")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // 포그라운드 서비스는 PRIORITY_DEFAULT 이상 권장
            // PRIORITY_LOW는 시스템 저메모리 상황에서 서비스를 먼저 종료할 수 있음
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)

        // 일시정지/재개 액션 버튼
        if (paused) {
            val resumeIntent = PendingIntent.getService(
                this, 1,
                Intent(this, RecordingService::class.java).apply { action = ACTION_RESUME },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "재개", resumeIntent)
        } else {
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, RecordingService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "일시정지", pauseIntent)
        }

        // 중지 액션 버튼
        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_delete, "중지", stopIntent)

        return builder.build()
    }

    private fun updateNotification(text: String, paused: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(text, paused))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_HIGH: 녹음 서비스가 시스템 메모리 부족 상황에서 먼저 종료되는 것을 방지
            // 포그라운드 서비스는 IMPORTANCE_HIGH 권장 (저메모리 상황에서도 유지)
            val channel = NotificationChannel(
                CHANNEL_ID, "녹음 서비스",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "회의 녹음 진행 중 알림 — 시스템에서 우선 유지됨"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 화면 꺼짐 상태에서도 알림 표시 (포그라운드 서비스 시각화)
                setBypassDnd(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
