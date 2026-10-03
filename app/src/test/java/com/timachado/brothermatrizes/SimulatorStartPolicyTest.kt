package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatorStartPolicyTest {

    @Test
    fun simulationStartsAtLastLeadingPositioningPointBeforeFirstStitch() {
        val points =
            listOf(
                EmbroideryPoint(
                    40,
                    20,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    80,
                    40,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    120,
                    60,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    122,
                    62,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    125,
                    64,
                    StitchCommand.STITCH,
                    0
                )
            )

        assertEquals(
            "Em 0%, a simulação deve ficar no último JUMP de posicionamento, pronta para a primeira pontada.",
            3,
            initialSimulationIndex(
                points
            )
        )
    }

    @Test
    fun simulationDoesNotSkipWhenDesignStartsDirectlyWithStitch() {
        val points =
            listOf(
                EmbroideryPoint(
                    10,
                    10,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    12,
                    12,
                    StitchCommand.STITCH,
                    0
                )
            )

        assertEquals(
            0,
            initialSimulationIndex(
                points
            )
        )
    }
}
