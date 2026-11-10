package com.fatewindow.app.game

import com.fatewindow.app.vehicle.Seat
import kotlin.random.Random

/**
 * 공정한 룰렛 추첨 엔진 (순수 Kotlin, 단위테스트 가능).
 * - 가중치 랜덤
 * - 직전 당첨자 연속 당첨 방지(옵션)
 */
class RouletteEngine(private val rng: Random = Random.Default) {

    private var lastWinner: Seat? = null

    /** 참가 좌석 중 당첨자 1명 선정 */
    fun draw(candidates: List<Seat>, avoidRepeat: Boolean = true): Seat {
        require(candidates.isNotEmpty()) { "참가자가 없습니다." }
        val pool = if (avoidRepeat && candidates.size > 1)
            candidates.filter { it != lastWinner } else candidates
        val winner = pool[rng.nextInt(pool.size)]
        lastWinner = winner
        return winner
    }

    /**
     * 룰렛 조명 회전 시퀀스 생성: 빠르게 시작→감속.
     * 반환: (좌석, 그 좌석에 머무는 ms) 리스트. 마지막 원소가 당첨 좌석.
     */
    fun spinSequence(
        candidates: List<Seat>,
        winner: Seat,
        minDelayMs: Long = 80,
        maxDelayMs: Long = 400,
        extraLoops: Int = 2
    ): List<Pair<Seat, Long>> {
        val winnerIdx = candidates.indexOf(winner)
        val totalSteps = candidates.size * extraLoops + winnerIdx + 1
        val seq = ArrayList<Pair<Seat, Long>>(totalSteps)
        for (i in 0 until totalSteps) {
            val seat = candidates[i % candidates.size]
            // 진행률에 따라 선형 감속
            val t = i.toDouble() / (totalSteps - 1).coerceAtLeast(1)
            val delay = (minDelayMs + (maxDelayMs - minDelayMs) * t).toLong()
            seq.add(seat to delay)
        }
        return seq
    }
}
