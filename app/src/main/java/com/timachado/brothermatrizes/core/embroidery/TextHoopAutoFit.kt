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

    /**
     * Fontes TTF/OTF exigem digitalizacao Satin relativamente cara.
     * Recalcula o desenho em no maximo tres geracoes em vez de executar
     * as doze iteracoes do ajuste para fontes internas.
     */
    fun fitImported(
        hoop: HoopProfile,
        requestedHeightMm: Float,
        generator: (Float) -> Result<EmbroideryDesign>
    ): Result<TextHoopAutoFitResult> = runCatching {
        require(requestedHeightMm > 0f) {
            "A altura solicitada deve ser positiva."
        }

        fun frame(design: EmbroideryDesign): Pair<Float, Float> {
            val designLandscape =
                design.bounds.widthMm > design.bounds.heightMm
            val hoopLandscape = hoop.widthMm > hoop.heightMm
            val rotated = hoop.widthMm != hoop.heightMm &&
                designLandscape != hoopLandscape

            return if (rotated) {
                hoop.usableHeightMm to hoop.usableWidthMm
            } else {
                hoop.usableWidthMm to hoop.usableHeightMm
            }
        }

        fun scaleToSafeArea(design: EmbroideryDesign): Float {
            val (width, height) = frame(design)
            return minOf(
                width / design.bounds.widthMm.coerceAtLeast(0.1f),
                height / design.bounds.heightMm.coerceAtLeast(0.1f)
            )
        }

        fun fits(design: EmbroideryDesign): Boolean {
            val (width, height) = frame(design)
            return design.bounds.widthMm <= width &&
                design.bounds.heightMm <= height
        }

        val initial = generator(requestedHeightMm).getOrThrow()
        var resolvedHeight = requestedHeightMm
        var resolvedDesign = initial

        val targetHeight = (
            requestedHeightMm *
                scaleToSafeArea(initial) *
                0.96f
        ).coerceIn(MIN_HEIGHT_MM, MAX_HEIGHT_MM)

        if (kotlin.math.abs(targetHeight - requestedHeightMm) >= 0.1f) {
            resolvedDesign = generator(targetHeight).getOrThrow()
            resolvedHeight = targetHeight
        }

        // Algumas fontes possuem margens de glifos nao lineares.
        // Se a primeira estimativa ultrapassar a area util, refina uma vez.
        if (!fits(resolvedDesign)) {
            val correctedHeight = (
                resolvedHeight *
                    scaleToSafeArea(resolvedDesign) *
                    0.94f
            ).coerceIn(MIN_HEIGHT_MM, MAX_HEIGHT_MM)

            if (correctedHeight < resolvedHeight - 0.1f) {
                resolvedDesign = generator(correctedHeight).getOrThrow()
                resolvedHeight = correctedHeight
            }
        }

        require(fits(resolvedDesign)) {
            "O texto nao cabe na area segura do bastidor " +
                hoop.displayName + "."
        }

        val (safeWidth, safeHeight) = frame(resolvedDesign)
        TextHoopAutoFitResult(
            heightMm = resolvedHeight,
            design = resolvedDesign,
            widthFillRatio = resolvedDesign.bounds.widthMm / safeWidth,
            heightFillRatio = resolvedDesign.bounds.heightMm / safeHeight
        )
    }

    fun dominantFillRatio(
        result: TextHoopAutoFitResult
    ): Float =
        max(
            result.widthFillRatio,
            result.heightFillRatio
        )
}
