package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSatinObjectEditorTest {
    private fun image(
        width: Int = 120, height: Int = 80,
        paint: (Int, Int) -> Boolean
    ): RasterImagePixels {
        val pixels = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            if (paint(x, y)) pixels[y * width + x] = 0xFFEA8721.toInt()
        }
        return RasterImagePixels(width, height, pixels, "qa objeto")
    }

    private fun railSet(): List<ManualSatinSection> {
        val top = .06f
        val bottom = .94f
        return listOf(ManualSatinSection(
            rails = (0..8).map { i ->
                val x = .06f + i * .11f
                SatinRailPair(x, top, x, bottom)
            }
        ))
    }

    @Test fun independentlySelectsComponentsAndPreservesTheirCoordinates() {
        val source = image { x, y ->
            (x in 10..64 && y in 30..36) ||
                (x in 78..104 && y in 30..36)
        }
        val parts = RasterSatinObjectEditor.detect(source).getOrThrow()
        assertEquals(2, parts.size)
        assertEquals(10, parts[0].mask.minX)
        assertEquals(64, parts[0].mask.maxX)
        assertEquals(78, parts[1].mask.minX)
        assertEquals(104, parts[1].mask.maxX)
        assertTrue(parts.all { it.pixelCount >= 8 })
        assertTrue(parts.all { it.mask.colors.size == 1 })
    }

    @Test fun editedRailsGenerateRealStitchesOnlyInsideSimpleObject() {
        val source = image { x, y -> x in 10..100 && y in 29..36 }
        val part = RasterSatinObjectEditor.detect(source).getOrThrow().single()
        val out = RasterSatinObjectEditor.preview(
            source, part, railSet(), 60f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_SATIN_REVIEW", out.format)
        assertEquals(0, out.sourceBytes.size)
        assertTrue(out.stitchCount > 10)
        assertTrue(out.jumpCount > 0)
        assertTrue(out.isModified)
        assertTrue(out.endFound)
        assertEquals(HoopProfile.H130X180, out.hoopProfile)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun cannotDrawStraightSatinThroughCenterHole() {
        val source = image { x, y ->
            x in 20..95 && y in 15..60 &&
                (x <= 27 || x >= 88 || y <= 22 || y >= 53)
        }
        val part = RasterSatinObjectEditor.detect(source).getOrThrow().single()
        val result = RasterSatinObjectEditor.preview(
            source, part,
            listOf(ManualSatinSection(listOf(
                SatinRailPair(.08f, .2f, .92f, .2f),
                SatinRailPair(.08f, .8f, .92f, .8f)
            ))),
            60f, HoopProfile.H130X180
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("vazio"))
    }

    @Test fun untouchedSatinIsNotMachineReadyAndOversizeIsRejected() {
        val source = image { x, y -> x in 10..100 && y in 29..36 }
        val part = RasterSatinObjectEditor.detect(source).getOrThrow().single()
        assertTrue(RasterSatinObjectEditor.preview(
            source, part, railSet(), 190f, HoopProfile.H100X100
        ).isFailure)
        assertTrue(RasterSatinObjectEditor.preview(
            source, part, emptyList(), 60f, HoopProfile.H130X180
        ).isFailure)
    }
}
