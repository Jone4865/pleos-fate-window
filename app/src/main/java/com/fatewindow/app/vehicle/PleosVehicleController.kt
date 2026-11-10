package com.fatewindow.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK(ai.pleos.playground:Vehicle:2.3.2) 실제 구현.
 *
 * 에뮬레이터(Pleos Connect v2.1.0)에서 확인한 사항 (2026-11-10):
 * - CONTROL_CAR_* 퍼미션은 normal 레벨이라 설치 시 자동 허용된다. 포털의 "Vehicle Control 준비 중"과 무관하게 호출된다.
 * - Virtual Console(adb forward tcp:8088 tcp:8088 → http://127.0.0.1:8088)이 DDS ONLINE 일 때 쓰기가 상태에 반영된다.
 * - 창문 위치 범위는 0~10 (10 = 100%). 범위는 차마다 다를 수 있어 capability 로 읽어서 환산한다.
 * - 실내등(cabin) OFF 는 켜져 있던 독서등도 끈다. 그 뒤 개별 독서등 ON 은 정상 동작한다.
 */
class PleosVehicleController(context: Context) : VehicleController {

    private val tag = "FateWindow/Pleos"
    private val vehicle = Vehicle(context.applicationContext).also { it.initialize() }

    private val light get() = vehicle.getLight()
    private val window get() = vehicle.getWindow()

    /** 창문별 위치 범위. readCapabilities 에서 채운다. */
    private var windowBounds: Map<WindowValue, AreaBounds<Int>> = emptyMap()
    /**
     * 앰비언트 제어 영역. capability 가 돌려준 SeatArea 를 그대로 써야 한다.
     * 에뮬레이터는 실내 전체를 하나의 영역(여러 좌석 묶음)으로 제공해서 좌석 단위 호출은
     * "area ID not supported" 로 실패한다 (2026-11-10 확인).
     */
    private var ambientAreas: List<SeatArea> = emptyList()
    /** 좌석 하나짜리 앰비언트 영역 = 좌석별 룰렛 가능 (티어1 조건) */
    private var ambientZones: Map<SeatValue, SeatArea> = emptyMap()

    /** 암전 직전 조명 상태 (라운드 종료 시 복귀용) */
    private var savedCabin: LightSwitch? = null
    private var savedAmbient: Map<SeatArea, LightSwitch>? = null

    private var ttsReady = false
    private val tts = TextToSpeech(context.applicationContext) { status -> onTtsInit(status) }

    private fun onTtsInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            ttsReady = true
        } else {
            Log.w(tag, "TTS init failed: $status (멘트는 화면 표시만)")
        }
    }

    // ---------------- 조회 ----------------

    override suspend fun readCapabilities(): VehicleCapabilities {
        val winCaps = await<Map<WindowArea, AreaBounds<Int>>>("window caps") { ok, err ->
            window.checkWindowPositionCapabilities(
                { cfg: CapabilityConfig, m: Map<WindowArea, AreaBounds<Int>> -> ok(if (cfg.writable()) m else emptyMap()) },
                err
            )
        }.orEmpty()
        windowBounds = winCaps.entries.associate { (area, bounds) -> area.values.first() to bounds }

        val readingCaps = await<Map<SeatArea, List<LightSwitch>>>("reading caps") { ok, err ->
            light.checkReadingLightSwitchCapabilities(
                { cfg: CapabilityConfig, m: Map<SeatArea, List<LightSwitch>> -> ok(if (cfg.writable()) m else emptyMap()) },
                err
            )
        }.orEmpty()

        val ambientCaps = await<Map<SeatArea, List<LightSwitch>>>("ambient caps") { ok, err ->
            light.checkAmbientLightSwitchCapabilities(
                { cfg: CapabilityConfig, m: Map<SeatArea, List<LightSwitch>> -> ok(if (cfg.writable()) m else emptyMap()) },
                err
            )
        }.orEmpty()
        ambientAreas = ambientCaps.filterValues { LightSwitch.ON in it }.keys.toList()
        ambientZones = ambientAreas.filter { it.values.size == 1 }.associateBy { it.values.first() }

        val front = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val wiperState = await<WiperState>("wiper state") { ok, err -> vehicle.getWiper().getCurrentWiperState(front, ok, err) }
        val wiperSwitch = await<WiperSwitch>("wiper switch") { ok, err -> vehicle.getWiper().getCurrentWiperSwitch(front, ok, err) }
        val raining = wiperState == WiperState.ON ||
                (wiperSwitch != null && wiperSwitch !in setOf(WiperSwitch.OFF, WiperSwitch.AUTO, WiperSwitch.OTHER, WiperSwitch.SERVICE))

        val lowBeam = await<LightState>("low beam") { ok, err -> light.getCurrentLowBeamLightState(ok, err) }

        return VehicleCapabilities(
            hasAmbientLight = ambientZones.isNotEmpty(),
            hasSpotlightPerSeat = readingCaps.isNotEmpty(),
            canControlWindow = windowBounds.isNotEmpty(),
            isRaining = raining,
            isNight = lowBeam == LightState.ON
        ).also { Log.i(tag, "capabilities $it windowBounds=${windowBounds.mapValues { (_, b) -> "${b.minValue}..${b.maxValue}" }}") }
    }

    override suspend fun occupiedSeats(): List<Seat> = Seat.ALL.filter { s ->
        await<Boolean>("occupancy ${s.label}") { ok, err ->
            vehicle.getSeat().getCurrentSeatOccupancy(SeatArea(seatValue(s)), ok, err)
        } == true
    }

    override suspend fun isParked(): Boolean =
        await<VehicleGear>("gear") { ok, err -> vehicle.getGear().getCurrentGearSelectionState(ok, err) } == VehicleGear.GEAR_PARK

    // ---------------- 제어 ----------------

    override suspend fun setSpotlight(seat: Seat, on: Boolean) {
        await<LightSwitch>("reading ${seat.label} $on") { ok, err ->
            light.setReadingLightSwitch(SeatArea(seatValue(seat)), on.toSwitch(), ok, err)
        }
    }

    override suspend fun setAmbient(seat: Seat, on: Boolean) {
        val area = ambientZones[seatValue(seat)] ?: return
        setAmbientArea(area, on.toSwitch(), "ambient ${seat.label} $on")
    }

    private suspend fun setAmbientArea(area: SeatArea, sw: LightSwitch, what: String) {
        await<Unit>(what) { ok, err ->
            light.setAmbientLightSwitch(area, sw, { _: SeatArea, _: LightSwitch? -> ok(Unit) }, err)
        }
    }

    override suspend fun allLightsOff() {
        if (savedCabin == null) {
            savedCabin = await<LightSwitch>("cabin get") { ok, err -> light.getCabinLightSwitch(ok, err) }
            savedAmbient = ambientAreas.mapNotNull { area ->
                await<LightSwitch>("ambient get") { ok, err -> light.getAmbientLightSwitch(area, ok, err) }
                    ?.let { area to it }
            }.toMap()
        }
        await<LightSwitch>("cabin OFF") { ok, err -> light.setCabinLightSwitch(LightSwitch.OFF, ok, err) }
        Seat.ALL.forEach { setSpotlight(it, false) }
        ambientAreas.forEach { setAmbientArea(it, LightSwitch.OFF, "ambient OFF") }
    }

    override suspend fun restoreLights() {
        Seat.ALL.forEach { setSpotlight(it, false) }
        savedAmbient?.forEach { (area, sw) -> setAmbientArea(area, sw, "ambient restore") }
        savedCabin?.let { sw -> await<LightSwitch>("cabin restore") { ok, err -> light.setCabinLightSwitch(sw, ok, err) } }
        savedCabin = null
        savedAmbient = null
    }

    override suspend fun openWindow(seat: Seat, positionPercent: Int) {
        val wv = windowValue(seat)
        val b = windowBounds[wv]
        val min = b?.minValue ?: 0
        val max = b?.maxValue ?: 10
        val value = (min + (max - min) * positionPercent / 100).coerceIn(min, max)
        setWindow(seat, wv, value)
    }

    override suspend fun closeWindow(seat: Seat) {
        val wv = windowValue(seat)
        setWindow(seat, wv, windowBounds[wv]?.minValue ?: 0)
    }

    private suspend fun setWindow(seat: Seat, wv: WindowValue, value: Int) {
        await<Unit>("window ${seat.label} -> $value") { ok, err ->
            window.setWindowPosition(WindowArea(wv), value, { _: WindowArea, _: Int? -> ok(Unit) }, err)
        }
    }

    override fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
        else Log.i(tag, "TTS(미준비): $text")
    }

    override fun release() {
        runCatching { tts.shutdown() }
        runCatching { vehicle.release() }
    }

    // ---------------- helpers ----------------

    /**
     * SDK 콜백을 suspend 로 감싼다. 실패·타임아웃이면 null 을 돌려주고 로그만 남긴다.
     */
    private suspend fun <T : Any> await(what: String, call: (ok: (T?) -> Unit, err: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<T?> { cont ->
                val ok: (T?) -> Unit = { v -> if (cont.isActive) cont.resume(v) }
                val err: (Exception) -> Unit = { e ->
                    Log.w(tag, "$what failed: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
                try {
                    call(ok, err)
                } catch (e: Exception) {
                    err(e)
                }
            }
        } ?: run { Log.w(tag, "$what -> no result"); null }

    private fun CapabilityConfig.writable() = accessType.name.contains("WRITE")

    private fun Boolean.toSwitch() = if (this) LightSwitch.ON else LightSwitch.OFF

    private fun seatValue(s: Seat): SeatValue = when (s.row) {
        1 -> when (s.side) {
            Seat.Side.DRIVER -> SeatValue.ROW_1_LEFT
            Seat.Side.PASSENGER -> SeatValue.ROW_1_RIGHT
            Seat.Side.MIDDLE -> SeatValue.ROW_1_CENTER
        }
        2 -> when (s.side) {
            Seat.Side.DRIVER -> SeatValue.ROW_2_LEFT
            Seat.Side.PASSENGER -> SeatValue.ROW_2_RIGHT
            Seat.Side.MIDDLE -> SeatValue.ROW_2_CENTER
        }
        else -> when (s.side) {
            Seat.Side.DRIVER -> SeatValue.ROW_3_LEFT
            Seat.Side.PASSENGER -> SeatValue.ROW_3_RIGHT
            Seat.Side.MIDDLE -> SeatValue.ROW_3_CENTER
        }
    }

    private fun windowValue(s: Seat): WindowValue = when {
        s.row == 1 && s.side == Seat.Side.PASSENGER -> WindowValue.ROW_1_RIGHT
        s.row == 1 -> WindowValue.ROW_1_LEFT
        s.side == Seat.Side.PASSENGER -> WindowValue.ROW_2_RIGHT
        else -> WindowValue.ROW_2_LEFT
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
    }
}
