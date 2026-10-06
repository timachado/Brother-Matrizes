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
