package com.fatewindow.app.vehicle

/** 좌석 식별. 한국 LHD 기준: DRIVER=왼쪽, PASSENGER=오른쪽. */
data class Seat(val row: Int, val side: Side) {
    enum class Side { DRIVER, PASSENGER, MIDDLE }
    val label: String
        get() = when (row to side) {
            1 to Side.DRIVER -> "운전석"
            1 to Side.PASSENGER -> "조수석"
            else -> "뒷좌석-${if (side == Side.DRIVER) "좌" else "우"}"
        }

    companion object {
        /** 게임이 다루는 좌석(1·2열 좌우). 룰렛은 이 순서로 돈다: 운전석 → 조수석 → 뒷좌석-우 → 뒷좌석-좌 */
        val ALL = listOf(
            Seat(1, Side.DRIVER),
            Seat(1, Side.PASSENGER),
            Seat(2, Side.PASSENGER),
            Seat(2, Side.DRIVER),
        )
    }
}

/** 차량이 실제로 제공하는 캐빈 제어 능력. 라운드 시작마다 다시 읽는다. */
data class VehicleCapabilities(
    val hasAmbientLight: Boolean,     // Light.checkAmbientLightSwitchCapabilities (차종 옵션)
    val hasSpotlightPerSeat: Boolean, // Light.checkReadingLightSwitchCapabilities
    val canControlWindow: Boolean,    // Window.checkWindowPositionCapabilities (READ_WRITE)
    val isRaining: Boolean,           // Wiper.getCurrentWiperSwitch(앞유리) != OFF
    val isNight: Boolean              // Light.getCurrentLowBeamLightState == ON (전조등 켜짐)
)

/**
 * Pleos Vehicle SDK 추상화.
 * - 실제 차량/에뮬레이터: PleosVehicleController (Vehicle SDK 2.3.2)
 * - SDK 서비스가 없는 기기·단위테스트: StubVehicleController
 *
 * SDK 가 콜백 기반이라 전부 suspend. 액추에이터 호출은 실패해도 예외를 던지지 않고
 * 로그만 남긴다(조명 하나 실패로 게임이 멈추면 안 되므로).
 */
interface VehicleController {

    suspend fun readCapabilities(): VehicleCapabilities

    suspend fun occupiedSeats(): List<Seat>          // 착좌 감지

    suspend fun isParked(): Boolean                  // 기어 P (SDK 에 차속 API 없음)

    // --- 연출용 액추에이터 ---
    suspend fun setSpotlight(seat: Seat, on: Boolean)  // 좌석별 독서등
    suspend fun setAmbient(seat: Seat, on: Boolean)    // 좌석별 앰비언트 (있는 차만)
    /** 암전: 실내등·독서등·앰비언트 전부 OFF. 라운드 첫 암전 때 원래 조명 상태를 기억해 둔다. */
    suspend fun allLightsOff()
    /** 라운드 종료: 독서등 OFF, 실내등·앰비언트를 암전 전 상태로 복귀. */
    suspend fun restoreLights()
    suspend fun openWindow(seat: Seat, positionPercent: Int)
    suspend fun closeWindow(seat: Seat)

    fun speak(text: String)                          // 멘트 (Pleos TTS 는 준비 중이라 Android TTS)

    fun release() {}
}
