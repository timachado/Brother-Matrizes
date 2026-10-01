package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatrixGeneratorTest {

    @Test
    fun generatesRealStitchesForName() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 10f,
                        outputFormat =
                            "DST"
                    )
                )
                .getOrThrow()

        assertEquals(
            "MARIA",
            design.label
        )

        assertEquals(
            "DST",
            design.format
        )

        assertTrue(
            design.stitchCount > 20
        )

        assertTrue(
            design.jumpCount > 0
        )

        assertTrue(
            design.bounds.widthMm >
                20f
        )

        assertTrue(
            design.isModified
        )
    }

    @Test
    fun supportsPortugueseAccents() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "JOÃO Ç",
                        heightMm = 12f,
                        outputFormat =
                            "PES"
                    )
                )
                .getOrThrow()

        assertEquals(
            "PES",
            design.format
        )

        assertTrue(
            design.bounds.maxYUnits >
                120
        )

        assertTrue(
            design.bounds.minYUnits <
                0
        )
    }

    @Test
    fun presetsChangeWidth() {
        val compact =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        font =
                            EmbroideryFontPreset
                                .COMPACT
                    )
                )
                .getOrThrow()

        val wide =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        font =
                            EmbroideryFontPreset
                                .WIDE
                    )
                )
                .getOrThrow()

        assertTrue(
            wide.bounds.widthMm >
                compact.bounds.widthMm
        )
    }

    @Test
    fun generatedNameExportsToAllReleasedFormats() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "FIO",
                        heightMm = 8f
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach { format ->
                val output =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "nome-teste"
                        )
                        .getOrThrow()

                assertTrue(
                    output.bytes
                        .isNotEmpty()
                )
            }
    }
    @Test
    fun satinCreatesDenseRealZigzag() {
        val running =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 12f,
                        style =
                            TextStitchStyle.RUNNING
                    )
                )
                .getOrThrow()

        val satin =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 12f,
                        style =
                            TextStitchStyle.SATIN,
                        satinWidthMm = 2.4f,
                        satinDensityMm = 0.45f,
                        satinUnderlayMode = SatinUnderlayMode.CENTER
                    )
                )
                .getOrThrow()

        assertTrue(
            satin.stitchCount >
                running.stitchCount *
                    2
        )

        assertTrue(
            satin.bounds.widthMm >
                running.bounds.widthMm ||
                satin.bounds.heightMm >
                    running.bounds.heightMm
        )
    }

    @Test
    fun denserSatinCreatesMoreStitches() {
        val dense =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        style =
                            TextStitchStyle.SATIN,
                        satinDensityMm =
                            0.35f
                    )
                )
                .getOrThrow()

        val loose =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        style =
                            TextStitchStyle.SATIN,
                        satinDensityMm =
                            0.8f
                    )
                )
                .getOrThrow()

        assertTrue(
            dense.stitchCount >
                loose.stitchCount
        )
    }

    @Test
    fun satinUnderlayAddsStructuralStitches() {
        val withUnderlay =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "FIO",
                        style =
                            TextStitchStyle.SATIN,
                        satinUnderlayMode = SatinUnderlayMode.CENTER
                    )
                )
                .getOrThrow()

        val withoutUnderlay =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "FIO",
                        style =
                            TextStitchStyle.SATIN,
                        satinUnderlayMode = SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        assertTrue(
            withUnderlay.stitchCount >
                withoutUnderlay.stitchCount
        )
    }

    @Test
    fun satinNameExportsToAllReleasedFormats() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 10f,
                        style =
                            TextStitchStyle.SATIN,
                        satinWidthMm = 2.2f,
                        satinDensityMm = 0.45f
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach { format ->
                val output =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "satin-teste"
                        )
                        .getOrThrow()

                assertTrue(
                    output.bytes
                        .isNotEmpty()
                )
            }
    }

    @Test
    fun pullCompensationExpandsSatinEnvelope() {
        val base =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "I",
                        heightMm = 14f,
                        style =
                            TextStitchStyle.SATIN,
                        satinWidthMm = 2.4f,
                        satinPullCompensationMm = 0f,
                        satinUnderlayMode =
                            SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        val compensated =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "I",
                        heightMm = 14f,
                        style =
                            TextStitchStyle.SATIN,
                        satinWidthMm = 2.4f,
                        satinPullCompensationMm = 0.4f,
                        satinUnderlayMode =
                            SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        assertTrue(
            compensated.bounds.widthMm >
                base.bounds.widthMm ||
                compensated.bounds.heightMm >
                    base.bounds.heightMm
        )
    }

    @Test
    fun bothUnderlaysAddMoreStructure() {
        val none =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "M",
                        style =
                            TextStitchStyle.SATIN,
                        satinUnderlayMode =
                            SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        val both =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "M",
                        style =
                            TextStitchStyle.SATIN,
                        satinUnderlayMode =
                            SatinUnderlayMode.BOTH
                    )
                )
                .getOrThrow()

        assertTrue(
            both.stitchCount >
                none.stitchCount
        )
    }

    @Test
    fun shortStitchesChangeSharpCornerGeometry() {
        val enabled =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "M",
                        heightMm = 16f,
                        style =
                            TextStitchStyle.SATIN,
                        satinShortStitches = true,
                        satinUnderlayMode =
                            SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        val disabled =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "M",
                        heightMm = 16f,
                        style =
                            TextStitchStyle.SATIN,
                        satinShortStitches = false,
                        satinUnderlayMode =
                            SatinUnderlayMode.NONE
                    )
                )
                .getOrThrow()

        assertTrue(
            enabled.points !=
                disabled.points
        )
    }

    @Test
    fun fontLibraryHasDistinctGeometry() {
        val signatures =
            EmbroideryFontPreset
                .entries
                .map {
                        font ->
                    val design =
                        TextMatrixGenerator
                            .generate(
                                TextMatrixOptions(
                                    text = "FIO",
                                    heightMm =
                                        14f,
                                    style =
                                        TextStitchStyle
                                            .SATIN,
                                    font =
                                        font
                                )
                            )
                            .getOrThrow()

                    buildString {
                        append(
                            design.bounds
                                .minXUnits
                        )
                        append(':')
                        append(
                            design.bounds
                                .maxXUnits
                        )
                        append(':')
                        append(
                            design.bounds
                                .minYUnits
                        )
                        append(':')
                        append(
                            design.bounds
                                .maxYUnits
                        )
                        append('|')

                        design.points
                            .forEach {
                                    point ->
                                append(
                                    point.xUnits
                                )
                                append(',')
                                append(
                                    point.yUnits
                                )
                                append(',')
                                append(
                                    point.command
                                        .ordinal
                                )
                                append(';')
                            }
                    }
                }

        assertEquals(
            EmbroideryFontPreset
                .entries
                .size,
            signatures
                .distinct()
                .size
        )
    }

    @Test
    fun everyFontExportsToReleasedFormats() {
        EmbroideryFontPreset
            .entries
            .forEach {
                    font ->
                val design =
                    TextMatrixGenerator
                        .generate(
                            TextMatrixOptions(
                                text = "AB",
                                heightMm =
                                    10f,
                                style =
                                    TextStitchStyle
                                        .SATIN,
                                font =
                                    font
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
                                    "fonte-" +
                                        font.name
                                            .lowercase()
                                )
                                .getOrThrow()

                        assertTrue(
                            converted.bytes
                                .isNotEmpty()
                        )
                    }
            }
    }

}
