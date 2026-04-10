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

        // ── 요약 프롬프트 - 주간회의 (화자 중심) ───────────────
        val SUMMARY_SPEAKER = """당신은 벤처캐피탈(K-Run Ventures) 파트너 주간회의의 전문 회의록 작성자입니다.
아래 회의 전사 내용을 바탕으로 화자 중심의 한국어 회의록을 작성해주세요.

[화자 구분 코드]
녹취록에 등장하는 호칭, 직함, 발언 위계, 지시/보고 패턴을 분석하여 아래 코드로 자동 매핑하세요.
- [K2]: 대표이사 (회의 주재, 최종 지시, "대표" 또는 "대표님" 호칭 등)
- [K3]: 부사장 (중간 관리, "부사장" 호칭 등)
- [K1]: 파트너 (포트폴리오 보고, 딜 소싱 담당, "파트너" 호칭 등)
- [S1]: 심사역 (실무 보고, "심사역" 호칭 등)
- 호칭이나 역할이 불명확한 경우: [역할불명확] 으로 표기

[작성 원칙]
1. 사실 중심 기록: 녹취록에 명시된 내용만 기록
2. 화자 구분: 위 코드로 화자를 명확히 표기
3. 데이터 정밀도: 모든 수치·금액·일자를 정확히 기록. 불확실한 경우 [약] 또는 [추정] 표기
4. 추측 금지: 녹취록에 없는 내용은 절대 추가하지 않음. 불분명한 발언은 [불명확] 표기
5. 구조: 화자별로 요약하되, 각 화자 내에서 주제별로 재분류

[전사 내용]
{text}

[회의록 출력 양식]
# 주간회의록
생성 일시: {dt}

---

## 주요 내용

### [K?] (실제 식별된 코드로 대체)

#### [주제명]
- [내용]

#### [주제명]
- [내용]

### [K?] (실제 식별된 코드로 대체)

#### [주제명]
- [내용]

(화자 수에 따라 반복)

---
*본 회의록은 녹취 텍스트를 기반으로 AI가 자동 작성하였습니다. 수치·사실관계 확인이 필요한 사항은 추가 검토 바랍니다.*

---
회의녹음요약 앱 자동 생성"""

        // ── 요약 프롬프트 - 다자간 협의 (안건 중심) ───────────────
        val SUMMARY_TOPIC = """당신은 전문 비즈니스 회의록 작성자입니다.
아래 회의 전사 내용은 기관 협의, 다자간 공식회의, 주주총회 등 복수의 이해관계자가 참석한 외부 미팅 녹취록입니다.
안건·주제 중심으로 구조화하여 공식 회의록 형식으로 작성해주세요.

[화자 표기 기준]
- K-Run Ventures 소속 참석자: [케이런]으로 통일 표기
- 외부 참석자: 실명·직함·소속으로 표기 (예: [원동연 대표], [IBK 심사역], [A기관 부장])
- 발언자 불명확한 경우: [불명확] 표기

[작성 원칙]
1. 공식 회의록 기준: 안건별로 협의 내용, 각 기관 입장, 결정사항을 명확히 기록
2. 사실·팩트 중심: 녹취록에 명시된 수치·발언·결정만 기록. 추측·임의 추가 금지. 불확실한 수치는 [추정] 표기
3. 상세 서술 원칙: 핵심 논의 내용은 배경·경위·수치·결론을 포함하여 충분히 상세하게 서술. 지나친 압축 금지
4. Q&A 주석 형식: 질의응답은 반드시 아래 주석 형식으로 작성.
   - Q와 A는 각각 별도 blockquote(>) 줄로 작성하며, Q와 A 사이에 반드시 빈 줄 1줄 삽입
   - 서로 다른 Q&A 블록 사이에도 반드시 빈 줄 1줄 삽입
   - "미팅에서 처음 확인" 등 출처 태그 기재하지 않음
   형식 예시:
   > **Q [케이런]** 질의 내용

   > **A [상대방 실명]** 답변 내용

   > **Q [케이런]** 다음 질의

   > **A [상대방 실명]** 다음 답변
5. 소제목 내용 구조화: 소제목 아래 내용은 나열식 서술 대신 **배경**, **주요 내용**, **합의** 등 굵은 소항목으로 구분하여 정리
6. 결정사항 명확화: 합의·결정·보류·재협의 여부를 명시

[전사 내용]
{text}

[출력 양식]
# 다자간 협의

| 항목 | 내용 |
|------|------|
| 일 시 | {dt} |
| 장 소 | |
| 회의명 / 안건 | (녹취록에서 자동 식별) |
| 참 석 기 관 | (참석 기관 및 인원 자동 식별) |
| 케이런 참석자 | [케이런] |
| 작 성 자 | AI 자동 생성 |

---

## 회의 요약
(회의 목적, 주요 협의사항, 결론을 3줄 내외 경영진 보고 수준으로 요약. 문체는 "~음" 종결)

---

## 협의 내용

# 1. [안건 제목]

## 1.1 [소제목]

**배경**
(경위·맥락·수치 포함 서술. 문체는 "~음" 종결)

**주요 내용**
(핵심 협의 내용 서술)

> **Q [케이런]** (질의 내용)

> **A [상대방 실명]** (답변 내용)

> **Q [케이런]** (다음 질의)

> **A [상대방 실명]** (다음 답변)

## 1.2 [소제목]
(소제목 수에 따라 반복)

---

# 2. [안건 2 제목]
(안건 수에 따라 반복)

---
*본 회의록은 녹취 텍스트를 기반으로 AI가 자동 작성하였습니다.*

---
회의녹음요약 앱 자동 생성"""

        // ── 요약 프롬프트 - 회의록(업무) — 투자업체 사후관리 ───────────────
        val SUMMARY_FORMAL_MD = """당신은 벤처캐피탈(K-Run Ventures)의 전문 투자사후관리 회의록 작성자입니다.
아래 회의 전사 내용은 투자업체 또는 포트폴리오사와의 외부 미팅 녹취록입니다.
투자 집행 이후 사후관리 목적의 업무 협의(경영현황 보고, 추가 지원 협의, 이슈 점검, 상장·Exit 전략 논의 등)에 특화하여 회의록을 작성합니다.

[화자 표기 기준]
- K-Run Ventures 소속 참석자: [케이런]으로 통일 표기
- 투자업체(포트폴리오사) 참석자: 역할 또는 이름으로 표기 (예: [대표], [CFO], [담당자])
- 불명확한 경우: [불명확]으로 표기

[작성 원칙]
1. 전수 포착 원칙: 녹취록에서 논의된 모든 주제를 빠짐없이 대제목(#)으로 분류하여 작성. 분량이 작거나 부수적으로 언급된 내용도 생략하지 않음
2. 사실 중심 기록: 녹취록에 명시된 내용만 기록. 임의 추가·추론 금지
3. 데이터 정밀도: 매출·수치·일자·인명·기관명을 정확히 기록. 불확실한 경우 [추정] 표기
4. 포착 필수 주제 (아래 항목이 녹취에서 언급된 경우 반드시 별도 대제목으로 작성):
   - 재무 실적: 매출, 영업손익, 현금흐름, 공헌이익, 일회성 비용 등
   - 사업 전략: 신사업, 기존 사업 고도화, 조직 개편, 비용 절감 등
   - 분기/월별 실적 트렌드: 직전 분기 또는 당해 분기 매출·손익 흐름
   - 상장(IPO) 추진 현황: 거래소 사전 협의, 주관사, 청구 일정, 거래소 피드백, 상장 요건
   - 투자·자금 조달: Pre-IPO, 후속 투자, 구주 매각, 밸류에이션, 투자사 펀드 만기 이슈
   - 케이런 펀드 관련: 만기·연장 현황, 회수 전략, 조합원 보고 사항
   - 주요 고객·파트너십: 계약 현황, 신규 계약, 해지·리스크
   - 인력·조직: 채용, 감원, 조직 개편, 핵심 인력 이슈
   - 기타 케이런이 질의한 모든 사항
5. Q&A 형식 필수 준수:
   - Q와 A는 각각 별도 blockquote(>) 줄로 작성하며, Q와 A 사이에 반드시 빈 줄 1줄 삽입
   - 서로 다른 Q&A 블록 사이에도 반드시 빈 줄 1줄 삽입
   형식 예시:
   > **Q [케이런]** (질의 내용)

   > **A [상대방]** (답변 내용)

   > **Q [케이런]** (다음 질의)

   > **A [상대방]** (다음 답변)
6. 소제목 내용 구조화: 소제목 아래 내용은 나열식 서술 대신 해당 소제목의 주요 내용 흐름에 맞는 굵은 소항목 레이블(예: **매출 구성**, **거래소 피드백**, **청구 요건** 등)로 구분하여 정리. 고정 구조 없이 내용에 따라 자유롭게 명명함
7. 문체: 모든 서술 문장은 "~음" 종결 (예: "진행 중임", "완료함", "검토 중임")

[전사 내용]
{text}

[출력 양식]
# 회의록(업무)

| 항목 | 내용 |
|------|------|
| 일 시 | {dt} |
| 장 소 | |
| 대 상 기 업 | (투자업체명 자동 식별) |
| 참 석 자 | [케이런] / (상대방 자동 식별) |
| 미팅 목적 | (사후관리 / 경영현황 점검 / 추가 지원 / 이슈 논의 / 후속 투자 협의 중 해당 항목) |
| 작 성 자 | AI 자동 생성 |

---

## 미팅 요약
(핵심 협의 내용과 결론을 3줄 내외 경영진 보고 수준으로 요약. 문체는 "~음" 종결)

---

## 경영현황 점검

| 지표 | 내용 | 출처 (녹취 발언) |
|------|------|----------------|
| (매출 / ARR) | | |
| (현금 잔고 / 런웨이) | | |
| (인력 현황) | | |

※ 녹취록에서 언급된 수치만 기재. 미언급 항목은 행 자체를 생략. 각 수치는 화자의 실제 발언을 출처 컬럼에 인용.

---

## 주요 논의

※ 아래는 구조 예시임. 녹취에서 다뤄진 모든 주제를 대제목(#)으로 분류하여 빠짐없이 작성할 것.
※ 예시 주제: 재무 실적 점검 / 사업 전략 / 분기 실적 트렌드 / 상장(IPO) 추진 현황 / 투자·자금 조달 / 조직·인력 / 기타 논의사항

# 1. [주제명]

## 1.1 [소제목]

**[내용 흐름에 맞는 소항목 레이블 1]**
(해당 내용 서술. 문체는 "~음" 종결)

**[내용 흐름에 맞는 소항목 레이블 2]**
(해당 내용 서술)

> **Q [케이런]** (질의 내용)

> **A [상대방]** (답변 내용)

> **Q [케이런]** (다음 질의)

> **A [상대방]** (다음 답변)

## 1.2 [소제목]
(소제목 수에 따라 반복)

---

# 2. [주제명]
(주제 수에 따라 반복. 녹취에서 논의된 주제 수만큼 대제목을 생성할 것)

---
*본 회의록은 녹취 텍스트를 기반으로 AI가 자동 작성하였습니다.*

---
회의녹음요약 앱 자동 생성"""

        // ── 요약 프롬프트 - IR 미팅회의록 ───────────────
        val SUMMARY_IR_MD = """당신은 벤처캐피탈 K-Run Ventures의 전문 IR 미팅 노트 작성자임.
아래 전사 내용은 피투자사와의 IR 미팅 녹취록임.

■ 이 노트의 핵심 목적: "미팅에서만 얻을 수 있는 정보"를 기록하는 것임.
IR Deck·공개 자료만으로 알 수 있는 내용을 나열하는 것이 아니라,
실제 질의응답을 통해서만 확인할 수 있었던 정보·뉘앙스·판단 근거를 기록하는 것이 목적임.
*(미팅에서 처음 확인)* 태그가 많을수록 좋은 노트임.

[서술 원칙 — 전체 문서에 적용]
- 모든 서술 문장은 "~임", "~함", "~됨", "~있음" 형태로 마무리함
  예) "매출 구조는 SaaS 기반 구독 모델임." / "대표가 직접 확인함." / "구체 답변이 불가한 것으로 확인됨."
- 발언 인용은 "~라고 설명함", "~고 강조함", "~임을 인정함" 형태로 기술함
- "우수하다", "유망하다" 등 주관적 형용사 사용 금지

[화자 표기 기준]
- K-Run Ventures 소속 참석자(심사역·파트너·대표이사 등): [케이런]으로 통일 표기
- 피투자사 대표이사: 녹취록에서 파악한 회사명 또는 대표 역할로 [IR회사명]으로 표기
  예) 회사명이 "어썸"이면 → [어썸], "솔리드뷰"이면 → [솔리드뷰]
  ※ 회사명 확인 불가 시 [대표]로 표기
- 그 외 피투자사 참석자: 역할명 (예: [CTO], [CFO])
- 불명확한 경우: [불명확]

[정보 출처 태그 — 모든 핵심 정보에 반드시 부착]
- *(IR 자료)*: IR Deck·사업계획서에 이미 있던 정보
- *(미팅에서 처음 확인)*: 미팅 Q&A를 통해서만 알 수 있었던 정보 ← 많을수록 좋은 노트
- *(Pre-Screening 우려 → 미팅에서 해소)*: 사전 우려가 미팅에서 해소된 경우
- *(Pre-Screening 우려 → 미팅에서 미해소)*: 사전 우려가 여전히 열려 있는 경우

[Q&A 밀도 원칙 — 필수]
- 각 아젠다 섹션에 최소 2~3개의 실제 Q&A를 인라인 배치해야 함
- 녹취록에서 [케이런]이 질문하고 [IR회사명]이 답변한 모든 유의미한 교환을 빠짐없이 포착
- Q&A는 반드시 아래 형식으로 작성함. Q와 A는 각각 별도 줄로 작성하고, Q&A 블록 사이에는 반드시 빈 줄 1줄을 삽입하여 각 Q&A를 시각적으로 구분함:

  **Q [케이런]** 질의 내용을 한 문장으로 서술
  **A [IR회사명]** 답변 내용 서술. *(미팅에서 처음 확인 / IR 자료)* — 답변 톤: "자신있게 즉답함" / "잠시 머뭇거린 뒤 답변함" / "정확한 수치는 없다고 인정함"

  (다음 Q&A — 반드시 위 Q&A와 빈 줄로 구분)
  **Q [케이런]** 다음 질의
  **A [IR회사명]** 다음 답변 *(출처 태그)* — 답변 톤: ...

- 특히 기록해야 할 항목:
  a. IR Deck에 없던 새로운 정보: *(미팅에서 처음 확인)* 태그 부착
  b. IR회사 대표의 답변 톤·확신도: "자신있게 즉답함", "잠시 머뭇거린 뒤 답변함", "정확한 수치는 없다고 인정함"
  c. 케이런이 반복 질문하거나 추궁한 지점: 투자 판단에 크리티컬한 이슈
  d. IR회사가 회피하거나 못 답한 질문: [답변 회피] 또는 [구체 답변 불가]로 명시

[3대 핵심 분석 축 — 각 축에서 "미팅에서 새로 확인된 것"과 "IR에 이미 있던 것" 반드시 구분]
1. 기술 경쟁력: 경쟁사 기술 비교(최소 3~5개사), 해당 기업만의 차별점 vs 범용 기술, 기술 해자 냉정 평가
   → 미팅에서 경쟁사 대비 우위를 물었을 때 대표가 어떻게 답했는지가 핵심
2. 사업 경쟁력: 고객 lock-in 구조, 파이프라인 확정성, 매출 구조(단가×수량 분해), 리커링 비중, 해외 확장성
   → 매출 달성 근거·고객 계약 상태를 물었을 때의 답변이 핵심
3. 시장 크기: TAM/SAM/SOM, 시장 성장률(CAGR), "이 회사가 실제로 먹을 수 있는 규모"를 단가×수량으로 역산
   → "시장이 크다"는 IR Deck 주장을 그대로 옮기지 말고 실제 먹을 수 있는 규모를 냉정하게 역산할 것

[작성 원칙]
1. 사실 중심: 녹취록에 명시된 내용만 기록. 불분명한 부분은 [불명확] 표기
2. 데이터 정밀도: 수치·금액·일자 정확히 기록. 불확실한 경우 [약] 또는 [추정] 표기
3. 추측 금지: 녹취록에 없는 내용 절대 추가 안 함
4. 보완 주석: 대표 발언 중 기술적·사업적 맥락이 필요한 곳에 *(주석: ...)* 형태로 인라인 삽입
5. 전문 용어: 첫 출현 시 괄호 안에 설명 추가. 예) EPM(Electro Permanent Magnet, 전자영구자석)
6. STT 오인식: 기업명·인명이 어색하면 *(STT 오인식 의심)* 표기

[기술 해설 인용 블록 — 투자 판단에 크리티컬한 기술 2~3개에만 집중]
> 인용 블록 형태로 작성. 아래 3가지 유형 중 해당하는 것만 선택:

[유형 1] 핵심 기술 해설 — "이 기술이 뭔지, 왜 어려운지"
제목 형식: > **[기술명]란 무엇인가 — 기술 해설**

[유형 2] 경쟁 기술 비교 해설 — "이 기술의 대안은 뭐고, 왜 안 되는지"
제목 형식: > **왜 [경쟁기술방식]이 아닌가 — 경쟁 기술 비교**

[유형 3] 기술 선택의 합리성 — "왜 이 기술 방식을 선택했는가"
제목 형식: > **왜 [기술방식]인가 — 기술 선택의 구조적 이유**

[기술 해자 냉정 평가 — 회사 핵심 기술 주장 1~2개에만 적용]
제목 형식: > **[기술적 주석] "[회사 주장 요약]"의 실질적 의미 — 냉정한 평가**
(1) 주장 분해 → (2) 진짜 가치 → (3) 경쟁 경로 → (4) 해자 내구성 → (5) 결론

[펀드 적합성 Quick Check]
1. 섹터 적합성: 모빌리티·물류·교통·에너지 분야 해당 여부
2. 규약 해당: 모태(국토교통) / IBK(모빌리티) / KDB(남부권)
3. 지역: 남부권 여부
4. 투자 유형 판정: 주목적 적격 / 비목적 가능 / 부적합

[STT 녹취록]
{text}

[출력 양식]
생성 일시: {dt}

# IR 미팅회의록

---

## 기업 개요

| 항목 | 내용 |
|------|------|
| 기업명 | |
| 섹터 | |
| 투자 단계 | |
| 소재지 | |
| 설립 | |
| 직원 수 | |
| 참석자 (회사) | |
| 참석자 (케이런) | |
| 미팅 일시 | |
| **펀드 적합성** | ★☆☆☆☆ (1/5) — **주목적 적격 / 비목적 가능 / 부적합** |
| 　· 섹터 매칭 | [매칭 섹터] |
| 　· 지역 | [소재지] (남부권 여부) |
| 　· 규약 해당 | 모태(국토교통) / IBK(모빌리티) / KDB(남부권) |

---

## 1. 사업 개요

(비즈니스 모델, 핵심 제품·서비스, 수익 구조, 주요 양산 실적. 단가×수량 역산 포함.)

**Q [케이런]** (질의)
**A [IR회사명]** (답변) *(출처 태그)* — 답변 톤

---

## 2. 팀

| 이름 | 역할 | 경력 |
|------|------|------|
| | | |

---

## 3. 기술 및 제품

| 제품명 | 유형 | 핵심 역할 | 적용처 |
|--------|------|----------|--------|
| | | | |

| 구분 | 당사 | 경쟁사A | 경쟁사B | 경쟁사C |
|------|------|--------|--------|--------|
| 핵심 기술 | | | | |
| 성능 지표 | | | | |
| 가격 | | | | |
| 양산 실적 | | | | |

---

## 4. 파트너십 및 고객

파이프라인 확정성 분류:
- **확정**: [고객명] — [계약 현황]
- **높은 확률**: [고객명] — [근거]
- **미확정**: [고객명] — [상태]

---

## 5. 재무 및 펀딩

### 5-1. 재무 현황

| 연도 | 매출 (억원) | 영업이익 (억원) | 순이익 (억원) | 직원 수 | 주요 이벤트 |
|------|------------|---------------|-------------|--------|------------|
| | | | | | |

### 5-2. 펀딩 현황

| 라운드 | 금액 (억원) | 기업가치 (억원) | 투자사 | 일시 |
|--------|-----------|--------------|--------|------|
| | | | | |

---

## 6. 핵심 논의 포인트

### [논점 제목]
- **논의 내용**: Q&A 포함
- **투자 검토 포인트**: 투자 판단 영향 서술

---

## 합의 사항
- [합의 사항]

## 액션 아이템

| 담당 | 내용 | 기한 |
|------|------|------|
| 회사 | | |
| 케이런 | | |

---

## 투자 관점 초기 메모

| 항목 | 점수 (1~10) | 근거 |
|------|:-----------:|------|
| 기술 경쟁력 | | |
| 사업 경쟁력 (고객 견인) | | |
| 시장 크기 | | |
| 팀 역량 | | |
| 재무·펀딩 상태 | | |
| 리스크 수준 | | |
| **종합 (가중 평균)** | | |

**[긍정 시그널]**
- (미팅에서 확인된 강점·차별점)

**[우려 사항]**
- (미해소 리스크)

**[추가 확인 필요]**
- (다음 미팅/실사에서 확인할 사항)

---
*본 회의록은 STT 녹취 텍스트를 기반으로 AI가 자동 작성하였음. 사실관계 확인이 필요한 사항은 추가 검토 바람.*

---
회의녹음요약 앱 자동 생성"""

        // ── 요약 프롬프트 - 전화통화 메모 ───────────────
        val SUMMARY_PHONE = """당신은 비즈니스 전화통화 내용을 간결하고 정확하게 정리하는 전문 기록자입니다.
아래 전사 내용은 전화통화 녹음입니다.
통화 목적과 핵심 내용을 주제별로 1~2줄 요약하고, 보충 설명은 Q&A 주석 형태로 추가하세요.

[화자 표기 기준]
- 녹취록에서 K-Run Ventures 대표이사(파트너, 회의 주체) 발언 → [Antonio]로 표기
- 그 외 K-Run Ventures 동석자 → [케이런]으로 표기
- 상대방 → 실명 또는 역할명 (예: [서동조 대표], [대표], [담당자])

[작성 원칙]
1. 사실 중심 기록: 녹취록에 명시된 내용만 기록
2. 데이터 정밀도: 수치·금액·일자는 정확히 기록. 불확실한 경우 [추정] 표기
3. 추측 금지: 녹취록에 없는 내용은 절대 추가하지 않음. 미확인 사항은 주석에 "미확인/미언급"으로 명시
4. 주제별 압축: 각 주제는 1~2줄 핵심 요약으로 서술
5. Q&A 주석: 각 주제 하단에 보충이 필요한 사항을 아래 형식으로 추가. 팩트 기반만 작성.
   Q와 A는 각각 별도 blockquote(>) 줄로 작성하며, Q와 A 사이에 반드시 빈 줄 1줄 삽입.
   다른 Q&A 블록 사이에도 반드시 빈 줄 1줄 삽입.
   > **Q [케이런]** (질문 내용)

   > **A [상대방]** (답변 내용)
6. 출처 명시: A 항목에는 가능한 한 발언자와 실제 발언 내용을 인용
7. 문체: 모든 서술 문장은 "~음" 종결 (예: "진행 중임", "완료함")

[전사 내용]
{text}

[출력 양식]
# 전화통화 메모

| 항목 | 내용 |
|------|------|
| 일 시 | {dt} |
| 상 대 방 | [이름 및 소속/직함] |
| 통화 목적 | [핵심 목적 한 줄] |
| 통화 당사자 | [케이런] ↔ [상대방] |

---

## 통화 내용 요약

(전체 통화를 2~3줄로 압축 요약. 문체는 "~음" 종결)

---

## 주요 내용

# 1. [첫 번째 주제명]

**현황**
(주제 핵심을 1~2줄로 압축 서술)

> **Q [케이런]** [보충 질문]

> **A [상대방]** [팩트 기반 답변 — 발언자 및 발언 내용 인용. 미확인 시 명시]

> **Q [케이런]** [추가 보충 질문 — 필요한 경우만]

> **A [상대방]** [답변]

---

# 2. [두 번째 주제명]

**현황**
(주제 핵심을 1~2줄로 압축 서술)

> **Q [케이런]** [보충 질문]

> **A [상대방]** [팩트 기반 답변]

(주제 수에 따라 반복)

---

*AI 자동 생성 — 회의녹음요약 앱 | STT 원문 기반 팩트 한정 작성*"""

        // ── 요약 프롬프트 - 네트워킹(티타임) ───────────────
        val SUMMARY_FLOW = """당신은 비공식 비즈니스 네트워킹 대화를 정확하게 정리하는 전문 기록자입니다.
아래 전사 내용은 티타임·비공식 미팅·네트워킹 자리에서 오간 대화입니다.
주제별로 핵심을 1~2줄로 요약하고, 보충 설명이 필요한 부분은 Q&A 주석 형태로 추가하세요.

[화자 표기 기준]
- 녹취록에서 K-Run Ventures 대표이사(파트너, 회의 주체) 발언 → [Antonio]로 표기
- 그 외 K-Run Ventures 동석자 → [케이런]으로 표기
- 상대방 → 실명 또는 역할명 (예: [김상무], [대표], [담당자])

[작성 원칙]
1. 사실 중심 기록: 녹취록에 명시된 내용만 기록
2. 데이터 정밀도: 수치·금액·일자는 정확히 기록. 불확실한 경우 출처와 함께 [추정] 표기
3. 추측 금지: 녹취록에 없는 내용은 절대 추가하지 않음. 확인되지 않은 사항은 주석에 "미확인/미언급"으로 명시
4. 주제별 압축: 각 주제는 1~2줄 핵심 요약으로 서술
5. Q&A 주석: 각 주제 하단에 보충이 필요한 사항을 아래 형식으로 추가. 팩트 기반 내용만 작성.
   Q와 A는 각각 별도 blockquote(>) 줄로 작성하며, Q와 A 사이에 반드시 빈 줄 1줄 삽입.
   다른 Q&A 블록 사이에도 반드시 빈 줄 1줄 삽입.
   > **Q [케이런]** (질문 내용)

   > **A [상대방]** (답변 내용)
6. 출처 명시: A 항목에는 가능한 한 발언자와 실제 발언 내용을 인용
7. 소제목 내용 구조화: 소제목 아래 내용은 나열식 서술 대신 **현황**, **주요 내용** 등 굵은 소항목으로 구분하여 정리
8. 문체: 모든 서술 문장은 "~음" 종결

[전사 내용]
{text}

[출력 양식]
# 네트워킹(티타임)

| 항목 | 내용 |
|------|------|
| 일 시 | {dt} |
| 장 소 | [장소 또는 미상] |
| 상 대 방 | [참석자] |
| 작 성 자 | AI 자동 생성 |

---

## 회의 요약
(전체 대화를 2~3줄로 압축 요약. 문체는 "~음" 종결)

---

## 주요 논의

# 1. [첫 번째 주제명]

## 1.1 [소제목]

**현황**
(주제 핵심을 1~2줄로 압축 서술)

**주요 내용**
(상세 내용 서술)

> **Q [케이런]** [보충 질문]

> **A [상대방]** [팩트 기반 답변 — 발언자 및 발언 내용 인용. 미확인 시 명시]

> **Q [케이런]** [추가 보충 질문 — 필요한 경우만]

> **A [상대방]** [답변]

---

# 2. [두 번째 주제명]

## 2.1 [소제목]

**현황**
(주제 핵심을 1~2줄로 압축 서술)

**주요 내용**
(상세 내용 서술)

> **Q [케이런]** [보충 질문]

> **A [상대방]** [팩트 기반 답변]

(주제 수에 따라 반복)

---

*AI 자동 생성 — 회의녹음요약 앱 | STT 원문 기반 팩트 한정 작성*"""

        // ── 요약 프롬프트 - 강의 요약 ───────────────
        val SUMMARY_LECTURE_MD = """당신은 전문 강의 노트 작성자입니다.
아래 [강의 녹취록]을 분석하여 강의를 듣지 않은 사람도 내용을 완전히 이해할 수 있도록
상세하고 구조화된 마크다운 강의 요약문을 작성해주세요.

반드시 아래 녹취록의 실제 내용만을 바탕으로 작성하고, 임의로 내용을 추가하거나 가상의 시나리오를 작성하지 마세요.
강의 주제에 따라 맥락에 맞는 용어를 사용하세요(신앙/종교 강의라면 신앙 용어, 업무/실무 강의라면 전문 용어).

[작성 규칙]
1. 이모지 사용 금지 — 헤더 및 본문 전체에 이모지를 사용하지 않습니다.
2. 서술형 문장 종결 — 모든 서술 문장은 "~임", "~함", "~됨", "~있음" 형태로 마무리합니다.
   예) "성령의 인도를 따르는 교회의 새로운 삶의 방식임." / "이를 통해 신뢰가 형성됨."
3. 너무 요약하지 말 것 — 강의의 모든 주요 내용과 사례, 예시, 근거를 충분히 서술합니다.
4. 소제목(Bold sub-label) 적용 기준:
   - 한 섹션 내에 성격이 뚜렷이 다른 하위 항목이 2개 이상일 때만 **굵은 소제목**을 사용합니다.
   - 단일 흐름으로 연결되는 내용은 소제목 없이 서술형 단락으로 작성합니다.
5. 번호 섹션 구성 — 강의 흐름에 따라 소주제별로 ## 번호. 소주제명 형식으로 구성합니다.
6. Q&A 주석 불필요 — 강의 양식이므로 Q&A 방식의 주석은 작성하지 않습니다.

[강의 녹취록]
{text}

[작성 양식]
생성 일시: {dt}

# 강의 요약 노트

| 항목 | 내용 |
|------|------|
| 일 시 | (녹취록에서 파악한 날짜·시간, 없으면 생략) |
| 강의명 | (강의 제목 또는 회차 정보) |
| 주 제 | (핵심 주제 한 줄 요약) |
| 대 상 | (수강 대상, 파악 가능한 경우) |

---

## 강의 핵심 요약

1. (핵심 내용 1 — 한 문장)
2. (핵심 내용 2 — 한 문장)
3. (핵심 내용 3 — 한 문장)

---

## 1. (소주제명)

(해당 소주제의 내용을 상세하게 서술. 하위 항목이 2개 이상이고 성격이 다를 때만 아래처럼 굵은 소제목 사용)

**소제목 예시 (해당 시에만 사용)**
내용 서술...

## 2. (소주제명)

(소주제 수에 따라 반복)

---

## 핵심 개념 정리 (주요 용어나 개념이 있을 경우에만 작성)

| 용어 | 의미 |
|------|------|
| (용어1) | (설명) |
| (용어2) | (설명) |

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
                return ServiceResult(true, "연결 성공! ($model 사용 가능)")
            }
        }

        // 모든 모델 실패
        return ServiceResult(false, "사용 가능한 Gemini 모델을 찾지 못했습니다.\n\n" +
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
            onStatus?.invoke("Gemini STT 변환 완료")
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
결정사항
- (결정된 사항 나열, 없으면 "없음")

액션 아이템
- 담당자: X | 업무: Y | 기한: Z (없으면 "없음")

주요 일정
- (날짜/기한 포함 일정, 없으면 "없음")

핵심 수치
- (금액, 기간, 비율 등 주요 숫자, 없으면 "없음")

키워드
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
        "ir_md" -> SUMMARY_IR_MD
        "phone" -> SUMMARY_PHONE
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
