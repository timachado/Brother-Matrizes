package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutGeneratorTest {

    private fun base(
        text: String =
            "MARIA"
    ) =
        TextMatrixOptions(
            text = text,
            heightMm = 12f,
            style =
                TextStitchStyle.SATIN,
            font =
                EmbroideryFontPreset
                    .ELEGANT,
            hoopProfile =
                HoopProfile.H140X200
        )

    @Test
    fun arcUpMovesMiddleLettersHigherThanStraight() {
        val straight =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .STRAIGHT
                    )
                )
                .getOrThrow()

        val curved =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .ARC_UP,
                        arcHeightMm =
                            12f
                    )
                )
                .getOrThrow()

        assertTrue(
            curved.bounds.heightMm >
                straight.bounds.heightMm
        )
    }

    @Test
    fun arcUpAndDownGenerateDifferentGeometry() {
        val up =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .ARC_UP,
                        arcHeightMm =
                            10f
                    )
                )
                .getOrThrow()

        val down =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base(),
                        layoutMode =
                            TextLayoutMode
                                .ARC_DOWN,
                        arcHeightMm =
                            10f
                    )
                )
                .getOrThrow()

        assertTrue(
            up.points !=
                down.points
        )
    }

    @Test
    fun individualLetterRotationChangesGeometry() {
        val normal =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ANA")
                    )
                )
                .getOrThrow()

        val edited =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ANA"),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        1,
                                    rotationDegrees =
                                        25f
                                )
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            normal.points !=
                edited.points
        )
    }

    @Test
    fun individualSpacingCanWidenText() {
        val normal =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO")
                    )
                )
                .getOrThrow()

        val spaced =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO"),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        0,
                                    spacingAfterMm =
                                        6f
                                )
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            spaced.bounds.widthMm >
                normal.bounds.widthMm
        )
    }

    @Test
    fun finalTextIsAutomaticallyCentered() {
        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIOLAB"),
                        layoutMode =
                            TextLayoutMode
                                .ARC_UP,
                        arcHeightMm =
                            8f
                    )
                )
                .getOrThrow()

        val centerX =
            (
                design.bounds.minXUnits +
                    design.bounds.maxXUnits
                ) /
                2f

        val centerY =
            (
                design.bounds.minYUnits +
                    design.bounds.maxYUnits
                ) /
                2f

        assertTrue(
            kotlin.math.abs(
                centerX
            ) <= 1f
        )

        assertTrue(
            kotlin.math.abs(
                centerY
            ) <= 1f
        )
    }

    @Test
    fun curvedTextExportsToReleasedFormats() {
        val source =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO"),
                        layoutMode =
                            TextLayoutMode
                                .ARC_DOWN,
                        arcHeightMm =
                            6f
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
                            "curva-teste"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )
            }
    }
    @Test
    fun perLetterColorsCreateRealColorChangeBlocks() {
        val gold =
            0xE6BE70

        val blue =
            0x457B9D

        val red =
            0xE63946

        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ABC")
                                .copy(
                                    color =
                                        gold
                                ),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        1,
                                    color =
                                        blue
                                ),
                                LetterAdjustment(
                                    sourceIndex =
                                        2,
                                    color =
                                        red
                                )
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            design.colorChanges ==
                2
        )

        assertTrue(
            design.threadColors ==
                listOf(
                    gold,
                    blue,
                    red
                )
        )

        assertTrue(
            design.points
                .count {
                    it.command ==
                        StitchCommand
                            .COLOR_CHANGE
                } ==
                2
        )

        assertTrue(
            design.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }
                .map {
                    it.colorIndex
                }
                .toSet()
                .containsAll(
                    setOf(
                        0,
                        1,
                        2
                    )
                )
        )
    }

    @Test
    fun perLetterSequenceNeverReturnsToPreviousGlyphBlock() {
        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ABC")
                                .copy(
                                    color =
                                        0x111111
                                ),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        0,
                                    color =
                                        0x111111
                                ),
                                LetterAdjustment(
                                    sourceIndex =
                                        1,
                                    color =
                                        0x222222
                                ),
                                LetterAdjustment(
                                    sourceIndex =
                                        2,
                                    color =
                                        0x333333
                                )
                            )
                    )
                )
                .getOrThrow()

        val stitchBlocks =
            design.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }
                .map {
                    it.colorIndex
                }

        assertTrue(
            stitchBlocks.zipWithNext()
                .all {
                        pair ->
                    pair.first <=
                        pair.second
                }
        )
    }

    @Test
    fun adjacentSameColorStaysInSameBlock() {
        val gold =
            0xE6BE70

        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("ABC")
                                .copy(
                                    color =
                                        gold
                                ),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        1,
                                    color =
                                        gold
                                )
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            design.colorChanges ==
                0
        )

        assertTrue(
            design.threadColors ==
                listOf(
                    gold
                )
        )
    }

    @Test
    fun multicolorTextExportsToReleasedFormats() {
        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            base("FIO"),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex =
                                        1,
                                    color =
                                        0x457B9D
                                ),
                                LetterAdjustment(
                                    sourceIndex =
                                        2,
                                    color =
                                        0xE63946
                                )
                            )
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
                            design,
                            format,
                            "multicor"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )
            }
    }

}
