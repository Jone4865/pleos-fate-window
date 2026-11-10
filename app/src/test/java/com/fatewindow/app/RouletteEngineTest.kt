package com.fatewindow.app

import com.fatewindow.app.game.RouletteEngine
import com.fatewindow.app.vehicle.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RouletteEngineTest {

    private val seats = listOf(
        Seat(1, Seat.Side.DRIVER),
        Seat(1, Seat.Side.PASSENGER),
        Seat(2, Seat.Side.DRIVER),
        Seat(2, Seat.Side.PASSENGER)
    )

    @Test fun draw_returns_a_candidate() {
        val e = RouletteEngine(Random(1))
        val w = e.draw(seats)
        assertTrue(w in seats)
    }

    @Test fun draw_avoids_immediate_repeat() {
        val e = RouletteEngine(Random(42))
        var prev = e.draw(seats)
        repeat(50) {
            val next = e.draw(seats, avoidRepeat = true)
            assertTrue("연속 당첨 발생", next != prev)
            prev = next
        }
    }

    @Test fun spin_sequence_ends_on_winner() {
        val e = RouletteEngine(Random(7))
        val winner = seats[2]
        val seq = e.spinSequence(seats, winner)
        assertEquals(winner, seq.last().first)
    }
}
