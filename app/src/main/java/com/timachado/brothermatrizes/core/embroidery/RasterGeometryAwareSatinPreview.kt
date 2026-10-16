package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt

/**
 * Conservative image-to-stitches QA route.
 *
 * Never decompose a complex glyph/logo into arbitrary horizontal raster bands
 * or fixed vertical zigzags. A color is accepted only if the existing
 * shape-aware straight/curved Satin generators can construct continuous rails.
 * If any color contains holes, crossings or branches they cannot describe,
 * reject the ENTIRE preview rather than display an inaccurate simulation.
 *
 * Read-only, not calibrated or approved for machine embroidery.
 */
internal object RasterGeometryAwareSatinPreview {
    private const val DENSITY_MM = 0.5f
    private const val COMPENSATION_MM = 0.15f

    fun create(
        source: RasterImagePixels,
        widthMm: Float,
        hoop: HoopProfile
    ): Result<EmbroideryDesign> = runCatching {
        require(source.width in 8..512 && source.height in 8..512 &&
            widthMm.isFinite() && widthMm in 10f..200f) {
            "Imagem ou dimensão incompatível."
        }
        val totalHeightMm = widthMm * source.height / source.width.toFloat()
        require(widthMm <= hoop.usableWidthMm &&
            totalHeightMm <= hoop.usableHeightMm) {
            "A arte não cabe na área útil do bastidor."
        }

        val base = MultiColorRasterOptions(
            widthMm = widthMm, maxColors = 2, hoopProfile = hoop,
            preserveFineDetails = true
        )
        val art = MultiColorRasterDigitizer.separate(source, base).getOrThrow()

        // Preserve the scale and placement of the IMAGE displayed in the hoop,
        // even if the visible ink does not occupy the entire input bitmap.
        val mmPerPixel = widthMm / source.width.toFloat()
        val inkWidth = art.maxX - art.minX + 1
        val inkWidthMm = inkWidth * mmPerPixel
        require(inkWidthMm >= 10f) {
            "Esta parte é pequena para gerar pontos confiáveis. Aumente a arte."
        }
        val options = base.copy(widthMm = inkWidthMm)

        val layers = art.colors.indices.map { color ->
            val curve = RasterCurvedSatin.generate(
                source = source, art = art,
                sourceColorIndex = color, outputColorIndex = color,
                widthMm = inkWidthMm, densityMm = DENSITY_MM,
                compensationMm = COMPENSATION_MM, centerUnderlay = false
            )
            val mode = if (curve.isSuccess) {
                RasterLayerStitchMode.SATIN_CURVED
            } else {
                val straight = RasterStraightSatin.generate(
                    source = source, art = art,
                    sourceColorIndex = color, outputColorIndex = color,
                    widthMm = inkWidthMm, densityMm = DENSITY_MM,
                    compensationMm = COMPENSATION_MM, centerUnderlay = false,
                    verticalNeedleDirectionOnly = false
                )
                require(straight.isSuccess) {
                    "Esta imagem contém letras, curvas fechadas ou ramificações " +
                        "que exigem trilhos Satin editáveis. " +
                        "O desenho NÃO corresponde a uma matriz automática segura. " +
                        "Selecione um elemento simples ou revise os contornos."
                }
                RasterLayerStitchMode.SATIN_STRAIGHT
            }
            ColorLayerEdit(
                sourceIndex = color, stitchMode = mode,
                satinCenterUnderlay = false
            )
        }
        // Crucially: NO FILL/Tatami or adaptive vertical raster fallback.
        val result = MultiColorRasterDigitizer.generateSeparated(
            source, art, options.copy(layers = layers)
        ).getOrThrow()

        val unitsPerPixel = mmPerPixel * 10f
        val centerInkX = (art.minX + art.maxX + 1) / 2f
        val centerInkY = (art.minY + art.maxY + 1) / 2f
        val dx = ((centerInkX - source.width / 2f) * unitsPerPixel).roundToInt()
        val dy = ((source.height / 2f - centerInkY) * unitsPerPixel).roundToInt()
        val b = result.bounds
        result.copy(
            fileName = "SATIN-REVISAO-SEM-EXPORTACAO.qa",
            format = "QA_SATIN_REVIEW",
            label = "Satin por contornos — revisão obrigatória",
            points = result.points.map {
                it.copy(xUnits = it.xUnits + dx, yUnits = it.yUnits + dy)
            },
            bounds = b.copy(
                minXUnits = b.minXUnits + dx, maxXUnits = b.maxXUnits + dx,
                minYUnits = b.minYUnits + dy, maxYUnits = b.maxYUnits + dy
            ),
            sourceBytes = ByteArray(0),
            isModified = true,
            hoopProfile = hoop
        )
    }
}
