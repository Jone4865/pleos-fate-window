# ✅ 제출 체크리스트 — 현대차그룹 차량용 앱 개발 공모전

마감: **2026-11-15 23:59** (1차 접수)

## 제출물
- [x] 기획서 (`README.md`)
- [x] 앱 소스코드 (Kotlin, 이 저장소)
- [x] 룰렛 엔진 단위테스트 (`app/src/test/...`)
- [ ] **GitHub 공개 저장소** ← `gh auth login` 후 푸시
- [ ] **APK** ← GitHub Actions 빌드 artifact 또는 Android Studio 빌드
- [ ] **시연 영상** ← 에뮬레이터 녹화 (`docs/VIDEO_STORYBOARD.md`)

## 내가(사용자) 해야 하는 것
1. **GitHub 로그인**: 터미널에서 `gh auth login` (브라우저 인증). → 그 다음 에이전트가 repo 생성·푸시.
2. **Pleos Playground 가입 & SDK 액세스 신청**: https://developers.pleos.ai
   - 프로젝트 생성 → Vehicle SDK / TextToSpeech SDK 액세스 요청.
3. **실제 SDK 연동**: `StubVehicleController` → `PleosVehicleController` 로 교체,
   `app/build.gradle.kts` 의 Pleos 의존성 주석 해제, `settings.gradle.kts` 의 Pleos maven 저장소 추가.
4. **에뮬레이터 설치 & 데모 녹화**: Pleos Connect Emulator + Virtual Console.
5. **APK 서명/빌드**: Android Studio 또는 CI artifact 다운로드.

## 에이전트가 이미 한 것
- 기획서 작성·3티어 연출 설계
- 앱 스켈레톤(룰렛 엔진, 티어 자동감지, SDK 추상화, UI, 단위테스트)
- GitHub Actions CI (테스트 + APK 자동 빌드)
- 영상 콘티 / 본 체크리스트

## 남은 리스크
- 실차 창문 actuation 권한(OEM 정책) → 에뮬레이터 데모 기본, "조용한 모드"로 우회.
- Pleos SDK 실제 패키지명·신호 경로는 공식 문서로 최종 확인 필요.
