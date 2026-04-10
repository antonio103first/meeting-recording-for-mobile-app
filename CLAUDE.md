# CLAUDE.md — 회의녹음요약 모바일 앱 v3.0

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
