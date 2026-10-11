package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimulationCursorTest {
    private val points = listOf(
        EmbroideryPoint(20, 50, StitchCommand.JUMP, 0),
        EmbroideryPoint(25, 50, StitchCommand.STITCH, 0),
        EmbroideryPoint(25, 50, StitchCommand.TRIM, 0),
        EmbroideryPoint(25, 50, StitchCommand.COLOR_CHANGE, 1),
        EmbroideryPoint(40, 50, StitchCommand.JUMP, 1),
        EmbroideryPoint(45, 50, StitchCommand.STITCH, 1),
        EmbroideryPoint(45, 50, StitchCommand.END, 1)
    )

    @Test fun initialNeedleShowsActualEmbroideryEntry() {
        assertEquals(points[0], SimulationCursor.needlePoint(points, 0))
        assertEquals(points[0], SimulationCursor.needlePoint(points, -1))
        assertNull(SimulationCursor.needlePoint(emptyList(), 0))
    }

    @Test fun executedStitchIsNeedlePositionNotNextCommand() {
        assertEquals(points[0], SimulationCursor.needlePoint(points, 1))
        assertEquals(points[1], SimulationCursor.needlePoint(points, 2))
        assertEquals(points[1], SimulationCursor.needlePoint(points, 4))
        assertEquals(points[4], SimulationCursor.needlePoint(points, 5))
        assertEquals(points[5], SimulationCursor.needlePoint(points, 6))
        assertEquals(points[5], SimulationCursor.needlePoint(points, 999))
    }

    @Test fun initialControlCommandsCannotPlaceNeedleAtFabricOrigin() {
        val preamble = listOf(
            EmbroideryPoint(0, 0, StitchCommand.COLOR_CHANGE, 1),
            EmbroideryPoint(0, 0, StitchCommand.TRIM, 1)
        ) + points
        assertEquals(points[0], SimulationCursor.needlePoint(preamble, 0))
        assertEquals(points[0], SimulationCursor.needlePoint(preamble, 1))
    }

    @Test fun hundredPercentSeekIncludesFinalNonStitchCommands() {
        val prefix = intArrayOf(0, 0, 1, 1, 1, 1, 2, 2)
        assertEquals(0, SimulationCursor.pointIndexForStitchCount(prefix, 0))
        assertEquals(2, SimulationCursor.pointIndexForStitchCount(prefix, 1))
        assertEquals(7, SimulationCursor.pointIndexForStitchCount(prefix, 2))
        assertEquals(7, SimulationCursor.pointIndexForStitchCount(prefix, 400))
        assertEquals(0, SimulationCursor.pointIndexForStitchCount(intArrayOf(), 4))
    }
}
