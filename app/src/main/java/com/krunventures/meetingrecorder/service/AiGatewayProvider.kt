package com.krunventures.meetingrecorder.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.krunventures.meetingrecorder.data.ConfigManager

/**
 * ★ v3.13: AI 요약 창구 — 같은 서명의 앱(통합검색 앱)이 이 앱에 설정된 AI 엔진·API 키로 텍스트를 요약하게 한다.
 *
 * - 매니페스트에서 `protectionLevel="signature"` 권한으로 잠금 → 같은 키로 서명된 앱만 호출 가능.
 * - 상시 실행 없음: 다른 앱이 call() 할 때만 시스템이 이 앱 프로세스를 띄운다.
 * - call() 은 바인더 스레드에서 실행되므로 네트워크 호출 가능 (호출자는 IO 스레드에서 불러야 함).
 *
 * 메서드
 *  - "status"    → ok, engine(gemini|claude|chatgpt), engineLabel, hasKey
 *  - "summarize" → extras["prompt"] 를 그대로 엔진에 전달(raw 양식) → ok, text | error
 */
class AiGatewayProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return error("컨텍스트 없음")
        val config = ConfigManager(ctx)
        val engine = config.aiEngine
        val label = when (engine) { "claude" -> "Claude"; "chatgpt" -> "GPT-4o"; else -> "Gemini" }
        val key = when (engine) { "claude" -> config.claudeApiKey; "chatgpt" -> config.chatGptApiKey; else -> config.geminiApiKey }

        return when (method) {
            "status" -> Bundle().apply {
                putBoolean("ok", true); putString("engine", engine); putString("engineLabel", label)
                putBoolean("hasKey", key.isNotBlank())
            }
            "summarize" -> {
                val prompt = extras?.getString("prompt").orEmpty()
                if (prompt.isBlank()) return error("요약할 내용이 비어 있습니다")
                if (key.isBlank()) return error("회의녹음 앱 설정에 $label API 키가 없습니다")
                val started = System.currentTimeMillis()
                val (ok, text) = runCatching {
                    when (engine) {
                        "claude" -> ClaudeService().summarize(prompt, key, RAW).let { it.success to it.text }
                        "chatgpt" -> ChatGptService().summarize(prompt, key, RAW).let { it.success to it.text }
                        else -> GeminiService().summarize(prompt, key, RAW).let { it.success to it.text }
                    }
                }.getOrElse { false to "$label 호출 오류: ${it.message}" }
                Log.i(TAG, "summarize by ${callingPackage} via $label ok=$ok ${System.currentTimeMillis() - started}ms")
                if (ok) Bundle().apply { putBoolean("ok", true); putString("text", text); putString("engineLabel", label) }
                else error(text)
            }
            else -> error("알 수 없는 메서드: $method")
        }
    }

    private fun error(msg: String) = Bundle().apply { putBoolean("ok", false); putString("error", msg) }

    // 데이터 제공용이 아니므로 나머지는 사용하지 않음
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "AiGateway"
        /** 프롬프트를 템플릿 없이 그대로 보내는 요약 양식 */
        const val RAW = "raw"
    }
}
