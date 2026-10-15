package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterLayerEditorTest {
    private fun artwork(): RasterImagePixels {
        val pixels = IntArray(96 * 60) { 0xFF080808.toInt() }
        for (y in 10..46) {
            for (x in 10..38) pixels[y * 96 + x] = 0xFFF08A1D.toInt()
            for (x in 54..84) pixels[y * 96 + x] = 0xFFFDFDFD.toInt()
        }
        return RasterImagePixels(96, 60, pixels, "Logo camadas")
    }

    private fun palette(): Pair<RasterImagePixels, SeparatedColorArtwork> {
        val source = artwork()
        val separated = MultiColorRasterDigitizer.separate(
            source, MultiColorRasterOptions(maxColors = 2, widthMm = 80f)
        ).getOrThrow()
        assertEquals(2, separated.colors.size)
        return source to separated
    }

    @Test fun reordersColorsInStitchSequenceAndPreservesPesThreadPalette() {
        val (source, separated) = palette()
        val orangeIndex = separated.pixels[25 * 96 + 20]
        val whiteIndex = separated.pixels[25 * 96 + 64]
        assertTrue(orangeIndex in 0..1 && whiteIndex in 0..1 && orangeIndex != whiteIndex)
        val options = MultiColorRasterOptions(
            maxColors = 2, widthMm = 80f,
            layers = listOf(
                ColorLayerEdit(whiteIndex, threadColor = 0xFFFFFF),
                ColorLayerEdit(orangeIndex, threadColor = 0xE77C18)
            )
        )
        val matrix = MultiColorRasterDigitizer.generateSeparated(
            source, separated, options
        ).getOrThrow()
        assertEquals(listOf(0xFFFFFF, 0xE77C18), matrix.threadColors)
        assertEquals(1, matrix.colorChanges)
        val stitched = matrix.points.filter { it.command == StitchCommand.STITCH }
        assertEquals(0, stitched.first().colorIndex)
        assertTrue("Primeira camada deve ser a região branca à direita",
            stitched.first().xUnits > 0)
        assertTrue(matrix.points.last().command == StitchCommand.END)

        val reopened = GeneratedMatrixPipeline.canonicalize(matrix, "layer-qa")
        assertTrue("PES editado deve reabrir", reopened is EmbroideryLoadResult.Success)
        val design = (reopened as EmbroideryLoadResult.Success).design
        assertEquals(2, design.threadColors.size)
        assertEquals(1, design.colorChanges)
    }

    @Test fun canHideOneLayerWithoutLeavingOrphanColorChange() {
        val (source, separated) = palette()
        val orangeIndex = separated.pixels[25 * 96 + 20]
        val whiteIndex = separated.pixels[25 * 96 + 64]
        val options = MultiColorRasterOptions(
            maxColors = 2,
            layers = listOf(
                ColorLayerEdit(whiteIndex, enabled = false),
                ColorLayerEdit(orangeIndex, threadColor = 0xE88322)
            )
        )
        val matrix = MultiColorRasterDigitizer.generateSeparated(
            source, separated, options
        ).getOrThrow()
        assertEquals(listOf(0xE88322), matrix.threadColors)
        assertEquals(0, matrix.colorChanges)
        assertFalse(matrix.points.any { it.command == StitchCommand.COLOR_CHANGE })
        assertTrue(matrix.points.filter { it.command == StitchCommand.STITCH }
            .all { it.colorIndex == 0 })
    }

    @Test fun rejectsDuplicateOrAllHiddenLayers() {
        val (source, separated) = palette()
        val duplicate = MultiColorRasterOptions(
            layers = listOf(ColorLayerEdit(0), ColorLayerEdit(0))
        )
        assertTrue(MultiColorRasterDigitizer.generateSeparated(
            source, separated, duplicate
        ).isFailure)
        val hidden = MultiColorRasterOptions(
            layers = listOf(
                ColorLayerEdit(0, enabled = false),
                ColorLayerEdit(1, enabled = false)
            )
        )
        assertTrue(MultiColorRasterDigitizer.generateSeparated(
            source, separated, hidden
        ).isFailure)
    }

    @Test fun densityIsIndependentPerLayer() {
        val (source, separated) = palette()
        val dense = MultiColorRasterDigitizer.generateSeparated(
            source, separated,
            MultiColorRasterOptions(
                layers = listOf(
                    ColorLayerEdit(0, rowSpacingMm = 0.4f),
                    ColorLayerEdit(1, rowSpacingMm = 0.9f)
                )
            )
        ).getOrThrow()
        val inverted = MultiColorRasterDigitizer.generateSeparated(
            source, separated,
            MultiColorRasterOptions(
                layers = listOf(
                    ColorLayerEdit(0, rowSpacingMm = 0.9f),
                    ColorLayerEdit(1, rowSpacingMm = 0.4f)
                )
            )
        ).getOrThrow()
        val firstLayerDense = dense.points.count {
            it.command == StitchCommand.STITCH && it.colorIndex == 0
        }
        val firstLayerLight = inverted.points.count {
            it.command == StitchCommand.STITCH && it.colorIndex == 0
        }
        assertTrue("Camada com linhas mais próximas deve ter mais pontos",
            firstLayerDense > firstLayerLight)
    }
}
