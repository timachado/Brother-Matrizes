package com.timachado.brothermatrizes.core.embroidery

import java.util.Locale

/**
 * Prepara a simulação de uma criação exatamente como uma matriz pronta:
 * primeiro exporta para o formato final escolhido e depois interpreta os
 * bytes gerados pelo mesmo parser usado ao abrir um arquivo externo.
 */
object SimulationMatrixRoundTrip {
    fun prepare(
        design: EmbroideryDesign,
        targetFormat: String
    ): Result<EmbroideryDesign> =
        MatrixConverter
            .convert(
                design =
                    design,
                targetFormat =
                    targetFormat,
                outputSuffix =
                    "simulacao"
            )
            .mapCatching {
                    converted ->
                val parsed =
                    when (
                        converted.format
                            .uppercase(
                                Locale.ROOT
                            )
                    ) {
                        "DST" ->
                            DstParser.parse(
                                fileName =
                                    converted.fileName,
                                bytes =
                                    converted.bytes
                            )

                        "PES",
                        "JEF" ->
                            EmbroideryIoParser
                                .parse(
                                    fileName =
                                        converted.fileName,
                                    bytes =
                                        converted.bytes
                                )

                        else ->
                            EmbroideryLoadResult
                                .Error(
                                    "Formato não suportado para simulação."
                                )
                    }

                when (
                    parsed
                ) {
                    is EmbroideryLoadResult
                        .Success ->
                        parsed.design
                            .copy(
                                // DST não carrega informação de cor de linha e
                                // PES/JEF podem quantizar a cor para a paleta do
                                // formato. Na simulação de uma criação devemos
                                // respeitar exatamente a cor que o usuário
                                // selecionou no editor.
                                threadColors =
                                    if (
                                        design.threadColors
                                            .isNotEmpty()
                                    ) {
                                        design.threadColors
                                    } else {
                                        parsed.design
                                            .threadColors
                                    },
                                label =
                                    design.label
                                        ?: parsed.design
                                            .label,
                                hoopProfile =
                                    design.hoopProfile,
                                fabricProfile =
                                    design.fabricProfile,
                                machineFinishing =
                                    design.machineFinishing
                            )

                    is EmbroideryLoadResult
                        .Error ->
                        throw IllegalArgumentException(
                            buildString {
                                append(
                                    parsed.userMessage
                                )

                                parsed.technicalMessage
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {
                                        append(" ")
                                        append(it)
                                    }
                            }
                        )
                }
            }
}
