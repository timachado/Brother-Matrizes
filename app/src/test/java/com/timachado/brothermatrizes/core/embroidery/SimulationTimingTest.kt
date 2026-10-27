package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationTimingTest {

    @Test
    fun matchesReferenceMachinePaceAtOneX() {
        val seconds =
            SimulationTiming
                .estimatedSeconds(
                    stitches = 1852,
                    speedMultiplier = 1f
                )

        assertTrue(
            seconds in 147..149
        )
    }

    @Test
    fun doublesSpeedAtTwoX() {
        val oneX =
            SimulationTiming
                .estimatedSeconds(
                    1500,
                    1f
                )

        val twoX =
            SimulationTiming
                .estimatedSeconds(
                    1500,
                    2f
                )

        assertTrue(
            twoX < oneX
        )

        assertTrue(
            twoX in 59..61
        )
    }

    @Test
    fun allReferenceCommandsUseTheSameTimerTick() {
        val stitch =
            SimulationTiming
                .eventDelayMs(
                    StitchCommand.STITCH,
                    1f
                )

        listOf(
            StitchCommand.JUMP,
            StitchCommand.TRIM,
            StitchCommand.COLOR_CHANGE,
            StitchCommand.STOP,
            StitchCommand.SEQUIN
        ).forEach {
                command ->
            assertEquals(
                stitch,
                SimulationTiming
                    .eventDelayMs(
                        command,
                        1f
                    )
            )
        }

        assertEquals(
            80L,
            stitch
        )
    }

    @Test
    fun batchesAtHighSpeedButKeepsExactOneXRate() {
        val oneX = SimulationTiming.commandsForFrame(
            carryNanos = 0.0,
            elapsedNanos = 80_000_000L,
            speedMultiplier = 1f
        )
        assertEquals(1, oneX.first)

        val fast = SimulationTiming.commandsForFrame(
            carryNanos = 0.0,
            elapsedNanos = 16_000_000L,
            speedMultiplier = 32f
        )
        assertEquals(6, fast.first)
        assertTrue(fast.second >= 0.0)
        assertTrue(fast.second < SimulationTiming.eventIntervalNanos(32f))
    }

    @Test
    fun accumulatesFractionalFramesWithoutSkippingAtNormalSpeed() {
        var carry = 0.0
        var commands = 0
        repeat(5) {
            val next = SimulationTiming.commandsForFrame(
                carry, 16_000_000L, 1f
            )
            commands += next.first
            carry = next.second
        }
        assertEquals(1, commands)
        assertEquals(0.0, carry, 0.0001)
    }

    @Test
    fun limitsCatchUpWorkToFortyEventsPerFrame() {
        val jumped = SimulationTiming.commandsForFrame(
            carryNanos = 300_000_000.0,
            elapsedNanos = 100_000_000L,
            speedMultiplier = 32f
        )
        assertEquals(40, jumped.first)
        assertTrue(jumped.second > 0.0)
    }

    @Test
    fun batchPlaybackMustStopAtNeedleStopCommand() {
        val points = listOf(
            EmbroideryPoint(0, 0, StitchCommand.JUMP, 0),
            EmbroideryPoint(10, 0, StitchCommand.STITCH, 0),
            EmbroideryPoint(10, 0, StitchCommand.STOP, 0),
            EmbroideryPoint(20, 0, StitchCommand.STITCH, 1),
            EmbroideryPoint(20, 0, StitchCommand.END, 1)
        )
        val first = SimulationTiming.advancePlayback(points, 0, 40)
        assertEquals(3, first.nextIndex)
        assertTrue(first.stopped)
        val resumed = SimulationTiming.advancePlayback(
            points, first.nextIndex, 40
        )
        assertEquals(5, resumed.nextIndex)
        assertTrue(!resumed.stopped)
    }

    @Test
    fun invalidValuesReturnZeroTime() {
        assertEquals(
            0,
            SimulationTiming
                .estimatedSeconds(
                    0,
                    1f
                )
        )

        assertEquals(
            0,
            SimulationTiming
                .estimatedSeconds(
                    100,
                    0f
                )
        )
    }
}
