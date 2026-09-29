package com.timachado.brothermatrizes.core.embroidery

import com.timachado.brothermatrizes.core.project.ProjectCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixStressTest {
    @Test
    fun maximumBuiltInNameStillGeneratesWithinSafeBudget() {
        val text =
            "ABCDEFGHIJKLMNOPQRSTUVWX"

        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text =
                            text,
                        heightMm =
                            40f,
                        spacingMm =
                            8f,
                        style =
                            TextStitchStyle.SATIN,
                        satinWidthMm =
                            6f,
                        satinDensityMm =
                            0.3f,
                        satinPullCompensationMm =
                            1f,
                        satinUnderlayMode =
                            SatinUnderlayMode.BOTH,
                        outputFormat =
                            "DST"
                    )
                )
                .getOrThrow()

        assertTrue(
            design.stitchCount >
                0
        )

        assertTrue(
            design.points
                .size <=
                EmbroideryStressPolicy
                    .MAX_GENERATED_COMMANDS
        )
    }

    @Test
    fun denseNameSurvivesConvertAndReparseInEveryReleasedFormat() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text =
                            "BROTHERTESTE1234567890ABC",
                        heightMm =
                            22f,
                        style =
                            TextStitchStyle.SATIN,
                        satinDensityMm =
                            0.3f,
                        satinUnderlayMode =
                            SatinUnderlayMode.BOTH
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach {
                    format ->
                val converted =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "stress"
                        )
                        .getOrThrow()

                val reparsed =
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
                    reparsed is
                        EmbroideryLoadResult.Success
                )

                val design =
                    (
                        reparsed as
                            EmbroideryLoadResult.Success
                        ).design

                assertTrue(
                    design.stitchCount >
                        0
                )
            }
    }

    @Test
    fun largeMultiColorMatrixConvertsToAllReleasedFormats() {
        val source =
            syntheticDesign(
                stitchCount =
                    20_000,
                colorBlocks =
                    32
            )

        MatrixConverter
            .supportedFormats
            .forEach {
                    format ->
                val converted =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "stress-grande"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )

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

                val reopened =
                    (
                        parsed as
                            EmbroideryLoadResult.Success
                        ).design

                assertTrue(
                    reopened.stitchCount >
                        15_000
                )

                assertTrue(
                    reopened.colorCount >=
                        2
                )
            }
    }

    @Test
    fun editSaveReopenConvertAndSimulateStressCycle() {
        val source =
            syntheticDesign(
                stitchCount =
                    8_000,
                colorBlocks =
                    12
            )

        val edited =
            MatrixEditor
                .apply(
                    source,
                    EditTransform(
                        scale =
                            1.08f,
                        rotationDegrees =
                            17f,
                        offsetXUnits =
                            25,
                        offsetYUnits =
                            -18
                    )
                )

        val reopenedProject =
            ProjectCodec
                .decode(
                    ProjectCodec
                        .encode(
                            edited
                        )
                )

        assertEquals(
            edited.stitchCount,
            reopenedProject
                .stitchCount
        )

        val converted =
            MatrixConverter
                .convert(
                    reopenedProject,
                    "PES",
                    "stress-ciclo"
                )
                .getOrThrow()

        val reparsed =
            EmbroideryIoParser
                .parse(
                    converted.fileName,
                    converted.bytes
                )

        assertTrue(
            reparsed is
                EmbroideryLoadResult.Success
        )

        val finalDesign =
            (
                reparsed as
                    EmbroideryLoadResult.Success
                ).design

        assertTrue(
            finalDesign.stitchCount >
                0
        )

        assertTrue(
            SimulationTiming
                .estimatedSeconds(
                    stitches =
                        finalDesign
                            .stitchCount,
                    speedMultiplier =
                        1f
                ) >
                0
        )
    }


    @Test
    fun pathologicalMoveIsRejectedBeforeHugeExpansion() {
        val source =
            EmbroideryDesign(
                fileName =
                    "patologico.dst",
                format =
                    "DST",
                label =
                    "Patológico",
                points =
                    listOf(
                        EmbroideryPoint(
                            Int.MIN_VALUE /
                                2,
                            0,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            Int.MAX_VALUE /
                                2,
                            0,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            Int.MAX_VALUE /
                                2,
                            0,
                            StitchCommand.END,
                            0
                        )
                    ),
                bounds =
                    EmbroideryBounds(
                        Int.MIN_VALUE /
                            2,
                        Int.MAX_VALUE /
                            2,
                        0,
                        0
                    ),
                stitchCount =
                    2,
                jumpCount =
                    0,
                colorChanges =
                    0,
                endFound =
                    true,
                sourceBytes =
                    ByteArray(0)
            )

        assertTrue(
            MatrixConverter
                .convert(
                    source,
                    "DST"
                )
                .isFailure
        )
    }


    private fun syntheticDesign(
        stitchCount: Int,
        colorBlocks: Int
    ): EmbroideryDesign {
        require(
            stitchCount >
                0
        )

        require(
            colorBlocks >
                0
        )

        val points =
            mutableListOf<
                EmbroideryPoint
            >()

        var colorIndex =
            0

        val blockSize =
            (
                stitchCount /
                    colorBlocks
                ).coerceAtLeast(
                1
            )

        var minX =
            Int.MAX_VALUE

        var maxX =
            Int.MIN_VALUE

        var minY =
            Int.MAX_VALUE

        var maxY =
            Int.MIN_VALUE

        repeat(
            stitchCount
        ) {
                index ->
            if (
                index >
                    0 &&
                index %
                    blockSize ==
                    0 &&
                colorIndex <
                    colorBlocks -
                        1
            ) {
                colorIndex++

                val previous =
                    points.last()

                points +=
                    EmbroideryPoint(
                        previous.xUnits,
                        previous.yUnits,
                        StitchCommand.COLOR_CHANGE,
                        colorIndex
                    )
            }

            val x =
                (
                    index %
                        200
                    ) *
                    8

            val row =
                index /
                    200

            val y =
                (
                    row %
                        100
                    ) *
                    8

            points +=
                EmbroideryPoint(
                    x,
                    y,
                    StitchCommand.STITCH,
                    colorIndex
                )

            minX =
                minOf(
                    minX,
                    x
                )

            maxX =
                maxOf(
                    maxX,
                    x
                )

            minY =
                minOf(
                    minY,
                    y
                )

            maxY =
                maxOf(
                    maxY,
                    y
                )
        }

        val end =
            points.last()

        points +=
            EmbroideryPoint(
                end.xUnits,
                end.yUnits,
                StitchCommand.END,
                colorIndex
            )

        return EmbroideryDesign(
            fileName =
                "stress.dst",
            format =
                "DST",
            label =
                "Stress",
            points =
                points,
            bounds =
                EmbroideryBounds(
                    minX,
                    maxX,
                    minY,
                    maxY
                ),
            stitchCount =
                stitchCount,
            jumpCount =
                0,
            colorChanges =
                colorIndex,
            endFound =
                true,
            sourceBytes =
                ByteArray(0),
            threadColors =
                List(
                    colorIndex +
                        1
                ) {
                        index ->
                    0x101010 *
                        (
                            index +
                                1
                            )
                },
            isModified =
                true
        )
    }
}
