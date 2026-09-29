package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.floor
import kotlin.math.max

data class TextHoopAutoFitResult(
    val heightMm: Float,
    val design: EmbroideryDesign,
    val widthFillRatio: Float,
    val heightFillRatio: Float
)

object TextHoopAutoFit {
    const val MIN_HEIGHT_MM =
        4f

    const val MAX_HEIGHT_MM =
        60f

    private const val TARGET_FILL =
        0.98f

    private const val ITERATIONS =
        12

    fun fit(
        hoop: HoopProfile,
        minHeightMm: Float =
            MIN_HEIGHT_MM,
        maxHeightMm: Float =
            MAX_HEIGHT_MM,
        generator:
            (Float) ->
                Result<EmbroideryDesign>
    ): Result<TextHoopAutoFitResult> =
        runCatching {
            require(
                minHeightMm > 0f &&
                    maxHeightMm >=
                        minHeightMm
            ) {
                "Intervalo de altura inválido."
            }

            val targetWidth =
                hoop.usableWidthMm *
                    TARGET_FILL

            val targetHeight =
                hoop.usableHeightMm *
                    TARGET_FILL

            fun fitsSafe(
                design: EmbroideryDesign
            ): Boolean =
                HoopValidator
                    .validate(
                        design,
                        hoop
                    )
                    .fits

            fun fitsTarget(
                design: EmbroideryDesign
            ): Boolean =
                design.bounds
                    .widthMm <=
                    targetWidth &&
                    design.bounds
                        .heightMm <=
                    targetHeight

            fun result(
                height: Float,
                design: EmbroideryDesign
            ): TextHoopAutoFitResult =
                TextHoopAutoFitResult(
                    heightMm =
                        height,
                    design =
                        design,
                    widthFillRatio =
                        design.bounds
                            .widthMm /
                            hoop.usableWidthMm,
                    heightFillRatio =
                        design.bounds
                            .heightMm /
                            hoop.usableHeightMm
                )

            val minimum =
                generator(
                    minHeightMm
                ).getOrThrow()

            require(
                fitsSafe(
                    minimum
                )
            ) {
                "O texto não cabe na área segura do bastidor " +
                    hoop.displayName +
                    " nem no tamanho mínimo."
            }

            if (
                !fitsTarget(
                    minimum
                )
            ) {
                return@runCatching result(
                    minHeightMm,
                    minimum
                )
            }

            val maximum =
                generator(
                    maxHeightMm
                ).getOrNull()

            if (
                maximum !=
                    null &&
                fitsTarget(
                    maximum
                )
            ) {
                return@runCatching result(
                    maxHeightMm,
                    maximum
                )
            }

            var low =
                minHeightMm

            var high =
                maxHeightMm

            var bestHeight =
                minHeightMm

            var bestDesign =
                minimum

            repeat(
                ITERATIONS
            ) {
                val candidateHeight =
                    (
                        low +
                            high
                        ) /
                        2f

                val candidate =
                    generator(
                        candidateHeight
                    ).getOrNull()

                if (
                    candidate !=
                        null &&
                    fitsTarget(
                        candidate
                    )
                ) {
                    bestHeight =
                        candidateHeight

                    bestDesign =
                        candidate

                    low =
                        candidateHeight
                } else {
                    high =
                        candidateHeight
                }
            }

            val roundedDown =
                (
                    floor(
                        bestHeight *
                            10f
                    ) /
                        10f
                    ).coerceIn(
                    minHeightMm,
                    maxHeightMm
                )

            val roundedDesign =
                if (
                    roundedDown <
                        bestHeight -
                            0.001f
                ) {
                    generator(
                        roundedDown
                    ).getOrNull()
                } else {
                    null
                }

            if (
                roundedDesign !=
                    null &&
                fitsSafe(
                    roundedDesign
                )
            ) {
                result(
                    roundedDown,
                    roundedDesign
                )
            } else {
                result(
                    bestHeight,
                    bestDesign
                )
            }
        }

    fun dominantFillRatio(
        result: TextHoopAutoFitResult
    ): Float =
        max(
            result.widthFillRatio,
            result.heightFillRatio
        )
}
