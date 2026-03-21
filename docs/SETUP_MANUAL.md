# 회의녹음요약 모바일 앱 — PC 포맷 후 재설치 매뉴얼

**작성일:** 2026-03-22
**대상:** 대표이사 / 개발 담당자
**프로젝트:** meeting-recording-mobile (Android / Kotlin / Jetpack Compose)

---

## 1. 개발 환경 설치

### 1-1. Android Studio 설치

1. https://developer.android.com/studio 에서 최신 버전 다운로드
2. 설치 후 SDK Manager에서 다음 설치:
   - Android SDK Platform 35 (Android 15)
   - Android SDK Build-Tools 35.0.0
   - Android SDK Command-line Tools

### 1-2. JDK 확인

- Android Studio 내장 JBR(JetBrains Runtime) 17 사용
- 별도 JDK 설치 불필요

---

## 2. GitHub 리포지토리 설정 및 소스코드 다운로드

### 2-1. 리포지토리가 이미 존재하는 경우

```bash
git clone https://github.com/antonio103first/meeting-recording-mobile-app.git
cd meeting-recording-mobile-app
```

### 2-2. 리포지토리를 새로 생성해야 하는 경우

> ⚠ GitHub 계정을 변경했거나 리포지토리가 삭제된 경우, 먼저 GitHub에서 리포지토리를 생성해야 합니다.

1. https://github.com/new 접속 (antonio103first 계정으로 로그인)
2. **Repository name**: `meeting-recording-mobile-app`
3. **Visibility**: Private 또는 Public 선택
4. **README, .gitignore, license**: 모두 체크 해제 (빈 리포지토리로 생성)
5. **"Create repository"** 클릭

로컬에 소스가 있는 경우 아래 명령으로 연결 및 푸시:

```bash
cd meeting-recording-mobile-app
git remote add origin https://github.com/antonio103first/meeting-recording-mobile-app.git
git push -u origin main
```

> 💡 `remote: Repository not found` 오류가 발생하면 GitHub에 리포지토리가 아직 생성되지 않은 것입니다. 위 절차로 먼저 생성하세요.

---

## 3. google-services.json 설정 (필수)

> ⚠ 이 파일은 보안상 GitHub에 포함되어 있지 않습니다. 반드시 수동으로 설정해야 합니다.

### 방법 1: 기존 파일 복원

PC 포맷 전에 백업해둔 `google-services.json` 파일을 `app/` 폴더에 복사

### 방법 2: Firebase Console에서 재다운로드

1. https://console.firebase.google.com/ 접속
2. **MeetRecordSummary** 프로젝트 선택
3. ⚙ 프로젝트 설정 → 내 앱 → Android 앱 (`com.krunventures.meetingrecorder`)
4. `google-services.json` 다운로드
5. `app/` 폴더에 배치

### google-services.json 핵심 정보

| 항목 | 값 |
|------|-----|
| 프로젝트 번호 | 673495310643 |
| 프로젝트 ID | meetrecordsummary-488002 |
| 패키지명 | com.krunventures.meetingrecorder |
| OAuth client_type | 1 (Android) |

### 방법 3: Firebase 프로젝트 신규 생성 시

1. Firebase Console → 프로젝트 추가 → "MeetRecordSummary" GCP 프로젝트 가져오기
2. Android 앱 추가 → 패키지명: `com.krunventures.meetingrecorder`
3. SHA-1 지문 등록 (아래 명령어로 확인)
4. `google-services.json` 다운로드 → `app/` 폴더에 배치

---

## 4. SHA-1 디버그 키 확인

### Windows (PowerShell)

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android
```

### Mac/Linux

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

> 기존 SHA-1: `B7:DD:F0:DC:0B:0E:31:3E:A2:33:77:BE:C8:A4:F8:CF:FB:9A:A1:C4`
> PC 포맷 후에는 debug.keystore가 새로 생성되므로, 새 SHA-1을 Firebase에 등록해야 합니다.

---

## 5. Google Cloud Console 설정

### 5-1. Drive API 활성화

1. https://console.cloud.google.com/ → 프로젝트: MeetRecordSummary
2. API 및 서비스 → 라이브러리 → "Google Drive API" 검색 → 활성화

### 5-2. OAuth 동의 화면

1. API 및 서비스 → OAuth 동의 화면
2. 앱 이름: MeetRecordSummary
3. 테스트 사용자에 본인 이메일 추가 (외부 게시 전까지)

### 5-3. OAuth 클라이언트 ID

1. API 및 서비스 → 사용자 인증 정보 → OAuth 2.0 클라이언트 ID
2. Android 유형, 패키지명: `com.krunventures.meetingrecorder`, SHA-1 등록

---

## 6. 빌드 환경 정보

| 항목 | 버전 |
|------|------|
| AGP (Android Gradle Plugin) | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Gradle | 8.9 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Compose BOM | 2024.12.01 |

> ⚠ Android Studio Upgrade Assistant가 AGP/Kotlin을 자동 업그레이드하면 빌드가 깨집니다.
> 업그레이드 제안을 거부하고 위 버전을 유지하세요.

---

## 7. 빌드 및 설치

### 빌드

1. Android Studio에서 프로젝트 열기
2. File → Sync Project with Gradle Files
3. Build → Rebuild Project
4. APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

### Galaxy S25 설치

1. APK를 Google Drive에 업로드
2. S25에서 Google Drive 앱 → 다운로드 → 설치
3. "출처를 알 수 없는 앱" 허용

---

## 8. API 키 설정 (앱 내 설정 메뉴)

앱 설치 후 설정 탭에서 입력:

| API | 설정 위치 |
|-----|-----------|
| CLOVA Speech Invoke URL | 설정 → CLOVA Speech API |
| CLOVA Speech Secret Key | 설정 → CLOVA Speech API |
| Gemini API Key | 설정 → Gemini API |
| Claude API Key (선택) | 설정 → Claude API |

### API 키 발급처

- **Gemini**: https://aistudio.google.com/apikey
- **CLOVA Speech**: https://console.ncloud.com/ → AI-NAVER API → CLOVA Speech
- **Claude**: https://console.anthropic.com/

---

## 9. Google Drive 폴더 설정

앱 기본 하드코딩된 Drive 폴더:

| 용도 | 폴더 ID |
|------|---------|
| 녹음파일 (mp3/m4a) | 1Yu6snQUtwl62j98b64Foi5iZ7mqn2GpS |
| 회의록 (txt) | 1R8WbbJrhm3PLG0wZ0NPim_Kc6KRt9GHX |

> 설정 → Google Drive → 각 폴더 "변경" 버튼으로 다른 폴더 선택 가능

---

## 10. 주요 파일 구조

```
app/src/main/java/com/krunventures/meetingrecorder/
├── MeetingApp.kt              # Application 클래스 (글로벌 크래시 핸들러)
├── MainActivity.kt            # 메인 액티비티 (Compose NavHost)
├── data/
│   ├── ConfigManager.kt       # SharedPreferences 기반 설정 관리
│   ├── MeetingDao.kt          # Room DB DAO
│   └── MeetingDatabase.kt     # Room DB 정의
├── service/
│   ├── ClovaService.kt        # CLOVA Speech STT API
│   ├── GeminiService.kt       # Gemini REST API (OkHttp 직접 호출)
│   ├── ClaudeService.kt       # Claude API
│   ├── GoogleDriveService.kt  # Google Drive 업로드/폴더 관리
│   ├── FileManager.kt         # 파일 저장/변환
│   ├── RecordingService.kt    # 녹음 Foreground Service
│   └── CallManager.kt         # 통화 감지 (자동 일시정지/재개)
├── viewmodel/
│   ├── RecordingViewModel.kt  # 녹음→STT→요약 파이프라인
│   ├── SettingsViewModel.kt   # 설정 관리 + Drive 연동
│   └── MeetingListViewModel.kt # 회의 목록 + 파일 관리
└── ui/screens/
    ├── RecordingScreen.kt     # 녹음 화면
    ├── SettingsScreen.kt      # 설정 화면
    └── MeetingListScreen.kt   # 회의 목록 화면
```

---

## 11. 문제 해결

### 빌드 에러: KSP 버전 불일치

`build.gradle.kts`에서 AGP, Kotlin, KSP 버전이 §6의 표와 일치하는지 확인

### Google Drive 로그인 실패

1. `app/google-services.json` 존재 여부 확인
2. Firebase에 SHA-1 지문 등록 확인
3. Google Cloud에 Drive API 활성화 확인

### 앱 크래시 시 로그 확인

크래시 로그 위치: `/Android/data/com.krunventures.meetingrecorder/files/Documents/crash_log.txt`

---

*본 매뉴얼은 2026-03-22 기준으로 작성되었습니다.*
