package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSatinTopologyPlannerTest {
    private fun image(width: Int = 140, height: Int = 100, draw: (Int, Int) -> Boolean):
        RasterImagePixels {
        val argb = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (draw(x, y)) argb[y * width + x] = 0xFFE98727.toInt()
        }
        return RasterImagePixels(width, height, argb, "satin-topologia-qa")
    }
    private fun only(source: RasterImagePixels): RasterSatinObject =
        RasterSatinObjectEditor.detect(source).getOrThrow().single()

    @Test fun ringWithCounterNeverBridgesItsEmptyCenter() {
        val source = image { x, y ->
            x in 30..106 && y in 13..89 &&
                (x <= 39 || x >= 97 || y <= 22 || y >= 80)
        }
        val part = only(source)
        val vector = RasterVectorContours.trace(part.mask, source.width).getOrThrow()
        assertEquals(1, vector.count { it.isHole })
        val proposed = RasterSatinTopologyPlanner.propose(source, part, 70f)
        assertTrue("Closed ring should split into multiple rails: " +
            proposed.exceptionOrNull(), proposed.isSuccess)
        val sections = proposed.getOrThrow()
        assertTrue(sections.size >= 4)
        assertTrue(sections.all { !it.connectToPrevious })
        // Stitch geometry must never be submitted without image validation.
        val sim = RasterSatinObjectEditor.preview(
            source,part,sections,70f,HoopProfile.H130X180
        )
        assertTrue("Ring preview has missing area or crosses holes: " +
            sim.exceptionOrNull(), sim.isSuccess)
        assertTrue(sim.getOrThrow().jumpCount >= 3)
        assertTrue(sim.getOrThrow().sourceBytes.isEmpty())
    }

    @Test fun branchedLetterTRequiresMoreThanOneIndependentSection() {
        val source = image { x,y ->
            (x in 25..114 && y in 16..26) ||
                (x in 65..75 && y in 26..87)
        }
        val part = only(source)
        val proposed = RasterSatinTopologyPlanner.propose(source,part,70f)
        assertTrue("T junction must be planned: " + proposed.exceptionOrNull(),
            proposed.isSuccess)
        val rails = proposed.getOrThrow()
        assertTrue(rails.size >= 3)
        assertTrue(rails.all { !it.connectToPrevious })
        val sim = RasterSatinObjectEditor.preview(
            source,part,rails,70f,HoopProfile.H130X180
        )
        assertTrue("T coverage must pass: " + sim.exceptionOrNull(),sim.isSuccess)
    }

    @Test fun shapeNeverProducesExportablePES() {
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun faintSinglePixelTraceIsRejectedRatherThanPretendStitches() {
        val source = image { x,y -> x in 20..100 && y == 45 }
        val parts = RasterSatinObjectEditor.detect(source)
        // A one-pixel hairline can be refused by source segmentation itself.
        // If segmentation accepts it, planning MUST still reject Satin.
        if (parts.isSuccess) {
            assertTrue(
                RasterSatinTopologyPlanner.propose(
                    source, parts.getOrThrow().single(), 84f
                ).isFailure
            )
        } else assertTrue(parts.isFailure)
    }
}
