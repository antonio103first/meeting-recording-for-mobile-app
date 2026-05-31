# Changelog

모든 주요 변경사항을 이 파일에 기록합니다.

---

## [v3.4.2] — 2026-05-31

### 수정 (Fixed)
- **회의록 요약본이 Obsidian vault 루트에 저장되던 문제 해결** — 이제 항상 `{vault}/08_회의록/` 서브폴더에 저장
  - `RecordingViewModel.kt` 의 3개 저장 지점 (요약 완료 즉시 저장 / 메인 vault 저장 / 재요약 즉시 저장) 모두 `writeTextToSafDir()` → `writeTextToSafSubDir(uri, "08_회의록", ...)` 로 변경
  - 모든 양식(주간회의/다자간협의/회의록업무/IR미팅/전화통화메모/네트워킹/강의요약)의 요약본이 동일하게 `08_회의록/` 으로 들어감
  - 음성메모는 기존대로 `00_Inbox/voice_memos/` 유지 (별도 파이프라인)
  - 신규 상수 `OBSIDIAN_MEETING_SUBDIR = "08_회의록"`

---

## [v3.4.1] — 2026-05-29

### 수정 (Fixed)
- 음성메모(VOICE_MEMO 모드)가 Obsidian vault 루트에 저장되던 회귀 문제 해결 — `00_Inbox/voice_memos/` 서브폴더에 정확히 저장 (`writeTextToSafDir()` → `writeTextToSafSubDir()`)

---

## [v3.4] — 2026-05-24

### 추가 (Added)
- 녹음 정지 즉시 회의목록 자동 등록 — MEETING/VOICE_MEMO 공통 (가져오기 불필요)
- `pendingMeetingId` 필드 신설 — 회의녹음 preliminary DB insert ID 추적
- 설정 화면 "🔍 로컬 파일 스캔 (기존 파일 DB 등록)" 버튼
- 음성메모 파이프라인 (`RecordingMode.VOICE_MEMO`): 파일명 다이얼로그 없이 자동 저장
  - 녹음: `메모녹음_YYYYMMDD_HHmmss.m4a`
  - STT: `메모녹음_YYYYMMDD.txt`, 요약: `메모녹음_YYYYMMDD.md`
  - 2~3문장 간단 요약 (`SUMMARY_VOICE_MEMO` 템플릿 — GeminiService/ClaudeService)
  - Obsidian `00_Inbox/voice_memos/` 자동 저장

### 변경 (Changed)
- `confirmFileName()`: update-or-insert 패턴 — preliminary insert 레코드가 있으면 `dao.update*()`으로 갱신, 없으면 `dao.insert()`

### 수정 (Fixed)
- 녹음 후 파이프라인 미실행 시 회의목록 미표시 문제 해결 (두 모드 모두 즉시 등록)
- 음성메모 DB 레코드가 STT 완료 후 중복 insert되는 버그 수정 (update로 변경)

---

## [V1.5] — 2026-03-23

### 추가 (Added)
- CLOVA Speech STT 엔진 통합 (한국어 특화, 화자분리 최대 8명 지원)
- Claude AI 요약 엔진 추가 (claude-sonnet-4-6)
- Google Drive 자동 업로드 (녹음파일/회의록 2개 폴더 지정)
- WebView 기반 마크다운 렌더링 (표, 헤더, 볼드, 리스트 정상 표시)
- 전체화면 보기 다이얼로그 (스크롤 + 텍스트 선택/복사)
- 회의목록 상세 뷰 (요약 미리보기 6줄, STT 미리보기 4줄)
- "전체 내용 보기" 통합 버튼
- 앱 아이콘 업데이트 (ic_launcher, ic_launcher_round)

### 변경 (Changed)
- 저장 파일 형식 .txt → .md (마크다운) 전환
- Divider → HorizontalDivider (Material3 마이그레이션)
- 요약 결과/STT 결과 개별 "전체보기" 버튼 추가

### 수정 (Fixed)
- 마크다운 테이블이 깨져 보이는 문제 해결 (WebView 렌더링)
- 전체화면에서 스크롤/복사 충돌 문제 해결
- Compose 제스처 충돌로 인한 텍스트 선택 불가 문제 해결

---

## [V1.0] — 2026-02-15

### 추가 (Added)
- 실시간 회의 녹음 (MP3 형식)
- 녹음 일시정지/재개 기능
- Gemini STT 음성인식 변환
- Gemini AI 회의록 요약 (5가지 방식)
  - 화자 중심, 주제 중심, 공식 양식(MD), 공식 양식(텍스트), 강의 요약
- 핵심 지표 자동 추출 (결정사항, 액션 아이템, 주요 논점)
- 기존 오디오 파일 임포트 (MP3, WAV, M4A, OGG)
- Room Database 기반 회의록 저장/관리
- 회의목록 조회/삭제
- 설정 화면 (API 키 관리, 엔진 선택, 요약 방식)
- 홈 화면 녹음 위젯 (원터치 녹음 시작)
- Jetpack Compose + Material3 UI

---

## 향후 계획 (V2.0) — 개발계획서 확정 (2026-03-24)

- 멀티엔진 지원 확장 (ChatGPT Whisper STT, GPT-4o 요약)
- STT 변환파일 기반 재요약 기능
- 화자이름 변경 (일괄 치환)
- 공유 기능 (카카오톡, 이메일, 클립보드 복사)
- 탭 기반 설정 UI 재설계
- 저장경로 사용자 지정 (SAF)
- 흐름 중심 요약 방식 추가
- **변환 완료 알림** — STT/AI 요약 완료 시 시스템 Notification 표시 (백그라운드 대응)
- **녹음파일 우선 저장** — 녹음 정지 즉시 .mp3 파일 로컬 저장 및 Drive 업로드 (데이터 유실 방지)
