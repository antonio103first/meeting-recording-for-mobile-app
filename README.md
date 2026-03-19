# 🎙 회의녹음요약 (모바일)

회의·강의 음성을 녹음하거나 오디오 파일을 불러와
**STT 변환 → 회의록/강의 요약 → 핸드폰 저장 → Google Drive 자동 업로드**
까지 한 번에 처리하는 Android 모바일 앱입니다.

> **데스크톱 버전**: [meeting-recording-minute-app](https://github.com/antonio103first/meeting-recording-minute-app)

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| 🎙 실시간 녹음 | 마이크 녹음 → M4A 자동 저장 (백그라운드 녹음 지원) |
| 📂 파일 불러오기 | MP3/WAV/M4A 등 기존 오디오 파일 선택 |
| 📝 STT 변환 | CLOVA Speech(NAVER) 또는 Gemini(Google) |
| 📋 회의록 요약 | Gemini 또는 Claude로 5가지 요약 방식 지원 |
| 📊 핵심 지표 | 결정사항, 액션아이템, 주요 일정 자동 추출 |
| ☁ Google Drive | 녹음·요약 각각 지정 폴더에 자동 업로드 |
| 📱 홈 위젯 | 원터치 녹음 시작 위젯 |
| 🌙 다크모드 | 시스템 설정 연동 자동 전환 |

### STT 엔진

| 엔진 | 특징 | 권장 상황 |
|------|------|-----------|
| **CLOVA Speech** ★권장★ | 한국어 특화, 화자 분리 지원 | 긴 회의 (30분 이상) |
| **Gemini** | 무료 API, 설정 간편 | 짧은 녹음 (5분 이내) |

### 요약 방식

1. **화자 중심** — 참석자별 발언 정리
2. **주제 중심** — 안건별 논의 정리
3. **공식 양식 (MD)** — 마크다운 공식 회의록
4. **공식 양식 (Text)** — 텍스트 공식 회의록
5. **강의 요약** — 강의 노트 요약

---

## 저장 경로

```
내장 저장소/Documents/Meeting recording/
├── 녹음파일/              ← M4A 녹음 파일
│   ├── 20260319_143000_녹음.m4a
│   └── 회의록(요약)/      ← STT + 요약 텍스트
│       ├── 20260319_143000_녹음.txt
│       └── 20260319_143000_요약.txt
```

---

## 시스템 요구사항

- **디바이스**: Samsung Galaxy S25 (또는 Android 8.0+ 기기)
- **OS**: Android 8.0 (API 26) 이상
- **권장**: Android 14+ (API 34)
- **인터넷 연결 필수**

---

## 빌드 및 설치

### 사전 준비
- [Android Studio](https://developer.android.com/studio) Hedgehog 2023.1 이상
- JDK 17

### 빌드

```bash
git clone https://github.com/antonio103first/meeting-recording-mobile-app.git
cd meeting-recording-mobile-app
```

1. Android Studio에서 프로젝트 열기
2. Gradle Sync 완료 대기
3. Galaxy S25 USB 디버깅 연결
4. ▶ Run 버튼 클릭

---

## 초기 설정

### Step 1. Gemini API 키 (필수)
1. [aistudio.google.com/apikey](https://aistudio.google.com/apikey) 접속
2. **Create API Key** 클릭
3. 앱 설정 탭에 입력

### Step 2. CLOVA Speech API (STT 권장)
1. [console.ncloud.com](https://console.ncloud.com) → CLOVA Speech
2. Invoke URL + Secret Key 발급
3. 앱 설정 탭에 입력

### Step 3. Claude API (선택)
1. [console.anthropic.com](https://console.anthropic.com) → API Keys
2. 앱 설정 탭에 입력

### Step 4. Google Drive (선택)
1. 앱 설정 탭에서 Google 로그인
2. 업로드 폴더 자동 생성

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| 언어 | Kotlin 1.9 |
| UI | Jetpack Compose + Material3 |
| 녹음 | Android MediaRecorder (AAC) |
| DB | Room (SQLite ORM) |
| HTTP | OkHttp3 |
| AI | Google Generative AI SDK, Anthropic API |
| STT | CLOVA Speech Long API, Gemini |
| Drive | Google Sign-In + Drive API v3 |
| 비동기 | Kotlin Coroutines + StateFlow |

---

## 프로젝트 구조

```
app/src/main/java/com/krunventures/meetingrecorder/
├── MainActivity.kt              # 메인 (3탭 네비게이션)
├── MeetingApp.kt                # Application
├── data/
│   ├── Meeting.kt               # Room Entity
│   ├── MeetingDao.kt            # Room DAO
│   ├── AppDatabase.kt           # Room Database
│   └── ConfigManager.kt         # 설정 관리
├── service/
│   ├── AudioRecorderManager.kt  # 녹음
│   ├── ClovaService.kt          # CLOVA STT
│   ├── GeminiService.kt         # Gemini STT + 요약
│   ├── ClaudeService.kt         # Claude 요약
│   ├── GoogleDriveService.kt    # Drive 연동
│   ├── FileManager.kt           # 파일 관리
│   └── RecordingService.kt      # 포그라운드 서비스
├── ui/
│   ├── theme/Theme.kt           # 테마 (다크모드)
│   └── screens/
│       ├── RecordingScreen.kt   # 녹음/변환
│       ├── MeetingListScreen.kt # 회의목록
│       └── SettingsScreen.kt    # 설정
├── viewmodel/
│   ├── RecordingViewModel.kt
│   ├── MeetingListViewModel.kt
│   └── SettingsViewModel.kt
└── widget/
    └── RecordingWidget.kt       # 홈 위젯
```

---

## 의존성

```
Jetpack Compose BOM 2024.01
Room 2.6.1
OkHttp 4.12.0
Google Generative AI 0.9.0
Google Drive API v3
Google Sign-In 21.0.0
Gson 2.10.1
Kotlin Coroutines 1.7.3
```

---

## 변경 이력

| 버전 | 날짜 | 주요 변경 |
|------|------|-----------|
| v1.0.0 | 2026-03-19 | 모바일 버전 최초 릴리스 (데스크톱 v2.0 기반 전환) |

---

## 라이선스

Private — K-Run Ventures 내부 사용
