package com.krunventures.meetingrecorder.service

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

class ClovaService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)  // 긴 녹음 대응 (최대 10분)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class TranscribeResult(val success: Boolean, val text: String)

    fun testConnection(invokeUrl: String, secretKey: String): TranscribeResult {
        if (invokeUrl.isBlank() || secretKey.isBlank()) {
            return TranscribeResult(false, "Invoke URL과 Secret Key를 모두 입력해주세요.")
        }
        val url = invokeUrl.trimEnd('/') + "/recognizer/upload"
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("X-CLOVASPEECH-API-KEY", secretKey.trim())
                .post(FormBody.Builder().build())
                .build()
            val response = client.newCall(request).execute()
            when (response.code) {
                400, 200 -> TranscribeResult(true, "CLOVA Speech 연결 성공")
                401 -> TranscribeResult(false, "인증 실패 (HTTP 401): Secret Key를 다시 확인해주세요.")
                403 -> TranscribeResult(false, "접근 거부 (HTTP 403): CLOVA Speech 서비스 이용 신청을 확인해주세요.")
                else -> TranscribeResult(true, "응답 확인 (HTTP ${response.code}) — 정상 연결")
            }
        } catch (e: Exception) {
            TranscribeResult(false, "연결 오류: ${e.message}")
        }
    }

    fun transcribe(
        audioFile: File,
        invokeUrl: String,
        secretKey: String,
        numSpeakers: Int = 0,
        onProgress: ((Int) -> Unit)? = null,
        onStatus: ((String) -> Unit)? = null
    ): TranscribeResult {
        if (!audioFile.exists()) return TranscribeResult(false, "파일을 찾을 수 없습니다: ${audioFile.name}")
        if (invokeUrl.isBlank() || secretKey.isBlank()) return TranscribeResult(false, "Invoke URL과 Secret Key를 설정에서 입력해주세요.")

        val fileSizeMb = audioFile.length() / (1024.0 * 1024.0)
        if (fileSizeMb > 200) return TranscribeResult(false, "파일 크기(${String.format("%.1f", fileSizeMb)}MB)가 최대 200MB를 초과합니다.")

        onStatus?.invoke("CLOVA Speech 변환 시작... (${String.format("%.1f", fileSizeMb)}MB)")
        onProgress?.invoke(5)

        val params = JsonObject().apply {
            addProperty("language", "ko-KR")
            addProperty("completion", "sync")
            addProperty("resultToDisplay", true)
            addProperty("noiseFiltering", true)
            addProperty("wordAlignment", false)
            val diarization = JsonObject().apply {
                addProperty("enable", true)
                if (numSpeakers > 0) {
                    addProperty("speakerCountMax", numSpeakers)
                    addProperty("speakerCountMin", 1)
                } else {
                    addProperty("speakerCountMax", 8)
                    addProperty("speakerCountMin", 1)
                }
            }
            add("diarization", diarization)
        }

        val url = invokeUrl.trimEnd('/') + "/recognizer/upload"
        onStatus?.invoke("서버에 업로드 중...")
        onProgress?.invoke(15)

        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("media", audioFile.name,
                    audioFile.asRequestBody("application/octet-stream".toMediaType()))
                .addFormDataPart("params", gson.toJson(params))
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("X-CLOVASPEECH-API-KEY", secretKey.trim())
                .post(requestBody)
                .build()

            onStatus?.invoke("변환 처리 중... (긴 녹음은 수 분 소요)")
            onProgress?.invoke(30)

            val response = client.newCall(request).execute()
            onProgress?.invoke(80)

            if (response.code != 200) {
                val body = response.body?.string() ?: ""
                return TranscribeResult(false, "API 오류 (HTTP ${response.code}): ${body.take(200)}")
            }

            val data = gson.fromJson(response.body?.string(), JsonObject::class.java)
            onProgress?.invoke(90)
            onStatus?.invoke("결과 정리 중...")

            val text = formatResult(data)
            if (text.isBlank()) {
                return TranscribeResult(false, "변환 결과가 비어 있습니다. 오디오 품질을 확인해주세요.")
            }

            onProgress?.invoke(100)
            onStatus?.invoke("✅ CLOVA Speech 변환 완료")
            TranscribeResult(true, text)
        } catch (e: Exception) {
            TranscribeResult(false, "오류: ${e.message}")
        }
    }

    private fun formatResult(data: JsonObject): String {
        val segments = data.getAsJsonArray("segments") ?: return data.get("text")?.asString ?: ""
        val lines = mutableListOf<String>()
        var currentSpeaker: String? = null
        val currentTexts = mutableListOf<String>()

        for (seg in segments) {
            val obj = seg.asJsonObject
            val text = obj.get("text")?.asString?.trim() ?: continue
            if (text.isEmpty()) continue

            val diar = obj.getAsJsonObject("diarization")
            val label = diar?.get("label")?.asString ?: ""
            val speaker = if (label.isNotEmpty()) "[화자$label]" else ""

            if (speaker != currentSpeaker) {
                if (currentSpeaker != null && currentTexts.isNotEmpty()) {
                    val block = currentTexts.joinToString("")
                    lines.add(if (currentSpeaker!!.isNotEmpty()) "$currentSpeaker $block" else block)
                }
                currentSpeaker = speaker
                currentTexts.clear()
                currentTexts.add(text)
            } else {
                currentTexts.add(" $text")
            }
        }
        if (currentTexts.isNotEmpty()) {
            val block = currentTexts.joinToString("")
            lines.add(if (currentSpeaker?.isNotEmpty() == true) "$currentSpeaker $block" else block)
        }
        return lines.joinToString("\n")
    }
}
