package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterGeometryAwareSatinPreviewTest {
    private fun simpleBand(): RasterImagePixels {
        val width = 120
        val height = 80
        val pixels = IntArray(width * height)
        for (y in 32..37) for (x in 20..100) {
            pixels[y * width + x] = 0xFFED8C29.toInt()
        }
        return RasterImagePixels(width, height, pixels, "faixa horizontal")
    }

    private fun complexClosedLetter(): RasterImagePixels {
        val width = 120
        val height = 80
        val pixels = IntArray(width * height)
        // Large ring with an empty center: it is NOT one Satin column.
        for (y in 14..65) for (x in 20..100) {
            if (x <= 25 || x >= 95 || y <= 19 || y >= 60) {
                pixels[y * width + x] = 0xFFED8C29.toInt()
            }
        }
        return RasterImagePixels(width, height, pixels, "logo com furo")
    }

    @Test fun narrowUnbranchedBandAllowsOnlyRealGeometryBasedSatinPreview() {
        val result = RasterGeometryAwareSatinPreview.create(
            simpleBand(), widthMm = 60f, hoop = HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_SATIN_REVIEW", result.format)
        assertTrue(result.stitchCount > 10)
        assertTrue(result.points.any { it.command == StitchCommand.STITCH })
        assertTrue(result.sourceBytes.isEmpty())
        assertTrue(result.isModified)
        assertEquals(HoopProfile.H130X180, result.hoopProfile)
    }

    @Test fun closedOrBranchedComplexRegionNeverFallsBackToRasterBands() {
        val result = RasterGeometryAwareSatinPreview.create(
            complexClosedLetter(), widthMm = 60f, hoop = HoopProfile.H130X180
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("trilhos Satin"))
    }

    @Test fun neverExportsRasterGeneratedPES() {
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun rejectsImageThatDoesNotFitHoop() {
        assertTrue(RasterGeometryAwareSatinPreview.create(
            simpleBand(), 180f, HoopProfile.H100X100
        ).isFailure)
    }
}
