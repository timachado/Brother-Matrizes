package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RasterVectorContoursAndAutoRailsTest {
    private fun image(width: Int = 140, height: Int = 90, draw: (Int, Int) -> Boolean):
        RasterImagePixels {
        val bitmap = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (draw(x, y)) bitmap[y * width + x] = 0xFFEF8220.toInt()
        }
        return RasterImagePixels(width, height, bitmap, "vetor QA")
    }

    private fun single(source: RasterImagePixels): RasterSatinObject =
        RasterSatinObjectEditor.detect(source).getOrThrow().single()

    @Test fun rectangleProducesExactFourCornersAndNoHole() {
        val source = image { x, y -> x in 20..117 && y in 33..40 }
        val obj = single(source)
        val edges = RasterVectorContours.trace(obj.mask, source.width).getOrThrow()
        assertEquals(1, edges.size)
        assertFalse(edges.single().isHole)
        assertEquals(4, edges.single().vertices.size)
        assertEquals(4, obj.vectorContours.single().vertices.size)
        val bounds = edges.single().vertices
        assertEquals(0f, bounds.minOf { it.x }, .001f)
        assertEquals(98f, bounds.maxOf { it.x }, .001f)
        assertEquals(0f, bounds.minOf { it.y }, .001f)
        assertEquals(8f, bounds.maxOf { it.y }, .001f)
    }

    @Test fun ringKeepsInnerCounterAsHoleAndRefusesSingleSatin() {
        val source = image { x, y ->
            x in 20..115 && y in 15..76 &&
                (x < 29 || x > 106 || y < 24 || y > 67)
        }
        val obj = single(source)
        val loops = RasterVectorContours.trace(obj.mask, source.width).getOrThrow()
        assertEquals(2, loops.size)
        assertEquals(1, loops.count { it.isHole })
        assertTrue(
            RasterAutoSatinRails.propose(source, obj, fullWidthMm = 84f).isFailure
        )
    }

    @Test fun diagonalCornerAmbiguityIsNotPresentedAsValidClosedVector() {
        val source = image { x, y ->
            (x in 35..39 && y in 35..39) ||
                (x in 40..44 && y in 40..44)
        }
        val obj = single(source)
        assertTrue(RasterVectorContours.trace(obj.mask, source.width).isFailure)
        assertTrue(
            RasterAutoSatinRails.propose(source, obj, fullWidthMm = 84f).isFailure
        )
    }

    @Test fun horizontalStrokeGetsShapeFollowingRailsAndNoPES() {
        val source = image { x, y -> x in 20..117 && y in 33..40 }
        val obj = single(source)
        val proposed = RasterAutoSatinRails.propose(source, obj, fullWidthMm = 84f)
            .getOrThrow()
        assertTrue(proposed.rails.size >= 7)
        assertTrue(proposed.rails.all { r ->
            abs(r.ax - r.bx) < .03f && r.by > r.ay
        })
        val simulated = RasterSatinObjectEditor.preview(
            source, obj, listOf(proposed), 84f, HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(simulated.stitchCount > 15)
        assertEquals("QA_SATIN_REVIEW", simulated.format)
        assertTrue(simulated.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun verticalStrokeGetsRailsAcrossActualWidth() {
        val source = image { x, y -> x in 60..67 && y in 9..78 }
        val obj = single(source)
        val proposed = RasterAutoSatinRails.propose(source, obj, fullWidthMm = 84f)
            .getOrThrow()
        assertTrue(proposed.rails.size >= 7)
        assertTrue(proposed.rails.all { r ->
            abs(r.ay - r.by) < .04f
        })
    }

    @Test fun branchingTRequiresManualSectionsNotPseudoSatin() {
        val source = image { x, y ->
            (x in 20..117 && y in 15..24) ||
                (x in 62..72 && y in 24..75)
        }
        val obj = single(source)
        assertTrue(
            RasterAutoSatinRails.propose(source, obj, fullWidthMm = 84f).isFailure
        )
    }

    @Test fun wideBlockIsRejectedAsSatinColumn() {
        val source = image { x, y -> x in 25..112 && y in 12..77 }
        val obj = single(source)
        assertTrue(
            RasterAutoSatinRails.propose(source, obj, fullWidthMm = 84f).isFailure
        )
    }
}
