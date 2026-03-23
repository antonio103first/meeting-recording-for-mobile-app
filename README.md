# 회의녹음요약 (Meeting Recording & Summary)

> AI 기반 회의 녹음, 음성인식(STT), 지능형 요약을 하나의 앱으로 — Android용

---

## 소개

**회의녹음요약**은 회의 현장에서 녹음부터 텍스트 변환, AI 요약까지 원스톱으로 처리하는 Android 애플리케이션입니다. CLOVA Speech와 Gemini의 음성인식 기술, Gemini와 Claude의 AI 요약 기술을 결합하여 전문적인 회의록을 자동으로 생성합니다.

### 주요 기능

- **실시간 회의 녹음** — 고품질 MP3 녹음, 일시정지/재개 지원
- **듀얼 STT 엔진** — CLOVA Speech (한국어 특화) + Gemini (멀티모달)
- **듀얼 AI 요약** — Gemini + Claude, 5가지 요약 방식 제공
- **핵심 지표 자동 추출** — 결정사항, 액션 아이템, 주요 논점
- **Google Drive 자동 업로드** — 녹음파일 / 회의록 2개 폴더 지정
- **마크다운 회의록** — WebView 기반 표·서식 완벽 렌더링
- **전체화면 보기** — 스크롤 + 텍스트 선택/복사 지원
- **원터치 위젯** — 홈 화면에서 바로 녹음 시작
- **기존 오디오 임포트** — MP3, WAV, M4A, OGG 파일 분석

---

## 스크린샷

> 추후 스크린샷 이미지 추가 예정

| 녹음/변환 | 회의목록 | 설정 |
|-----------|---------|------|
| (screenshot) | (screenshot) | (screenshot) |

---

## 시스템 요구사항

| 항목 | 사양 |
|------|------|
| 운영체제 | Android 14.0 이상 |
| RAM | 8GB 이상 권장 |
| 저장공간 | 최소 500MB |
| 네트워크 | Wi-Fi 또는 모바일 데이터 (STT/요약 시 필수) |

---

## 설치 방법

### APK 직접 설치

1. [Releases](https://github.com/antonio103first/meeting-recording-for-mobile-app/releases) 페이지에서 최신 APK 다운로드
2. 기기에서 `설정 > 보안 > 알 수 없는 앱 설치` 허용
3. 다운로드한 APK 파일 실행하여 설치
4. 설치 완료 후 "회의녹음요약" 앱 실행

---

## 초기 설정 (API 키)

앱을 사용하려면 최소 1개 이상의 API 키가 필요합니다.

### 필수: Gemini API 키

1. [Google AI Studio](https://aistudio.google.com/) 접속
2. API 키 생성
3. 앱 > 설정 > Gemini API Key에 입력

### 권장: CLOVA Speech API

1. [Naver Cloud Console](https://console.ncloud.com/) 접속
2. AI Services > CLOVA Speech 활성화
3. Invoke URL + Secret Key 발급
4. 앱 > 설정 > CLOVA Speech에 입력

### 선택: Claude API 키

1. [Anthropic Console](https://console.anthropic.com/) 접속
2. API Key 생성
3. 앱 > 설정 > Claude API Key에 입력

> 상세한 설정 방법은 [사용자 매뉴얼](docs/USER_MANUAL.md)을 참고하세요.

---

## 사용법 요약

```
[녹음 시작] 또는 [MP3 선택]
       ↓
  STT 음성인식 변환
       ↓
  AI 회의록 요약 (5가지 방식 선택)
       ↓
  파일이름 입력 → 저장
       ↓
  Google Drive 자동 업로드 (설정 시)
```

### 요약 방식 5종

| 방식 | 설명 | 추천 상황 |
|------|------|-----------|
| 화자 중심 | 발화자별 의견/주장 정리 | 임원회의, 협상 |
| 주제 중심 | 안건별 논의사항 정리 | 정기회의, 리뷰 |
| 공식 양식(MD) | 표준 회의록 포맷 (마크다운) | 공식 기록, 아카이빙 |
| 공식 양식(텍스트) | 이메일/메신저 공유용 텍스트 | 즉시 공유 |
| 강의 요약 | 강의/세미나 핵심 정리 | 교육, 세미나 |

---

## 프로젝트 구조

```
meeting-recording-for-mobile-app/
├── README.md                    # 프로젝트 소개 (현재 문서)
├── LICENSE                      # MIT 라이선스
├── CHANGELOG.md                 # 버전 변경 이력
├── releases/                    # APK 릴리즈 파일
│   └── (APK 파일 추가 예정)
└── docs/
    ├── USER_MANUAL.md           # 상세 사용자 매뉴얼
    ├── SETUP_MANUAL.md          # API 설정 상세 가이드
    ├── DEVELOPMENT_HISTORY.md   # 개발 히스토리
    └── V2.0_개발계획서.md        # V2.0 개발 로드맵
```

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI 프레임워크 | Jetpack Compose (Material3) |
| 데이터베이스 | Room Database |
| STT 엔진 | CLOVA Speech API, Google Gemini API |
| AI 요약 | Google Gemini (gemini-2.5-flash), Anthropic Claude (claude-sonnet-4-6) |
| 클라우드 | Google Drive API v3 |
| 오디오 | MediaRecorder (MP3), MediaPlayer |
| 마크다운 렌더링 | WebView + 커스텀 HTML 변환 |
| 설정 관리 | SharedPreferences (ConfigManager) |

---

## 버전 정보

| 버전 | 날짜 | 주요 변경사항 |
|------|------|---------------|
| V1.0 | 2026-02 | 초기 릴리즈 — Gemini STT/요약, 기본 녹음 |
| V1.5 | 2026-03 | CLOVA STT, Claude 요약, Drive 업로드, WebView 마크다운 렌더링 |
| V2.0 | 개발 예정 | 멀티엔진, 재요약, 화자이름 변경, 공유, 탭 UI |

---

## 라이선스

이 프로젝트는 [MIT License](LICENSE) 하에 배포됩니다.

---

## 개발자

- **K-Run Ventures** (케이런벤처스)
- 문의: antonio103@gmail.com
- GitHub: [antonio103first](https://github.com/antonio103first)
