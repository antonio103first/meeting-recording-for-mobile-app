# CLAUDE.md — 회의녹음요약 모바일 앱 v3.5.1

> v3.5.1 (2026-06-19): 음성메모 요약 프롬프트(`SUMMARY_VOICE_MEMO`) 개선 — 주절주절 발화에서 군더더기 제거 후 핵심만 압축. 핵심 1개면 1~2문장, 2개 이상이면 짧은 불릿. 할 일·일정·수치는 반드시 포함
>
> **이전 버전:**
> - v3.5.0 (2026-06-16): 녹음 엔진 AudioRecord+게인 교체(음량 증폭) + 음성메모 저장 위치 분기(데일리 노트 주입)
> - v3.4.3 (2026-06-02): 전화통화 메모 Q&A 제거 + 화자 번호 병기 금지
> - v3.4.2 (2026-05-31): 회의록 요약본 Obsidian 저장 폴더 수정 — vault 루트 → `08_회의록/` 서브폴더
> - v3.4.1 (2026-05-29): 음성메모 Obsidian 저장 폴더 회귀 버그 수정 — `00_Inbox/voice_memos/` 서브폴더에 정확히 저장
> - v3.4 (2026-05-24): 녹음 정지 즉시 회의목록 자동 등록 + 음성메모 파이프라인 완성
> - PC 데스크톱 v3.0.8 동기화 (2026-05-12): 파일 저장명 포맷 `_YYYYMMDD_모드` (언더스코어)
> - PC 데스크톱 v3.0.6 동기화 (2026-05-06): 전 양식 Q&A 규칙 통일, 컨퍼런스/간담회 양식

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
