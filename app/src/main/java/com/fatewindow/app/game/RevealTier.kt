package com.fatewindow.app.game

import com.fatewindow.app.vehicle.VehicleCapabilities

/** 하드웨어별 연출 티어 (기획서 3절) */
enum class RevealTier {
    TIER1_AMBIENT,        // 앰비언트 O: 둘레 색 회전 → 당첨 좌석 앰비언트 점등
    TIER2_WINDOW,         // 앰비언트 X, 창문 O: 독서등 깜빡 → 창문 오픈으로 당첨
    TIER3_SPOTLIGHT_ONLY; // 앰비언트 X, 창문 X(비/밤): 암전 → 당첨 좌석 독서등만

    companion object {
        /** 가용 기능 + 날씨/시간 + 사용자 창문옵션으로 티어 결정 */
        fun decide(caps: VehicleCapabilities, windowOptionEnabled: Boolean): RevealTier {
            val windowUsable = caps.canControlWindow && windowOptionEnabled &&
                    !caps.isRaining && !caps.isNight
            return when {
                caps.hasAmbientLight -> TIER1_AMBIENT
                windowUsable -> TIER2_WINDOW
                else -> TIER3_SPOTLIGHT_ONLY
            }
        }
    }
}
