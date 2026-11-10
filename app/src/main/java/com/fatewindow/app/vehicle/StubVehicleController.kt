package com.fatewindow.app.vehicle

import android.util.Log

/**
 * SDK 없이 로직을 돌려보기 위한 스텁.
 * 에뮬레이터/실차 연동 시 PleosVehicleController 로 교체한다.
 *
 * 실제 구현 예시(의사코드):
 *   override fun openWindow(seat, pct) =
 *       vehicle.set("Vehicle.Cabin.Door.Row${seat.row}.${seat.side}.Window.Position", pct)
 *   override fun setSpotlight(seat, on, i) =
 *       vehicle.set("Vehicle.Cabin.Light.Spotlight.Row${seat.row}.${seat.side}.IsLightOn", on)
 */
class StubVehicleController(
    private val caps: VehicleCapabilities = VehicleCapabilities(
        hasAmbientLight = false,
        hasSpotlightPerSeat = true,
        canControlWindow = true,
        isRaining = false,
        isNight = false
    ),
    private val seats: List<Seat> = listOf(
        Seat(1, Seat.Side.DRIVER),
        Seat(1, Seat.Side.PASSENGER),
        Seat(2, Seat.Side.DRIVER),
        Seat(2, Seat.Side.PASSENGER)
    )
) : VehicleController {

    private val tag = "FateWindow/Vehicle"

    override fun readCapabilities() = caps
    override fun occupiedSeats() = seats
    override fun isParked() = true

    override fun setSpotlight(seat: Seat, on: Boolean, intensity: Int) =
        Log.d(tag, "spotlight ${seat.label} on=$on i=$intensity")

    override fun setAmbientAll(on: Boolean) = Log.d(tag, "ambientAll on=$on")
    override fun ambientSpin(step: Int) = Log.d(tag, "ambientSpin step=$step")
    override fun allLightsOff() = Log.d(tag, "ALL LIGHTS OFF (암전)")
    override fun openWindow(seat: Seat, positionPercent: Int) =
        Log.d(tag, "openWindow ${seat.label} $positionPercent%")
    override fun closeWindow(seat: Seat) = Log.d(tag, "closeWindow ${seat.label}")
    override fun speak(text: String) = Log.d(tag, "TTS: $text")
}
