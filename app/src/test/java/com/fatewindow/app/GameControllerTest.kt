package com.fatewindow.app

import com.fatewindow.app.game.GameController
import com.fatewindow.app.game.RevealTier
import com.fatewindow.app.game.RoundEvent
import com.fatewindow.app.vehicle.Seat
import com.fatewindow.app.vehicle.VehicleCapabilities
import com.fatewindow.app.vehicle.VehicleController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameControllerTest {

    /** 호출을 순서대로 기록하는 가짜 차량 */
    private class FakeVehicle(
        var caps: VehicleCapabilities,
        var parked: Boolean = true,
        val seats: List<Seat> = Seat.ALL
    ) : VehicleController {
        val calls = mutableListOf<String>()
        val spot = mutableMapOf<Seat, Boolean>()
        /** n 번째 기어 확인부터 주행(D)으로 바뀜. null 이면 계속 parked 값 사용 */
        var driveFromCheck: Int? = null
        private var gearChecks = 0
        override suspend fun readCapabilities() = caps
        override suspend fun occupiedSeats() = seats
        override suspend fun isParked(): Boolean {
            gearChecks++
            val d = driveFromCheck
            return if (d != null && gearChecks >= d) false else parked
        }
        override suspend fun setSpotlight(seat: Seat, on: Boolean) { spot[seat] = on; calls += "spot ${seat.label} $on" }
        override suspend fun setAmbient(seat: Seat, on: Boolean) { calls += "ambient ${seat.label} $on" }
        override suspend fun allLightsOff() { spot.keys.forEach { spot[it] = false }; calls += "blackout" }
        override suspend fun restoreLights() { spot.keys.forEach { spot[it] = false }; calls += "restore" }
        override suspend fun openWindow(seat: Seat, positionPercent: Int) { calls += "open ${seat.label} $positionPercent" }
        override suspend fun closeWindow(seat: Seat) { calls += "close ${seat.label}" }
        override fun speak(text: String) { calls += "tts $text" }
    }

    private fun caps(ambient: Boolean = false, window: Boolean = true, rain: Boolean = false, night: Boolean = false) =
        VehicleCapabilities(ambient, true, window, rain, night)

    @Test fun tier2_blackout_then_only_winner_lit_and_window_opened() = runTest {
        val v = FakeVehicle(caps())
        val events = mutableListOf<RoundEvent>()
        val r = GameController(v).playRound(windowOptionEnabled = true, ambientOptionEnabled = true) { events += it }

        assertEquals(RevealTier.TIER2_WINDOW, r.tier)
        assertTrue(r.windowOpened)
        // 마지막 암전 이후에는 당첨 좌석 독서등만 켜진다
        val afterBlackout = v.calls.drop(v.calls.lastIndexOf("blackout") + 1)
        assertEquals(listOf("spot ${r.winner.label} true"), afterBlackout.filter { it.startsWith("spot") })
        assertTrue("open ${r.winner.label} 50" in afterBlackout)
        assertEquals(r.winner, (events.last() as RoundEvent.Winner).seat)
        assertTrue(events.first() is RoundEvent.Started)
        assertEquals(listOf(r.winner), v.spot.filterValues { it }.keys.toList())
    }

    @Test fun rain_falls_back_to_spotlight_only_no_window() = runTest {
        val v = FakeVehicle(caps(rain = true))
        val r = GameController(v).playRound(true, true) {}
        assertEquals(RevealTier.TIER3_SPOTLIGHT_ONLY, r.tier)
        assertFalse(r.windowOpened)
        assertTrue(v.calls.none { it.startsWith("open") })
    }

    @Test fun ambient_tier_spins_with_ambient_and_window_is_bonus() = runTest {
        val v = FakeVehicle(caps(ambient = true))
        val r = GameController(v).playRound(true, true) {}
        assertEquals(RevealTier.TIER1_AMBIENT, r.tier)
        assertTrue(r.windowOpened)
        assertTrue(v.calls.any { it.startsWith("ambient") })
    }

    @Test fun ambient_option_off_uses_window_tier() = runTest {
        val v = FakeVehicle(caps(ambient = true))
        val r = GameController(v).playRound(true, ambientOptionEnabled = false) {}
        assertEquals(RevealTier.TIER2_WINDOW, r.tier)
    }

    @Test fun reset_closes_window_and_restores_lights() = runTest {
        val v = FakeVehicle(caps())
        val c = GameController(v)
        val r = c.playRound(true, true) {}
        v.calls.clear()
        c.resetRound(r)
        assertEquals(listOf("close ${r.winner.label}", "restore"), v.calls)
    }

    @Test(expected = GameController.NotParkedException::class)
    fun refuses_when_not_parked() = runTest {
        GameController(FakeVehicle(caps(), parked = false)).playRound(true, true) {}
    }


    @Test fun driving_mid_spin_stops_round_before_reveal() = runTest {
        val v = FakeVehicle(caps()).apply { driveFromCheck = 4 }   // 시작 확인(1) 후 룰렛 도중 주행
        val c = GameController(v)
        var thrown: Throwable? = null
        try { c.playRound(true, true) {} } catch (e: GameController.DrivingStartedException) { thrown = e }
        assertTrue(thrown is GameController.DrivingStartedException)
        assertTrue("당첨 연출 전에 멈춰야 함", v.calls.none { it.startsWith("open") })
        c.emergencyReset()
        assertEquals("restore", v.calls.last())
    }

    @Test fun emergency_reset_closes_the_window_opened_this_round() = runTest {
        val v = FakeVehicle(caps())
        val c = GameController(v)
        val r = c.playRound(true, true) {}
        v.calls.clear()
        c.emergencyReset()
        assertEquals(listOf("close ${r.winner.label}", "restore"), v.calls)
        // 두 번 불러도 창문을 다시 건드리지 않는다
        v.calls.clear()
        c.emergencyReset()
        assertEquals(listOf("restore"), v.calls)
    }
}
