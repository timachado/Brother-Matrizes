package com.timachado.brothermatrizes.core.embroidery

/**
 * Transforma uma criação interna em uma matriz real e a reabre pelo mesmo
 * pipeline usado em "Abrir Matriz".
 *
 * Assim, nomes criados com fontes internas ou importadas TTF/OTF passam a
 * trabalhar com a mesma sequência canônica de STITCH/JUMP/TRIM/COLOR_CHANGE
 * que o Viewer/Simulator recebem ao abrir um arquivo externo.
 */
object GeneratedMatrixPipeline {
    internal fun collapseLeadingPositioningJumps(
        points: List<EmbroideryPoint>
    ): List<EmbroideryPoint> {
        val firstStitchIndex =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.STITCH
            }

        if (
            firstStitchIndex <=
                1
        ) {
            return points
        }

        val leading =
            points.take(
                firstStitchIndex
            )

        if (
            leading.any {
                it.command !=
                    StitchCommand.JUMP
            }
        ) {
            return points
        }

        val finalPositioningJump =
            leading.last()

        return buildList(
            1 +
                points.size -
                firstStitchIndex
        ) {
            add(
                finalPositioningJump
            )

            addAll(
                points.drop(
                    firstStitchIndex
                )
            )
        }
    }

    fun canonicalize(
        design: EmbroideryDesign,
        outputSuffix: String,
        preserveGuidePoints: Boolean =
            false
    ): EmbroideryLoadResult {
        val converted =
            MatrixConverter
                .convert(
                    design =
                        design,
                    targetFormat =
                        design.format,
                    outputSuffix =
                        outputSuffix
                )
                .getOrElse {
                        error ->
                    return EmbroideryLoadResult
                        .Error(
                            "Não foi possível preparar a matriz gerada.",
                            error.message
                        )
                }

        val opened =
            EmbroideryLoader
                .loadBytes(
                    displayName =
                        converted.fileName,
                    bytes =
                        converted.bytes
                )

        return when (
            opened
        ) {
            is EmbroideryLoadResult
                .Success -> {
                val normalizedPoints =
                    collapseLeadingPositioningJumps(
                        opened.design.points
                    )

                EmbroideryLoadResult
                    .Success(
                        opened.design
                            .copy(
                                points =
                                    normalizedPoints,
                                jumpCount =
                                    normalizedPoints.count {
                                        it.command ==
                                            StitchCommand.JUMP
                                    },
                                // Alguns formatos não preservam a cor escolhida
                                // pelo usuário (DST, por exemplo).
                                threadColors =
                                    if (
                                        design.threadColors
                                            .isNotEmpty()
                                    ) {
                                        design.threadColors
                                    } else {
                                        opened.design
                                            .threadColors
                                    },
                                hoopProfile =
                                    design.hoopProfile,
                                fabricProfile =
                                    design.fabricProfile,
                                machineFinishing =
                                    design.machineFinishing,
                                guidePoints =
                                    if (
                                        preserveGuidePoints &&
                                        design.guidePoints
                                            .isNotEmpty()
                                    ) {
                                        design.guidePoints
                                    } else {
                                        opened.design
                                            .guidePoints
                                    },
                                label =
                                    design.label
                                        ?: opened.design
                                            .label
                            )
                    )
            }

            is EmbroideryLoadResult
                .Error ->
                opened
        }
    }
}
