package com.fatewindow.app.vehicle

/** 좌석 식별 (VSS: Vehicle.Cabin.Seat.Row{n}.{Side}) */
data class Seat(val row: Int, val side: Side) {
    enum class Side { DRIVER, PASSENGER, MIDDLE }
    val label: String
        get() = when (row to side) {
            1 to Side.DRIVER -> "운전석"
            1 to Side.PASSENGER -> "조수석"
            else -> "뒷좌석-${if (side == Side.DRIVER) "좌" else "우"}"
        }
}

/** 차량이 실제로 제공하는 캐빈 제어 능력. CapabilityDetector가 시동 시 채운다. */
data class VehicleCapabilities(
    val hasAmbientLight: Boolean,   // Vehicle.Cabin.Light.AmbientLight.*  (차종 옵션)
    val hasSpotlightPerSeat: Boolean, // Vehicle.Cabin.Light.Spotlight.Row{n}.{Side}.*
    val canControlWindow: Boolean,  // Vehicle.Cabin.Door.*.Window.Switch/Position (W)
    val isRaining: Boolean,         // Vehicle.Body.Windshield.Front.Wiping.Mode
    val isNight: Boolean
)

/**
 * Pleos Vehicle SDK 추상화.
 * - 독립 빌드/테스트: StubVehicleController
 * - 실제 제출: PleosVehicleController(아래 주석) 로 교체
 */
interface VehicleController {

    fun readCapabilities(): VehicleCapabilities

    fun occupiedSeats(): List<Seat>                 // 착좌 감지

    fun isParked(): Boolean                         // 기어 P & 속도 0

    // --- 연출용 액추에이터 ---
    fun setSpotlight(seat: Seat, on: Boolean, intensity: Int = 100)
    fun setAmbientAll(on: Boolean)
    fun ambientSpin(step: Int)                      // 둘레 1스텝 회전(앰비언트 있는 차)
    fun allLightsOff()
    fun openWindow(seat: Seat, positionPercent: Int)
    fun closeWindow(seat: Seat)

    fun speak(text: String)                         // TextToSpeech SDK
}
