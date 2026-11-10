"""시연 영상 오디오 v2: 해설 없음. 차량 진행 멘트 + 효과음 + 직접 합성한 배경음악.

외부 음원·라이브러리 없이 Python 표준 라이브러리와 macOS say/afconvert 만 사용.
usage: python3 -I audio2.py <workdir> <total_seconds>
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
rng = random.Random(11)

CAR = "Reed (한국어(한국))"   # 차가 진행자 (앱 실제 기능의 멘트)
CAR_LINES = [
    (4.5, "운명의 창문, 시작합니다."),
    (9.0, "운명은"),
    (10.5, "뒷좌석 왼쪽, 당첨!"),
    (35.7, "운명의 창문, 시작합니다."),
    (40.1, "운명은"),
    (41.6, "운전석, 당첨!"),
    (55.4, "운명의 창문, 시작합니다."),
    (59.8, "운명은"),
    (61.3, "조수석, 당첨!"),
]
ROUND_TAPS = [4.2, 35.4, 55.1]          # 룰렛 tap+1.0~4.6, 암전 tap+4.6~6.1, 당첨 tap+6.1
DRUMS_IN = 4.2                          # 첫 라운드부터 드럼
OUTRO = 72.3                            # 엔딩 카드


def zeros():
    return array('f', [0.0]) * N


def add(buf, samples, at, gain=1.0):
    i0 = int(at * SR)
    for k, v in enumerate(samples):
        i = i0 + k
        if 0 <= i < N:
            buf[i] += v * gain


def env(n, attack, decay):
    a = max(1, int(attack * SR))
    return [min(1.0, k / a) * math.exp(-k / (decay * SR)) for k in range(n)]


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ================= 배경음악 =================
BPM = 112
BEAT = 60 / BPM
BAR = BEAT * 4
# I–V–vi–IV (C G Am F): 근음 MIDI, 코드 구성음
CHORDS = [(48, [60, 64, 67]), (43, [59, 62, 67]), (45, [60, 64, 69]), (41, [60, 65, 69])]


def kick():
    n = int(0.28 * SR)
    out, ph = [], 0.0
    for k in range(n):
        f = 45 + 95 * math.exp(-k / (0.03 * SR))
        ph += 2 * math.pi * f / SR
        out.append(math.sin(ph) * math.exp(-k / (0.09 * SR)))
    return out


def snare():
    n = int(0.22 * SR)
    return [math.exp(-k / (0.06 * SR)) * (0.7 * (rng.random() * 2 - 1) + 0.35 * math.sin(2 * math.pi * 190 * k / SR)) for k in range(n)]


def hat(open_=False):
    n = int((0.12 if open_ else 0.04) * SR)
    prev, out = 0.0, []
    for k in range(n):
        x = rng.random() * 2 - 1
        out.append((x - prev) * 0.5 * math.exp(-k / ((0.05 if open_ else 0.012) * SR)))   # 고역 통과 근사
        prev = x
    return out


def crash():
    n = int(1.6 * SR)
    prev, out = 0.0, []
    for k in range(n):
        x = rng.random() * 2 - 1
        out.append((x - 0.6 * prev) * 0.5 * math.exp(-k / (0.55 * SR)))
        prev = x
    return out


def bass_note(m, dur):
    n = int(dur * SR)
    f = midi(m)
    e = env(n, 0.004, dur * 0.9)
    out, lp = [], 0.0
    for k in range(n):
        t = k / SR
        raw = sum(math.sin(2 * math.pi * f * h * t) / h for h in (1, 2, 3, 4))   # 톱니 근사
        lp += 0.18 * (raw - lp)                                                   # 저역 통과
        out.append(lp * e[k])
    return out


def pluck(m, dur=0.32):
    n = int(dur * SR)
    f = midi(m)
    e = env(n, 0.002, 0.11)
    return [e[k] * (math.sin(2 * math.pi * f * k / SR) + 0.35 * math.sin(2 * math.pi * 2 * f * k / SR)) for k in range(n)]


def pad_chord(notes, dur):
    n = int(dur * SR)
    out = []
    for k in range(n):
        t = k / SR
        a = min(1.0, t / 0.4, (dur - t) / 0.4)
        out.append(a * sum(math.sin(2 * math.pi * midi(m - 12) * t * (1 + 0.002 * j)) for j, m in enumerate(notes)) / 3)
    return out


def build_music():
    drums, bass, keys, pad = zeros(), zeros(), zeros(), zeros()
    cache = {}

    def cached(key, fn):
        if key not in cache:
            cache[key] = fn()
        return cache[key]

    bar_i = 0
    t = 0.0
    while t < TOTAL:
        root, notes = CHORDS[bar_i % 4]
        last = t >= OUTRO
        if last:
            # 엔딩: C 코드 길게
            add(pad, pad_chord([60, 64, 67], TOTAL - t), t, 1.0)
            add(bass, bass_note(36, min(3.0, TOTAL - t)), t, 0.9)
            add(drums, cached('crash', crash), t, 0.8)
            for j, m in enumerate([72, 76, 79, 84]):
                add(keys, cached(('p', m), lambda m=m: pluck(m, 0.9)), t + j * 0.12, 0.8)
            break
        add(pad, cached(('pad', bar_i % 4), lambda n=notes: pad_chord(n, BAR)), t, 1.0)
        for b in range(8):                                   # 8분음표
            tt = t + b * BEAT / 2
            if tt >= OUTRO:
                break
            if tt >= DRUMS_IN:
                if b % 4 == 0:
                    add(drums, cached('kick', kick), tt, 1.0)
                if b % 4 == 2:
                    add(drums, cached('snare', snare), tt, 0.7)
                add(drums, cached(('hat', b == 7), lambda o=(b == 7): hat(o)), tt, 0.35)
                add(bass, cached(('bass', root, b % 2), lambda r=root, b=b: bass_note(r + (12 if b % 2 else 0), BEAT / 2)), tt, 0.55)
            arp = notes[[0, 1, 2, 1][b % 4]] + 12
            add(keys, cached(('p', arp), lambda m=arp: pluck(m)), tt, 0.45 if tt < DRUMS_IN else 0.32)
        t += BAR
        bar_i += 1

    music = zeros()
    for k in range(N):
        music[k] = 0.55 * drums[k] + 0.35 * bass[k] + 0.30 * keys[k] + 0.06 * pad[k]
    return music


def music_gain(t, voice_spans):
    g = 1.0
    if t < 3.4:
        g = 0.75
    for tap in ROUND_TAPS:
        if tap + 1.0 <= t < tap + 4.6:
            g = min(g, 0.45)                                   # 룰렛 틱이 잘 들리게
        if tap + 4.45 <= t < tap + 6.1:
            g = 0.0                                            # 암전: 완전 정적
    for a, b in voice_spans:
        if a - 0.1 <= t <= b + 0.15:
            g = min(g, 0.3)                                    # 차량 멘트 덕킹
    g *= min(1.0, t / 0.8)
    g *= max(0.0, min(1.0, (TOTAL - t) / 2.5))
    return g


# ================= 효과음 =================
def tick(strength=1.0):
    n = int(0.09 * SR)
    e = env(n, 0.002, 0.025)
    return [strength * e[k] * (0.55 * (rng.random() * 2 - 1) + 0.6 * math.sin(2 * math.pi * 170 * k / SR)) for k in range(n)]


def boom():
    n = int(1.4 * SR)
    e = env(n, 0.005, 0.45)
    return [e[k] * 0.9 * math.sin(2 * math.pi * (55 - 15 * k / n) * k / SR) for k in range(n)]


def chime():
    out = [0.0] * int(1.8 * SR)
    for j, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):
        st = int(j * 0.07 * SR)
        n = len(out) - st
        e = env(n, 0.004, 0.5)
        for k in range(n):
            out[st + k] += 0.22 * e[k] * (math.sin(2 * math.pi * f * k / SR) + 0.3 * math.sin(2 * math.pi * 2 * f * k / SR))
    return out


def read_wav(path):
    with wave.open(path, 'rb') as w:
        raw = w.readframes(w.getnframes())
    return [v / 32768.0 for v in struct.unpack('<%dh' % (len(raw) // 2), raw)]


def tts(i, text):
    aiff, wav = os.path.join(WORK, f"c{i}.aiff"), os.path.join(WORK, f"c{i}.wav")
    subprocess.run(["say", "-v", CAR, "-r", "200", "-o", aiff, text], check=True)
    subprocess.run(["afconvert", "-f", "WAVE", "-d", f"LEI16@{SR}", "-c", "1", aiff, wav], check=True)
    return read_wav(wav)


# ================= 믹스 =================
voices = [(at, tts(i, txt)) for i, (at, txt) in enumerate(CAR_LINES)]
spans = [(at, at + len(s) / SR) for at, s in voices]

music = build_music()
mix = zeros()
for k in range(N):
    mix[k] = music[k] * music_gain(k / SR, spans)

sfx = zeros()
for tap in ROUND_TAPS:
    t = tap + 1.0
    steps = 0
    while t < tap + 4.6:
        prog = (t - (tap + 1.0)) / 3.6
        add(sfx, tick(0.9 if steps % 2 == 0 else 0.6), t)
        t += 0.08 + 0.32 * prog
        steps += 1
    add(sfx, boom(), tap + 4.6, 0.9)
    add(sfx, chime(), tap + 6.1, 1.0)
    add(sfx, crash(), tap + 6.1, 0.35)          # 당첨 순간 음악 복귀 신호

for k in range(N):
    mix[k] += 0.85 * sfx[k]
for at, s in voices:
    add(mix, s, at, 1.0)

peak = max(1e-6, max(abs(v) for v in mix))
scale = 0.89 / peak
out = os.path.join(WORK, "track.wav")
with wave.open(out, 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(struct.pack('<%dh' % N, *(int(max(-1, min(1, v * scale)) * 32767) for v in mix)))

# 구간별 음량(RMS) 확인용 출력
def rms(a, b):
    i, j = int(a * SR), int(b * SR)
    seg = mix[i:j]
    return (sum(v * v for v in seg) / max(1, len(seg))) ** 0.5 * scale
print(f"track {N / SR:.1f}s peak {peak:.2f}")
for name, a, b in [("title", 0.5, 3.0), ("music", 20, 22), ("spin", 5.5, 8.5), ("blackout", 9.3, 10.2), ("voice", 4.6, 5.8), ("outro", 73, 76)]:
    print(f"  {name:8} rms {rms(a, b):.3f}")
