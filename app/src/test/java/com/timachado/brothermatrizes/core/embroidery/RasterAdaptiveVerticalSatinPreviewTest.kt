package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterAdaptiveVerticalSatinPreviewTest {
    private fun mark(): RasterImagePixels {
        val w = 156
        val h = 156
        val pixels = IntArray(w * h)
        // A complex orange logo with a hole (not a simple Satin bar).
        for (y in 18..73) for (x in 50..105) {
            val border = x in 50..55 || x in 100..105 ||
                y in 18..23 || y in 68..73
            if (border) pixels[y * w + x] = 0xFFEF851F.toInt()
        }
        // PANOBIANCO, represented as multiple disconnected white glyphs.
        for (x0 in 12..129 step 13) {
            for (y in 94..125) for (x in x0..x0 + 7) {
                if (x in x0..x0 + 1 || x in x0 + 6..x0 + 7 ||
                    y in 94..96 || y in 123..125) {
                    pixels[y * w + x] = 0xFFF3F3F3.toInt()
                }
            }
        }
        // Additional thin small letters below.
        for (x in 27..136 step 12) for (y in 135..141) {
            pixels[y * w + x] = 0xFFF3F3F3.toInt()
        }
        return RasterImagePixels(w, h, pixels, "foto Panobianco simplificada")
    }

    private fun result(
        hoop: HoopProfile = HoopProfile.H130X180,
        widthMm: Float = 90f
    ): Result<EmbroideryDesign> {
        val image = mark()
        val art = MultiColorRasterDigitizer.separate(
            image, MultiColorRasterOptions(maxColors = 2)
        ).getOrThrow()
        return RasterAdaptiveVerticalSatinPreview.create(image, art, widthMm, hoop)
    }

    @Test fun complexTwoColorLogoGeneratesRealStitchCommandsForOriginalSimulator() {
        val design = result().getOrThrow()
        assertEquals("QA_SATIN_REVIEW", design.format)
        assertTrue(design.stitchCount > 45)
        assertTrue(design.jumpCount > 0)
        assertTrue(design.colorChanges >= 1)
        assertEquals(2, design.threadColors.size)
        assertEquals(0, design.sourceBytes.size)
        assertEquals(HoopProfile.H130X180, design.hoopProfile)
        assertTrue(design.endFound)
    }

    @Test fun noHorizontalSewnTransitionsOrOversizedNeedleStitches() {
        val design = result().getOrThrow()
        for ((i, point) in design.points.withIndex()) {
            if (point.command != StitchCommand.STITCH) continue
            val from = design.points[i - 1]
            val dx = abs(point.xUnits - from.xUnits)
            val dy = abs(point.yUnits - from.yUnits)
            assertTrue("no horizontal raster fill", dy >= dx)
            assertTrue("no stitch > 7.5 mm", hypot(dx.toDouble(), dy.toDouble()) <= 75.0)
        }
    }

    @Test fun disconnectedPartsNeverStitchAcrossGaps() {
        val design = result().getOrThrow()
        assertTrue(design.points.count { it.command == StitchCommand.JUMP } > 2)
        assertTrue(design.points.count { it.command == StitchCommand.TRIM } > 1)
    }

    @Test fun hoopLimitsAndNoExportStayEnforced() {
        assertTrue(result(HoopProfile.H100X100, 115f).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(result().getOrThrow().sourceBytes.isEmpty())
    }

    @Test fun noInkProducesErrorInsteadOfInventedEmbroidery() {
        val image = RasterImagePixels(90, 90, IntArray(90 * 90), "blank")
        val options = MultiColorRasterOptions(maxColors = 2)
        assertTrue(MultiColorRasterDigitizer.separate(image, options).isFailure)
    }
}
