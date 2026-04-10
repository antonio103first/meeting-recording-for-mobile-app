# 회의녹음요약 모바일 앱 — PC 포맷 후 재설치 매뉴얼

**문서 버전:** v2.0
**작성일:** 2026-03-22
**대상:** 대표이사 / 개발 담당자
**프로젝트:** meeting-recording-mobile (Android / Kotlin / Jetpack Compose)
**패키지명:** `com.krunventures.meetingrecorder`

---

## 1. 개발 환경 설치

### 1-1. Android Studio 설치

1. https://developer.android.com/studio 에서 최신 버전 다운로드 및 설치
2. 설치 완료 후 **SDK Manager** (File → Settings → Languages & Frameworks → Android SDK)에서 다음 항목 설치:
   - **SDK Platforms 탭:** Android 15.0 (API 35) 체크 → Apply
   - **SDK Tools 탭:** Android SDK Build-Tools 35.0.0, Android SDK Command-line Tools, Android SDK Platform-Tools 체크 → Apply

### 1-2. JDK 확인

- Android Studio 내장 JBR(JetBrains Runtime) 17 사용
- 별도 JDK 설치 불필요
- 확인 방법: File → Settings → Build → Gradle → Gradle JDK → `jbr-17` 선택

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

> ⚠ 이 파일은 보안상 GitHub에 포함되어 있지 않습니다 (`.gitignore`에 등록됨). 반드시 수동으로 설정해야 합니다.
> 이 파일이 없으면 빌드는 되지만 **Google Drive 로그인이 작동하지 않습니다.**

### 방법 1: 기존 파일 복원

PC 포맷 전에 백업해둔 `google-services.json` 파일을 `app/` 폴더에 복사

### 방법 2: Firebase Console에서 재다운로드

1. https://console.firebase.google.com/ 접속
2. **MeetRecordSummary** 프로젝트 선택 (project_number: 673495310643)
3. ⚙ 프로젝트 설정 → 내 앱 → Android 앱 (`com.krunventures.meetingrecorder`)
4. `google-services.json` 다운로드
5. 프로젝트의 `app/` 폴더에 배치

### google-services.json 핵심 정보

| 항목 | 값 |
|------|-----|
| 프로젝트 번호 | 673495310643 |
| 프로젝트 ID | meetrecordsummary-488002 |
| 패키지명 | com.krunventures.meetingrecorder |
| OAuth client_type | 1 (Android) |
| Storage Bucket | meetrecordsummary-488002.firebasestorage.app |

### 방법 3: Firebase 프로젝트 신규 생성 시

1. Firebase Console → 프로젝트 추가 → "MeetRecordSummary" GCP 프로젝트 가져오기
2. Android 앱 추가 → 패키지명: `com.krunventures.meetingrecorder`
3. SHA-1 지문 등록 (§4 참조)
4. `google-services.json` 다운로드 → `app/` 폴더에 배치

### google-services.json 정상 여부 확인

파일을 텍스트 에디터로 열어 `oauth_client` 배열에 `client_type: 1` (Android) 항목이 있는지 확인:

```json
"oauth_client": [
  {
    "client_id": "673495310643-xxx.apps.googleusercontent.com",
    "client_type": 1,
    "android_info": {
      "package_name": "com.krunventures.meetingrecorder",
      "certificate_hash": "b7ddf0dc..."
    }
  }
]
```

> ⚠ `oauth_client`가 빈 배열 `[]`이면 Google Drive 로그인이 실패합니다. §4의 SHA-1 등록 후 재다운로드하세요.

---

## 4. SHA-1 디버그 키 등록

> **PC 포맷 시 `debug.keystore`가 새로 생성되므로 SHA-1이 변경됩니다. 반드시 새 SHA-1을 Firebase에 등록해야 합니다.**

### 4-1. SHA-1 확인

**Windows (PowerShell)**

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android
```

**Mac/Linux**

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

출력에서 `SHA1:` 뒤의 값을 복사합니다.

> 이전 SHA-1 (참고): `B7:DD:F0:DC:0B:0E:31:3E:A2:33:77:BE:C8:A4:F8:CF:FB:9A:A1:C4`

### 4-2. Firebase에 SHA-1 등록

1. https://console.firebase.google.com/ → MeetRecordSummary 프로젝트
2. ⚙ 프로젝트 설정 → 내 앱 → Android 앱 (`com.krunventures.meetingrecorder`)
3. **"디지털 지문 추가"** 클릭 → 새 SHA-1 붙여넣기 → 저장
4. **`google-services.json` 재다운로드** → `app/` 폴더에 덮어쓰기

### 4-3. Google Cloud Console에서도 확인

1. https://console.cloud.google.com/ → 프로젝트: MeetRecordSummary
2. API 및 서비스 → 사용자 인증 정보 → OAuth 2.0 클라이언트 ID
3. Android 유형 클라이언트에 새 SHA-1이 반영되었는지 확인 (Firebase에서 등록하면 자동 동기화됨)

---

## 5. Google Cloud Console 설정

### 5-1. Drive API 활성화 확인

1. https://console.cloud.google.com/ → 프로젝트: MeetRecordSummary
2. API 및 서비스 → 라이브러리 → "Google Drive API" 검색
3. **"사용"** 상태인지 확인 (비활성화 상태면 활성화)

### 5-2. OAuth 동의 화면

1. API 및 서비스 → OAuth 동의 화면
2. 앱 이름: MeetRecordSummary
3. 테스트 사용자에 본인 이메일 추가 (외부 게시 전까지 필수)

> ⚠ 테스트 사용자에 등록되지 않은 Google 계정으로는 로그인 불가

### 5-3. OAuth 클라이언트 ID

1. API 및 서비스 → 사용자 인증 정보 → OAuth 2.0 클라이언트 ID
2. Android 유형, 패키지명: `com.krunventures.meetingrecorder`, SHA-1 등록 확인

---

## 6. Android Studio 프로젝트 열기 및 Gradle Sync

> ⚠ **이 단계에서 실수하면 빌드가 반복적으로 깨집니다. 아래 절차를 정확히 따르세요.**

### 6-1. 프로젝트 열기

1. Android Studio → **File → Open** → `meeting-recording-mobile-app` 폴더 선택
2. "Trust Project?" 팝업 → **Trust Project** 클릭
3. Gradle sync가 자동으로 시작됩니다 (우측 하단 진행바 확인)

### 6-2. Gradle Sync 실패 시 대처

**증상 1: "Gradle sync failed"**

1. **File → Invalidate Caches** → Invalidate and Restart
2. 재시작 후 File → **Sync Project with Gradle Files** (툴바의 코끼리+화살표 아이콘)

**증상 2: "SDK location not found"**

1. 프로젝트 루트에 `local.properties` 파일 확인
2. 없으면 생성:
```properties
sdk.dir=C\:\\Users\\anton\\AppData\\Local\\Android\\Sdk
```
(경로는 본인 PC에 맞게 수정)

**증상 3: "Could not resolve dependencies"**

1. File → Settings → Build → Gradle → Gradle JDK → **jbr-17** 선택
2. `gradle-wrapper.properties`에서 Gradle 버전 확인:
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
```

### 6-3. ⚠ AGP/Kotlin 자동 업그레이드 거부 (매우 중요)

> Android Studio가 AGP(Android Gradle Plugin), Kotlin 업그레이드를 제안하는 팝업이 뜰 수 있습니다. **반드시 "Don't remind me again" 또는 "Cancel"을 클릭하세요.**

현재 안정 동작하는 버전 조합 (절대 변경 금지):

| 항목 | 버전 | 파일 위치 |
|------|------|-----------|
| AGP (Android Gradle Plugin) | **8.7.3** | `build.gradle.kts` (project) |
| Kotlin | **2.0.21** | `build.gradle.kts` (project) |
| KSP | **2.0.21-1.0.28** | `build.gradle.kts` (project) |
| Gradle | **8.9** | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk | **35** | `build.gradle.kts` (app) |
| minSdk | **26** | `build.gradle.kts` (app) |
| Compose BOM | **2024.12.01** | `build.gradle.kts` (app) |
| google-services plugin | **4.4.0** | `build.gradle.kts` (project) |

> 💡 AGP를 9.x로 업그레이드하면 Kotlin/KSP/Compose BOM 전체가 호환성 문제를 일으킵니다. 개발 당시 이 문제로 3차례 빌드가 깨진 경험이 있습니다.

---

## 7. APK 빌드 및 디바이스 설치

### 7-1. Debug APK 빌드 (권장)

**방법 A — Android Studio 메뉴**

1. 상단 메뉴 → **Build → Rebuild Project** (전체 클린 빌드)
2. 빌드 완료까지 대기 (하단 Build 탭에서 진행 확인, 첫 빌드 시 3~5분 소요)
3. 빌드 성공 메시지: `BUILD SUCCESSFUL`

**방법 B — 터미널에서 직접 빌드**

```bash
cd meeting-recording-mobile-app
./gradlew assembleDebug
```

**APK 출력 위치:**

```
app/build/outputs/apk/debug/app-debug.apk
```

### 7-2. Release APK 빌드 (배포용)

```bash
./gradlew assembleRelease
```

출력 위치: `app/build/outputs/apk/release/app-release.apk`

> ⚠ Release 빌드는 ProGuard 난독화가 적용됩니다. 서명 키(keystore)가 필요합니다.

### 7-3. 빌드 에러 발생 시 체크리스트

| 에러 메시지 | 해결 방법 |
|-------------|-----------|
| `google-services.json not found` | §3 참조 — `app/` 폴더에 파일 배치 |
| `KSP version is not compatible` | `build.gradle.kts` (project)에서 KSP 버전이 2.0.21-1.0.28인지 확인 |
| `Unresolved reference: collectAsStateWithLifecycle` | `lifecycle-runtime-compose:2.8.7` 의존성 확인 |
| `Execution failed for task ':app:mergeDebugResources'` | Build → Clean Project → Rebuild Project |
| `Java heap space` | `gradle.properties`에 `org.gradle.jvmargs=-Xmx2048m` 설정 |

### 7-4. Galaxy S25에 설치

**방법 A — USB 직접 설치 (권장)**

1. Galaxy S25에서: 설정 → 개발자 옵션 → USB 디버깅 ON
   - 개발자 옵션이 없으면: 설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호 7번 탭
2. USB 케이블로 PC와 연결
3. Android Studio 상단 디바이스 드롭다운에서 Galaxy S25 선택
4. **▶ Run 버튼** 클릭 (APK 빌드 + 설치 + 실행을 자동으로 수행)

**방법 B — APK 파일 전송**

1. 빌드된 `app-debug.apk`를 Google Drive에 업로드
2. S25에서 Google Drive 앱 → 다운로드 → 설치
3. "출처를 알 수 없는 앱" 허용 팝업 → 허용

**방법 C — ADB 명령어**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 8. 앱 설정 — API 키 입력

앱 설치 후 **설정 탭**에서 다음 API 키를 입력해야 STT 및 AI 요약이 작동합니다.

### 8-1. 엔진 설정 (설정 메뉴 최상단)

| 항목 | 옵션 |
|------|------|
| STT 엔진 | CLOVA Speech (기본) |
| 요약 엔진 | Gemini (기본) / Claude 선택 |

### 8-2. API 키 입력

| API | 설정 위치 | 발급처 |
|-----|-----------|--------|
| CLOVA Speech Invoke URL | 설정 → CLOVA Speech API | https://console.ncloud.com/ → AI-NAVER API → CLOVA Speech |
| CLOVA Speech Secret Key | 설정 → CLOVA Speech API | 위와 동일 |
| Gemini API Key | 설정 → Gemini API | https://aistudio.google.com/apikey |
| Claude API Key (선택) | 설정 → Claude API | https://console.anthropic.com/ |

### 8-3. Gemini 모델 자동 감지

Gemini API는 아래 순서로 사용 가능한 모델을 자동 탐색합니다 (수동 설정 불필요):

1. `gemini-2.5-flash` → 2. `gemini-2.0-flash` → 3. `gemini-1.5-flash` → 4. `gemini-pro`

---

## 9. Google Drive 연동 설정

### 9-1. Drive 로그인

설정 탭 → Google Drive 섹션 → **"Google Drive 연결"** 버튼 → Google 계정 선택 → 권한 허용

### 9-2. 저장 폴더 설정

앱 기본 하드코딩된 Drive 폴더:

| 용도 | 폴더 ID |
|------|---------|
| 녹음파일 (mp3/m4a) | 1Yu6snQUtwl62j98b64Foi5iZ7mqn2GpS |
| 회의록 (txt) | 1R8WbbJrhm3PLG0wZ0NPim_Kc6KRt9GHX |

> 설정 → Google Drive → 각 폴더 "변경" 버튼으로 다른 폴더 선택 가능
> **하위 폴더 탐색 지원**: 폴더명을 클릭하면 하위 디렉토리로 진입, Breadcrumb 경로로 이동

### 9-3. Drive 로그인 실패 시 체크리스트

| 체크 항목 | 확인 방법 |
|-----------|-----------|
| `google-services.json` 존재 | `app/` 폴더에 파일 있는지 확인 |
| `oauth_client` 정상 | 파일 열어서 빈 배열 `[]`이 아닌지 확인 |
| SHA-1 등록 | Firebase Console → 프로젝트 설정 → 디지털 지문 |
| Drive API 활성화 | Google Cloud Console → API 라이브러리 |
| OAuth 동의 화면 | 테스트 사용자에 본인 이메일 등록 여부 |

---

## 10. 앱 기능 요약 (v2.0 기준)

| 기능 | 설명 |
|------|------|
| 녹음 | Foreground Service 기반 백그라운드 녹음, 통화 시 자동 일시정지/재개 |
| STT (음성→텍스트) | CLOVA Speech API (한국어 최적화) |
| AI 요약 | Gemini / Claude 선택, 5가지 요약 형식 지원 |
| Google Drive 자동 업로드 | 녹음파일 + 회의록 자동 업로드, 하위폴더 탐색 |
| 파일 관리 | 삭제, 이름변경, 공유, 파일만삭제 (DB 유지) |
| 텍스트 복사 | STT/요약/지표 텍스트 영역 선택 복사 |
| 파일이름 지정 | 저장 전 파일이름 입력 다이얼로그 |
| 홈스크린 위젯 | 녹음 상태 표시 위젯 |

---

## 11. 주요 파일 구조

```
meeting-recording-mobile-app/
├── app/
│   ├── build.gradle.kts              # 앱 모듈 빌드 설정 (dependencies)
│   ├── google-services.json          # ⚠ .gitignore — 수동 배치 필요
│   ├── proguard-rules.pro            # Release 난독화 규칙
│   └── src/main/
│       ├── AndroidManifest.xml       # 권한, 서비스, FileProvider 선언
│       ├── java/com/krunventures/meetingrecorder/
│       │   ├── MeetingApp.kt              # Application (글로벌 크래시 핸들러)
│       │   ├── MainActivity.kt            # Compose NavHost
│       │   ├── data/
│       │   │   ├── ConfigManager.kt       # SharedPreferences 설정 관리
│       │   │   ├── MeetingDao.kt          # Room DAO
│       │   │   └── MeetingDatabase.kt     # Room DB 정의
│       │   ├── service/
│       │   │   ├── ClovaService.kt        # CLOVA Speech STT
│       │   │   ├── GeminiService.kt       # Gemini REST API (OkHttp 직접 호출)
│       │   │   ├── ClaudeService.kt       # Claude API
│       │   │   ├── GoogleDriveService.kt  # Drive 업로드/폴더 관리
│       │   │   ├── FileManager.kt         # 파일 저장/변환/통합저장
│       │   │   ├── RecordingService.kt    # Foreground Service 녹음
│       │   │   └── CallManager.kt         # 통화 감지 자동 일시정지
│       │   ├── viewmodel/
│       │   │   ├── RecordingViewModel.kt  # 녹음→STT→요약 파이프라인
│       │   │   ├── SettingsViewModel.kt   # 설정 + Drive 연동 + 폴더탐색
│       │   │   └── MeetingListViewModel.kt # 회의목록 + 파일관리
│       │   └── ui/screens/
│       │       ├── RecordingScreen.kt     # 녹음 화면
│       │       ├── SettingsScreen.kt      # 설정 화면 (엔진설정 상단)
│       │       └── MeetingListScreen.kt   # 회의목록 화면
│       └── res/
│           ├── mipmap-anydpi-v26/         # Adaptive Icon XML
│           ├── mipmap-*/ic_launcher*.png  # 앱 아이콘 (5 해상도)
│           ├── values/ic_launcher_background.xml  # 아이콘 배경색
│           └── xml/file_paths.xml         # FileProvider 경로
├── build.gradle.kts                  # 프로젝트 레벨 (AGP/Kotlin/KSP 버전)
├── gradle.properties                 # JVM 메모리, AndroidX 설정
├── gradle/wrapper/gradle-wrapper.properties  # Gradle 8.9
├── settings.gradle.kts               # 모듈 설정
└── docs/
    ├── SETUP_MANUAL.md               # 본 문서
    ├── DEVELOPMENT_PLAN.md           # 개발 기획서
    └── DEVELOPMENT_HISTORY.md        # 개발 히스토리
```

---

## 12. 문제 해결 (Troubleshooting)

### 12-1. 빌드 관련

| 문제 | 원인 | 해결 |
|------|------|------|
| KSP 버전 불일치 | AGP/Kotlin 자동 업그레이드 | `build.gradle.kts` (project)에서 §6-3의 버전으로 되돌리기 |
| Compose 컴파일 에러 | Kotlin과 Compose 버전 불일치 | Kotlin 2.0.21 + Compose BOM 2024.12.01 유지 |
| `mergeDebugResources` 실패 | 리소스 캐시 손상 | Build → Clean → Rebuild |
| Gradle sync 무한 로딩 | 네트워크/프록시 | VPN 끄고 재시도, `gradle.properties`에 프록시 설정 |

### 12-2. 런타임 관련

| 문제 | 원인 | 해결 |
|------|------|------|
| Google Drive 로그인 실패 | oauth_client 비어있음 | §4 SHA-1 등록 → google-services.json 재다운로드 |
| 녹음 시작 안됨 | 마이크 권한 거부 | 앱 설정 → 권한 → 마이크 허용 |
| STT 변환 실패 | API 키 미입력 | 설정 → CLOVA Speech → URL/Key 입력 |
| AI 요약 실패 | Gemini 모델 미지원 | 모델 자동 감지가 모두 실패한 경우 API 키 확인 |
| 파일 저장 실패 | Scoped Storage | 앱 전용 디렉토리 사용 (코드에서 처리됨) |
| 앱 크래시 | 다양한 원인 | 크래시 로그 확인 (아래 경로) |

### 12-3. 크래시 로그 확인

앱 내 글로벌 크래시 핸들러가 로그를 자동 저장합니다:

```
/Android/data/com.krunventures.meetingrecorder/files/Documents/crash_log.txt
```

Android Studio에서 실시간 로그 확인:

```
Logcat → 필터: package:com.krunventures.meetingrecorder level:error
```

---

## 13. 전체 재설치 체크리스트

PC 포맷 후 아래 순서대로 진행하면 됩니다:

1. ☐ Android Studio 설치 (§1)
2. ☐ `git clone` 소스코드 다운로드 (§2)
3. ☐ Firebase Console에서 `google-services.json` 재다운로드 → `app/` 폴더 배치 (§3)
4. ☐ 새 PC의 SHA-1 확인 → Firebase에 등록 → `google-services.json` 재다운로드 (§4)
5. ☐ Google Cloud에서 Drive API 활성화 확인 (§5)
6. ☐ Android Studio에서 프로젝트 열기 → Gradle Sync 성공 확인 (§6)
7. ☐ **AGP/Kotlin 자동 업그레이드 제안 거부** (§6-3)
8. ☐ Build → Rebuild Project → `BUILD SUCCESSFUL` 확인 (§7)
9. ☐ Galaxy S25에 APK 설치 (§7-4)
10. ☐ 앱 실행 → 설정 → API 키 입력 (§8)
11. ☐ Google Drive 로그인 테스트 (§9)

---

*본 매뉴얼은 v2.0 (2026-03-22) 기준으로 작성되었습니다.*
*v1.0 대비 주요 추가: Android Studio sync 상세 절차, APK 빌드 방법, 빌드 에러 대응, Galaxy S25 설치 방법, GitHub 리포지토리 생성 안내, 전체 재설치 체크리스트*
