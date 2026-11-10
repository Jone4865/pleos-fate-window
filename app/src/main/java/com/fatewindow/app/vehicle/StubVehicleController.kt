package com.fatewindow.app.vehicle

import android.util.Log

/**
 * Pleos 차량 서비스가 없는 기기(일반 폰·태블릿 에뮬레이터)에서 앱을 돌려보기 위한 스텁.
 * 모든 동작을 로그로만 남긴다.
 */
class StubVehicleController(
    private val caps: VehicleCapabilities = VehicleCapabilities(
        hasAmbientLight = false,
        hasSpotlightPerSeat = true,
        canControlWindow = true,
        isRaining = false,
        isNight = false
    ),
    private val seats: List<Seat> = Seat.ALL
) : VehicleController {

    private val tag = "FateWindow/Stub"

    override suspend fun readCapabilities() = caps
    override suspend fun occupiedSeats() = seats
    override suspend fun isParked() = true

    override suspend fun setSpotlight(seat: Seat, on: Boolean) { log("spotlight ${seat.label} on=$on") }
    override suspend fun setAmbient(seat: Seat, on: Boolean) { log("ambient ${seat.label} on=$on") }
    override suspend fun allLightsOff() { log("ALL LIGHTS OFF (암전)") }
    override suspend fun restoreLights() { log("lights restored") }
    override suspend fun openWindow(seat: Seat, positionPercent: Int) { log("openWindow ${seat.label} $positionPercent%") }
    override suspend fun closeWindow(seat: Seat) { log("closeWindow ${seat.label}") }
    override fun speak(text: String) { log("TTS: $text") }

    // 단위테스트(JVM)에서는 android.util.Log 가 없으므로 실패해도 무시
    private fun log(msg: String) { runCatching { Log.d(tag, msg) } }
}
