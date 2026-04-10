package com.krunventures.meetingrecorder.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * OpenAI ChatGPT / Whisper API 서비스
 *
 * STT: Whisper API (whisper-1) — 최대 25MB, 한국어 특화
 * 요약: GPT-4o — 128K 컨텍스트 윈도우
 */
class ChatGptService {

    companion object {
        private const val TAG = "ChatGptService"
        const val STT_MODEL = "whisper-1"
        const val SUMMARY_MODEL = "gpt-4o"
        private const val WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
        private const val CHAT_URL = "https://api.openai.com/v1/chat/completions"
    }

    data class ServiceResult(val success: Boolean, val text: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)  // 3시간 녹음 STT 처리 대기
        .writeTimeout(300, TimeUnit.SECONDS)  // Whisper 25MB 업로드 대기
        .build()
    private val gson = Gson()

    // ── 연결 테스트 ────────────────────────────────────────────

    fun testConnection(apiKey: String): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "OpenAI API 키가 없습니다.")
        return try {
            val body = JsonObject().apply {
                addProperty("model", SUMMARY_MODEL)
                addProperty("max_tokens", 10)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", "안녕")
                    })
                })
            }
            val request = Request.Builder()
                .url(CHAT_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                ServiceResult(true, "✅ 연결 성공! ($SUMMARY_MODEL)")
            } else {
                val errorBody = response.body?.string() ?: ""
                ServiceResult(false, "연결 실패 (HTTP ${response.code}): ${friendlyError(errorBody)}")
            }
        } catch (e: Exception) {
            ServiceResult(false, "오류: ${e.message?.take(200)}")
        }
    }

    // ── Whisper STT ────────────────────────────────────────────

    fun transcribe(
        audioFile: File,
        apiKey: String,
        numSpeakers: Int = 0,
        onProgress: ((Int) -> Unit)? = null,
        onStatus: ((String) -> Unit)? = null
    ): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "OpenAI API 키가 없습니다. 설정에서 입력해주세요.")
        if (!audioFile.exists()) return ServiceResult(false, "파일을 찾을 수 없습니다: ${audioFile.name}")

        val sizeMb = audioFile.length() / (1024.0 * 1024.0)
        if (sizeMb > 25) {
            return ServiceResult(false, "파일 크기(${String.format("%.1f", sizeMb)}MB)가 Whisper 최대 25MB를 초과합니다.\nCLOVA Speech(최대 200MB) 또는 Gemini(최대 50MB)를 사용해주세요.")
        }

        onProgress?.invoke(5)
        onStatus?.invoke("Whisper STT 준비 중... (${String.format("%.1f", sizeMb)}MB)")

        return try {
            val mimeType = when (audioFile.extension.lowercase()) {
                "mp3" -> "audio/mp3"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/mp4"
            }

            // Whisper API는 multipart/form-data로 파일 전송
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(mimeType.toMediaType())
                )
                .addFormDataPart("model", STT_MODEL)
                .addFormDataPart("language", "ko")
                .addFormDataPart("response_format", "text")
                .build()

            onProgress?.invoke(30)
            onStatus?.invoke("Whisper STT 변환 중... (1~5분 소요)")

            val request = Request.Builder()
                .url(WHISPER_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            onProgress?.invoke(90)

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                return ServiceResult(false, "Whisper STT 오류 (HTTP ${response.code}): ${friendlyError(errorBody)}")
            }

            val text = response.body?.string()?.trim() ?: ""
            if (text.isBlank()) {
                return ServiceResult(false, "Whisper 응답이 비어 있습니다. 오디오 파일을 확인해주세요.")
            }

            // Whisper는 화자 구분이 없으므로, numSpeakers > 1이면 후처리 안내
            val finalText = if (numSpeakers > 1) {
                "[참고: Whisper는 화자 구분을 지원하지 않습니다. 화자 구분이 필요하면 CLOVA Speech를 사용해주세요.]\n\n$text"
            } else {
                text
            }

            onProgress?.invoke(100)
            onStatus?.invoke("✅ Whisper STT 변환 완료")
            Log.d(TAG, "Whisper STT completed — ${finalText.length} chars")
            ServiceResult(true, finalText)
        } catch (e: Exception) {
            Log.e(TAG, "Whisper STT failed", e)
            ServiceResult(false, "Whisper STT 오류: ${e.message?.take(300)}")
        }
    }

    // ── GPT-4o 요약 ────────────────────────────────────────────

    fun summarize(
        sttText: String,
        apiKey: String,
        summaryMode: String = "speaker",
        customInstruction: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "OpenAI API 키가 없습니다.")
        if (sttText.isBlank()) return ServiceResult(false, "변환된 텍스트가 비어 있습니다.")

        val template = when (summaryMode) {
            "topic" -> GeminiService.SUMMARY_TOPIC
            "formal_md" -> GeminiService.SUMMARY_FORMAL_MD
            "ir_md" -> GeminiService.SUMMARY_IR_MD
            "phone" -> GeminiService.SUMMARY_PHONE
            "flow" -> GeminiService.SUMMARY_FLOW
            "lecture_md" -> GeminiService.SUMMARY_LECTURE_MD
            else -> GeminiService.SUMMARY_SPEAKER
        }

        return try {
            onProgress?.invoke(10)
            val dt = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREAN).format(Date())
            var prompt = template.replace("{text}", sttText.take(500000)).replace("{dt}", dt)
            if (customInstruction.isNotBlank()) {
                prompt += "\n\n[추가 지시사항]\n${customInstruction.trim()}"
            }
            onProgress?.invoke(30)

            val body = JsonObject().apply {
                addProperty("model", SUMMARY_MODEL)
                addProperty("max_tokens", 8192)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    })
                })
            }

            val request = Request.Builder()
                .url(CHAT_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            onProgress?.invoke(90)

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                return ServiceResult(false, "GPT-4o 오류 (HTTP ${response.code}): ${friendlyError(errorBody)}")
            }

            val respJson = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val choices = respJson?.getAsJsonArray("choices")
            val text = choices?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: return ServiceResult(false, "GPT-4o 요약 응답이 비어 있습니다.")

            onProgress?.invoke(100)
            Log.d(TAG, "GPT-4o summary completed — ${text.length} chars")
            ServiceResult(true, text.trim())
        } catch (e: Exception) {
            Log.e(TAG, "GPT-4o summary failed", e)
            ServiceResult(false, "GPT-4o 오류: ${e.message?.take(300)}")
        }
    }

    // ── 유틸리티 ────────────────────────────────────────────────

    private fun friendlyError(msg: String): String = when {
        "invalid_api_key" in msg || "Incorrect API key" in msg ->
            "API 키가 올바르지 않습니다. 키를 다시 확인해주세요."
        "insufficient_quota" in msg || "exceeded" in msg ->
            "API 사용 한도를 초과했습니다. OpenAI 대시보드에서 결제를 확인해주세요."
        "rate_limit" in msg || "429" in msg ->
            "요청 한도 초과입니다. 잠시 후 다시 시도해주세요."
        "model_not_found" in msg || "404" in msg ->
            "모델을 찾을 수 없습니다. ($SUMMARY_MODEL)"
        "timeout" in msg.lowercase() ->
            "처리 시간이 초과되었습니다."
        else -> msg.take(300)
    }
}
