package com.krunventures.meetingrecorder.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ClaudeService {
    companion object {
        const val MODEL = "claude-sonnet-4-6"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
    }

    // ★ v3.0: Read Timeout 300초→1800초 (30분) — 긴 회의록 요약 시 타임아웃 방지
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(1800, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class ServiceResult(val success: Boolean, val text: String)

    fun testConnection(apiKey: String): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "Claude API 키가 없습니다.")
        return try {
            val body = JsonObject().apply {
                addProperty("model", MODEL)
                addProperty("max_tokens", 10)
                val messages = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", "안녕")
                    })
                }
                add("messages", messages)
            }
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                ServiceResult(true, "연결 성공! ($MODEL)")
            } else {
                ServiceResult(false, "연결 실패 (HTTP ${response.code})")
            }
        } catch (e: Exception) {
            ServiceResult(false, "오류: ${e.message}")
        }
    }

    fun summarize(
        sttText: String,
        apiKey: String,
        summaryMode: String = "speaker",
        customInstruction: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "Claude API 키가 없습니다.")
        if (sttText.isBlank()) return ServiceResult(false, "변환된 텍스트가 비어 있습니다.")

        val template = when (summaryMode) {
            "topic" -> GeminiService.SUMMARY_TOPIC
            "formal_md" -> GeminiService.SUMMARY_FORMAL_MD
            "ir_md" -> GeminiService.SUMMARY_IR_MD
            "phone" -> GeminiService.SUMMARY_PHONE
            "flow" -> GeminiService.SUMMARY_FLOW
            "lecture_md" -> GeminiService.SUMMARY_LECTURE_MD
            "conference" -> GeminiService.SUMMARY_CONFERENCE
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
                addProperty("model", MODEL)
                addProperty("max_tokens", 8192)
                val messages = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    })
                }
                add("messages", messages)
            }

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            onProgress?.invoke(90)

            if (!response.isSuccessful) {
                return ServiceResult(false, "Claude API 오류 (HTTP ${response.code})")
            }

            val respJson = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val content = respJson.getAsJsonArray("content")
            val text = content?.get(0)?.asJsonObject?.get("text")?.asString
                ?: return ServiceResult(false, "요약 응답이 비어 있습니다.")

            onProgress?.invoke(100)
            ServiceResult(true, text.trim())
        } catch (e: Exception) {
            ServiceResult(false, "Claude 오류: ${e.message?.take(300)}")
        }
    }
}
