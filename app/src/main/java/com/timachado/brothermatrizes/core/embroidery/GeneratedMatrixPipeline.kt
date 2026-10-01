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
    fun canonicalize(
        design: EmbroideryDesign,
        outputSuffix: String
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
                EmbroideryLoadResult
                    .Success(
                        opened.design
                            .copy(
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
