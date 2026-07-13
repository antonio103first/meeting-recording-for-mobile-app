# CLAUDE.md — 회의녹음요약 모바일 앱 v3.11.0

> v3.11.0 (2026-07-14): **📞 최근 통화 원클릭 STT·요약 (versionCode 45).** 메인 녹음화면 모드토글(회의/음성메모) **바로 아래** `📞 최근 통화 요약` 버튼 → 휴대폰 통화녹음 폴더(`Recordings/TPhoneCallRecords`)의 최근 통화를 **최신순 목록**으로 띄우고, 항목을 탭하면 즉시 STT → **`phone`(전화통화 메모) 양식 고정** 요약 → 기존 파일명 확인·저장 흐름으로 이어짐. 진입점을 메인 화면에 둔 이유: STT 진행률·요약 결과·파일명 다이얼로그가 이미 그 화면에 있어 **파이프라인 전체를 재사용**(회의목록 탭에 두면 진행 UI 복제 필요, 녹음모드 3번째 토글은 '외부 파일 가져오기'라 축이 안 맞음). ① **신규 `service/CallRecordingRepository.kt`** — SAF tree URI를 `DocumentsContract.buildChildDocumentsUriUsingTree` + **ContentResolver 커서 1회 조회**(DOCUMENT_ID/DISPLAY_NAME/LAST_MODIFIED/SIZE)로 스캔 후 메모리 정렬. `DocumentFile.listFiles()`는 파일당 쿼리라 수천 건(12GB) 폴더에서 수 초씩 걸려 의도적으로 회피. T전화 파일명 `{이름}({소속})_{번호}_{yyyyMMddHHmmss}.m4a`(예 `성낙환(디캠프)_01031678395_20260713183840.m4a`)를 정규식 파싱 → 이름·소속·번호·통화시각. 규칙 미매칭 파일(옛 `{번호}_{ts}.mp3` 등)은 파일명 그대로 폴백. 정렬 키는 **파일명 타임스탬프**(수정시각 아님 → 복사·이동에 흔들리지 않음). ② **파일명 = 확정 메타데이터** — 요약본의 `| 일 시 |`·`| 상 대 방 |` 행을 `applyCallHeader()`가 **파일명에서 뽑은 값으로 덮어씀**. 원래 이 두 칸은 AI가 녹취에서 추정해 자주 비었고(전화에선 서로 이름을 안 밝힘), 일시는 프롬프트 `{dt}`(=요약 실행 시각)로 채워져 **며칠 전 통화도 오늘 날짜로 찍히던** 문제가 있었음. `Regex.replace` 는 replacement 의 `$`를 그룹참조로 해석하므로 람다 overload 사용. ③ **Obsidian 저장 분기** — 통화 요약만 `00_Inbox/`(회의록은 `08_회의록/` 유지) + 기본 파일명 `{인물}_YYYYMMDD_전화통화`. 자동화 `route_inbox_notes` 가 이 규칙을 `02_Persons/{인물}/`로 라우팅하고 신규 인물이면 인덱스 노트까지 생성 — `08_회의록`에 넣으면 이 경로를 못 탐. ④ **SAF 1회 등록** — Android 11+ Scoped Storage 라 File API 불가. `OpenDocumentTree` + `EXTRA_INITIAL_URI`(`primary:Recordings/TPhoneCallRecords`)로 선택창이 해당 폴더에서 바로 열림 → `takePersistableUriPermission`(읽기) 보관. **새 매니페스트 권한 불필요.** 설정 💾저장/Drive 탭에 「📞 통화녹음 폴더」 카드 추가. ⑤ 통화 컨텍스트(`pendingCallRecording`)는 `startRecording`/`setAudioFile(FromUri)`/`confirmFileName`/`cancelFileName` 에서 해제해 **다음 녹음으로 새지 않음**. 처리 완료 통화는 `processedCallRecordings`(SharedPreferences)에 기록해 목록에 ✅ 표시(중복 요약 방지). **실기기 검증**: 설치(versionCode 45) → 📞 버튼 표시 → 폴더 선택창이 TPhoneCallRecords 에서 열림 → 「허용」 → 목록 최신순·이름/소속 파싱 정확(성낙환·디캠프 07-13 18:38 …). **미검증**: 통화 1건 STT+요약 실주행(및 00_Inbox → 02_Persons 라우팅). versionCode 44→45 / versionName 3.10.0→3.11.0
>
> v3.7.24 (2026-07-05): **요약(summary) 타임아웃·실패 근본 수정 — STT v3.7.20 락 로직을 요약 단계에도 이식**. 증상: 회의요약이 자주 타임아웃/실패. 원인(코드 진단): STT는 v3.7.20에서 WakeLock+WifiLock을 잡아 화면 꺼짐 시 `connection abort`를 막았으나 **요약 단계는 락을 전혀 안 잡았음** — 요약(긴 회의록은 수 분 소요, Gemini는 maxOutputTokens 65536) 중 화면이 꺼지면 OS가 유휴 Wi-Fi를 끊어 요약이 abort로 실패(STT와 동일 원인이 요약엔 미적용). 게다가 `runSummary`의 재시도 키워드가 한글("연결","네트워크")뿐이라 **Claude/GPT의 영문 `Software caused connection abort` 오류는 매칭 실패 → 재시도 없이 즉시 실패**(Gemini만 오류 문구에 한글이 있어 우연히 재시도됨). 수정: ① `runSummary`(일반 회의)·`runVoiceMemo` 요약 루프·`startResummarize`(수동 재요약) **3개 요약 경로 전부**를 `acquirePipelineLocks()`/`releasePipelineLocks()`로 감쌈 — 화면 꺼져도 네트워크 유지. ② 재시도 판정을 STT와 동일한 튼튼한 `isNetworkError()`(abort/socket/reset/broken pipe/ssl 등 포함)로 통일 → Claude/GPT 연결 끊김도 자동 재시도. ③ `startResummarize`는 기존에 락·재시도가 전혀 없던 단발 호출이었음 → 락 + 최대 3회 재시도(backoff 3s·8s) 추가. ④ 음성메모 요약도 비네트워크 오류면 즉시 중단(무의미 재시도 방지). 문자열/제어흐름-only 변경, 컴파일 검증 완료. (미조정: Gemini 요약 maxOutputTokens 65536은 품질 트레이드오프라 유지) versionCode 38 / versionName 3.7.24
>
> v3.7.23 (2026-07-01): **설정 화면 '요약 방식'에 본당/단체(org) 누락 수정**. v3.7.18에서 org 양식을 재요약 시트(`SummaryModeBottomSheet`)엔 넣었으나 **`SettingsScreen`의 기본 요약방식 목록엔 빠져** 있었음 → 설정에서 안 보임(새 녹음의 기본 양식은 설정값이라 여기 꼭 필요). `SettingsScreen.kt` 요약 방식 listOf에 `"org" to "본당/단체 회의 (비영리)"` 추가. versionCode 37 / versionName 3.7.23
>
> v3.7.22 (2026-07-01): **회의록에 STT 엔진 표기**. 회의록(요약) 끝에 `*STT 엔진: Clova*` / `*STT 엔진: Gemini*` / `*STT 엔진: Gemini (CLOVA Speech 실패 → 자동 폴백)*` 자동 추가 — 나중에 "이 회의록은 어느 엔진으로 뽑혔는지" 추적해 품질 편차 파악. `runStt`가 실제 성공 엔진을 `lastSttEngineUsed`에 기록, 회의 파이프라인 요약 저장 직전(`summaryText + sttEngineFooter()`)에 1회 주입돼 로컬·SAF·Obsidian·Drive 전 저장처에 반영. versionCode 36 / versionName 3.7.22
>
> v3.7.21 (2026-07-01): **STT 실패 시 Gemini 자동 폴백**. v3.7.20(락+재시도)에도 긴 녹음(87분) Clova STT가 "재시도 3회 모두 끊김"으로 간헐 실패(Wi-Fi인데도, 나중에 재시도하면 성공). 원인: **Clova 동기 STT가 긴 파일을 한 연결로 수십 분 붙잡는 방식** 자체가 취약(서버 부하·NAT 유휴 타임아웃에 따라 간헐 abort). 근본 대응: `runStt`에서 clova/whisper가 **네트워크 재시도 3회 모두 실패하면 Gemini STT로 자동 폴백**(Gemini는 10분 청크라 요청이 짧아 강함). 사용자가 수동 재시도 안 해도 됨. Gemini 키 있을 때만 동작. versionCode 35 / versionName 3.7.21
>
> v3.7.20 (2026-07-01): **STT `connection abort` 근본 수정 — 파이프라인 락 + 재시도**. 증상: 긴 녹음(예: 87분) Clova STT 중 `Software caused connection abort` 실패. 원인(로그 확인): Clova 동기 STT가 서버 전사를 기다리는 동안 화면이 잠기면(AOD) OS가 유휴 Wi-Fi 연결을 끊음 + Clova STT엔 재시도 없음 + STT/요약은 녹음 정지 후 코루틴에서 돌아 **WakeLock·포그라운드 없음**(녹음 FGS는 이미 해제됨). 수정: ① `runStt`에 **PARTIAL_WAKE_LOCK + WifiLock(FULL_HIGH_PERF)** 획득/해제(`acquirePipelineLocks`/`releasePipelineLocks`) — 화면 꺼져도 네트워크 유지. ② **네트워크성 오류(abort/socket/timeout/ssl 등) 최대 3회 재시도**(backoff 3s·8s) — clova/whisper 자체 재시도 없던 것 흡수(`isNetworkError`). ③ 3회 모두 실패 시 안내(Wi-Fi·화면 켜기 / 긴 녹음은 Gemini STT). Manifest에 `ACCESS_WIFI_STATE` 추가. versionCode 34 / versionName 3.7.20
>
> v3.7.19 (2026-07-01): **프롬프트 통일 1단계 — Q&A 정밀화(PC 규율 이식)**. PC↔모바일 회의록 프롬프트 통일 작업의 일부. 모바일의 **주간회의·다자간협의·회의록업무·컨퍼런스** 양식 Q&A 규칙에 PC에만 있던 **"Q를 임의로 만들지 말 것"(추정 Q 생성 금지) + "한 Q에 여러 명 답변 시 Q-A-A 나열"** 규율을 추가 → Gemini가 회의록에서 Q&A를 날조하던 문제 방어(상임위 사례). 주체 표기는 이미 양식군별(업무·IR·다자간·주간회의=[케이런] / 전화·네트워킹=[Antonio] / 본당·단체=[화자N])로 정리돼 있어 유지. **⚠️ 빌드 보류**(문자열-only 편집, 컴파일 안전) — 다음 빌드 시 반영. versionCode 33 / versionName 3.7.19. (남은 통일: PC에 주간회의·IR·본당 양식 신설은 별도 작업)
>
> v3.7.18 (2026-06-30): **본당/단체 회의 양식 신설 + 클리핑 방지 + Obsidian 이중폴더 수정**. ① **`SUMMARY_ORG`(본당/단체 회의, 비영리·안건 중심)** 신규 양식 — `[케이런]` 미사용, **직책 추정 금지(자기소개로 확인된 경우만 표기, 아니면 `[화자N]`)**, **천주교 도메인 용어집 내장**(상임위/미사/사목/전례/레지오/묵주기도 등 오인식 보정), 깨진 구간 `*(STT 불명확)*` 표기. 배경: 상임위 회의를 다자간협의로 요약했더니 `[케이런]` 오염 + 화자 직책 날조 발생. GeminiService 템플릿+디스패처, Claude·ChatGpt 엔진, RecordingScreen 양식목록, FileManager 라벨(`단체회의`) 배선. ② **소프트 리미터(클리핑 방지)** — 캡처 루프에 임계(≈-2dBFS) 위는 tanh로 부드럽게 누르는 리미터 추가 → 큰 소리에서 0dBFS 포화(찌그러짐) 방지(STT 정확도 보호). ③ **Obsidian 회의록 이중폴더(`08_회의록/08_회의록`) 수정** — `writeTextToSafSubDir`에 "선택 폴더명이 subPath 선두 세그먼트와 같으면 건너뜀" 가드 추가(사용자가 vault 루트 대신 08_회의록을 고른 경우 대응). versionCode 32 / versionName 3.7.18
>
> v3.7.16 (2026-06-30): **녹음 음량 근본 해결 — 음원 VOICE_COMMUNICATION + 소프트웨어 AGC**. 증상: 녹음이 너무 작게(또는 간헐 무음)으로 담겨 STT가 비거나 "녹음 안 됨"으로 느껴짐. 실측 진단(adb+ffmpeg)으로 원인 규명: 이 단말(Galaxy SM-S938N)에서 `MIC`/`VOICE_RECOGNITION`/`CAMCORDER` 음원은 **원시 입력이 -32~-63dB로 비정상적으로 작았고**(시스템은 `not silenced`로 정상 전달, 권한·빅스비·차단 모두 무관), 16k 샘플레이트가 저게인 경로를 악화. **하드웨어 AGC는 이 기기에서 미지원(`AGC not available`).** 해결: ① 녹음 음원을 **`VOICE_COMMUNICATION` 1순위**(통화용 경로 — HAL이 음성을 크고 깨끗하게 자동 레벨링)로 변경, 실패 시 MIC→VOICE_RECOGNITION→CAMCORDER→DEFAULT 폴백. **실측 rawPeak 53→3754(70배↑), 파일 -22dB→-5.7dB peak/-24dB mean** 로 기본 녹음기 수준 확보. ② `AudioRecorderManager`에 **소프트웨어 AGC**(목표 -6dBFS, 동적 게인 최대 ×8, 노이즈 게이트 0.004로 조용한 구간 잡음 부각 방지) 내장 — 거리·음량 무관 일정 크기. ③ 하드웨어 AGC 가용 시 함께 사용(`setupAudioEffects`). 알려진 특성: VOICE_COMMUNICATION은 첫 ~1초 AEC/AGC 워밍업으로 무음 → 녹음 시작 후 1초 뒤 발화 권장. versionCode 30 / versionName 3.7.16
>
> v3.7.9 (2026-06-30): **음성메모 파이프라인 복원력 + 재개(resume)**. 녹음 정지→STT→요약→Obsidian 자동 처리 중 **STT/요약이 실패하면 전체가 그대로 멈추던 문제** 개선. ① `runVoiceMemo`에 **STT 3회·요약 3회 재시도(backoff 2s/5s)** 추가 — 네트워크·429·타임아웃 등 일시 오류 흡수. ② 하드 실패 시 그냥 중단하지 않고 **재처리 컨텍스트**(`resumableVoiceMemoAudio`/`resumableVoiceMemoStt`)를 남겨 같은 녹음으로 이어서 재시도 가능. STT는 성공·요약만 실패한 경우 **STT 재변환 없이 요약부터 재개**(`presetStt`). ③ 신규 `resumeVoiceMemo()` + UiState `voiceMemoResumeFile`. 오류 다이얼로그에 **"🔁 다시 시도"** 버튼, 닫아도 음성메모 화면에 **"🔁 STT·요약 다시 시도"** 진입점 유지. ④ 녹음 m4a·DB 목록 등록은 파이프라인 이전에 완료되므로 실패해도 **녹음 자체는 항상 보존**. versionCode 23 / versionName 3.7.9
>
> v3.7.8 (2026-06-29): **회의록 요약 템플릿 정밀화 (전 8개 양식)**. `GeminiService.kt`의 모든 회의록 양식(주간회의·다자간협의·회의록업무·IR·전화통화·네트워킹·강의·컨퍼런스)에 `[공통 정밀화 규칙]` 3종을 삽입 — ① **사실 충실성**(녹취에 없는 회사명·숫자·인명을 지어내지 않음, 불확실하면 비워 둠), ② **화자 분리 불신**(STT diarization이 한 화자로 몰리거나 오배정될 수 있으므로 화자 태그가 아닌 내용·문맥으로 판단, 불분명하면 `[불명확]`), ③ **STT 오인식 표기**(`*(STT 오인식 의심)*`). 네트워킹(`SUMMARY_FLOW`)은 Q&A·현황/주요내용 소항목을 강제→**선택**으로 전환(추정 Q&A 창작 차단). 배경: 2인 티타임 녹음이 Clova 화자 분리 실패로 한 화자에 몰려, 요약이 화자 귀속·Q&A를 추측으로 메우던 문제. ChatGPT·Claude 엔진은 GeminiService 프롬프트 공유로 자동 반영. versionCode 22 / versionName 3.7.8
>
> v3.7.7 (2026-06-28): 요약방식 시트 하단 취소/요약실행 버튼 높이 88dp → 60dp(적당). versionCode 21 / versionName 3.7.7
>
> v3.7.6 (2026-06-28): **요약방식 시트 레이아웃 + 저장 실패 경고**. ① `SummaryModeBottomSheet`: 양식 목록(8개)만 스크롤(weight+verticalScroll)하고 취소/요약실행 버튼을 스크롤 밖 하단에 고정·높이 88dp → 항목이 많아도 버튼이 잘리지 않음(글씨 15sp). ② 지정폴더/Obsidian SAF 권한 만료 시 저장이 조용히 실패하던 것을 `confirmFileName` 상태에 "⚠️ 권한 만료 — 폴더 재선택" 경고로 노출. versionCode 20 / versionName 3.7.6
>
> **이전 버전 (이번 세션 STT/UI 작업):**
> - v3.7.5 (2026-06-28): 요약실행/취소 버튼 글씨↓(15sp)·디자인↑. 저장 실패 경고 추가
> - v3.7.4 (2026-06-28): **Gemini STT 반복루프 근본 해결** — `[화자1] 네. [화자2] 네.` 화자 태그 교대 구조가 루프의 뼈대였음. STT 프롬프트를 **화자 태그 없는 줄글 전사**로 변경(맞장구 생략, 반복 금지), temperature 0.4. 실측으로 동일 오디오가 루프 없이 정상 전사 확인. 화자 구분은 요약 단계가 이름·문맥으로 재추론
> - v3.7.3 (2026-06-28): STT 구간별 재시도+backoff(429/일시오류), 실패 시 실제 원인 인라인 표시
> - v3.7.2 (2026-06-28): STT 오디오 10분 청크 분할(`AudioChunker`, MediaExtractor/MediaMuxer remux) + 반복루프 감지·트리밍. 요약실행 버튼 확대
>
> **이전 버전:**
> - v3.7.1 (2026-06-28): 음성메모 액션 아이템에 메모 위키링크 부착, vault `06_Resources/음성메모/` 저장, frontmatter `action_items` 라우팅 대상만
> - v3.7.0 (2026-06-28): **날짜 인식 음성메모 → 해당 날짜 Action Item 라우팅.** 음성메모 요약을 날짜 인식 JSON(`{summary, action_items:[{date,text}]}`)으로 출력. 녹음 시점(`{today}`) 기준으로 "다음주 화요일/7월 3일/7월 2일까지" 등을 절대 날짜(YYYY-MM-DD)로 해석. 앱은 `action_items` frontmatter 작성 + 오늘/무날짜 항목 즉시 주입, 그 외 날짜 항목은 자동화(`voice_memo_inject` v2.25)가 해당 날짜 데일리노트로 라우팅. 날짜 없는 메모는 기존 동작 유지. 기획서 `docs/기획서_날짜인식_음성메모_v3.7.md`
> - v3.6.1 (2026-06-28): **Gemini STT 복구** — `gemini-2.5-flash` thinking 토큰이 전사 출력을 잠식해 빈 응답이 나오던 문제를 STT에서 `thinkingBudget=0`으로 차단. SSE 파싱을 8192바이트 청크→`readUtf8Line()` 라인 단위로 교체(JSON/한글 멀티바이트 쪼개짐 유실 방지) + `finishReason`(MAX_TOKENS 등) 노출. 회의록 템플릿 BottomSheet 하단 취소/요약실행 버튼 키움(높이 60dp·18sp). MP3/STT 파일 선택을 인앱 다이얼로그(최신 날짜순)로 교체 + ‘기기에서 찾기’ 폴백. versionCode 13 / versionName 3.6.1
> - v3.5.1 (2026-06-19): 음성메모 요약 프롬프트(`SUMMARY_VOICE_MEMO`) 개선 — 군더더기 제거 후 핵심만 압축. 할 일·일정·수치는 반드시 포함
> - v3.5.0 (2026-06-16): 녹음 엔진 AudioRecord+게인 교체(음량 증폭) + 음성메모 저장 위치 분기(데일리 노트 주입)
> - v3.4.3 (2026-06-02): 전화통화 메모 Q&A 제거 + 화자 번호 병기 금지
> - v3.4.2 (2026-05-31): 회의록 요약본 Obsidian 저장 폴더 수정 — vault 루트 → `08_회의록/` 서브폴더
> - v3.4.1 (2026-05-29): 음성메모 Obsidian 저장 폴더 회귀 버그 수정 — `00_Inbox/voice_memos/` 서브폴더에 정확히 저장
> - v3.4 (2026-05-24): 녹음 정지 즉시 회의목록 자동 등록 + 음성메모 파이프라인 완성
> - PC 데스크톱 v3.0.8 동기화 (2026-05-12): 파일 저장명 포맷 `_YYYYMMDD_모드` (언더스코어)
> - PC 데스크톱 v3.0.6 동기화 (2026-05-06): 전 양식 Q&A 규칙 통일, 컨퍼런스/간담회 양식

## v3.7.1 주요 변경사항 (2026-06-28)

**음성메모 액션 아이템에 메모 위키링크 부착 + 주입 형식 통일**

- 데일리노트 주입 형식을 `- 🔜 음성메모 : {할 일} [[06_Resources/음성메모/음성메모_…]]` 로 통일 (당일 즉시 주입·자동화 주입 동일)
- 배경: 당일 즉시 주입 경로는 메모 본문(.md)을 vault에 넣지 않아 위키링크를 걸 수 없었음
- `RecordingViewModel.runVoiceMemo`: 메모 본문(.md)을 **항상 vault `06_Resources/음성메모/`** 에 저장 → 링크 연결. `## 요약`엔 전 항목, frontmatter `action_items`엔 자동화 라우팅 대상만(즉시 주입분 제외) → 자동화 중복 주입 방지
- `injectIntoDailyNoteActionItems(content, baseName, items, memoLink)` — link 인자 추가, 항목 단위 멱등 마커 유지
- 자동화 `voice_memo_inject` v2.25: frontmatter `action_items` 키가 있으면 **권위적**으로 사용(빈 리스트면 라우팅 없음, `## 요약` 폴백 안 함). 모바일이 전부 즉시 주입한 파일(`action_items: []`)은 synced·이동 처리
- `VOICE_MEMO_VAULT_DIR = "06_Resources/음성메모"` 상수 추가. versionCode 14→15, versionName 3.7.0→3.7.1

## v3.7.0 주요 변경사항 (2026-06-28)

**날짜 인식 음성메모 → 해당 날짜 Today Action Item 자동 라우팅**

- `GeminiService.SUMMARY_VOICE_MEMO`: 구조화 JSON 출력으로 교체 — `{"summary":...,"action_items":[{"date":"YYYY-MM-DD|null","text":...}]}`
- 날짜 해석은 **녹음 시점**에 1회: `{today}`(요일 포함 기준 날짜)를 프롬프트에 제공 → "내일/다음주 화요일/7월 3일/7월 2일까지"를 절대 날짜로 변환. "~까지" 마감 작업은 마감일을 date로
- `GeminiService`/`ClaudeService` `summarize()`에 `{today}` 치환 추가(두 엔진 공용 프롬프트)
- `RecordingViewModel`: `parseVoiceMemoJson()`(JSON 파싱, 실패 시 date=null 단일 폴백), `MemoItem` 데이터클래스, `renderMemoItemLine()`, `yamlActionItems()` 추가
- 라우팅: 오늘/무날짜 항목은 당일 노트 있으면 즉시 주입(기존 동작 유지), 그 외 날짜 항목은 자동화가 날짜대로 데일리노트(`get_or_create`)에 배치
- 자동화 `voice_memo_inject.py`: `_extract_action_items`/`_parse_frontmatter`/`_normalize_date` 추가, 날짜별 그룹 주입. 명시 날짜는 morning/evening mode 무시
- 기획서: `docs/기획서_날짜인식_음성메모_v3.7.md`

## v3.6.1 주요 변경사항 (2026-06-28)

**1. Gemini STT 복구 (`GeminiService.kt`)**
- `transcribe()`에서 `thinkingBudget=0` — `gemini-2.5-flash` thinking 토큰이 전사 출력(maxOutputTokens)을 잠식해 빈/잘린 응답이 나오던 문제 차단
- SSE 파싱을 8192바이트 청크 즉시 `readUtf8()` → `readUtf8Line()` 라인 단위로 교체 (JSON 1건·한글 멀티바이트가 읽기 경계에서 쪼개져 유실되던 버그 수정)
- 빈 응답 시 `finishReason`(MAX_TOKENS/SAFETY 등) 사용자 노출

**2. UI**
- 회의록 템플릿 BottomSheet(`SummaryModeBottomSheet`) 하단 취소/요약 실행 버튼 키움(높이 60dp·18sp)
- MP3/STT 파일 선택을 시스템 선택기 → **인앱 다이얼로그(최신 날짜순)** 로 교체. `LocalFilePickerDialog`(이름·수정일시·크기 표시) + ‘기기에서 찾기’ 시스템 선택기 폴백. `RecordingViewModel.listLocalAudioFiles()`/`listLocalSttFiles()`
- 배경: `ActivityResultContracts.GetContent()` 시스템 선택기는 정렬 순서를 앱에서 제어 불가 → 인앱 목록으로 최신순 보장

versionCode 12→13, versionName 3.6.0→3.6.1

## v3.6.0 주요 변경사항 (2026-06-27)

**1. 음성 메모 완전 자동화 (`RecordingViewModel.kt`)**

- 음성 메모 모드에서 **녹음 정지만 누르면** → 메모 저장 → STT 변환 → 회의록 요약 → Obsidian 저장까지 **모두 자동** 진행. 별도 "📝 음성 메모 저장" 버튼을 누를 필요 없음
- `saveRecordingImmediately()` 가 녹음 저장·DB insert·Drive 업로드를 마친 직후, 모드가 `VOICE_MEMO` 면 동일 IO 코루틴 내에서 `runVoiceMemo(audioFile)` 를 이어서 호출
  - 동일 코루틴 순차 실행이므로 `pendingVoiceMemoId` 가 먼저 세팅된 뒤 요약 단계가 돌아 **DB update 순서 보장(중복 insert 없음)**
- 자동 실행 전 STT 엔진 키 사전검증 헬퍼 `sttKeyError()` 신설 — clova/whisper/gemini 각 엔진 키 누락 시 명확한 에러, 정상일 때만 파이프라인 진입. 요약 키(claude/gemini)도 함께 검증

**2. 음성 메모 화면 간소화 (`RecordingScreen.kt`)**

- `isVoiceMemo = state.recordingMode == RecordingMode.VOICE_MEMO` 플래그 도입
- 음성 메모 모드에서는 **STT 변환 결과·회의록 요약 결과만** 표시하고 아래 부가 기능을 숨김:
  - Section 1: "선택 파일 / 파일 선택" 줄
  - Section 2(STT): MP3 선택 버튼, 수동 실행 버튼 → *"녹음 정지 시 STT 변환·요약·Obsidian 저장이 자동으로 진행됩니다"* 안내(처리 중엔 스피너)로 대체
  - Section 3(요약): "STT txt 파일 선택" 버튼, "회의록 요약 시작" 버튼
  - Section 4: 핵심지표 카드
  - Section 5: "STT 변환파일로 재요약" 카드 전체
- 회의 녹음 모드는 기존 기능 전부 유지

**3. 정렬 / 버전**

- 앱 내 "회의 목록"(MP3/STT/회의록 탭)은 기존대로 `MeetingDao.getAll()` = `ORDER BY createdAt DESC`(최신 저장 순) 유지
- ⚠️ 휴대폰 기본 파일 관리자의 정렬 기준은 앱/빌드에서 변경 불가 — 해당 파일 관리자 앱 내 "정렬 → 날짜순(최신순)"으로 사용자가 1회 설정해야 함
- versionCode 11→12, versionName 3.5.1→3.6.0

---

## v3.5.1 주요 변경사항 (2026-06-19)

**음성메모 요약 프롬프트 개선 (`GeminiService.SUMMARY_VOICE_MEMO`)**

- 기존: "2~3문장으로 간결하게 요약" — 발화가 장황하면 요약도 늘어지는 문제
- 변경: 군더더기·반복·잡담·배경 설명을 모두 버리고 **요점만 추출**하도록 지시 강화
  - 핵심이 1개면 1~2문장으로 압축, 서로 다른 핵심이 2개 이상이면 짧은 불릿(-)으로 분리
  - 할 일·일정(날짜)·수치(금액·마감 등)는 반드시 포함 (데일리 노트 Action Items 주입 용도)
  - 녹취에 없는 내용 추가 금지 유지
- Claude·Gemini 엔진 모두 `GeminiService.SUMMARY_VOICE_MEMO`를 참조하므로 한 곳 수정으로 반영
- versionCode 10→11, versionName 3.5.0→3.5.1

---

## v3.5.0 주요 변경사항 (2026-06-16)

**1. 녹음 음량 증폭 — 녹음 엔진 전면 교체 (`AudioRecorderManager.kt`)**

- 기존 `MediaRecorder`는 입력 음량(게인) 조절 API가 전혀 없어 "녹음이 너무 작다" 문제를 해결할 수 없었음
- → `AudioRecord`로 16-bit PCM 원본을 직접 캡처 → 샘플마다 소프트웨어 게인 곱셈(클리핑 ±32767 방지) → `MediaCodec`(AAC-LC) 인코딩 → `MediaMuxer`로 M4A 저장 파이프라인으로 교체
- 게인 배수는 `ConfigManager.recordingGain` (기본 **4.0배**, 범위 1.0~8.0). 설정 > ⚙엔진 설정 탭의 "녹음 음량 (게인)" 슬라이더로 조정 (0.5 단위)
- 음성 효과: `AutomaticGainControl`/`AcousticEchoCanceler` **OFF**(수동 게인 보존), `NoiseSuppressor` **ON**(증폭 잡음 억제)
- 출력 스펙(16kHz mono AAC 32kbps)·파일명(`YYYYMMDD_HHmmss_녹음.m4a`)·공개 API·v3.0 오디오 포커스 인터럽트 완전 차단 로직은 모두 그대로 유지
- 진폭(파형) 측정은 캡처 스레드에서 PCM 최대 샘플로 직접 계산

**2. 음성메모 저장 위치 분기 — 아침 동기화 전/후 (`RecordingViewModel.runVoiceMemo`)**

- **당일 데일리 노트(`01_Daily/daily_YYYYMMDD.md`)가 존재하면** (= 아침 자동화 실행 완료)
  → 데일리 노트의 `## ✅ Action Items` 섹션 헤더 바로 아래에 요약을 직접 주입
  - 마커 `<!-- 🤖 from-mobile-voicememo | {baseName} -->` 로 멱등 보장 (중복 주입 방지)
  - Action Items 섹션이 없으면 문서 끝에 새로 생성
- **데일리 노트가 아직 없으면** (= 아침 동기화 전) → 기존대로 `00_Inbox/voice_memos/` 에 저장 (저녁 자동화 `voice_memo_inject` 가 나중에 픽업)
- 판정 기준: "데일리 노트 존재 = 아침 동기화 완료" (아침 자동화가 데일리 노트를 생성하므로 가장 정확한 신호)
- 신규 `ConfigManager.readTextFromSafSubDir()` 헬퍼 추가 (SAF 서브폴더 텍스트 읽기)

**3. versionCode 9→10, versionName 3.4.3→3.5.0**

---

## v3.4.3 주요 변경사항 (2026-06-02)

**프롬프트 개선**:

1. **전화통화 메모 (`SUMMARY_PHONE`) — Q&A 완전 제거**
   - 기존: 주제별 1~2줄 요약 + Q&A 보충 주석 혼합
   - 변경: 주제별 2~3줄 순수 서술 요약만 출력. Q&A 형식 완전 삭제
   - `RecordingScreen.kt` 양식 설명 문구도 동일하게 업데이트

2. **화자 표기 `(화자 N)` 병기 금지**
   - `makeSttPrompt()`: 실명 확인 시 `[홍길동 대표]`만 사용, `[홍길동 대표 (화자1)]` 혼합 금지 규칙 추가
   - `SUMMARY_TOPIC` (다자간 협의): 화자 표기 기준에 `(화자 N)` 추가 금지 명시
   - `SUMMARY_PHONE` (전화통화 메모): 동일 규칙 추가

3. **versionCode 8→9, versionName 3.4.2→3.4.3**

## v3.4.2 주요 변경사항 (2026-05-31)

**버그 수정**: 회의록 요약본이 Obsidian vault 루트에 저장되던 문제 해결. 이제 모든 양식의 요약본이 `{vault}/08_회의록/` 서브폴더로 저장됨.

- **RecordingViewModel.kt** 3개 Obsidian 저장 지점 모두 `writeTextToSafDir()` → `writeTextToSafSubDir(uri, OBSIDIAN_MEETING_SUBDIR, "...md")` 로 변경
  - L704: 요약 완료 즉시 저장 (`runSummary` 임시 저장)
  - L848: 메인 vault 저장 (`confirmFileName` 최종 저장)
  - L1363: 재요약 완료 즉시 저장 (`resummarize` 임시 저장)
- 신규 companion 상수 `OBSIDIAN_MEETING_SUBDIR = "08_회의록"`
- 7개 양식(주간회의/다자간협의/회의록업무/IR미팅/전화통화메모/네트워킹/강의요약) 전부 동일 적용 — 요약본은 파일명·양식과 무관하게 항상 `08_회의록/` 에 들어감
- 음성메모는 기존 `00_Inbox/voice_memos/` 유지 (v3.4.1)
- versionCode 7→8, versionName 3.4.1→3.4.2


## v3.4.1 주요 변경사항 (2026-05-29)

**버그 수정**: 음성메모(VOICE_MEMO 모드)가 의도와 다르게 Obsidian vault 루트에 저장되던 회귀 문제 해결.

- **RecordingViewModel.kt:1558-1567** `runVoiceMemo()` 의 Obsidian 저장 호출을 `writeTextToSafDir()` → `writeTextToSafSubDir(uri, "00_Inbox/voice_memos", "...md")` 로 변경
- v3.4 주석에는 "00_Inbox/voice_memos 자동 저장"으로 명시돼 있었으나 실제 코드는 vault 루트 직접 저장 — 일반 회의록과 같은 폴더에 섞여 들어가는 회귀였음
- 결과: 음성메모는 항상 `{vault}/00_Inbox/voice_memos/음성메모_YYYYMMDD_HHmmss.md` 에 저장됨
- 자동화 `voice_memo_inject` 모듈 (Obsidian-Automation 저장소 v2.17.1) 이 이 폴더를 정상 픽업하여 daily note ✅ Action Items 섹션에 1회만 주입


## 프로젝트 개요
Android 모바일 회의 녹음 → STT 변환 → AI 회의록 요약 앱 (Kotlin + Jetpack Compose + Material3)

## 핵심 아키텍처
- **UI**: Jetpack Compose, Material3 (Single Activity, RecordingScreen)
- **State**: ViewModel + StateFlow (RecordingViewModel, SettingsViewModel)
- **녹음**: MediaRecorder + Foreground Service + WakeLock
- **STT 엔진**: CLOVA Speech / Whisper (ChatGPT) / Gemini
- **요약 엔진**: Claude / ChatGPT (GPT-4o) / Gemini
- **저장**: SAF (Storage Access Framework) 직접 저장 + 앱 전용 백업
- **DB**: Room (MeetingDao)
- **클라우드**: Google Drive 자동 업로드

## 디렉토리 구조
```
app/src/main/java/com/krunventures/meetingrecorder/
├── MainActivity.kt
├── data/
│   ├── ConfigManager.kt          # 설정 관리 (SAF URI, API 키, 저장 경로)
│   ├── MeetingDao.kt             # Room DAO
│   └── MeetingApp.kt             # Application class
├── service/
│   ├── AudioRecorderManager.kt   # 녹음 관리 (MediaRecorder, AudioFocus)
│   ├── RecordingService.kt       # Foreground Service
│   ├── CallManager.kt            # 통화 상태 감지
│   ├── FileManager.kt            # 파일 저장/이름변경/목록
│   ├── ClovaService.kt           # CLOVA Speech STT
│   ├── ChatGptService.kt         # Whisper STT + GPT-4o 요약
│   ├── GeminiService.kt          # Gemini STT + 요약
│   ├── ClaudeService.kt          # Claude 요약
│   └── GoogleDriveService.kt     # Drive 업로드
├── ui/screens/
│   ├── RecordingScreen.kt        # 메인 화면 (녹음, STT, 요약, 재요약)
│   └── SettingsScreen.kt         # 설정 화면 (엔진, API키, 저장/Drive)
├── viewmodel/
│   ├── RecordingViewModel.kt     # 핵심 비즈니스 로직 + 파이프라인
│   └── SettingsViewModel.kt      # 설정 상태 관리
└── widget/
    └── RecordingWidget.kt        # 홈 화면 위젯
```

## v3.4 주요 변경사항 (2026-05-24)

1. **녹음 정지 즉시 회의목록 자동 등록** — MEETING/VOICE_MEMO 모드 공통
   - 녹음 정지 → `REC_YYYYMMDD_HHmmss.m4a` / `메모녹음_YYYYMMDD_HHmmss.m4a`로 즉시 DB insert
   - `pendingMeetingId` / `pendingVoiceMemoId` 로 레코드 ID 추적
   - 가져오기(스캔) 없이 녹음 후 바로 회의목록에 표시됨
2. **confirmFileName() — update-or-insert 패턴**
   - preliminary insert된 레코드가 있으면 update (파일명·경로·STT·요약 반영)
   - 재요약 등 preliminary 없는 경우에만 새로 insert
3. **음성메모 파이프라인** — 별도 파이프라인 (파일명 다이얼로그 없음, 자동 저장)
   - STT → `메모녹음_YYYYMMDD.txt`, 요약 → `메모녹음_YYYYMMDD.md`
   - 2~3문장 간단 요약 (`SUMMARY_VOICE_MEMO` 템플릿)
   - Obsidian `00_Inbox/voice_memos/` 자동 저장
4. **로컬 파일 스캔** — 설정 > 💾저장/Drive > DB백업 카드에 "🔍 로컬 파일 스캔" 버튼 추가

### RecordingViewModel 핵심 플로우 (v3.4)

```
녹음 정지 (stopRecording)
  └─ saveRecordingImmediately()
       ├─ fileManager.saveRecordingImmediately(prefix="메모녹음"|"REC")  → m4a 저장
       ├─ dao.insert(Meeting) → pendingVoiceMemoId 또는 pendingMeetingId 저장
       └─ SAF 복사 + Drive 업로드

파이프라인 실행 (startPipeline)
  ├─ VOICE_MEMO → runVoiceMemo()
  │     └─ STT → 간단 요약 → 파일 저장 → dao.updateSummary/FilePaths/FileName(pendingVoiceMemoId)
  └─ MEETING → runStt() → runSummary() → 파일명 다이얼로그
               └─ confirmFileName()
                     └─ pendingMeetingId > 0: dao.update*(id) (update)
                        else: dao.insert() (재요약 등)
```

## v3.0 주요 변경사항
1. **녹음 중 인터럽트 완전 차단** — 전화, 카메라, 다른 앱 마이크 점유 시에도 녹음 계속
2. **회의록 .md → .txt** — 회의록 요약 파일을 .txt로 저장
3. **Claude 타임아웃 확대** — 300초 → 1800초 (30분)
4. **요약 재시도 로직** — 타임아웃/네트워크 오류 시 최대 2회 자동 재시도
5. **SAF 직접 저장** — 2단계 복사 제거, 설정 폴더에 직접 저장 + 앱 전용 백업
6. **STT 창에 MP3 파일 선택** — 녹음 없이 기존 오디오 파일 직접 변환
7. **회의록 창에 STT txt 선택** — STT 텍스트 파일로 요약만 단독 실행
8. **회의록 요약 재시작 버튼** — 실패 시 요약만 재실행
9. **양식별 프롬프트·샘플 미리보기** — 5가지 양식 선택 시 설명 및 샘플 표시
10. **SAF 미설정 경고** — 메인 화면 배너 + 저장 완료 메시지에 경고

## 빌드
```bash
# JAVA_HOME 설정 (Android Studio JBR)
export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

# Gradle 빌드
./gradlew assembleRelease

# 또는 Android Studio: Build → Build APK(s)
```

## 주요 제약사항
| 항목 | 값 |
|---|---|
| 녹음 WakeLock | 4시간 |
| CLOVA 파일 한도 | 200MB |
| Whisper 파일 한도 | 25MB |
| Gemini 파일 한도 | 50MB |
| STT Read Timeout | 3600초 |
| 요약 Read Timeout | 1800초 (Claude), 3600초 (Gemini/ChatGPT) |
| 요약 출력 토큰 | Claude/GPT: 8192, Gemini: 65536 |
| 텍스트 Truncation | 50만자 |
| 요약 재시도 | 최대 2회 (타임아웃/네트워크 오류만) |

## 코딩 컨벤션
- Kotlin, Jetpack Compose
- 한글 주석 사용
- v3.0 변경사항에 `★ v3.0:` 주석 접두사
- SAF URI는 `content://` 형식, SharedPreferences에 저장
- 파일 저장: SAF 직접 저장 우선, 앱 전용 디렉토리에 백업
