package com.krunventures.meetingrecorder.service

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GeminiService {

    data class ServiceResult(val success: Boolean, val text: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)  // 3시간 녹음 STT 처리 대기
        .writeTimeout(300, TimeUnit.SECONDS)  // 대용량 파일 업로드 대기
        .build()
    private val gson = Gson()

    // 사용 가능한 모델 (자동 감지 후 설정됨)
    private var activeModel: String = "gemini-2.5-flash"

    companion object {
        private val MODEL_CANDIDATES = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-pro"
        )
        const val STT_MODEL = "gemini-2.5-flash"
        const val SUMMARY_MODEL = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        val SUMMARY_SPEAKER = """당신은 전문 회의록 작성 전문가입니다.
다음 회의 전사 내용을 바탕으로 화자(참석자) 중심의 한국어 회의록을 작성해주세요.

[전사 내용]
{text}

[회의록 양식]
## 📋 회의록 (화자 중심)
생성 일시: {dt}

### 1. 회의 개요
- 주요 안건:
- 참석자:

### 2. 참석자별 주요 내용

### 3. 액션 아이템
| 담당자 | 내용 | 기한 |
|--------|------|------|

---
회의녹음요약 앱 자동 생성"""

        val SUMMARY_TOPIC = """당신은 전문 회의록 작성 전문가입니다.
다음 회의 전사 내용을 바탕으로 주제/안건 중심의 한국어 회의록을 작성해주세요.

[전사 내용]
{text}

[회의록 양식]
## 📋 회의록 (주제 중심)
생성 일시: {dt}

### 1. 회의 개요
- 참석자(언급된 경우):
- 회의요약: 3줄내외(경영진보고 수준)

### 2. 논의 내용

### 3. 추가 논의 필요사항

---
회의녹음요약 앱 자동 생성"""

        val SUMMARY_FORMAL_MD = """너는 복잡한 회의 녹취록을 분석하여 핵심 정보를 체계적으로 정리하는 비즈니스 컨설턴트야.
제공된 텍스트의 모든 논의 사항을 놓치지 않으면서도, 읽기 쉽게 요약하여 마크다운(MD) 형식으로 회의록을 작성해줘.

[작성 가이드라인]
1. 완결성: 회의에서 언급된 모든 수치, 업체명, 전략, 리스크 및 결정 사항을 빠짐없이 포함할 것.
2. 구조화: 주요 내용을 위계에 따라 분류할 것.
3. 간결성: 명사형 종결이나 '~함', '~임' 등의 간결한 문체를 사용할 것.
4. Action Item: 담당자, 기한, 구체적인 실행 과제를 명확히 추출할 것.

[전사 내용]
{text}

[출력 양식]
# 회 의 록
생성 일시: {dt}

---
| 항목 | 내용 |
|------|------|
| 일 시 | {dt} |
| 장 소 | |
| 주 제 | |
| 참 석 자 | |
| 작 성 자 | AI 자동 생성 |

## 1. 회의 배경 및 목적
## 2. 주요 논의 내용
## 3. 결정 사항
## 4. 리스크 및 우려 사항
## 5. Action Items

---
회의녹음요약 앱 자동 생성"""

        val SUMMARY_FLOW = """당신은 전문 회의록 작성 전문가입니다.
다음 회의 전사 내용을 바탕으로 시간 순서대로 회의 흐름을 정리해주세요.
각 논의의 맥락과 전환 과정을 명확히 보여주고, 발언 순서와 의사결정 과정이 드러나도록 작성해주세요.

[전사 내용]
{text}

[회의록 양식]
## 📋 회의록 (흐름 중심)
생성 일시: {dt}

### 1. 회의 시작
- 배경/목적:
- 참석자:

### 2. 논의 흐름
(시간 순서대로 각 논의 단계를 정리. 논의 주제가 전환되는 포인트를 명시)

#### 흐름 1: [첫 번째 논의 주제]
- 발단:
- 주요 발언:
- 결론/합의:

#### 흐름 2: [두 번째 논의 주제]
...

### 3. 최종 결론 및 후속 조치
| 항목 | 내용 | 담당자 |
|------|------|--------|

---
회의녹음요약 앱 자동 생성"""

        val SUMMARY_LECTURE_MD = """당신은 전문 강의 노트 작성자입니다.
아래 강의 녹취록을 분석하여 수강생이 복습과 학습에 바로 활용할 수 있는 구조화된 마크다운 강의 요약문을 작성해주세요.

[강의 녹취록]
{text}

[작성 양식]
생성 일시: {dt}

# 📚 강의 요약 노트
**주요 주제**: (핵심 주제 한 줄 요약)

## 1. 강의 개요
## 2. 주요 내용 정리
## 3. 핵심 요약 (3줄 정리)

---
강의녹음요약 앱 자동 생성"""
    }

    // ── REST API 직접 호출 ──────────────────────────────────────

    private fun callGeminiApi(
        model: String,
        apiKey: String,
        contents: JsonArray,
        temperature: Float = 0.3f,
        maxOutputTokens: Int = 65536
    ): ServiceResult {
        val trimmedKey = apiKey.trim()
        val url = "$BASE_URL/$model:generateContent"

        val bodyJson = JsonObject().apply {
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", temperature)
                addProperty("maxOutputTokens", maxOutputTokens)
            })
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-api-key", trimmedKey)
                .post(gson.toJson(bodyJson).toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return ServiceResult(false, friendlyError(respBody))
            }

            val json = gson.fromJson(respBody, JsonObject::class.java)
                ?: return ServiceResult(false, "응답 파싱 실패: 빈 응답")
            val candidates = json.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                // 차단된 응답인지 확인
                val blockReason = json.getAsJsonObject("promptFeedback")
                    ?.get("blockReason")?.asString
                if (blockReason != null) {
                    return ServiceResult(false, "Gemini 응답 차단: $blockReason")
                }
                return ServiceResult(false, "Gemini 응답이 비어 있습니다. 응답: ${respBody.take(200)}")
            }
            val content = candidates[0]?.asJsonObject?.getAsJsonObject("content")
                ?: return ServiceResult(false, "응답 콘텐츠가 비어 있습니다.")
            val parts = content.getAsJsonArray("parts")
            if (parts == null || parts.size() == 0) {
                return ServiceResult(false, "응답 파트가 비어 있습니다.")
            }
            val text = parts[0]?.asJsonObject?.get("text")?.asString
                ?: return ServiceResult(false, "응답 텍스트가 비어 있습니다.")
            ServiceResult(true, text)
        } catch (e: Exception) {
            ServiceResult(false, "연결 오류: ${e.message?.take(200)}")
        }
    }

    private fun makeTextContents(prompt: String): JsonArray {
        return JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "user")
                add("parts", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("text", prompt)
                    })
                })
            })
        }
    }

    // ── 공개 API ────────────────────────────────────────────────

    fun testConnection(apiKey: String): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "API 키가 비어 있습니다. 키를 입력하고 '저장'을 먼저 눌러주세요.")

        val contents = makeTextContents("안녕하세요. 테스트입니다. 한 문장으로 답해주세요.")

        // 여러 모델을 순서대로 시도하여 사용 가능한 모델 자동 감지
        for (model in MODEL_CANDIDATES) {
            val result = callGeminiApi(model, apiKey, contents, maxOutputTokens = 30)
            if (result.success) {
                activeModel = model  // 성공한 모델 저장
                return ServiceResult(true, "✅ 연결 성공! ($model 사용 가능)")
            }
        }

        // 모든 모델 실패
        return ServiceResult(false, "❌ 사용 가능한 Gemini 모델을 찾지 못했습니다.\n\n" +
                "시도한 모델: ${MODEL_CANDIDATES.joinToString(", ")}\n\n" +
                "API 키: ${apiKey.trim().take(8)}...${apiKey.trim().takeLast(4)}\n\n" +
                "Google AI Studio(aistudio.google.com)에서 키를 확인해주세요.")
    }

    fun transcribe(
        audioFile: File,
        apiKey: String,
        numSpeakers: Int = 0,
        onProgress: ((Int) -> Unit)? = null,
        onStatus: ((String) -> Unit)? = null
    ): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "Gemini API 키가 없습니다. 설정에서 입력해주세요.")
        if (!audioFile.exists()) return ServiceResult(false, "파일을 찾을 수 없습니다: ${audioFile.name}")

        val sizeMb = audioFile.length() / (1024.0 * 1024.0)
        if (sizeMb > 50) {
            return ServiceResult(false, "파일 크기(${String.format("%.1f", sizeMb)}MB)가 Gemini STT 최대 50MB를 초과합니다.\nCLOVA Speech(최대 200MB)를 사용해주세요.")
        }
        val sttPrompt = makeSttPrompt(numSpeakers)

        onProgress?.invoke(5)
        onStatus?.invoke("STT 변환 준비 중... (${String.format("%.1f", sizeMb)}MB)")

        // 오디오 파일을 Base64로 인코딩
        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: OutOfMemoryError) {
            return ServiceResult(false, "메모리 부족: 파일이 너무 큽니다(${String.format("%.1f", sizeMb)}MB).\nCLOVA Speech를 사용해주세요.")
        }
        val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        val mimeType = when (audioFile.extension.lowercase()) {
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "audio/mp4"
        }

        onStatus?.invoke("Gemini STT 변환 중... (1~10분 소요)")
        onProgress?.invoke(30)

        // 오디오 + 텍스트 프롬프트 구성
        val contents = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "user")
                add("parts", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("text", sttPrompt)
                    })
                    add(JsonObject().apply {
                        add("inline_data", JsonObject().apply {
                            addProperty("mime_type", mimeType)
                            addProperty("data", audioBase64)
                        })
                    })
                })
            })
        }

        onProgress?.invoke(40)

        val result = callGeminiApi(activeModel, apiKey, contents, temperature = 0.1f)

        if (result.success) {
            onProgress?.invoke(100)
            onStatus?.invoke("✅ Gemini STT 변환 완료")
        }

        return result
    }

    fun summarize(
        sttText: String,
        apiKey: String,
        summaryMode: String = "speaker",
        customInstruction: String = "",
        onProgress: ((Int) -> Unit)? = null
    ): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "Gemini API 키가 없습니다.")
        if (sttText.isBlank()) return ServiceResult(false, "변환된 텍스트가 비어 있습니다.")

        val template = getTemplate(summaryMode)
        onProgress?.invoke(10)

        val dt = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREAN).format(Date())
        var prompt = template.replace("{text}", sttText.take(500000)).replace("{dt}", dt)
        if (customInstruction.isNotBlank()) {
            prompt += "\n\n[추가 지시사항]\n${customInstruction.trim()}"
        }
        onProgress?.invoke(30)

        val contents = makeTextContents(prompt)
        val result = callGeminiApi(activeModel, apiKey, contents, temperature = 0.3f)

        if (result.success) {
            onProgress?.invoke(100)
            return ServiceResult(true, trimSummary(result.text))
        }
        return result
    }

    fun extractKeyMetrics(summaryText: String, apiKey: String): ServiceResult {
        if (apiKey.isBlank()) return ServiceResult(false, "Gemini API 키가 없습니다.")
        if (summaryText.isBlank()) return ServiceResult(false, "요약 텍스트가 비어 있습니다.")

        val prompt = """다음 회의록 요약에서 핵심 정보를 추출해주세요.

[요약 텍스트]
${summaryText.take(100000)}

[출력 형식]
📋 결정사항
- (결정된 사항 나열, 없으면 "없음")

✅ 액션 아이템
- 담당자: X | 업무: Y | 기한: Z (없으면 "없음")

📅 주요 일정
- (날짜/기한 포함 일정, 없으면 "없음")

🔢 핵심 수치
- (금액, 기간, 비율 등 주요 숫자, 없으면 "없음")

🏷️ 키워드
- (3~7개, 쉼표로 구분)"""

        val contents = makeTextContents(prompt)
        return callGeminiApi(activeModel, apiKey, contents, temperature = 0.2f, maxOutputTokens = 4096)
    }

    // ── 유틸리티 ────────────────────────────────────────────────

    private fun makeSttPrompt(numSpeakers: Int): String {
        val rule = when {
            numSpeakers == 1 -> "- 화자가 1명이므로 화자 구분 없이 전사\n"
            numSpeakers >= 2 -> "- 화자가 ${numSpeakers}명입니다. [화자1], [화자2]${if (numSpeakers >= 3) ", [화자3]" else ""} 형식으로 구분\n"
            else -> "- 여러 화자는 [화자1], [화자2] 형식으로 구분\n"
        }
        return """이 오디오 파일을 한국어 텍스트로 정확히 전사해주세요.

규칙:
${rule}- 불명확한 부분은 [불명확] 표시
- 의미 없는 짧은 반복(어, 음 등)은 생략
- 전사 결과만 출력하고 설명 없이 바로 시작"""
    }

    private fun getTemplate(mode: String): String = when (mode) {
        "topic" -> SUMMARY_TOPIC
        "formal_md" -> SUMMARY_FORMAL_MD
        "flow" -> SUMMARY_FLOW
        "lecture_md" -> SUMMARY_LECTURE_MD
        else -> SUMMARY_SPEAKER
    }

    private fun trimSummary(text: String): String {
        val marker = "회의녹음요약 앱 자동 생성"
        val idx = text.indexOf(marker)
        return if (idx != -1) text.substring(0, idx + marker.length).trim() else text.trim()
    }

    private fun friendlyError(msg: String): String = when {
        "API_KEY_INVALID" in msg || "API key not valid" in msg -> "API 키가 올바르지 않습니다. 키를 다시 확인해주세요."
        "UNAUTHENTICATED" in msg || "401" in msg -> "API 인증 실패입니다. 키를 다시 확인해주세요."
        "PERMISSION_DENIED" in msg || "403" in msg -> "API 키 권한이 없습니다. Google AI Studio에서 확인해주세요."
        "NOT_FOUND" in msg || "404" in msg -> "모델을 찾을 수 없습니다. 잠시 후 다시 시도해주세요."
        "429" in msg || "RESOURCE_EXHAUSTED" in msg -> "API 요청 한도 초과입니다. 잠시 후 다시 시도해주세요."
        "timeout" in msg.lowercase() -> "처리 시간이 초과되었습니다."
        else -> "Gemini 오류: ${msg.take(300)}"
    }
}
