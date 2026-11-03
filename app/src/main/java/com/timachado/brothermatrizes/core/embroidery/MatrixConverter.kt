package com.timachado.brothermatrizes.core.embroidery

import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import org.embroideryio.embroideryio.EmbConstant
import org.embroideryio.embroideryio.EmbPattern
import org.embroideryio.embroideryio.EmbThread
import org.embroideryio.embroideryio.EmbroideryIO

/** Perfil de saída PES. O formato da máquina deve permanecer intocado. */
enum class PesExportProfile { BROTHER_MACHINE, PE_DESIGN_PC }

object MatrixConverter {
    /*
     * Brother-first: PES é o formato preferencial do app.
     * DST e JEF continuam disponíveis para interoperabilidade.
     */
    val supportedFormats =
        listOf("PES", "DST", "JEF")

    const val preferredBrotherFormat =
        "PES"

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
        outputSuffix: String = "convertido",
        pesExportProfile: PesExportProfile = PesExportProfile.BROTHER_MACHINE
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

            require(pesExportProfile == PesExportProfile.BROTHER_MACHINE ||
                format == "PES") {
                "PE-Design para computador requer arquivo PES."
            }
            val forPeDesign = format == "PES" &&
                pesExportProfile == PesExportProfile.PE_DESIGN_PC

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
                    design =
                        normalized,
                    positiveOriginForPes = format == "PES" && !forPeDesign
                )
            if (forPeDesign) {
                // Rótulo PEC aceita melhor ASCII curto; o arquivo original não muda.
                pattern.name = brotherSafeFileStem(
                    normalized.label ?: normalized.fileName.substringBeforeLast('.')
                ).take(16)
            }

            val extension =
                format.lowercase(
                    Locale.ROOT
                )

            val rawBaseName =
                normalized.fileName
                    .substringBeforeLast('.')
                    .ifBlank { "matriz" }

            val baseName =
                if (
                    format ==
                        preferredBrotherFormat
                ) {
                    brotherSafeFileStem(
                        rawBaseName
                    )
                } else {
                    rawBaseName
                }

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
                    if (forPeDesign) 6 else 1
                )

                writer.set(
                    "truncated",
                    !forPeDesign
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
                if (format == "PES" && !forPeDesign) {
                    // O PES v1 otimizado para a máquina fica exatamente igual.
                    PesHoopPositionMetadata.write(
                        addBrotherPecOrigin(rawBytes, pattern),
                        normalized
                    )
                } else {
                    // PES PC v6 completo: não aplicar mutações PEC exclusivas da máquina.
                    rawBytes
                }

            if (forPeDesign) {
                validateCompletePcPes(bytes)
            }

            ConvertedMatrix(
                fileName = outputName,
                format = format,
                bytes = bytes
            )
        }

    /**
     * Exportação independente para o PE-DESIGN Windows (pontadas editáveis).
     * Não cria objetos de texto nativos do PE-DESIGN.
     */
    fun convertForPeDesign(design: EmbroideryDesign): Result<ConvertedMatrix> =
        convert(
            design = design,
            targetFormat = "PES",
            outputSuffix = "PE_DESIGN",
            pesExportProfile = PesExportProfile.PE_DESIGN_PC
        )

    private fun validateCompletePcPes(bytes: ByteArray) {
        require(bytes.size > 24) { "PES para PC incompleto." }
        require(bytes.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "#PES0060") {
            "O arquivo de computador precisa ser PES v6 completo."
        }
        val offset = (bytes[8].toInt() and 0xFF) or
            ((bytes[9].toInt() and 0xFF) shl 8) or
            ((bytes[10].toInt() and 0xFF) shl 16) or
            ((bytes[11].toInt() and 0xFF) shl 24)
        require(offset >= 0x16 && offset.toLong() + 512L < bytes.size.toLong()) {
            "O ponteiro PEC do PES de computador é inválido."
        }
    }

    internal fun brotherSafeFileStem(
        value: String
    ): String =
        Normalizer
            .normalize(
                value,
                Normalizer.Form.NFD
            )
            .replace(
                Regex(
                    "\\p{M}+"
                ),
                ""
            )
            .replace(
                Regex(
                    "[^A-Za-z0-9_-]+"
                ),
                "_"
            )
            .trim(
                '_'
            )
            .take(
                48
            )
            .ifBlank {
                "matriz"
            }

    private fun buildOutputPattern(
        design: EmbroideryDesign,
        positiveOriginForPes: Boolean = false
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
         * DST/JEF preservam a origem centralizada usada historicamente
         * pelo app.
         *
         * PEC/PES Brother é diferente: a Innov-is lê as coordenadas
         * das pontadas dentro da área declarada. Se centralizarmos em
         * (0,0), metade da matriz fica negativa e máquinas reais podem
         * cortá-la no canto mesmo com largura/altura corretas.
         *
         * Para PES deslocamos a geometria para 0..largura / 0..altura,
         * sem alterar tamanho, ordem, distâncias ou orientação.
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

        val originX =
            if (
                positiveOriginForPes
            ) {
                design.bounds
                    .minXUnits
                    .toLong()
            } else {
                centerX
            }

        val centeredWriterY =
            writerY(
                design = design,
                yUnits =
                    centerY
                        .toInt()
            )
                .toLong()

        val minimumWriterY =
            if (
                design.sourceYAxisDown
            ) {
                design.bounds
                    .minYUnits
                    .toLong()
            } else {
                -design.bounds
                    .maxYUnits
                    .toLong()
            }

        val originWriterY =
            if (
                positiveOriginForPes
            ) {
                minimumWriterY
            } else {
                centeredWriterY
            }

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
            val outputXLong =
                point.xUnits
                    .toLong() -
                    originX

            val rawWriterY =
                writerY(
                    design = design,
                    yUnits =
                        point.yUnits
                )
                    .toLong()

            val outputYLong =
                rawWriterY -
                    originWriterY

            require(
                outputXLong in
                    Int.MIN_VALUE.toLong()..
                        Int.MAX_VALUE.toLong() &&
                    outputYLong in
                        Int.MIN_VALUE.toLong()..
                            Int.MAX_VALUE.toLong()
            ) {
                "A matriz possui coordenadas fora do intervalo seguro para exportação."
            }

            val centeredX =
                outputXLong
                    .toInt()

            val outputY =
                outputYLong
                    .toInt()

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
