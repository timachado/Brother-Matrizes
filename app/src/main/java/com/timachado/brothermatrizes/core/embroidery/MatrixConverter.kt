package com.timachado.brothermatrizes.core.embroidery

import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import org.embroideryio.embroideryio.EmbConstant
import org.embroideryio.embroideryio.EmbPattern
import org.embroideryio.embroideryio.EmbThread
import org.embroideryio.embroideryio.EmbroideryIO

object MatrixConverter {
    val supportedFormats =
        listOf("DST", "PES", "JEF")

    private val fallbackColors =
        listOf(
            0xE6BE70,
            0xE76F51,
            0x2A9D8F,
            0x264653,
            0x9B5DE5,
            0xF4A261,
            0x457B9D,
            0xE63946
        )

    private const val SAFE_DELTA_UNITS = 120

    fun convert(
        design: EmbroideryDesign,
        targetFormat: String,
        outputSuffix: String = "convertido"
    ): Result<ConvertedMatrix> =
        runCatching {
            val format =
                targetFormat.uppercase(
                    Locale.ROOT
                )

            require(
                format in supportedFormats
            ) {
                "Formato de saída ainda não suportado."
            }

            val normalized =
                EmbroideryIntegrity
                    .normalize(
                        design
                    )
                    .getOrElse {
                            error ->
                        throw IllegalArgumentException(
                            "A matriz não passou na validação antes da conversão: " +
                                (
                                    error.message
                                        ?: "integridade inválida"
                                    ),
                            error
                        )
                    }
                    .design

            val pattern =
                buildOutputPattern(
                    normalized
                )

            val extension =
                format.lowercase(
                    Locale.ROOT
                )

            val baseName =
                normalized.fileName
                    .substringBeforeLast('.')
                    .ifBlank { "matriz" }

            val suffix =
                outputSuffix
                    .replace(
                        Regex("[^A-Za-z0-9_-]"),
                        "_"
                    )
                    .ifBlank {
                        "convertido"
                    }

            val outputName =
                baseName +
                    "-" +
                    suffix +
                    "." +
                    extension

            val writer =
                EmbroideryIO
                    .getWriterByFilename(
                        outputName
                    )
                    ?: error(
                        "Writer indisponível para $format."
                    )

            if (format == "PES") {
                /*
                 * Compatibilidade Brother/Innov-is:
                 *
                 * O PES v6 completo do EmbroideryIO inclui blocos CEmbOne/CSewSeg
                 * com uma transformação visual própria. Em máquinas Brother esses
                 * metadados podem deslocar a prévia para fora do bastidor, mesmo
                 * quando os pontos PEC estão corretamente centralizados.
                 *
                 * Para arquivos gerados pelo app usamos o contêiner PES v1
                 * truncado: mantém os pontos/cores PEC que a máquina borda e
                 * elimina o bloco visual problemático. DST e JEF não mudam.
                 */
                writer.set(
                    "pes version",
                    1
                )

                writer.set(
                    "truncated",
                    true
                )
            }

            val output =
                ByteArrayOutputStream()

            writer.write(
                pattern,
                output
            )

            val rawBytes =
                output.toByteArray()

            require(
                rawBytes.isNotEmpty()
            ) {
                "A conversão não gerou dados."
            }

            val bytes =
                if (
                    format == "PES"
                ) {
                    addBrotherPecOrigin(
                        rawBytes,
                        pattern
                    )
                } else {
                    rawBytes
                }

            ConvertedMatrix(
                fileName = outputName,
                format = format,
                bytes = bytes
            )
        }

    private fun buildOutputPattern(
        design: EmbroideryDesign
    ): EmbPattern {
        val pattern =
            EmbPattern().apply {
                name =
                    design.label
                        ?: design.fileName
                            .substringBeforeLast('.')
            }

        addThreads(
            pattern,
            design
        )

        /*
         * Formatos de máquina trabalham melhor com a origem do desenho
         * centralizada no bastidor. Preservamos tamanho, sequência e
         * distâncias; somente removemos o offset absoluto acumulado.
         */
        val centerX =
            (
                design.bounds.minXUnits
                    .toLong() +
                    design.bounds.maxXUnits
                        .toLong()
                ) /
                2L

        val centerY =
            (
                design.bounds.minYUnits
                    .toLong() +
                    design.bounds.maxYUnits
                        .toLong()
                ) /
                2L

        var currentX = 0
        var currentY = 0

        /*
         * O primeiro ponto de costura não pode ser usado como um
         * deslocamento costurado desde a origem do bastidor. A máquina
         * precisa primeiro posicionar a agulha com JUMP e só então
         * iniciar a perfuração naquele ponto.
         */
        var machinePositionInitialized =
            false

        design.points.forEach { point ->
            val centeredXLong =
                point.xUnits
                    .toLong() -
                    centerX

            val centeredYLong =
                point.yUnits
                    .toLong() -
                    centerY

            require(
                centeredXLong in
                    Int.MIN_VALUE.toLong()..
                        Int.MAX_VALUE.toLong() &&
                    centeredYLong in
                        Int.MIN_VALUE.toLong()..
                            Int.MAX_VALUE.toLong()
            ) {
                "A matriz possui coordenadas fora do intervalo seguro para centralização."
            }

            val centeredX =
                centeredXLong
                    .toInt()

            val centeredY =
                centeredYLong
                    .toInt()

            val outputY =
                writerY(
                    design = design,
                    yUnits =
                        centeredY
                )

            when (point.command) {
                StitchCommand.STITCH -> {
                    if (
                        !machinePositionInitialized
                    ) {
                        addSegmentedMove(
                            pattern = pattern,
                            fromX = currentX,
                            fromY = currentY,
                            toX = centeredX,
                            toY = outputY,
                            command =
                                EmbConstant.JUMP
                        )

                        pattern.addStitchAbs(
                            centeredX.toFloat(),
                            outputY.toFloat(),
                            EmbConstant.STITCH
                        )

                        machinePositionInitialized =
                            true
                    } else {
                        addSegmentedMove(
                            pattern = pattern,
                            fromX = currentX,
                            fromY = currentY,
                            toX = centeredX,
                            toY = outputY,
                            command =
                                EmbConstant.STITCH
                        )
                    }
                }

                StitchCommand.JUMP -> {
                    addSegmentedMove(
                        pattern = pattern,
                        fromX = currentX,
                        fromY = currentY,
                        toX = centeredX,
                        toY = outputY,
                        command =
                            EmbConstant.JUMP
                    )

                    machinePositionInitialized =
                        true
                }

                StitchCommand.TRIM -> {
                    pattern.addStitchAbs(
                        centeredX.toFloat(),
                        outputY.toFloat(),
                        EmbConstant.TRIM
                    )
                }

                StitchCommand.STOP -> {
                    pattern.addStitchAbs(
                        centeredX.toFloat(),
                        outputY.toFloat(),
                        EmbConstant.COLOR_CHANGE
                    )
                }

                StitchCommand.COLOR_CHANGE -> {
                    pattern.addStitchAbs(
                        centeredX.toFloat(),
                        outputY.toFloat(),
                        EmbConstant.COLOR_CHANGE
                    )
                }

                StitchCommand.SEQUIN -> {
                    if (
                        !machinePositionInitialized
                    ) {
                        addSegmentedMove(
                            pattern = pattern,
                            fromX = currentX,
                            fromY = currentY,
                            toX = centeredX,
                            toY = outputY,
                            command =
                                EmbConstant.JUMP
                        )

                        machinePositionInitialized =
                            true
                    }

                    pattern.addStitchAbs(
                        centeredX.toFloat(),
                        outputY.toFloat(),
                        EmbConstant.STITCH
                    )
                }

                StitchCommand.END -> {
                    pattern.addStitchAbs(
                        centeredX.toFloat(),
                        outputY.toFloat(),
                        EmbConstant.END
                    )
                }
            }

            currentX = centeredX
            currentY = outputY
        }

        if (!design.endFound) {
            pattern.end()
        }

        pattern.fixColorCount()

        return pattern
    }

    /*
     * A Innov-is usa os dois words de origem do segundo bloco PEC para
     * deslocar as coordenadas da matriz para dentro da área declarada.
     *
     * O EmbroideryIO 0.1.23 escreve largura/altura e começa imediatamente
     * as pontadas, omitindo esses 4 bytes. Como este app centraliza a
     * geometria em torno de (0,0), isso deixa coordenadas negativas e a
     * máquina corta o desenho no canto.
     *
     * Inserimos:
     *   u16BE 0x9000 | distanceLeft
     *   u16BE 0x9000 | distanceUp
     * e corrigimos o tamanho u24LE do stitch block.
     */
    private fun addBrotherPecOrigin(
        raw: ByteArray,
        pattern: EmbPattern
    ): ByteArray {
        require(
            raw.size >
                554
        ) {
            "PES gerado ficou pequeno demais para conter o bloco PEC."
        }

        val signature =
            raw.copyOfRange(
                0,
                8
            ).toString(
                Charsets.US_ASCII
            )

        require(
            signature ==
                "#PES0001"
        ) {
            "PES Brother esperado na versão 1."
        }

        val pecOffset =
            (
                raw[8].toInt() and
                    0xFF
                ) or
                (
                    (
                        raw[9].toInt() and
                            0xFF
                        ) shl
                        8
                    ) or
                (
                    (
                        raw[10].toInt() and
                            0xFF
                        ) shl
                        16
                    ) or
                (
                    (
                        raw[11].toInt() and
                            0xFF
                        ) shl
                        24
                    )

        val blockStart =
            pecOffset +
                512

        require(
            blockStart +
                20 <=
                raw.size
        ) {
            "Bloco PEC inválido."
        }

        require(
            raw[blockStart + 5]
                .toInt() and
                0xFF ==
                0x31 &&
                raw[blockStart + 6]
                    .toInt() and
                    0xFF ==
                    0xFF &&
                raw[blockStart + 7]
                    .toInt() and
                    0xFF ==
                    0xF0
        ) {
            "Assinatura interna PEC inválida."
        }

        val oldBlockLength =
            (
                raw[blockStart + 2]
                    .toInt() and
                    0xFF
                ) or
                (
                    (
                        raw[blockStart + 3]
                            .toInt() and
                            0xFF
                        ) shl
                        8
                    ) or
                (
                    (
                        raw[blockStart + 4]
                            .toInt() and
                            0xFF
                        ) shl
                        16
                    )

        val distanceLeft =
            kotlin.math
                .round(
                    -pattern
                        .getMinX()
                        .toDouble()
                )
                .toInt()

        val distanceUp =
            kotlin.math
                .round(
                    -pattern
                        .getMinY()
                        .toDouble()
                )
                .toInt()

        require(
            distanceLeft in
                0..0x0FFF &&
                distanceUp in
                    0..0x0FFF
        ) {
            "A origem PEC ficou fora do intervalo de 12 bits suportado pela máquina."
        }

        val insertAt =
            blockStart +
                16

        val result =
            ByteArray(
                raw.size +
                    4
            )

        raw.copyInto(
            result,
            destinationOffset =
                0,
            startIndex =
                0,
            endIndex =
                insertAt
        )

        writePecOriginWord(
            result,
            insertAt,
            distanceLeft
        )

        writePecOriginWord(
            result,
            insertAt + 2,
            distanceUp
        )

        raw.copyInto(
            result,
            destinationOffset =
                insertAt +
                    4,
            startIndex =
                insertAt,
            endIndex =
                raw.size
        )

        val newBlockLength =
            oldBlockLength +
                4

        result[blockStart + 2] =
            (
                newBlockLength and
                    0xFF
                ).toByte()

        result[blockStart + 3] =
            (
                (
                    newBlockLength shr
                        8
                    ) and
                    0xFF
                ).toByte()

        result[blockStart + 4] =
            (
                (
                    newBlockLength shr
                        16
                    ) and
                    0xFF
                ).toByte()

        return result
    }

    private fun writePecOriginWord(
        target: ByteArray,
        offset: Int,
        distance: Int
    ) {
        val word =
            0x9000 or
                (
                    distance and
                        0x0FFF
                    )

        target[offset] =
            (
                (
                    word shr
                        8
                    ) and
                    0xFF
                ).toByte()

        target[offset + 1] =
            (
                word and
                    0xFF
                ).toByte()
    }

    private fun writerY(
        design: EmbroideryDesign,
        yUnits: Int
    ): Int {
        if (design.sourceYAxisDown) {
            return yUnits
        }

        val inverted =
            -yUnits.toLong()

        require(
            inverted in
                Int.MIN_VALUE.toLong()..
                    Int.MAX_VALUE.toLong()
        ) {
            "A matriz possui coordenada vertical fora do intervalo seguro para exportação."
        }

        return inverted.toInt()
    }

    private fun addThreads(
        pattern: EmbPattern,
        design: EmbroideryDesign
    ) {
        val wantedColors =
            max(
                design.colorCount,
                1
            )

        repeat(
            wantedColors
        ) { index ->
            val color =
                design.threadColors
                    .getOrNull(index)
                    ?: fallbackColors[
                        index %
                            fallbackColors.size
                    ]

            pattern.addThread(
                EmbThread().apply {
                    setColor(color)
                }
            )
        }
    }

    private fun addSegmentedMove(
        pattern: EmbPattern,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        command: Int
    ) {
        val dx =
            toX.toLong() -
                fromX.toLong()

        val dy =
            toY.toLong() -
                fromY.toLong()

        val greatestDelta =
            max(
                abs(
                    dx
                ),
                abs(
                    dy
                )
            )

        val segmentsLong =
            max(
                1L,
                ceil(
                    greatestDelta.toDouble() /
                        SAFE_DELTA_UNITS
                ).toLong()
            )

        require(
            segmentsLong <=
                EmbroideryStressPolicy
                    .MAX_SEGMENTS_PER_MOVE
        ) {
            "A matriz possui um deslocamento extremo que não pode ser convertido com segurança."
        }

        val segments =
            segmentsLong
                .toInt()

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toDouble() /
                    segments

            val x =
                (
                    fromX.toDouble() +
                        dx.toDouble() *
                            ratio
                    ).roundToInt()

            val y =
                (
                    fromY.toDouble() +
                        dy.toDouble() *
                            ratio
                    ).roundToInt()

            pattern.addStitchAbs(
                x.toFloat(),
                y.toFloat(),
                command
            )
        }
    }
}
