# 제출 체크리스트 — 현대차그룹 차량용 앱 개발 공모전

마감: **2026-11-15 23:59** (1차 접수)

## 제출물
- [x] 기획서 (`README.md`, 실제 앱 화면 포함)
- [x] 앱 소스코드 — https://github.com/Jone4865/pleos-fate-window (공개)
- [x] APK — 저장소 `apk/운명의창문팀_운명의창문_release.apk` (서명된 release 빌드, 공식 파일명 규칙 `팀명_앱이름_release.apk`) + Release v1.0
- [x] 시연 영상 완성본 — 78초, 1920×1080, 자막·나레이션·효과음·타이틀 카드 포함 (편집 도구: `tools/demo-video/`)
- [x] 영상 업로드 — 릴리스 v1.0: https://github.com/Jone4865/pleos-fate-window/releases/download/v1.0/fate-window-demo.mp4
- [x] 접수양식(기획서) 작성·서명
- [x] 이메일 접수 완료 (2026-11-11, appdevchallenge@hyundai-partners.com, 제목 `[차량용 앱 개발 공모전] 운명의창문팀_채종원`)
- [x] 수정 제출 (2026-11-11, `[수정제출]`): 접수양식 + 시연 영상 720p(23.7MB) 직접 첨부 → 이 메일이 최종본

## 완료한 것 (2026-11-10)
- Pleos Playground 가입·프로젝트 생성 ("운명의 창문 (Fate Window)")
- Vehicle SDK 2.3.2 실제 연동, 에뮬레이터에서 조명·창문 제어 동작 확인
- 게임 모드 4종, 미션 풀, 차량 디스플레이용 UI, 원근 3D 실내 일러스트
- 주행 감지 즉시 중단·비상 복구, 앱 이탈 시 복구
- 단위테스트 16개, CI (테스트 + APK)

## 영상 구성 (78초)
- 0–3초 타이틀 → 복불복(룰렛·암전·당첨+창문·다른 미션·완료) → 비 오는 날 독서등만 → 벌칙(창문) → 기어 이동 시 즉시 중단·복구 → 아웃트로(GitHub 주소)

## 남은 리스크
- 포털상 Vehicle Control 은 "준비 중" → 에뮬레이터 시연 기준으로 출품 (README 12절)
