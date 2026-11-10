package com.fatewindow.app

import com.fatewindow.app.game.GameMode
import com.fatewindow.app.game.Missions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MissionsTest {

    @Test fun lucky_draw_only_uses_selected_stakes() {
        val rng = Random(3)
        repeat(50) {
            val m = Missions.pick(GameMode.LUCKY_DRAW, setOf("coffee", "drive"), rng)
            assertTrue(m.tag in setOf("커피", "다음 운전"))
        }
    }

    @Test fun lucky_draw_with_nothing_selected_falls_back_to_all() {
        val m = Missions.pick(GameMode.LUCKY_DRAW, emptySet(), Random(1))
        assertTrue(Missions.stakes.any { it.label == m.tag })
    }

    @Test fun truth_or_dare_has_both_lines() {
        val m = Missions.pick(GameMode.TRUTH_OR_DARE, emptySet(), Random(5))
        assertEquals(listOf("진실", "도전"), m.lines.map { it.first })
    }

    @Test fun every_mode_produces_a_mission() {
        GameMode.entries.forEach { mode ->
            assertTrue(Missions.pick(mode, emptySet()).lines.isNotEmpty())
        }
    }

    @Test fun reroll_gives_a_different_mission() {
        val rng = Random(9)
        val first = Missions.pick(GameMode.PENALTY, emptySet(), rng)
        repeat(20) { assertNotEquals(first, Missions.pickDifferent(GameMode.PENALTY, emptySet(), first, rng)) }
    }
}
