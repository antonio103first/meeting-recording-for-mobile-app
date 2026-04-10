# 회의녹음요약 모바일 앱 — 개발 기획서

**작성일:** 2026-03-22
**프로젝트명:** 회의녹음요약 (MeetingRecorder)
**플랫폼:** Android (Kotlin / Jetpack Compose / Material3)
**타겟 단말:** Samsung Galaxy S25 Ultra (Android 16, One UI 8.0)

---

## 1. 프로젝트 개요

### 1-1. 목적

회의 현장에서 음성을 녹음하고, AI 기반으로 음성→텍스트 변환(STT) 및 회의록 요약을 자동 생성하는 모바일 앱. 녹음부터 요약, 클라우드 백업까지 원스톱으로 처리하여 업무 효율을 극대화한다.

### 1-2. 핵심 가치

- **즉시성** — 회의 종료 후 30초 내 AI 요약 완성
- **정확성** — CLOVA Speech(한국어 특화 STT) + Gemini(요약) 이중 AI 파이프라인
- **안전성** — Google Drive 자동 백업, 로컬+클라우드 이중 저장
- **편의성** — 파일이름 지정, 텍스트 복사, 파일 관리(삭제/공유/이름변경) 앱 내 완결

---

## 2. 기능 명세

### 2-1. 녹음 기능

| 기능 | 상세 |
|------|------|
| 녹음 시작/일시정지/재개/중지 | Foreground Service 기반, 앱 백그라운드에서도 안정 녹음 |
| 녹음 포맷 | M4A (AAC 코덱), 고음질 |
| 통화 자동 감지 | 통화 수신 시 녹음 자동 일시정지, 통화 종료 후 자동 재개 |
| 타이머 표시 | 녹음 시간 실시간 표시 |

### 2-2. AI 변환 파이프라인

```
녹음 파일(M4A)
  ↓ STT 엔진 (CLOVA Speech 또는 Gemini)
음성→텍스트 변환
  ↓ 요약 엔진 (Gemini 또는 Claude)
회의록 요약 생성
  ↓ 핵심 지표 추출
참석자, 안건, 결정사항, TODO 정리
  ↓ 파일이름 입력 다이얼로그
사용자 확인/수정
  ↓ 저장
통합 파일(회의록요약 + STT원문) 로컬 저장
  ↓ Google Drive 자동 업로드 (옵션)
녹음파일 → 음성폴더, 회의록 → 회의록폴더
```

### 2-3. STT 엔진

| 엔진 | 특징 |
|------|------|
| **CLOVA Speech** (기본) | 한국어 최적화, 화자 분리 지원, 긴 오디오 안정 처리 |
| **Gemini** (대안) | Base64 인코딩 전송, 다국어 지원 |

### 2-4. 요약 엔진

| 엔진 | 특징 |
|------|------|
| **Gemini** (기본) | gemini-2.5-flash 자동 감지, OkHttp REST 직접 호출 |
| **Claude** (대안) | claude-sonnet-4-6, Anthropic API |

### 2-5. 요약 방식 (5종)

| 방식 | 설명 |
|------|------|
| 화자 중심 | 발화자별 발언 정리 |
| 주제 중심 | 논의 주제별 구조화 |
| 공식 양식 (MD) | 마크다운 포맷 공식 회의록 |
| 공식 양식 (텍스트) | 텍스트 포맷 공식 회의록 |
| 강의 요약 | 학습/세미나 요약 특화 |

### 2-6. Google Drive 연동

| 기능 | 상세 |
|------|------|
| Google Sign-In | OAuth 2.0, Drive File 스코프 |
| 자동 업로드 | 변환 완료 후 자동 실행 (ON/OFF 토글) |
| 폴더 선택 | 하위 디렉토리 탐색, Breadcrumb 경로, 새 폴더 생성 |
| 기본 폴더 | 녹음파일 / 회의록 별도 폴더 하드코딩 |

### 2-7. 파일 관리

| 기능 | 상세 |
|------|------|
| 전체 삭제 | DB 기록 + 로컬 파일 모두 삭제 |
| 로컬 파일만 삭제 | 파일만 삭제, DB 기록 유지 (용량 확보) |
| 이름 변경 | DB + 로컬 파일명 동시 변경 |
| 공유 | Android 공유 인텐트 (카카오톡, 이메일 등) |
| 텍스트 복사 | SelectionContainer 기반 영역선택 복사 |

---

## 3. 기술 스택

### 3-1. 빌드 환경

| 항목 | 버전 |
|------|------|
| Android Studio | Ladybug 이상 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Gradle | 8.9 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0+) |

### 3-2. 주요 라이브러리

| 라이브러리 | 용도 |
|-----------|------|
| Jetpack Compose (BOM 2024.12.01) | UI 프레임워크 |
| Material3 | 디자인 시스템 |
| Room | 로컬 DB (회의 메타데이터) |
| OkHttp 4.12.0 | HTTP 클라이언트 (Gemini, CLOVA API) |
| Gson 2.10.1 | JSON 파싱 |
| Google Play Services Auth 21.0.0 | Google Sign-In |
| Google API Client Android | Google Drive API |
| Lifecycle ViewModel Compose | ViewModel + Compose 연동 |
| Navigation Compose | 화면 간 네비게이션 |

### 3-3. 외부 API

| API | 제공사 | 용도 |
|-----|--------|------|
| CLOVA Speech | Naver Cloud | 한국어 STT |
| Gemini API | Google | STT(대안) + 요약 |
| Claude API | Anthropic | 요약(대안) |
| Google Drive API | Google | 클라우드 파일 저장 |

---

## 4. 데이터 구조

### 4-1. Room Database — Meeting 엔티티

| 필드 | 타입 | 설명 |
|------|------|------|
| id | Int (PK) | 자동증가 |
| fileName | String | 파일명 |
| date | String | 회의 일시 |
| duration | Long | 녹음 시간 (ms) |
| sttText | String | STT 원문 |
| summaryText | String | AI 요약 |
| metricsText | String | 핵심 지표 |
| mp3LocalPath | String | 녹음파일 로컬 경로 |
| sttLocalPath | String | STT파일 로컬 경로 |
| summaryLocalPath | String | 요약파일 로컬 경로 |

### 4-2. 로컬 저장 경로

```
/Android/data/com.krunventures.meetingrecorder/files/Documents/Meeting recording/
├── audio/           # 녹음 파일 (.m4a)
└── summary/         # 통합 회의록 (.txt)
```

---

## 5. 앱 화면 구성

| 탭 | 화면 | 주요 기능 |
|----|------|-----------|
| 1 | 녹음 화면 | 녹음 시작/정지, STT→요약 변환, 결과 표시, 파일이름 입력 |
| 2 | 회의목록 | 저장된 회의 목록, 상세보기, 파일 관리(삭제/이름변경/공유) |
| 3 | 설정 | 엔진 설정, Google Drive 연동, API 키 관리, 저장 경로 |

---

## 6. 보안 고려사항

- `google-services.json`은 `.gitignore`에 포함하여 GitHub에 미포함
- API 키는 앱 내 SharedPreferences에 저장 (로컬 전용)
- Google Drive OAuth는 테스트 사용자 제한 모드 (필요시 Google 검증 신청)
- 녹음 파일은 앱 전용 디렉토리에 저장 (Scoped Storage, 타 앱 접근 불가)

---

*본 기획서는 2026-03-22 기준으로 작성되었습니다.*
