package com.timachado.brothermatrizes.font

import kotlin.math.hypot

enum class ImportedGlyphTechnique {
    SATIN_COLUMNS,
    SATIN_COLUMNS_CONSERVATIVE
}

data class ImportedGlyphMetrics(
    val areaPixels: Int,
    val skeletonPixels: Int,
    val branchPixels: Int,
    val widthPixels: Int,
    val heightPixels: Int
) {
    val estimatedStrokeWidthPixels: Float
        get() =
            if (
                skeletonPixels <=
                    0
            ) {
                Float.POSITIVE_INFINITY
            } else {
                areaPixels.toFloat() /
                    skeletonPixels
            }

    val branchRatio: Float
        get() =
            if (
                skeletonPixels <=
                    0
            ) {
                1f
            } else {
                branchPixels.toFloat() /
                    skeletonPixels
            }
}

data class GeneratedStitchQuality(
    val stitchCount: Int,
    val jumpCount: Int,
    val maxStitchLengthUnits: Double,
    val zeroLengthStitches: Int
) {
    val zeroLengthRatio: Float
        get() =
            if (
                stitchCount <=
                    0
            ) {
                1f
            } else {
                zeroLengthStitches.toFloat() /
                    stitchCount
            }
}

/**
 * Política genérica de digitalização para fontes importadas.
 *
 * O modo Satin nunca troca silenciosamente para uma varredura horizontal.
 * Glifos simples usam colunas Satin normais; geometrias largas ou muito
 * ramificadas usam a mesma técnica com parâmetros conservadores.
 */
object AdaptiveFontPolicy {

    fun chooseTechnique(
        metrics: ImportedGlyphMetrics
    ): ImportedGlyphTechnique {
        val stroke =
            metrics
                .estimatedStrokeWidthPixels

        val complexGeometry =
            metrics.areaPixels <=
                0 ||
                metrics.skeletonPixels <
                    5 ||
                metrics.widthPixels <
                    5 ||
                metrics.heightPixels <
                    5 ||
                stroke >
                    24f ||
                metrics.branchRatio >
                    0.18f

        return if (
            complexGeometry
        ) {
            ImportedGlyphTechnique
                .SATIN_COLUMNS_CONSERVATIVE
        } else {
            ImportedGlyphTechnique
                .SATIN_COLUMNS
        }
    }

    fun isAcceptable(
        quality:
            GeneratedStitchQuality
    ): Boolean =
        quality.stitchCount >
            0 &&
            quality.maxStitchLengthUnits <=
                95.0 &&
            quality.zeroLengthRatio <=
                0.04f &&
            quality.jumpCount <=
                quality.stitchCount +
                    40

    fun qualityFromCoordinates(
        commands:
            List<Triple<Int, Int, Boolean>>
    ): GeneratedStitchQuality {
        var stitchCount =
            0

        var jumpCount =
            0

        var maxLength =
            0.0

        var zeroLength =
            0

        var previousX:
            Int? =
            null

        var previousY:
            Int? =
            null

        commands.forEach {
                item ->
            val x =
                item.first

            val y =
                item.second

            val isStitch =
                item.third

            if (
                isStitch
            ) {
                stitchCount++

                if (
                    previousX !=
                        null &&
                    previousY !=
                        null
                ) {
                    val length =
                        hypot(
                            (
                                x -
                                    previousX!!
                                ).toDouble(),
                            (
                                y -
                                    previousY!!
                                ).toDouble()
                        )

                    maxLength =
                        maxOf(
                            maxLength,
                            length
                        )

                    if (
                        length <
                            0.5
                    ) {
                        zeroLength++
                    }
                }
            } else {
                jumpCount++
            }

            previousX =
                x

            previousY =
                y
        }

        return GeneratedStitchQuality(
            stitchCount =
                stitchCount,
            jumpCount =
                jumpCount,
            maxStitchLengthUnits =
                maxLength,
            zeroLengthStitches =
                zeroLength
        )
    }
}
