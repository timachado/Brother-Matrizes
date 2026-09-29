package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixConverterTest {

    @Test
    fun convertsNormalizedDesignToAllReleasedFormats() {
        val source = sampleDesign()

        for (format in MatrixConverter.supportedFormats) {
            val converted =
                MatrixConverter.convert(
                    source,
                    format
                ).getOrThrow()

            assertEquals(
                format,
                converted.format
            )

            assertTrue(
                converted.bytes.isNotEmpty()
            )

            val parsed =
                when (format) {
                    "DST" ->
                        DstParser.parse(
                            converted.fileName,
                            converted.bytes
                        )

                    "PES",
                    "JEF" ->
                        EmbroideryIoParser.parse(
                            converted.fileName,
                            converted.bytes
                        )

                    else ->
                        error(
                            "Formato inesperado."
                        )
                }

            assertTrue(
                parsed is
                    EmbroideryLoadResult.Success
            )

            val design =
                (parsed as
                    EmbroideryLoadResult.Success)
                    .design

            assertTrue(
                design.stitchCount > 0
            )

            assertTrue(
                design.bounds.widthMm > 0f
            )

            assertTrue(
                design.bounds.heightMm > 0f
            )
        }
    }

    @Test
    fun cartesianDesignKeepsGeometryInDst() {
        assertVisualGeometryPreserved(
            asymmetricDesign(
                sourceYAxisDown = false
            ),
            "DST"
        )
    }

    @Test
    fun cartesianDesignKeepsGeometryInPes() {
        assertVisualGeometryPreserved(
            asymmetricDesign(
                sourceYAxisDown = false
            ),
            "PES"
        )
    }

    @Test
    fun cartesianDesignKeepsGeometryInJef() {
        assertVisualGeometryPreserved(
            asymmetricDesign(
                sourceYAxisDown = false
            ),
            "JEF"
        )
    }

    @Test
    fun importedScreenDesignKeepsGeometryInDst() {
        assertVisualGeometryPreserved(
            asymmetricDesign(
                sourceYAxisDown = true
            ),
            "DST"
        )
    }

    @Test
    fun importedScreenDesignKeepsGeometryInPes() {
        assertVisualGeometryPreserved(
            asymmetricDesign(
                sourceYAxisDown = true
            ),
            "PES"
        )
    }

    @Test
    fun importedScreenDesignKeepsGeometryInJef() {
        assertVisualGeometryPreserved(
            asymmetricDesign(
                sourceYAxisDown = true
            ),
            "JEF"
        )
    }


    @Test
    fun machineFormatsRecenterShiftedDesignWithoutChangingSize() {
        val source =
            shiftedDesign()

        for (
            format in
                MatrixConverter
                    .supportedFormats
        ) {
            val converted =
                MatrixConverter
                    .convert(
                        source,
                        format,
                        outputSuffix =
                            "centrado"
                    )
                    .getOrThrow()

            val parsed =
                when (
                    format
                ) {
                    "DST" ->
                        DstParser
                            .parse(
                                converted.fileName,
                                converted.bytes
                            )

                    "PES",
                    "JEF" ->
                        EmbroideryIoParser
                            .parse(
                                converted.fileName,
                                converted.bytes
                            )

                    else ->
                        error(
                            "Formato inesperado."
                        )
                }

            assertTrue(
                parsed is
                    EmbroideryLoadResult.Success
            )

            val target =
                (
                    parsed as
                        EmbroideryLoadResult.Success
                    ).design

            assertEquals(
                "A largura não pode mudar ao centralizar $format",
                source.bounds.maxXUnits - source.bounds.minXUnits,
                target.bounds.maxXUnits - target.bounds.minXUnits
            )

            assertEquals(
                "A altura não pode mudar ao centralizar $format",
                source.bounds.maxYUnits - source.bounds.minYUnits,
                target.bounds.maxYUnits - target.bounds.minYUnits
            )

            val centerX =
                (
                    target.bounds.minXUnits +
                        target.bounds.maxXUnits
                    ) /
                    2f

            val centerY =
                (
                    target.bounds.minYUnits +
                        target.bounds.maxYUnits
                    ) /
                    2f

            assertTrue(
                "A exportação $format precisa ficar centralizada no eixo X.",
                kotlin.math.abs(
                    centerX
                ) <=
                    1f
            )

            assertTrue(
                "A exportação $format precisa ficar centralizada no eixo Y.",
                kotlin.math.abs(
                    centerY
                ) <=
                    1f
            )
        }
    }


    @Test
    fun machineExportJumpsToFirstNeedlePointBeforeSewing() {
        val source =
            shiftedDesign()

        for (
            format in
                MatrixConverter
                    .supportedFormats
        ) {
            val converted =
                MatrixConverter
                    .convert(
                        source,
                        format,
                        outputSuffix =
                            "primeiro-ponto"
                    )
                    .getOrThrow()

            val parsed =
                when (
                    format
                ) {
                    "DST" ->
                        DstParser
                            .parse(
                                converted.fileName,
                                converted.bytes
                            )

                    "PES",
                    "JEF" ->
                        EmbroideryIoParser
                            .parse(
                                converted.fileName,
                                converted.bytes
                            )

                    else ->
                        error(
                            "Formato inesperado."
                        )
                }

            assertTrue(
                parsed is
                    EmbroideryLoadResult.Success
            )

            val target =
                (
                    parsed as
                        EmbroideryLoadResult.Success
                    ).design

            val firstStitchIndex =
                target.points
                    .indexOfFirst {
                        it.command ==
                            StitchCommand.STITCH
                    }

            assertTrue(
                "A exportação $format precisa conter ponto de costura.",
                firstStitchIndex >=
                    1
            )

            val jumpBeforeFirstStitch =
                target.points
                    .subList(
                        0,
                        firstStitchIndex
                    )
                    .lastOrNull {
                        it.command ==
                            StitchCommand.JUMP
                    }

            assertTrue(
                "A exportação $format deve posicionar a agulha com JUMP antes do primeiro STITCH.",
                jumpBeforeFirstStitch !=
                    null
            )

            val firstStitch =
                target.points[
                    firstStitchIndex
                ]

            assertEquals(
                "O primeiro STITCH em $format deve começar exatamente onde terminou o JUMP.",
                jumpBeforeFirstStitch
                    ?.xUnits,
                firstStitch.xUnits
            )

            assertEquals(
                "O primeiro STITCH em $format deve começar exatamente onde terminou o JUMP.",
                jumpBeforeFirstStitch
                    ?.yUnits,
                firstStitch.yUnits
            )
        }
    }

    private fun assertVisualGeometryPreserved(
        source: EmbroideryDesign,
        format: String
    ) {
        val sourceStitches =
            source.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        val converted =
            MatrixConverter.convert(
                source,
                format,
                outputSuffix =
                    "orientacao"
            ).getOrThrow()

        val parsed =
            when (format) {
                "DST" ->
                    DstParser.parse(
                        converted.fileName,
                        converted.bytes
                    )

                "PES",
                "JEF" ->
                    EmbroideryIoParser.parse(
                        converted.fileName,
                        converted.bytes
                    )

                else ->
                    error(
                        "Formato inesperado."
                    )
            }

        assertTrue(
            parsed is
                EmbroideryLoadResult.Success
        )

        val target =
            (parsed as
                EmbroideryLoadResult.Success)
                .design

        val targetStitches =
            target.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertEquals(
            "Quantidade de pontos mudou em $format",
            sourceStitches.size,
            targetStitches.size
        )

        assertEquals(
            "A geometria foi refletida/espelhada ao gerar $format",
            normalizedVisualPoints(
                design = source,
                points = sourceStitches
            ),
            normalizedVisualPoints(
                design = target,
                points = targetStitches
            )
        )
    }

    private fun normalizedVisualPoints(
        design: EmbroideryDesign,
        points: List<EmbroideryPoint>
    ): List<Pair<Int, Int>> {
        if (points.isEmpty()) {
            return emptyList()
        }

        val minX =
            points.minOf {
                it.xUnits
            }

        val visualYs =
            points.map {
                visualY(
                    design,
                    it.yUnits
                )
            }

        val minVisualY =
            visualYs.min()

        return points
            .mapIndexed {
                    index,
                    point ->
                Pair(
                    point.xUnits -
                        minX,
                    visualYs[index] -
                        minVisualY
                )
            }
            .sortedWith(
                compareBy<Pair<Int, Int>> {
                    it.first
                }.thenBy {
                    it.second
                }
            )
    }

    private fun visualY(
        design: EmbroideryDesign,
        yUnits: Int
    ): Int =
        if (
            design.sourceYAxisDown
        ) {
            yUnits
        } else {
            -yUnits
        }

    private fun asymmetricDesign(
        sourceYAxisDown: Boolean
    ): EmbroideryDesign {
        val points =
            listOf(
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    30,
                    90,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    90,
                    -20,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    20,
                    40,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    20,
                    40,
                    StitchCommand.END,
                    0
                )
            )

        return EmbroideryDesign(
            fileName =
                if (
                    sourceYAxisDown
                ) {
                    "importada.pes"
                } else {
                    "criada-no-brother_matrizes.dst"
                },
            format =
                if (
                    sourceYAxisDown
                ) {
                    "PES"
                } else {
                    "DST"
                },
            label = "ORIENTACAO",
            points = points,
            bounds =
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits = 90,
                    minYUnits = -20,
                    maxYUnits = 90
                ),
            stitchCount = 4,
            jumpCount = 0,
            colorChanges = 0,
            endFound = true,
            sourceBytes =
                ByteArray(0),
            threadColors =
                listOf(
                    0xE6BE70
                ),
            sourceYAxisDown =
                sourceYAxisDown
        )
    }

    private fun sampleDesign(): EmbroideryDesign {
        val points =
            listOf(
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    120,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    120,
                    80,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    0,
                    80,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.COLOR_CHANGE,
                    1
                ),
                EmbroideryPoint(
                    60,
                    40,
                    StitchCommand.JUMP,
                    1
                ),
                EmbroideryPoint(
                    80,
                    60,
                    StitchCommand.STITCH,
                    1
                ),
                EmbroideryPoint(
                    80,
                    60,
                    StitchCommand.END,
                    1
                )
            )

        return EmbroideryDesign(
            fileName = "teste.dst",
            format = "DST",
            label = "FIOLAB",
            points = points,
            bounds =
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits = 120,
                    minYUnits = 0,
                    maxYUnits = 80
                ),
            stitchCount = 6,
            jumpCount = 1,
            colorChanges = 1,
            endFound = true,
            sourceBytes =
                ByteArray(0),
            threadColors =
                listOf(
                    0xE6BE70,
                    0x2A9D8F
                )
        )
    }

    private fun shiftedDesign():
        EmbroideryDesign {
        val points =
            listOf(
                EmbroideryPoint(
                    1000,
                    2000,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    1200,
                    2000,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    1200,
                    2300,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    1000,
                    2300,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    1000,
                    2000,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    1000,
                    2000,
                    StitchCommand.END,
                    0
                )
            )

        return EmbroideryDesign(
            fileName =
                "deslocada.pes",
            format =
                "PES",
            label =
                "CENTRO",
            points =
                points,
            bounds =
                EmbroideryBounds(
                    minXUnits =
                        1000,
                    maxXUnits =
                        1200,
                    minYUnits =
                        2000,
                    maxYUnits =
                        2300
                ),
            stitchCount =
                5,
            jumpCount =
                0,
            colorChanges =
                0,
            endFound =
                true,
            sourceBytes =
                ByteArray(
                    0
                ),
            threadColors =
                listOf(
                    0xE6BE70
                ),
            sourceYAxisDown =
                true
        )
    }

}
