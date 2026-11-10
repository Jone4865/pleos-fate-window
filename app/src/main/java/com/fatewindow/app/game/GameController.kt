package com.fatewindow.app.game

import com.fatewindow.app.vehicle.Seat
import com.fatewindow.app.vehicle.VehicleController
import kotlinx.coroutines.delay

enum class GameMode(val title: String) {
    LUCKY_DRAW("복불복"),
    TRUTH_OR_DARE("진실 혹은 도전"),
    PENALTY("벌칙 결정기")
}

sealed interface RoundEvent {
    data class Spin(val seat: Seat) : RoundEvent      // 룰렛 조명이 이 좌석 비추는 중
    data object Blackout : RoundEvent                  // 완전 암전(뜸들이기)
    data class Winner(val seat: Seat, val tier: RevealTier) : RoundEvent
}

/**
 * 한 라운드 전체 연출을 오케스트레이션.
 * 안전가드 → 룰렛 → 암전 → 티어별 당첨 연출.
 */
class GameController(
    private val vehicle: VehicleController,
    private val engine: RouletteEngine = RouletteEngine()
) {
    class NotParkedException : Exception("주차(P) 후 이용하세요.")

    /** emit 으로 UI/TTS 에 이벤트 전달 */
    suspend fun playRound(
        windowOptionEnabled: Boolean,
        excludeDriver: Boolean,
        emit: (RoundEvent) -> Unit
    ) {
        if (!vehicle.isParked()) throw NotParkedException()

        val caps = vehicle.readCapabilities()
        val tier = RevealTier.decide(caps, windowOptionEnabled)

        var players = vehicle.occupiedSeats()
        if (excludeDriver) players = players.filter { !(it.row == 1 && it.side == Seat.Side.DRIVER) }
        require(players.isNotEmpty()) { "참가자가 없습니다." }

        val winner = engine.draw(players)

        // 1) 시작 암전 + 멘트
        vehicle.allLightsOff()
        vehicle.speak("운명의 창문, 시작합니다.")

        // 2) 룰렛 회전
        for ((seat, d) in engine.spinSequence(players, winner)) {
            when (tier) {
                RevealTier.TIER1_AMBIENT -> vehicle.ambientSpin(players.indexOf(seat))
                else -> {
                    players.forEach { vehicle.setSpotlight(it, on = it == seat, intensity = 60) }
                }
            }
            emit(RoundEvent.Spin(seat))
            delay(d)
        }

        // 3) 완전 암전 1.5초 (뜸들이기)
        vehicle.allLightsOff()
        emit(RoundEvent.Blackout)
        vehicle.speak("운명은…")
        delay(1500)

        // 4) 티어별 당첨 연출
        when (tier) {
            RevealTier.TIER1_AMBIENT -> vehicle.setSpotlight(winner, on = true)
            RevealTier.TIER2_WINDOW -> {
                vehicle.setSpotlight(winner, on = true)
                vehicle.openWindow(winner, positionPercent = 50)
            }
            RevealTier.TIER3_SPOTLIGHT_ONLY -> vehicle.setSpotlight(winner, on = true)
        }
        vehicle.speak("${winner.label}입니다!")
        emit(RoundEvent.Winner(winner, tier))
    }

    fun resetRound(winner: Seat) {
        vehicle.closeWindow(winner)
        vehicle.allLightsOff()
    }
}
