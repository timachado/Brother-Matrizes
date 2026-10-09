package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StitchThreadSegmentsTest {
    private fun p(
        x: Int, y: Int, command: StitchCommand,
        color: Int = 0
    ) = EmbroideryPoint(x, y, command, color)

    @Test
    fun jumpRepositionsNeedleButDoesNotDrawTravelThread() {
        val points = listOf(
            p(0, 0, StitchCommand.JUMP),
            p(10, 0, StitchCommand.STITCH),
            p(50, 0, StitchCommand.JUMP),
            p(55, 0, StitchCommand.STITCH),
            p(60, 0, StitchCommand.STITCH),
            p(60, 0, StitchCommand.END)
        )
        val edges = mutableListOf<Pair<Int, Int>>()
        visitRenderedStitchSegments(points) { from, to ->
            edges += from.xUnits to to.xUnits
        }
        assertEquals(listOf(0 to 10, 50 to 55, 55 to 60), edges)
        assertTrue("JUMP nao pode aparecer como linha de costura.",
            10 to 50 !in edges)
    }

    @Test
    fun colorChangeAndTrimNeverCreateFalseCrossColorConnector() {
        val points = listOf(
            p(2, 3, StitchCommand.JUMP),
            p(10, 3, StitchCommand.STITCH),
            p(10, 3, StitchCommand.COLOR_CHANGE, 1),
            p(18, 3, StitchCommand.STITCH, 1),
            p(18, 3, StitchCommand.TRIM, 1),
            p(40, 3, StitchCommand.JUMP, 1),
            p(45, 3, StitchCommand.STITCH, 1),
            p(45, 3, StitchCommand.END, 1),
            p(70, 3, StitchCommand.STITCH, 1)
        )
        val edges = mutableListOf<Triple<Int, Int, Int>>()
        visitRenderedStitchSegments(points) { from, to ->
            edges += Triple(from.xUnits, to.xUnits, to.colorIndex)
        }
        assertEquals(
            listOf(
                Triple(2, 10, 0),
                Triple(10, 18, 1),
                Triple(40, 45, 1)
            ),
            edges
        )
    }

    @Test
    fun simulationLimitNeverDrawsStitchesFromFutureFrames() {
        val points = listOf(
            p(0, 0, StitchCommand.JUMP),
            p(10, 0, StitchCommand.STITCH),
            p(20, 0, StitchCommand.STITCH),
            p(30, 0, StitchCommand.STITCH)
        )
        val edges = mutableListOf<Pair<Int, Int>>()
        visitRenderedStitchSegments(points, pointLimit = 3) { from, to ->
            edges += from.xUnits to to.xUnits
        }
        assertEquals(listOf(0 to 10, 10 to 20), edges)
    }
}
