# 회의녹음요약 모바일 앱 — 개발 히스토리

**프로젝트:** meeting-recording-mobile
**개발 기간:** 2026-03-19 ~ 2026-03-22
**개발 도구:** Claude AI (Cowork 모드) + Android Studio

---

## Phase 1: 초기 개발 (2026-03-19 ~ 03-20)

### v1.0.0 — 최초 릴리스

PC 버전 회의녹음요약 프로그램(Python/Tkinter)을 Android 네이티브 앱으로 전환하는 프로젝트로 시작.

**구현 내용:**
- Kotlin + Jetpack Compose + Material3 기반 UI 구축
- 3탭 구조: 녹음 / 회의목록 / 설정
- Foreground Service 기반 안정 녹음 (백그라운드 유지)
- CLOVA Speech API 연동 (한국어 STT)
- Gemini API 연동 (AI 요약) — Google AI SDK 사용
- Room Database로 회의 메타데이터 저장
- 5가지 요약 방식 (화자중심, 주제중심, 공식양식 MD/Text, 강의요약)

**첫 빌드 결과:** APK 생성 성공, Galaxy S25에 설치 확인

---

## Phase 2: 크래시 수정 및 안정화 (2026-03-20)

### 1차 수정 — 앱 크래시 수정

**문제:** 변환 시작 버튼 클릭 시 앱 즉시 크래시

**원인 (6건 발견):**
1. API 키 미설정 상태에서 예외 미처리
2. 녹음 서비스 중지 후 `startForegroundService()` 재호출 (Android 12+ 크래시)
3. 코루틴 예외 핸들러 부재
4. ClovaService `response.body?.string()` 이중 호출 → NPE (치명)
5. GeminiService JSON 파싱 시 null 체크 부재
6. STT/요약 콜백에서 예외 전파

**수정:** RecordingViewModel에 CoroutineExceptionHandler 추가, 각 서비스에 null-safe 처리 적용

### 2차 수정 — Gemini API 404 에러

**문제:** Gemini 연결 테스트 시 404 에러

**원인:** Google AI Android SDK가 `v1beta` API를 사용하며, 일부 모델 접근 불가

**5차 시도 끝에 해결:**
- Google AI SDK 완전 제거
- OkHttp + `x-goog-api-key` 헤더로 REST API 직접 호출
- 모델 자동 감지: gemini-2.5-flash → 2.0-flash → 1.5-flash → pro 순서

### 3차 수정 — 4개 에이전트 병렬 분석

4개 에이전트가 동시에 STT 파이프라인, ViewModel 흐름, 매니페스트/권한, UI/Compose를 분석하여 6건의 추가 취약점 발견 및 수정.

주요: Base64 OOM 방지(50MB 제한), `catch(Exception)` → `catch(Throwable)` 변경, `collectAsStateWithLifecycle()` 적용

### 4차 수정 — Android 16 호환성 (Galaxy S25 특화)

**문제:** 파일 저장 실패

**근본 원인:** `Environment.getExternalStoragePublicDirectory()`가 Android 16에서 Scoped Storage 엄격 적용으로 접근 차단됨

**수정:** 저장 경로를 `getExternalFilesDir()` (앱 전용 디렉토리)로 전면 변경 + 글로벌 크래시 핸들러 추가

---

## Phase 3: 빌드 환경 안정화 (2026-03-20)

Android Studio Upgrade Assistant가 AGP 9.1.0으로 자동 업그레이드하여 빌드가 반복적으로 깨지는 문제 발생. 3차례에 걸쳐 버전을 안정화:

**최종 안정 버전:** AGP 8.7.3 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Gradle 8.9

---

## Phase 4: 백그라운드 녹음 및 통화 제어 (2026-03-20)

**추가 기능:**
- `CallManager.kt` — 통화 수신 감지, 녹음 자동 일시정지/통화 종료 후 재개
- 위젯 `widget_recording_info.xml` — 홈스크린 위젯 (녹음 상태 표시)

---

## Phase 5: 신규 기능 일괄 추가 (2026-03-21)

### 5차 수정 — 요약파일 통합 + Google Drive 자동 업로드

**주요 변경:**
- `FileManager.kt`에 `saveCombinedSummary()` 추가 — 회의록요약 + STT원문을 1개 파일로 통합
- `GoogleDriveService.kt` 구현 완료 — Google Sign-In + Drive API 업로드
- `SettingsViewModel.kt` 전면 재작성 — Drive 로그인/로그아웃, 폴더 관리
- `SettingsScreen.kt` 전면 재작성 — Drive 설정 카드 UI

### 6차 수정 — 텍스트 복사, 폴더 하드코딩, 파일이름 다이얼로그

**주요 변경:**
- `SelectionContainer`로 STT/요약/지표 텍스트 영역선택 복사 가능
- Drive 폴더 ID를 `ConfigManager.kt`에 기본값 하드코딩
- 저장 전 파일이름 입력 다이얼로그 — 파이프라인 중단/재개 패턴

### 7차 수정 — 파일 관리 기능

**주요 변경:**
- `MeetingListViewModel.kt` 전면 재작성 — 삭제/이름변경/공유/파일만삭제
- `MeetingListScreen.kt` 전면 재작성 — 액션 메뉴, 파일상태 표시(✅/❌)
- `AndroidManifest.xml`에 FileProvider 추가
- `res/xml/file_paths.xml` 신규 생성

---

## Phase 6: Google Drive 로그인 문제 해결 (2026-03-21 ~ 03-22)

### 문제 상황

Google Drive 로그인이 작동하지 않음 — `google-services.json`의 `oauth_client`가 비어있음

### 원인 추적 과정

1. **google-services 플러그인 누락 발견** — `build.gradle.kts`에 플러그인 추가
2. **google-services.json 미존재** — Firebase Console에서 생성 필요
3. **SHA-1 지문 등록** — `keytool` 명령어로 디버그 키 추출 (Windows PowerShell 경로 문제 해결)
4. **프로젝트 불일치 발견** — Firebase 프로젝트(meeting-recorder-afa0e, 408124928848)와 OAuth가 설정된 GCP 프로젝트(MeetRecordSummary, 673495310643)가 다른 프로젝트
5. **Firebase 프로젝트 삭제됨** — 사용자가 meeting-recorder-afa0e를 실수로 삭제 예약
6. **MeetRecordSummary에서 재설정** — Firebase Console에서 이미 연결된 MeetRecordSummary 프로젝트 확인, SHA-1 지문 등록
7. **oauth_client 확인** — 재다운로드한 google-services.json에 Android OAuth 클라이언트 정상 포함 확인

### 핵심 교훈

- Firebase 프로젝트와 GCP 프로젝트가 1:1 매칭되어야 함
- OAuth 클라이언트는 GCP에서 생성하되, Firebase Android 앱에 SHA-1을 등록해야 google-services.json에 포함됨

---

## Phase 7: UI 개선 및 최종 마무리 (2026-03-22)

### 8차 수정 — 앱 아이콘 변경

- 사용자 제공 커스텀 아이콘 이미지 적용
- Android Adaptive Icon 설정 (foreground + background 분리)
- 5개 해상도(mdpi~xxxhdpi) + Play Store용(512px) 생성

### 9차 수정 — Drive 하위 폴더 탐색 + 설정 메뉴 재배치

- `DriveFolderPickerDialog` 전면 재작성 — Breadcrumb 경로, 하위 폴더 진입, 상위 이동, 현재 폴더 선택
- 설정 메뉴에서 "엔진 설정"을 맨 상단으로 이동

---

## 수정 파일 전체 목록 (누적)

| 파일 | 수정 유형 | 최종 상태 |
|------|-----------|-----------|
| `MeetingApp.kt` | 수정 | 글로벌 크래시 핸들러 |
| `MainActivity.kt` | 수정 | Compose NavHost |
| `service/ClovaService.kt` | 수정 (3회) | body 이중읽기 수정, JSON null-safe |
| `service/GeminiService.kt` | **전면 재작성** | OkHttp REST API, 모델 자동감지 |
| `service/ClaudeService.kt` | 유지 | Anthropic API |
| `service/GoogleDriveService.kt` | 유지 | Drive 업로드/폴더 관리 |
| `service/FileManager.kt` | 수정 | saveCombinedSummary() 추가 |
| `service/RecordingService.kt` | 수정 | Foreground Service 안정화 |
| `service/CallManager.kt` | **신규** | 통화 감지 |
| `data/ConfigManager.kt` | 수정 | Drive 폴더 ID 하드코딩, 저장경로 변경 |
| `data/MeetingDao.kt` | 수정 | updateFileName, updateFilePaths |
| `viewmodel/RecordingViewModel.kt` | 수정 (6회) | 파이프라인 안정화, 통합저장, 파일이름 다이얼로그, Drive 업로드 |
| `viewmodel/SettingsViewModel.kt` | **전면 재작성** | Drive 연동, 하위폴더 탐색 |
| `viewmodel/MeetingListViewModel.kt` | **전면 재작성** | 파일 관리 |
| `ui/screens/RecordingScreen.kt` | 수정 | SelectionContainer, FileNameInputDialog |
| `ui/screens/SettingsScreen.kt` | **전면 재작성** | Drive UI, 폴더 선택, 엔진설정 상단 배치 |
| `ui/screens/MeetingListScreen.kt` | **전면 재작성** | 파일 관리 UI |
| `AndroidManifest.xml` | 수정 | FileProvider, 권한 추가 |
| `build.gradle.kts` (app) | 수정 | 의존성 정리, google-services 플러그인 |
| `build.gradle.kts` (project) | 수정 | AGP/Kotlin/KSP 안정 버전 |
| `gradle.properties` | 수정 | JVM 메모리 설정 |
| `gradle-wrapper.properties` | 수정 | Gradle 8.9 |
| `proguard-rules.pro` | 수정 | 난독화 제외 규칙 |
| `res/xml/file_paths.xml` | **신규** | FileProvider 경로 |
| `res/mipmap-anydpi-v26/` | **신규** | Adaptive Icon XML |
| `res/values/ic_launcher_background.xml` | **신규** | 아이콘 배경색 |
| `res/mipmap-*/ic_launcher*.png` | 교체 | 커스텀 아이콘 |

---

## 개발 통계

| 항목 | 수치 |
|------|------|
| 총 개발 기간 | 4일 (2026-03-19 ~ 03-22) |
| 수정 차수 | 9차 |
| 수정/신규 파일 | 27개 |
| 전면 재작성 파일 | 7개 |
| 발견 및 수정된 버그 | 18건 이상 |
| 크래시 원인 분석 | 6건 (1~2차), 6건 (3차), 4건 (4차) |
| 외부 API 연동 | 4개 (CLOVA, Gemini, Claude, Google Drive) |

---

*본 문서는 2026-03-22 기준으로 작성되었습니다.*
