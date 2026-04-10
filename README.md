# 회의녹음요약 (모바일)

회의·강의 음성을 녹음하거나 오디오 파일을 불러와
**STT 변환 → AI 회의록 요약 → PDF 공유 → Google Drive 자동 업로드**
까지 한 번에 처리하는 Android 모바일 앱입니다.

> **데스크톱 버전**: [meeting-recording-minute-app](https://github.com/antonio103first/meeting-recording-minute-app)

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| 실시간 녹음 | Foreground Service 기반 백그라운드 녹음 (M4A 자동 저장) |
| 파일 불러오기 | MP3/WAV/M4A 등 기존 오디오 파일 선택 |
| STT 변환 | CLOVA Speech(NAVER) — 한국어 특화, 화자 분리 지원 |
| AI 회의록 요약 | Gemini / Claude / ChatGPT 선택, 5가지 요약 방식 |
| 핵심 지표 | 결정사항, 액션아이템, 주요 일정 자동 추출 |
| PDF 공유 | 회의록 MD→PDF 변환, 카카오톡 등 메신저로 인쇄 형태 공유 |
| Google Drive | 녹음파일·회의록 각각 지정 폴더에 자동 업로드 (하위폴더 탐색 지원) |
| 파일 관리 | 삭제, 이름변경, 공유, 파일만삭제 (DB 유지) |
| 녹음 보호 | v3.0: 전화·카메라·다른앱이 마이크 점유해도 녹음 계속 (인터럽트 완전 차단) |
| 텍스트 복사 | STT/요약/지표 텍스트 영역 선택 복사 |
| 파일이름 지정 | 저장 전 파일이름 입력 다이얼로그 |
| 홈 위젯 | 원터치 녹음 시작 위젯 |
| 다크모드 | 시스템 설정 연동 자동 전환 |

### STT 엔진

| 엔진 | 특징 | 권장 상황 |
|------|------|-----------|
| **CLOVA Speech** (권장) | 한국어 특화, 화자 분리 지원 | 긴 회의 (30분 이상) |

### 요약 엔진

| 엔진 | 특징 |
|------|------|
| **Gemini** (권장) | 모델 자동 감지 (2.5-flash → 2.0-flash → 1.5-flash → pro) |
| **Claude** (선택) | Anthropic API |
| **ChatGPT** (선택) | OpenAI API |

### 요약 방식

모든 요약 방식에 아래 공통 규칙이 적용됩니다:

- **넘버링 구조**: `1 → 1.1 → · → ·` (대주제 → 중주제 → 세부사항 → 상세내용)
- **볼드 마크 금지**: 넘버링 뒤 요약 내용에 `**` 사용 금지
- **회의개요 테이블**: 일시, 참석자, 회의 목적 자동 포함

| 방식 | 설명 |
|------|------|
| 화자 중심 | 참석자별 발언 정리 |
| 주제 중심 | 안건별 내용 중심 요약 (화자별 분류 아닌 내용 기반) |
| 공식 양식 (MD) | 마크다운 공식 회의록 |
| 시간순 흐름 | 시간 순서대로 논의 흐름 정리 |
| 강의 요약 | 강의 노트 요약 |

---

## 시스템 요구사항

- **디바이스**: Samsung Galaxy S25 (또는 Android 8.0+ 기기)
- **OS**: Android 8.0 (API 26) 이상, 권장 Android 14+ (API 34)
- **인터넷 연결 필수**

---

## 빌드 및 설치

### 사전 준비

- [Android Studio](https://developer.android.com/studio) (최신 버전)
- JDK 17 (Android Studio 내장 JBR 사용)

### 빌드

```bash
git clone https://github.com/antonio103first/meeting-recording-for-mobile-app.git
cd meeting-recording-for-mobile-app
```

1. Android Studio에서 프로젝트 열기
2. `app/google-services.json` 배치 (Firebase Console에서 다운로드 — 자세한 내용은 설치매뉴얼 참조)
3. Gradle Sync 완료 대기 (**AGP/Kotlin 자동 업그레이드 제안 거부**)
4. Build → Rebuild Project
5. Galaxy S25 USB 디버깅 연결 → Run

> 상세 빌드/설치 절차는 [docs/설치매뉴얼_v2.0.html](docs/설치매뉴얼_v2.0.html) 참조 (브라우저에서 열기 → PDF 인쇄 가능)

---

## 초기 설정 (앱 내 설정 탭)

### 1. 엔진 설정 (설정 메뉴 최상단)

STT 엔진과 요약 엔진을 선택합니다.

### 2. API 키 입력

| API | 발급처 |
|-----|--------|
| CLOVA Speech Invoke URL + Secret Key | [console.ncloud.com](https://console.ncloud.com) → CLOVA Speech |
| Gemini API Key | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| Claude API Key (선택) | [console.anthropic.com](https://console.anthropic.com) |
| ChatGPT API Key (선택) | [platform.openai.com](https://platform.openai.com) |

### 3. Google Drive 연동

설정 탭 → Google Drive 연결 → Google 계정 로그인 → 업로드 폴더 선택

> **참고**: Google Cloud Console에서 OAuth 동의 화면 구성 및 Drive API 활성화가 선행되어야 합니다. 자세한 절차는 설치매뉴얼 참조.

---

## 빌드 환경 (버전 변경 금지)

| 항목 | 버전 |
|------|------|
| AGP (Android Gradle Plugin) | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Gradle | 8.9 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Compose BOM | 2024.12.01 |
| google-services plugin | 4.4.0 |

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| 언어 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| 녹음 | Android MediaRecorder (AAC) + Foreground Service |
| DB | Room 2.6.1 (SQLite ORM) |
| HTTP | OkHttp 4.12.0 |
| AI 요약 | Gemini / Claude / ChatGPT REST API (OkHttp 직접 호출) |
| STT | CLOVA Speech Long API |
| Drive | Google Sign-In 21.0.0 + Drive API v3 |
| PDF 변환 | Android WebView + PdfDocument API (MD→HTML→PDF) |
| 비동기 | Kotlin Coroutines 1.8.1 + StateFlow |

---

## 프로젝트 구조

```
app/src/main/java/com/krunventures/meetingrecorder/
├── MeetingApp.kt                  # Application (글로벌 크래시 핸들러)
├── MainActivity.kt                # Compose NavHost (3탭 네비게이션)
├── data/
│   ├── ConfigManager.kt           # SharedPreferences 설정 관리
│   ├── MeetingDao.kt              # Room DAO
│   └── MeetingDatabase.kt         # Room Database
├── service/
│   ├── ClovaService.kt            # CLOVA Speech STT
│   ├── GeminiService.kt           # Gemini REST API (요약 템플릿 중앙 관리)
│   ├── ClaudeService.kt           # Claude API (GeminiService 템플릿 참조)
│   ├── ChatGptService.kt          # ChatGPT API (GeminiService 템플릿 참조)
│   ├── GoogleDriveService.kt      # Drive 업로드/폴더 관리
│   ├── FileManager.kt             # 파일 저장/변환/통합저장
│   ├── RecordingService.kt        # Foreground Service 녹음
│   └── CallManager.kt             # 통화 감지 자동 일시정지/재개
├── util/
│   └── MdToPdfConverter.kt        # MD→HTML→PDF 변환 (카카오톡 공유용)
├── viewmodel/
│   ├── RecordingViewModel.kt      # 녹음→STT→요약 파이프라인
│   ├── SettingsViewModel.kt       # 설정 + Drive 연동 + 오류 진단
│   └── MeetingListViewModel.kt    # 회의목록 + 파일관리 + PDF 공유
├── ui/screens/
│   ├── RecordingScreen.kt         # 녹음/변환 화면
│   ├── SettingsScreen.kt          # 설정 화면 (엔진설정 상단 배치)
│   └── MeetingListScreen.kt       # 회의목록 + 파일관리 화면
└── widget/
    └── RecordingWidget.kt         # 홈스크린 위젯
```

---

## 문서

| 문서 | 설명 |
|------|------|
| [docs/설치매뉴얼_v2.0.html](docs/설치매뉴얼_v2.0.html) | 설치 및 사용자 매뉴얼 (브라우저에서 열기) |
| [docs/v2.0_회의록_양식_샘플.md](docs/v2.0_회의록_양식_샘플.md) | v2.0 회의록 양식 샘플 |

---

## 변경 이력

| 버전 | 날짜 | 주요 변경 |
|------|------|-----------|
| v3.0.0 | 2026-04-09 | 녹음 중 인터럽트 완전 차단 (전화·카메라·다른앱), 회의록 .md→.txt 변경, SAF 직접 저장, 요약 타임아웃 확대+재시도(2회), STT창 MP3 파일 선택, 회의록창 STT txt 선택+재시작, 양식별 프롬프트·샘플 미리보기, SAF 미설정 경고 |
| v2.1.0 | 2026-03-27 | MD→PDF 변환 공유 (카카오톡 호환), Google Drive 오류 진단 강화, 넘버링 구조 통일 (`1→1.1→·→·`), 볼드마크 제거, 주제 요약 내용 중심화, ChatGPT 엔진 지원, 설치매뉴얼 추가 |
| v2.0.0 | 2026-03-22 | 크래시 수정 18건, Gemini OkHttp 전환, Drive 자동 업로드, 하위폴더 탐색, 파일관리, 통화 감지, 커스텀 아이콘, 문서화 |
| v1.0.0 | 2026-03-19 | 모바일 버전 최초 릴리스 (데스크톱 v2.0 기반 전환) |

---

## 라이선스

Private — K-Run Ventures 내부 사용
