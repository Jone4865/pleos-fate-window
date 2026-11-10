package com.fatewindow.app.game

import com.fatewindow.app.vehicle.Seat
import com.fatewindow.app.vehicle.VehicleController
import kotlinx.coroutines.delay

sealed interface RoundEvent {
    data class Started(val players: List<Seat>, val tier: RevealTier) : RoundEvent  // 참가자·연출 확정
    data class Spin(val seat: Seat) : RoundEvent      // 룰렛 조명이 이 좌석 비추는 중
    data object Blackout : RoundEvent                  // 완전 암전(뜸들이기)
    data class Winner(val seat: Seat, val tier: RevealTier, val windowOpened: Boolean) : RoundEvent
}

/** 라운드 결과. "완료" 때 창문을 닫고 조명을 되돌리는 데 쓴다. */
data class RoundResult(val winner: Seat, val tier: RevealTier, val windowOpened: Boolean)

/**
 * 한 라운드 전체 연출을 오케스트레이션.
 * 안전가드 → 암전 → 룰렛 → 완전 암전 → 티어별 당첨 연출.
 */
class GameController(
    private val vehicle: VehicleController,
    private val engine: RouletteEngine = RouletteEngine()
) {
    class NotParkedException : Exception("주차(P) 후 이용하세요.")

    /** emit 으로 UI 에 이벤트 전달 */
    suspend fun playRound(
        windowOptionEnabled: Boolean,
        ambientOptionEnabled: Boolean,
        emit: (RoundEvent) -> Unit
    ): RoundResult {
        if (!vehicle.isParked()) throw NotParkedException()

        val caps = vehicle.readCapabilities()
        val tier = RevealTier.decide(caps, windowOptionEnabled, ambientOptionEnabled)
        val windowReveal = when (tier) {
            RevealTier.TIER2_WINDOW -> true
            RevealTier.TIER1_AMBIENT -> RevealTier.windowUsable(caps, windowOptionEnabled) // 티어1의 창문은 보너스
            RevealTier.TIER3_SPOTLIGHT_ONLY -> false
        }

        val players = vehicle.occupiedSeats()
        require(players.isNotEmpty()) { "참가자가 없습니다. (착좌 감지된 좌석 없음)" }
        emit(RoundEvent.Started(players, tier))

        val winner = engine.draw(players)
        val useAmbient = tier == RevealTier.TIER1_AMBIENT

        suspend fun rouletteLight(seat: Seat, on: Boolean) =
            if (useAmbient) vehicle.setAmbient(seat, on) else vehicle.setSpotlight(seat, on)

        // 1) 시작 암전 + 멘트
        vehicle.allLightsOff()
        vehicle.speak("운명의 창문, 시작합니다.")
        delay(800)

        // 2) 룰렛 회전: 이전 좌석 끄고 현재 좌석 켜기
        var prev: Seat? = null
        for ((seat, d) in engine.spinSequence(players, winner)) {
            if (prev != null && prev != seat) rouletteLight(prev, false)
            rouletteLight(seat, true)
            prev = seat
            emit(RoundEvent.Spin(seat))
            delay(d)
        }

        // 3) 완전 암전 1.5초 (뜸들이기)
        vehicle.allLightsOff()
        emit(RoundEvent.Blackout)
        vehicle.speak("운명은…")
        delay(1500)

        // 4) 티어별 당첨 연출: 당첨 좌석만 점등 (+창문)
        if (useAmbient) vehicle.setAmbient(winner, true)
        vehicle.setSpotlight(winner, true)
        if (windowReveal) vehicle.openWindow(winner, positionPercent = 50)
        vehicle.speak("${winner.label}입니다!")
        emit(RoundEvent.Winner(winner, tier, windowReveal))

        return RoundResult(winner, tier, windowReveal)
    }

    /** "완료": 열었던 창문 닫고 조명 원상복구 */
    suspend fun resetRound(result: RoundResult) {
        if (result.windowOpened) vehicle.closeWindow(result.winner)
        vehicle.restoreLights()
    }
}
