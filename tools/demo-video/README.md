# 시연 영상 편집 도구

macOS 기본 기능만 사용 (AVFoundation, `say`, `afconvert`, Python 표준 라이브러리). 외부 설치 없음.

1. 에뮬레이터 화면 녹화: `adb shell screenrecord --size 1920x1080 --bit-rate 8000000 /sdcard/fate.mp4` → `adb pull`
2. `plan.json` 에 자를 구간(원본 초)·자막·타이틀 카드(편집본 초) 작성
3. 오디오: `python3 -I audio.py <작업폴더> <총길이초>` → 나레이션(Yuna)·차량 안내(Reed)·효과음·배경음 `track.wav`
4. 렌더: `swiftc -O -o edit edit.swift && ./edit render fate-raw.mp4 plan.json <작업폴더>/track.wav out.mp4`
5. 확인용 프레임: `./edit frames out.mp4 <폴더> 1.5 12.0 ...`

자막 글자는 CATextLayer 대신 비트맵으로 그린다 (오프라인 렌더에서 CATextLayer 글자가 그려지지 않음).
