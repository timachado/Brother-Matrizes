package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiColorRasterDigitizerTest {
    private fun twoColorLogo(transparent: Boolean = false): RasterImagePixels {
        val bg = if (transparent) 0 else 0xFF090909.toInt()
        val pixels = IntArray(84 * 54) { bg }
        for (y in 12..38) {
            for (x in 10..34) {
                if (x in 19..24 && y in 19..31) continue // furo transparente
                pixels[y * 84 + x] = 0xFFED8A1D.toInt()
            }
            for (x in 45..74) pixels[y * 84 + x] = 0xFFF7F7F7.toInt()
        }
        return RasterImagePixels(84, 54, pixels, "Logo branco e laranja")
    }

    @Test fun detectsWhiteAndOrangeAndRejectsBlackFabric() {
        val separated = MultiColorRasterDigitizer.separate(
            twoColorLogo(),
            MultiColorRasterOptions(widthMm = 75f, maxColors = 2)
        ).getOrThrow()
        assertEquals(2, separated.colors.size)
        val orange = separated.pixels[22 * 84 + 15]
        val white = separated.pixels[22 * 84 + 50]
        assertTrue(orange >= 0 && white >= 0 && orange != white)
        assertEquals(-1, separated.pixels[3 * 84 + 3])
        assertEquals(-1, separated.pixels[24 * 84 + 22])
    }

    @Test fun preservesTwoThreadColorsAndRealColorChangeInPes() {
        val design = MultiColorRasterDigitizer.generate(
            twoColorLogo(),
            MultiColorRasterOptions(widthMm = 80f, maxColors = 2, outputFormat = "PES")
        ).getOrThrow()
        assertEquals("PES", design.format)
        assertEquals(2, design.threadColors.size)
        assertEquals(1, design.colorChanges)
        assertTrue(design.points.any { it.command == StitchCommand.TRIM })
        assertTrue(design.points.any { it.command == StitchCommand.COLOR_CHANGE })
        assertTrue(design.points.count { it.colorIndex == 0 && it.command == StitchCommand.STITCH } > 10)
        assertTrue(design.points.count { it.colorIndex == 1 && it.command == StitchCommand.STITCH } > 10)
        val reopened = GeneratedMatrixPipeline.canonicalize(design, "qa-cores")
        assertTrue("O PES deve reabrir no app",
            reopened is EmbroideryLoadResult.Success)
        val checked = (reopened as EmbroideryLoadResult.Success).design
        assertEquals(2, checked.threadColors.size)
        assertTrue(checked.colorChanges >= 1)
        assertTrue(checked.sourceBytes.isNotEmpty())
    }

    @Test fun respectsAlphaAndAvoidsStitchingBackground() {
        val separated = MultiColorRasterDigitizer.separate(
            twoColorLogo(transparent = true),
            MultiColorRasterOptions(maxColors = 2)
        ).getOrThrow()
        assertEquals(2, separated.colors.size)
        assertEquals(-1, separated.pixels[0])
    }

    @Test fun rejectsCompletePhotoLikeBackground() {
        val black = RasterImagePixels(
            40, 40, IntArray(1600) { 0xFF000000.toInt() }, "sem logo"
        )
        assertTrue(MultiColorRasterDigitizer.separate(
            black, MultiColorRasterOptions()
        ).isFailure)
    }
}
