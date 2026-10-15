package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterCurvedSatinTest {
    private fun logo(hole: Boolean = false, branch: Boolean = false): RasterImagePixels {
        val w = 100
        val h = 70
        val pixels = IntArray(w * h) { 0xFF060606.toInt() }
        for (y in 12..56) {
            val cx = 22 + (3 * sin((y - 12) / 7.0)).roundToInt()
            for (x in cx - 2..cx + 2) {
                pixels[y * w + x] = 0xFFEE8B24.toInt()
            }
            if (hole && y == 34) pixels[y * w + cx] = 0xFF060606.toInt()
            if (branch && y == 34) {
                for (x in cx + 3..cx + 10) pixels[y * w + x] = 0xFFEE8B24.toInt()
            }
        }
        for (y in 20..46) {
            for (x in 56..85) pixels[y * w + x] = 0xFFF6F6F6.toInt()
        }
        return RasterImagePixels(w, h, pixels, "Logo curvas")
    }

    private fun digitize(
        image: RasterImagePixels,
        density: Float = 0.5f,
        underlay: Boolean = true
    ): Result<EmbroideryDesign> {
        val settings = MultiColorRasterOptions(
            widthMm = 52f, maxColors = 2, outputFormat = "PES"
        )
        val art = MultiColorRasterDigitizer.separate(image, settings).getOrThrow()
        val curved = art.pixels[23 * image.width + (22 + (3 *
            sin((23 - 12) / 7.0)).roundToInt())]
        assertTrue(curved >= 0)
        return MultiColorRasterDigitizer.generateSeparated(
            image, art,
            settings.copy(
                layers = art.colors.indices.map { i ->
                    if (i == curved) ColorLayerEdit(
                        sourceIndex = i,
                        stitchMode = RasterLayerStitchMode.SATIN_CURVED,
                        rowSpacingMm = density,
                        satinCenterUnderlay = underlay,
                        satinPullCompensationMm = 0.1f
                    ) else ColorLayerEdit(sourceIndex = i)
                }
            )
        )
    }

    @Test fun curvedRailsProduceTwoThreadPesAndReopen() {
        val matrixResult = digitize(logo())
        assertTrue(matrixResult.exceptionOrNull()?.message ?: "Unknown",
            matrixResult.isSuccess)
        val matrix = matrixResult.getOrThrow()
        assertEquals(2, matrix.threadColors.size)
        assertEquals(1, matrix.colorChanges)
        assertTrue(matrix.points.count { it.command == StitchCommand.STITCH } > 40)
        val reopened = GeneratedMatrixPipeline.canonicalize(matrix, "curved-qa")
        assertTrue("O arquivo PES deve ser reaberto", reopened is EmbroideryLoadResult.Success)
        val parsed = (reopened as EmbroideryLoadResult.Success).design
        assertEquals(1, parsed.colorChanges)
        assertEquals(2, parsed.threadColors.size)
        assertTrue(parsed.sourceBytes.isNotEmpty())
    }

    @Test fun densityAndUnderlayAffectActualStitchSequence() {
        val fine = digitize(logo(), density = 0.4f, underlay = true).getOrThrow()
        val coarse = digitize(logo(), density = 0.9f, underlay = false).getOrThrow()
        assertTrue(fine.stitchCount > coarse.stitchCount)
    }

    @Test fun rejectHoleRatherThanFillingLetterCounter() {
        val result = digitize(logo(hole = true))
        assertTrue("Furo interno não deve virar Satin curvo", result.isFailure)
    }

    @Test fun rejectWideBranchRatherThanInventingRails() {
        val result = digitize(logo(branch = true))
        assertTrue("Bifurcação não deve ser costurada como Satin curvo", result.isFailure)
    }

    @Test fun curvedModeFailsClosedWithInvalidCompensation() {
        val image = logo()
        val settings = MultiColorRasterOptions(widthMm = 52f)
        val art = MultiColorRasterDigitizer.separate(image, settings).getOrThrow()
        val layers = art.colors.indices.map { index ->
            ColorLayerEdit(index, stitchMode = RasterLayerStitchMode.SATIN_CURVED,
                satinPullCompensationMm = 1.0f)
        }
        assertTrue(MultiColorRasterDigitizer.generateSeparated(
            image, art, settings.copy(layers = layers)
        ).isFailure)
    }
}
