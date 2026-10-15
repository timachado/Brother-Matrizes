package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterStraightSatinTest {
    private fun artwork(
        horizontal: Boolean = false,
        hole: Boolean = false
    ): RasterImagePixels {
        val pixels = IntArray(100 * 64) { 0xFF080808.toInt() }
        for (y in 9..49) {
            for (x in 12..15) {
                if (!horizontal) pixels[y * 100 + x] = 0xFFEB8C21.toInt()
            }
        }
        if (horizontal) {
            for (y in 10..13) for (x in 12..55) {
                pixels[y * 100 + x] = 0xFFEB8C21.toInt()
            }
        }
        if (hole && !horizontal) {
            for (y in 9..49) for (x in 12..23) {
                pixels[y * 100 + x] = 0xFFEB8C21.toInt()
            }
            for (y in 18..35) for (x in 16..19) {
                pixels[y * 100 + x] = 0xFF080808.toInt()
            }
        }
        for (y in 20..43) for (x in 59..85) {
            pixels[y * 100 + x] = 0xFFF7F7F7.toInt()
        }
        return RasterImagePixels(100, 64, pixels, "Teste faixa Satin")
    }

    private fun edited(
        image: RasterImagePixels,
        density: Float = 0.5f,
        pull: Float = 0.1f,
        underlay: Boolean = true
    ): Result<EmbroideryDesign> {
        val opts = MultiColorRasterOptions(widthMm = 70f, maxColors = 2)
        val art = MultiColorRasterDigitizer.separate(image, opts).getOrThrow()
        val orange = art.pixels[11 * 100 + 13]
        val white = art.pixels[24 * 100 + 64]
        assertTrue(orange >= 0 && white >= 0 && orange != white)
        return MultiColorRasterDigitizer.generateSeparated(
            image, art, opts.copy(
                layers = listOf(
                    ColorLayerEdit(
                        sourceIndex = orange,
                        stitchMode = RasterLayerStitchMode.SATIN_STRAIGHT,
                        rowSpacingMm = density,
                        satinPullCompensationMm = pull,
                        satinCenterUnderlay = underlay
                    ),
                    ColorLayerEdit(sourceIndex = white)
                )
            )
        )
    }

    @Test fun verticalSatinPreservesWhiteFillLayerAndReopensPes() {
        val image = artwork()
        val result = edited(image)
        assertTrue(result.exceptionOrNull()?.message ?: "Falhou", result.isSuccess)
        val matrix = result.getOrThrow()
        assertEquals(2, matrix.threadColors.size)
        assertEquals(1, matrix.colorChanges)
        assertTrue(matrix.points.count {
            it.command == StitchCommand.STITCH && it.colorIndex == 0
        } > 20)
        assertTrue(matrix.points.count {
            it.command == StitchCommand.STITCH && it.colorIndex == 1
        } > 20)
        assertTrue(matrix.points.any { it.command == StitchCommand.COLOR_CHANGE })
        val reopened = GeneratedMatrixPipeline.canonicalize(matrix, "satin-qa")
        assertTrue("PES deve reabrir com ambas as linhas", reopened is EmbroideryLoadResult.Success)
        val parsed = (reopened as EmbroideryLoadResult.Success).design
        assertEquals(2, parsed.threadColors.size)
        assertEquals(1, parsed.colorChanges)
        assertTrue(parsed.sourceBytes.isNotEmpty())
    }

    @Test fun horizontalBarCanUseStraightSatin() {
        val image = artwork(horizontal = true)
        val result = edited(image)
        assertTrue(result.exceptionOrNull()?.message ?: "Falhou", result.isSuccess)
        assertTrue(result.getOrThrow().stitchCount > 30)
    }

    @Test fun rejectsHolesRatherThanSewingOverVoids() {
        val image = artwork(hole = true)
        val result = edited(image)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Satin") == true ||
            result.exceptionOrNull()?.message?.contains("coluna") == true)
    }

    @Test fun lowerStepAndUnderlayChangeActualCommands() {
        val image = artwork()
        val fine = edited(image, density = 0.4f, underlay = true).getOrThrow()
        val coarse = edited(image, density = 0.9f, underlay = false).getOrThrow()
        val fineStitches = fine.points.count { it.command == StitchCommand.STITCH && it.colorIndex == 0 }
        val coarseStitches = coarse.points.count { it.command == StitchCommand.STITCH && it.colorIndex == 0 }
        assertTrue(fineStitches > coarseStitches)
        assertFalse(fine.points.isEmpty())
    }

    @Test fun invalidCompensationFailsWithoutExport() {
        val image = artwork()
        val art = MultiColorRasterDigitizer.separate(
            image, MultiColorRasterOptions(maxColors = 2)
        ).getOrThrow()
        val invalid = MultiColorRasterDigitizer.generateSeparated(
            image, art,
            MultiColorRasterOptions(
                layers = listOf(
                    ColorLayerEdit(0, stitchMode = RasterLayerStitchMode.SATIN_STRAIGHT,
                        satinPullCompensationMm = 1.0f),
                    ColorLayerEdit(1)
                )
            )
        )
        assertTrue(invalid.isFailure)
    }
}
