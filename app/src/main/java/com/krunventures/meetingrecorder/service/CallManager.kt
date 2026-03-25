package com.krunventures.meetingrecorder.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 전화 수신 감지 및 녹음 중 통화 제어 관리자
 *
 * - 녹음 중 전화가 오면 감지하여 CallState 상태 변경
 * - ViewModel에서 상태를 관찰하여 UI에 수락/거절 버튼 노출
 * - Android 12+ (API 31): TelephonyCallback 사용
 * - Android 12 미만: PhoneStateListener(deprecated) 사용
 */
enum class CallState {
    NONE,           // 전화 없음
    RINGING,        // 전화 수신 중 (수락/거절 선택 대기)
    IN_CALL,        // 통화 중 (수락 후)
    CALL_ENDED      // 통화 종료 (녹음 재개 가능)
}

class CallManager(private val context: Context) {

    private val _callState = MutableStateFlow(CallState.NONE)
    val callState: StateFlow<CallState> = _callState

    private val _callerNumber = MutableStateFlow("")
    val callerNumber: StateFlow<String> = _callerNumber

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    // Android 12+ callback
    private var telephonyCallback: Any? = null

    // Legacy listener (Android < 12)
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    private var isRegistered = false

    /**
     * 전화 상태 모니터링 시작
     * READ_PHONE_STATE 권한 필요
     */
    fun startMonitoring() {
        if (isRegistered) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return // 권한 없으면 무시 (녹음은 계속 진행)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallback()
        } else {
            registerPhoneStateListener()
        }
        isRegistered = true
    }

    /**
     * 전화 상태 모니터링 중지
     */
    fun stopMonitoring() {
        if (!isRegistered) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterTelephonyCallback()
        } else {
            unregisterPhoneStateListener()
        }
        isRegistered = false
        _callState.value = CallState.NONE
        _callerNumber.value = ""
    }

    // ── Android 12+ (API 31): TelephonyCallback ──────────────────

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallback() {
        try {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    try {
                        handleCallState(state)
                    } catch (e: Exception) {
                        android.util.Log.e("CallManager", "Error handling call state: ${e.message}", e)
                        // 콜백 오류가 녹음을 중단하지 않도록 예외 처리
                    }
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(
                context.mainExecutor, callback
            )
            android.util.Log.d("CallManager", "TelephonyCallback registered")
        } catch (e: Exception) {
            android.util.Log.e("CallManager", "Failed to register TelephonyCallback: ${e.message}", e)
            // 등록 실패해도 녹음은 계속 진행
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterTelephonyCallback() {
        (telephonyCallback as? TelephonyCallback)?.let {
            telephonyManager?.unregisterTelephonyCallback(it)
        }
        telephonyCallback = null
    }

    // ── Android < 12: PhoneStateListener (deprecated) ────────────

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        try {
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    try {
                        _callerNumber.value = phoneNumber ?: ""
                        handleCallState(state)
                    } catch (e: Exception) {
                        android.util.Log.e("CallManager", "Error handling call state: ${e.message}", e)
                        // 콜백 오류가 녹음을 중단하지 않도록 예외 처리
                    }
                }
            }
            phoneStateListener = listener
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            android.util.Log.d("CallManager", "PhoneStateListener registered")
        } catch (e: Exception) {
            android.util.Log.e("CallManager", "Failed to register PhoneStateListener: ${e.message}", e)
            // 등록 실패해도 녹음은 계속 진행
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        phoneStateListener?.let {
            telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null
    }

    // ── 공통 상태 처리 ───────────────────────────────────────────

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                _callState.value = CallState.RINGING
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                _callState.value = CallState.IN_CALL
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (_callState.value == CallState.IN_CALL || _callState.value == CallState.RINGING) {
                    _callState.value = CallState.CALL_ENDED
                } else {
                    _callState.value = CallState.NONE
                }
            }
        }
    }

    /**
     * 전화 수락 — 시스템 기본 전화 앱으로 전환
     * ANSWER_PHONE_CALLS 권한 필요 (Android 8+)
     */
    fun acceptCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    telecomManager?.acceptRingingCall()
                } catch (e: Exception) {
                    // 권한 또는 시스템 제한 시 무시
                }
            }
        }
        // 상태는 TelephonyCallback/PhoneStateListener에서 자동으로 IN_CALL로 전환됨
    }

    /**
     * 전화 거절 — 수신 전화를 종료
     * ANSWER_PHONE_CALLS 권한 필요 (Android 9+)
     */
    fun rejectCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    telecomManager?.endCall()
                } catch (e: Exception) {
                    // 권한 또는 시스템 제한 시 무시
                }
            }
        }
        // 상태는 TelephonyCallback/PhoneStateListener에서 자동으로 IDLE로 전환됨
    }

    /**
     * CALL_ENDED 상태를 NONE으로 리셋 (녹음 재개 후 호출)
     */
    fun resetCallState() {
        _callState.value = CallState.NONE
        _callerNumber.value = ""
    }

    fun release() {
        stopMonitoring()
    }
}
