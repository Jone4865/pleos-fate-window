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
> 2026-11-10 실제 SDK·에뮬레이터로 검증한 내용으로 갱신함 (아래 4-1). 초기 VSS 경로 메모는 참고용으로만 남김.

### 4-1. 검증된 사실 (Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`, Pleos Connect Emulator v2.1.0)
- **저장소**: `https://nexus-playground.pleos.ai/repository/maven-releases/` (공개, 인증 불필요). 공개 버전은 2.0.3, 2.3.2 뿐 (문서 예시의 2.3.1 없음).
- **포털 상태**: Vehicle State·NaviHelper는 "사용 가능". **Vehicle Control(창문·조명 제어)·TextToSpeech는 "준비 중"이라 신청 버튼 없음.**
- **그런데 에뮬레이터에서는 제어가 된다**: `CONTROL_CAR_WINDOWS/LIGHTS` 퍼미션이 normal 레벨이라 설치 시 자동 허용, 차량 서비스가 퍼미션 검사 통과 → HAL 반영 → `onSuccess`.
- **Virtual Console 필수**: `adb forward tcp:8088 tcp:8088` 후 브라우저 `http://127.0.0.1:8088/`. 하단이 **DDS ONLINE** 이어야 앱의 쓰기가 상태에 반영됨(처음엔 반영 안 돼서 SDK 문제로 오인했었음). 착좌·기어·와이퍼·전조등을 여기서 바꿔 티어 전환 시연 가능.
- 사용 API: 독서등 `Light.setReadingLightSwitch(SeatArea)`, 암전 `Light.setCabinLightSwitch(OFF)`, 앰비언트 `Light.setAmbientLightSwitch(SeatArea)`, 창문 `Window.setWindowPosition(WindowArea, value)`, 착좌 `Seat.getCurrentSeatOccupancy`, 기어 `Gear.getCurrentGearSelectionState`, 비 `Wiper.getCurrentWiperState/Switch(FRONT_WINDSHIELD)`, 밤 `Light.getCurrentLowBeamLightState`.
- **창문 값 범위 0~10** (10 = 100%). 차마다 다를 수 있어 `checkWindowPositionCapabilities` 로 읽어 % 환산.
- **실내등 OFF 는 켜진 독서등도 끈다.** 그 뒤 개별 독서등 ON 은 정상 → "암전 → 당첨석만 점등" 순서 그대로 가능.
- **차속 API 없음** → 주차 판정은 기어 P 만.
- 멘트: Pleos TTS 준비 중이라 Android 기본 `TextToSpeech` 사용.
- 개발환경: Android API 28+, Gradle 8.9(wrapper 포함), Kotlin 2.0, JDK 17

### 4-2. (초기 메모, 참고용) VSS 경로
- 독서등 `Vehicle.Cabin.Light.Spotlight.Row{n}.{Side}.IsLightOn`, 창문 `Vehicle.Cabin.Door.Row{n}.{Side}.Window.Position`, 착좌 `Vehicle.Cabin.Seat.Row{n}.{Side}.Occupant`, 비 `Vehicle.Body.Windshield.Front.Wiping.Mode`

## 5. 현재 산출물
```
README.md                     기획서(전체)
HANDOFF.md                    이 문서
app/ ...                      Kotlin 앱
  game/RouletteEngine.kt      가중치 랜덤 + 회전 시퀀스
  game/RevealTier.kt          티어 자동 감지 (+앰비언트/창문 옵션)
  game/GameController.kt      라운드 연출 오케스트레이션 + 완료(창문 닫기·조명 복귀)
  vehicle/VehicleController.kt      SDK 추상 인터페이스 (suspend)
  vehicle/PleosVehicleController.kt 실제 Pleos Vehicle SDK 구현 + Android TTS
  vehicle/StubVehicleController.kt  Pleos 서비스 없는 기기용 스텁 (자동 선택)
  MainActivity.kt + res/      UI (시작/완료 버튼, 창문·앰비언트·운전자 제외 토글)
app/src/test/...              룰렛 엔진 + 게임 흐름 단위테스트 (10개)
gradlew, gradle/wrapper/      Gradle 8.9 wrapper
.github/workflows/android-ci.yml  main 푸시 시 테스트+APK 자동빌드
docs/VIDEO_STORYBOARD.md      영상 샷별 콘티
docs/SUBMISSION_CHECKLIST.md  제출 체크리스트
```
- 상태: `main` 브랜치. 로컬 빌드·테스트 통과, Pleos 에뮬레이터에서 실제 라운드 동작 확인. 원격 미푸시.
- Pleos Playground 프로젝트 생성 완료: "운명의 창문 (Fate Window)" (카테고리 엔터테인먼트).

## 6. 남은 작업
- [x] GitHub 공개 repo: https://github.com/Jone4865/pleos-fate-window (CI 통과, APK 아티팩트 생성)
- [x] 시연 영상 원본 녹화 (에뮬레이터 + Virtual Console, 약 2분 20초) + 자막 .srt — 저장소 밖 별도 파일
- [x] 주행(기어 P 이탈) 감지 시 즉시 중단 + 창문 닫기·조명 복구, 앱 이탈 시 복구
- [x] README(기획서)를 실제 구현·화면 기준으로 갱신
- [ ] 영상 편집·업로드: 대기 구간 자르기, 자막 입히기, 나레이션/효과음 (TTS 음성은 녹화에 안 담김)
- [ ] (선택) 공모전 문의처에 "Vehicle Control 준비 중 상태에서 에뮬레이터 시연 출품 가능 여부" 확인
- [ ] 1차 접수(~11/15 23:59): 기획서 + repo(소스+APK) + 영상 제출

## 7. 리스크
- 포털상 Vehicle Control 은 "준비 중" → 실차/마켓 배포 시 제어 권한이 막힐 수 있음. 에뮬레이터 시연 기준으로 출품.
- 실차 창문 actuation 은 OEM/안전정책 제약 가능 → "창문 연출" 토글 OFF 시 독서등만으로 완결(티어3).

## 8. 링크
- 공모전: https://appdevchallenge.auto-hmg.io/guide/development
- Pleos 개발자포털: https://developers.pleos.ai
- 대상작 '창밖동화' 저널: https://www.hyundaimotorgroup.com/ko/story/hyundai-motor-company-pleos-connect-app-idea-contest
