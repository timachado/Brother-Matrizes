package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterImageDigitizerTest {
    private fun logo(transparent: Boolean = false): RasterImagePixels {
        val background = if (transparent) 0x00000000 else 0xFFFFFFFF.toInt()
        val pixels = IntArray(40 * 30) { background }
        for (y in 6..23) {
            for (x in 8..31) {
                if (x in 14..25 && y in 12..17) continue // contraforma
                pixels[y * 40 + x] = 0xFF171717.toInt()
            }
        }
        return RasterImagePixels(40, 30, pixels, "Logo de teste")
    }

    @Test fun producesRealPesThatCanBeReopened() {
        val design = RasterImageDigitizer.generate(
            logo(),
            RasterImageOptions(widthMm = 40f, outputFormat = "PES")
        ).getOrThrow()
        assertEquals("PES", design.format)
        assertTrue(design.points.any { it.command == StitchCommand.JUMP })
        assertTrue(design.stitchCount > 30)
        assertTrue(design.bounds.widthMm <= 40f)
        assertTrue(design.points.last().command == StitchCommand.END)

        val reopened = GeneratedMatrixPipeline.canonicalize(
            design, outputSuffix = "teste-imagem"
        )
        assertTrue(
            (reopened as? EmbroideryLoadResult.Success)?.design
                ?.sourceBytes?.isNotEmpty() == true
        )
    }

    @Test fun transparentImageUsesAlphaInsteadOfLuminance() {
        val generated = RasterImageDigitizer.generate(
            logo(transparent = true),
            RasterImageOptions(widthMm = 50f, threshold = 20, invert = true)
        ).getOrThrow()
        assertTrue(generated.stitchCount > 30)
    }

    @Test fun rejectsUncroppedSolidBackground() {
        val allDark = RasterImagePixels(
            24, 24, IntArray(24 * 24) { 0xFF000000.toInt() }, "Fundo"
        )
        assertTrue(RasterImageDigitizer.generate(allDark, RasterImageOptions()).isFailure)
    }

    @Test fun rejectsMalformedPixelBuffer() {
        val malformed = RasterImagePixels(15, 15, intArrayOf(1, 2, 3), "Inválida")
        assertTrue(RasterImageDigitizer.generate(malformed, RasterImageOptions()).isFailure)
    }
}
