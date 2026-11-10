# 📦 인수인계 — "운명의 창문" (Pleos 공모전)

> 이 문서 하나로 프로젝트 전체를 이어받을 수 있게 정리함. 상세는 각 파일 참조.

## 1. 한 줄 요약
현대차그룹 **차량용 앱 개발 공모전(Code your App into Pleos App Market, ~2026-11-15)** 출품작.
**주차 중 차 안에서, 차량이 진행자가 되어 조명 룰렛 + 창문으로 당첨자를 지목하는 파티 게임** = "운명의 창문".

## 2. 최종 확정 컨셉
- 분야: 인카 엔터테인먼트 / 타겟: 주차 중 2~4인(친구·커플·가족)
- 플로우: 착좌감지로 참가자 자동세팅 → 암전 → 조명 룰렛(두구두구) → **완전 암전 뜸들이기** → 당첨자 지목 → 미션/벌칙 → 리셋
- 모드: 복불복 / 진실 혹은 도전 / 벌칙 결정기
- **하드웨어 3티어 자동 분기(핵심 설계):**
  | 티어 | 조건 | 룰렛 | 당첨 지목 |
  |---|---|---|---|
  | 1 | 앰비언트 O | 앰비언트 둘레 회전 | 당첨 좌석 앰비언트 점등(+창문 옵션) |
  | 2 | 앰비언트 X, 창문 O | 독서등 깜빡 | **창문 오픈으로 당첨** |
  | 3 | 앰비언트 X, 창문 X(비/밤) | 독서등 깜빡 | 당첨 좌석 독서등만 점등 |

## 3. 핵심 설계 결정(왜 이렇게 됐나 — 반복 금지)
- ❌ **마피아 등 "숨은 역할" 게임 폐기**: 공유 캐빈에선 비밀 정보 전달이 구조적으로 불가(조명은 옆사람도 봄, 창문은 바람으로 들킴, 개인 오디오존은 SDK 미확인).
- ✅ **"공개 랜덤 지목"으로 전환**: 비밀이 필요 없고 다 같이 보는 게 재미. 창문 개폐가 단점이 아니라 "운명의 손가락" 연출이 됨.
- ✅ **조명(독서등) 중심 + 창문은 옵션 레이어**: 비·밤엔 창문 자동 OFF, 조명만으로 완결(티어3). 어떤 차에서도 동작.
- 참고: 공모전 **대상작 '창밖동화'가 "창문 개폐 연출 = SDV 차별화"로 호평** → 본 메커니즘이 심사 코드에 부합.

## 4. 확인된 SDK 사실(VSS 신호)
- 독서등 개별제어 `Vehicle.Cabin.Light.Spotlight.Row{n}.{Side}.IsLightOn/.Intensity` ✅
- 창문 제어 `Vehicle.Cabin.Door.Row{n}.{Side}.Window.Position/.Switch` (쓰기) ✅
- 착좌 `Vehicle.Cabin.Seat.Row{n}.{Side}.Occupant/.Seating` ✅
- 주차 `Vehicle.Powertrain...CurrentGear`, `Vehicle.Speed` ✅
- 비감지 `Vehicle.Body.Windshield.Front.Wiping.Mode` ✅
- 멘트 `TextToSpeech SDK` ✅ / 앰비언트 색은 차종 옵션 ⚠️
- 개발환경: Android API 28+, Gradle 8+, Kotlin, Pleos Connect Emulator

## 5. 현재 산출물 (git 커밋 완료, 로컬)
```
README.md                     기획서(전체)
HANDOFF.md                    이 문서
app/ ...                      Kotlin 앱 스켈레톤
  game/RouletteEngine.kt      가중치 랜덤 + 회전 시퀀스
  game/RevealTier.kt          티어 자동 감지
  game/GameController.kt      라운드 연출 오케스트레이션
  vehicle/VehicleController.kt SDK 추상 인터페이스
  vehicle/StubVehicleController.kt  SDK 없이 구동되는 스텁
  MainActivity.kt + res/      간단 UI
app/src/test/...              룰렛 엔진 단위테스트
.github/workflows/android-ci.yml  테스트+APK 자동빌드 CI
docs/VIDEO_STORYBOARD.md      영상 샷별 콘티
docs/SUBMISSION_CHECKLIST.md  제출 체크리스트
```
- 상태: **로컬 git 커밋됨, 원격 미푸시.** Android SDK/Pleos SDK 미설치 환경이라 로컬 빌드 안 됨(CI에서 빌드).

## 6. 남은 작업 (담당: 사용자/후속자)
1. `gh auth login` → GitHub 공개 repo 생성 & 푸시
2. CI 실행되면 **APK artifact** 다운로드(또는 Android Studio 빌드)
3. **Pleos Playground 가입 + SDK 액세스 신청**(developers.pleos.ai) → `StubVehicleController` 를 실제 `PleosVehicleController` 로 교체(build.gradle 의존성·settings maven 주석 해제)
4. **에뮬레이터로 시연 영상 녹화**(콘티대로, 티어1/2/3 각각 시연)
5. 1차 접수(~11/15 23:59): 기획서 + repo(소스+APK) + 영상 제출 → 단계별 가산점

## 7. 리스크
- 실차 창문 actuation 권한(OEM/안전정책) 제약 가능 → 에뮬레이터 데모 기본, "조용한 모드"로 우회.
- Pleos SDK 실제 패키지명/신호 경로는 공식 문서로 최종 확인.

## 8. 링크
- 공모전: https://appdevchallenge.auto-hmg.io/guide/development
- Pleos 개발자포털: https://developers.pleos.ai
- 대상작 '창밖동화' 저널: https://www.hyundaimotorgroup.com/ko/story/hyundai-motor-company-pleos-connect-app-idea-contest
