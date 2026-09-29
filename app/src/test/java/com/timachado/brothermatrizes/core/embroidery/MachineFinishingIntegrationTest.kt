package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineFinishingIntegrationTest {

    @Test
    fun createdNameReceivesMachineFinishing() {
        val raw =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            TextMatrixOptions(
                                text = "FIO",
                                heightMm = 12f,
                                style =
                                    TextStitchStyle.SATIN
                            ),
                        machineFinishingSettings =
                            MachineFinishingSettings(
                                tieInEnabled = false,
                                tieOffEnabled = false,
                                autoTrimLongJumps = false,
                                optimizeTravel = false
                            )
                    )
                )
                .getOrThrow()

        val finished =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            TextMatrixOptions(
                                text = "FIO",
                                heightMm = 12f,
                                style =
                                    TextStitchStyle.SATIN
                            )
                    )
                )
                .getOrThrow()

        assertNotNull(
            finished.machineFinishing
        )

        assertTrue(
            finished.stitchCount >
                raw.stitchCount
        )
    }

    @Test
    fun monogramReceivesMachineFinishing() {
        val design =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials = "TM",
                        heightMm = 18f
                    )
                )
                .getOrThrow()

        assertNotNull(
            design.machineFinishing
        )

        assertTrue(
            design.stitchCount >
                0
        )
    }

    @Test
    fun finishingPreservesMulticolorSequence() {
        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            TextMatrixOptions(
                                text = "ABC",
                                heightMm = 12f,
                                style =
                                    TextStitchStyle.SATIN,
                                color =
                                    0xE6BE70
                            ),
                        letterAdjustments =
                            listOf(
                                LetterAdjustment(
                                    sourceIndex = 1,
                                    color =
                                        0x457B9D
                                ),
                                LetterAdjustment(
                                    sourceIndex = 2,
                                    color =
                                        0xE63946
                                )
                            )
                    )
                )
                .getOrThrow()

        assertEquals(
            2,
            design.colorChanges
        )

        assertEquals(
            listOf(
                0xE6BE70,
                0x457B9D,
                0xE63946
            ),
            design.threadColors
        )

        assertEquals(
            2,
            design.points
                .count {
                    it.command ==
                        StitchCommand.COLOR_CHANGE
                }
        )
    }

    @Test
    fun finishedNameStillExportsToAllFormats() {
        val design =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions =
                            TextMatrixOptions(
                                text = "MARIA",
                                heightMm = 12f,
                                style =
                                    TextStitchStyle.SATIN
                            ),
                        layoutMode =
                            TextLayoutMode.ARC_UP,
                        arcHeightMm = 6f
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
                            "acabamento"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )
            }
    }
}
