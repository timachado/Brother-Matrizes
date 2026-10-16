package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterOrthogonalSatinPlannerTest {
    private fun draw(
        w: Int = 150, h: Int = 100, fn: (Int, Int) -> Boolean
    ): RasterImagePixels {
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (fn(x, y)) pixels[y * w + x] = 0xFFEA8721.toInt()
        }
        return RasterImagePixels(w, h, pixels, "prova máscara ortogonal")
    }

    private fun part(image: RasterImagePixels) =
        RasterSatinObjectEditor.detect(image).getOrThrow().single()

    @Test fun asymmetricRingKeepsCounterEmptyAndStaysNonExportable() {
        val source = draw { x, y ->
            x in 25..125 && y in 12..85 &&
                (x < 33 || x > 115 || y < 22 || y > 76)
        }
        val item = part(source)
        val four = RasterOrthogonalSatinPlanner.propose(source, item, 72f)
            .getOrThrow()
        assertEquals(4, four.size)
        assertTrue(four.all { !it.connectToPrevious })
        val preview = RasterSatinObjectEditor.preview(
            source, item, four, 72f, HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(preview.stitchCount > 40)
        assertTrue(preview.jumpCount >= 4)
        assertTrue(preview.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun missingBorderPixelCannotBePassedOffAsCompleteRing() {
        val source = draw { x, y ->
            x in 25..125 && y in 12..85 &&
                (x < 33 || x > 115 || y < 22 || y > 76) &&
                !(y == 12 && x == 75)
        }
        val parts = RasterSatinObjectEditor.detect(source).getOrThrow()
        assertEquals(1, parts.size)
        assertTrue(RasterOrthogonalSatinPlanner.propose(source, parts.single(),72f)
            .isFailure)
    }

    @Test fun shiftedStemTIsDecomposedOnlyWhenItsInkMatchesThreeBars() {
        val source = draw { x, y ->
            (x in 15..130 && y in 12..22) ||
                (x in 72..81 && y in 22..89)
        }
        val item = part(source)
        val three = RasterOrthogonalSatinPlanner.propose(source, item, 72f)
            .getOrThrow()
        assertEquals(3, three.size)
        assertTrue(three.all { !it.connectToPrevious })
        val preview = RasterSatinObjectEditor.preview(
            source, item, three, 72f, HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(preview.stitchCount > 20)
    }

    @Test fun offAxisTIsNotGuessedByOrthogonalClassifier() {
        val source = draw { x, y ->
            (x in 15..130 && y in 12..22) ||
                (x in 112..121 && y in 22..89)
        }
        val item = part(source)
        assertTrue(RasterOrthogonalSatinPlanner.propose(source,item,72f).isFailure)
    }
}
