package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterVerticalVisualPreviewTest {
    private fun complexLogo(): RasterImagePixels {
        val w = 160; val h = 116
        val pixels = IntArray(w * h)
        // Simulate connected orange monogram and multiple disconnected white letters.
        for (y in 12..52) for (x in 40..98) {
            if (x in 40..47 || x in 91..98 || y in 12..18 || y in 45..52) {
                pixels[y * w + x] = 0xFFF28A20.toInt()
            }
        }
        for (x0 in listOf(12, 27, 42, 57, 72, 87, 102, 117, 132)) {
            for (y in 74..99) for (x in x0..x0 + 8) {
                pixels[y * w + x] = 0xFFF6F6F6.toInt()
            }
        }
        return RasterImagePixels(w, h, pixels, "logo de teste")
    }

    @Test fun complexWholeLogoStillOpensOriginalSimulatorPreview() {
        val source = complexLogo()
        val art = MultiColorRasterDigitizer.separate(
            source, MultiColorRasterOptions(maxColors = 2)
        ).getOrThrow()
        val preview = RasterVerticalVisualPreview.create(
            source, art, 60f, HoopProfile.H100X100
        ).getOrThrow()
        assertEquals("QA_PREVIEW", preview.format)
        assertTrue(preview.fileName.contains("NAO-EXPORTAR"))
        assertTrue(preview.stitchCount > 15)
        assertEquals(2, preview.threadColors.size)
        assertEquals(0, preview.sourceBytes.size)
        assertTrue(preview.hoopProfile == HoopProfile.H100X100)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun everyIllustrativeStitchIsVerticalOnly() {
        val source = complexLogo()
        val art = MultiColorRasterDigitizer.separate(
            source, MultiColorRasterOptions(maxColors = 2)
        ).getOrThrow()
        val preview = RasterVerticalVisualPreview.create(
            source, art, 60f, HoopProfile.H130X180
        ).getOrThrow()
        for ((i, point) in preview.points.withIndex()) {
            if (point.command == StitchCommand.STITCH) {
                val previous = preview.points[i - 1]
                assertEquals("No lateral stitch even on letters", previous.xUnits, point.xUnits)
            }
        }
    }

    @Test fun rejectOversizedPreviewForSelectedHoop() {
        val source = complexLogo()
        val art = MultiColorRasterDigitizer.separate(
            source, MultiColorRasterOptions(maxColors = 2)
        ).getOrThrow()
        assertTrue(RasterVerticalVisualPreview.create(
            source, art, 150f, HoopProfile.H100X100
        ).isFailure)
    }
}
