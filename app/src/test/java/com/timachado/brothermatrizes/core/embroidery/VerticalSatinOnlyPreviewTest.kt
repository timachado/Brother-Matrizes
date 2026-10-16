package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA must NEVER silently return to horizontal raster fill or export. */
class VerticalSatinOnlyPreviewTest {
    private fun coloredBar(horizontal: Boolean): RasterImagePixels {
        val w = 110
        val h = 90
        val pixels = IntArray(w * h) { 0 }
        if (horizontal) {
            for (y in 34..36) for (x in 20..82) {
                pixels[y * w + x] = 0xFFE28B20.toInt()
            }
        } else {
            for (y in 15..69) for (x in 45..47) {
                pixels[y * w + x] = 0xFFE28B20.toInt()
            }
        }
        return RasterImagePixels(w, h, pixels, "QA Satin")
    }

    private fun satinOptions() = MultiColorRasterOptions(
        widthMm = 60f,
        maxColors = 2,
        verticalSatinPreviewOnly = true
    )

    private fun create(image: RasterImagePixels): Result<EmbroideryDesign> {
        val options = satinOptions()
        return MultiColorRasterDigitizer.separate(image, options).fold(
            onSuccess = { art ->
                MultiColorRasterDigitizer.generateSeparated(
                    image, art,
                    options.copy(
                        layers = art.colors.indices.map {
                            ColorLayerEdit(
                                sourceIndex = it,
                                stitchMode = RasterLayerStitchMode.SATIN_STRAIGHT,
                                satinCenterUnderlay = false
                            )
                        }
                    )
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    @Test fun thinHorizontalBarGeneratesVerticalNeedleCrossingsForReadOnlyPreview() {
        val design = create(coloredBar(horizontal = true)).getOrThrow()
        assertTrue(design.stitchCount > 5)
        val stitches = design.points.filter { it.command == StitchCommand.STITCH }
        var vertical = 0
        for (i in 1 until stitches.size) {
            val p = stitches[i]
            val prev = stitches[i - 1]
            if (kotlin.math.abs(p.yUnits - prev.yUnits) >
                kotlin.math.abs(p.xUnits - prev.xUnits)) vertical++
        }
        assertTrue("Vertical crossings should be predominant", vertical > stitches.size / 2)
    }

    @Test fun verticalBarMustNotFallbackToHorizontalStitches() {
        val failure = create(coloredBar(horizontal = false))
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("Satin"))
    }

    @Test fun oldHorizontalRasterFillCannotPassStrictPreviewMode() {
        val img = coloredBar(horizontal = true)
        val options = satinOptions()
        val art = MultiColorRasterDigitizer.separate(img, options).getOrThrow()
        assertTrue(MultiColorRasterDigitizer.generateSeparated(
            img, art, options
        ).isFailure)
    }

    @Test fun failedPreviewDoesNotPermitExport() {
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
