"""시연 영상 오디오 트랙 생성 (표준 라이브러리 + macOS say/afconvert 만 사용).

출력 타임라인(편집 후 초) 기준으로 나레이션, 차량 안내 음성, 효과음, 배경 패드를 한 트랙에 믹스한다.
usage: python3 -I audio.py <workdir> <total_seconds>
"""
import math
import os
import random
import struct
import subprocess
import sys
import wave
from array import array

WORK = sys.argv[1]
TOTAL = float(sys.argv[2])
SR = 44100
N = int(TOTAL * SR)
buf = array('f', [0.0]) * N
rng = random.Random(7)

NARRATOR = "Yuna"   # 설명 나레이션
CAR = "Reed (한국어(한국))"   # 차량이 진행자 (게임 안내). 언어까지 붙여야 한국어 Reed 가 선택됨

# (출력 시각, 목소리, 문장, 볼륨)
SPEECH = [
    (0.4, NARRATOR, "주차 중, 차 안에서 뭐 하지?", 1.0),
    (4.5, CAR, "운명의 창문, 시작합니다.", 0.9),
    (9.0, CAR, "운명은", 0.9),
    (10.5, CAR, "뒷좌석 왼쪽, 당첨!", 1.0),
    (13.4, NARRATOR, "당첨 좌석만 불이 켜지고, 창문이 열립니다.", 1.0),
    (17.1, NARRATOR, "모드마다 다른 미션을 화면과 음성으로 알려줘요.", 1.0),
    (22.0, NARRATOR, "마음에 안 들면, 다른 미션.", 1.0),
    (25.6, NARRATOR, "완료하면 창문을 닫고, 조명을 되돌립니다.", 1.0),
    (29.3, NARRATOR, "비가 오면? 와이퍼를 감지해서, 창문 대신 조명만으로 지목합니다.", 1.0),
    (35.7, CAR, "운명의 창문, 시작합니다.", 0.9),
    (40.1, CAR, "운명은", 0.9),
    (41.6, CAR, "운전석, 당첨!", 1.0),
    (44.1, NARRATOR, "어떤 차, 어떤 날씨에서도 동작합니다.", 1.0),
    (49.3, NARRATOR, "날이 개면, 다시 창문 연출.", 1.0),
    (55.4, CAR, "운명의 창문, 시작합니다.", 0.9),
    (59.8, CAR, "운명은", 0.9),
    (61.3, CAR, "조수석, 당첨!", 1.0),
    (66.4, NARRATOR, "주행하려고 기어를 옮기는 순간, 게임을 멈추고 창문을 닫습니다.", 1.0),
    (72.7, NARRATOR, "운명의 창문. 차 안의 시간을, 같이 노는 시간으로.", 1.0),
]

# 라운드: (시작 탭 시각) → 룰렛 tap+1.0~tap+4.6, 암전 tap+4.6, 당첨 tap+6.1
ROUND_TAPS = [4.2, 35.4, 55.1]


def add(samples, at, gain=1.0):
    i0 = int(at * SR)
    for k, v in enumerate(samples):
        i = i0 + k
        if 0 <= i < N:
            buf[i] += v * gain


def read_wav(path):
    with wave.open(path, 'rb') as w:
        assert w.getsampwidth() == 2 and w.getnchannels() == 1 and w.getframerate() == SR
        raw = w.readframes(w.getnframes())
    vals = struct.unpack('<%dh' % (len(raw) // 2), raw)
    return [v / 32768.0 for v in vals]


def tts(i, voice, text):
    aiff = os.path.join(WORK, f"n{i}.aiff")
    wav = os.path.join(WORK, f"n{i}.wav")
    subprocess.run(["say", "-v", voice, "-r", "185" if voice == NARRATOR else "200", "-o", aiff, text], check=True)
    subprocess.run(["afconvert", "-f", "WAVE", "-d", f"LEI16@{SR}", "-c", "1", aiff, wav], check=True)
    return read_wav(wav)


def env(n, attack, decay):
    a = max(1, int(attack * SR))
    return [min(1.0, k / a) * math.exp(-k / (decay * SR)) for k in range(n)]


def tick(strength=1.0):
    """룰렛 '두구' 한 번: 짧은 노이즈 + 낮은 북소리"""
    n = int(0.09 * SR)
    e = env(n, 0.002, 0.025)
    return [strength * e[k] * (0.55 * (rng.random() * 2 - 1) + 0.6 * math.sin(2 * math.pi * 170 * k / SR)) for k in range(n)]


def boom():
    """암전 순간의 낮은 쿵"""
    n = int(1.4 * SR)
    e = env(n, 0.005, 0.45)
    return [e[k] * 0.9 * math.sin(2 * math.pi * (55 - 15 * k / n) * k / SR) for k in range(n)]


def chime():
    """당첨 효과음: 밝은 아르페지오"""
    out = [0.0] * int(1.8 * SR)
    for j, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):
        st = int(j * 0.07 * SR)
        n = len(out) - st
        e = env(n, 0.004, 0.5)
        for k in range(n):
            out[st + k] += 0.22 * e[k] * (math.sin(2 * math.pi * f * k / SR) + 0.3 * math.sin(2 * math.pi * 2 * f * k / SR))
    return out


def pad():
    """조용한 배경 패드 (전체 구간)"""
    out = array('f', [0.0]) * N
    chords = [(220.0, 277.2, 329.6), (196.0, 246.9, 293.7)]
    for k in range(N):
        t = k / SR
        c = chords[int(t // 8) % 2]
        v = sum(math.sin(2 * math.pi * f * t + 0.3 * math.sin(2 * math.pi * 0.2 * t)) for f in c)
        out[k] = 0.018 * v * (0.75 + 0.25 * math.sin(2 * math.pi * 0.07 * t))
    return out


# 배경 패드: 암전 구간은 비워 긴장감
p = pad()
quiet = [(tap + 4.6, tap + 6.1) for tap in ROUND_TAPS]
for k in range(N):
    t = k / SR
    g = 1.0
    for a, b in quiet:
        if a - 0.2 <= t <= b:
            g = 0.0
    fade = min(1.0, t / 1.5, (TOTAL - t) / 2.0)
    buf[k] += p[k] * g * max(0.0, fade)

# 룰렛 틱: 빠르게 시작해서 감속 (앱의 80ms → 400ms 와 같은 느낌)
for tap in ROUND_TAPS:
    t = tap + 1.0
    end = tap + 4.6
    steps = 0
    while t < end:
        prog = (t - (tap + 1.0)) / 3.6
        add(tick(0.9 if steps % 2 == 0 else 0.6), t)
        t += 0.08 + 0.32 * prog
        steps += 1
    add(boom(), tap + 4.6, 0.9)
    add(chime(), tap + 6.1, 1.0)

# 음성
for i, (at, voice, text, gain) in enumerate(SPEECH):
    add(tts(i, voice, text), at, gain)

# 정규화 → 16bit WAV
peak = max(1e-6, max(abs(v) for v in buf))
scale = 0.89 / peak
out = os.path.join(WORK, "track.wav")
with wave.open(out, 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(struct.pack('<%dh' % N, *(int(max(-1, min(1, v * scale)) * 32767) for v in buf)))
print("track", out, f"{N / SR:.1f}s peak {peak:.2f}")
