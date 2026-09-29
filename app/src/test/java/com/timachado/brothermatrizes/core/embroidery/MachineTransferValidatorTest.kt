package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineTransferValidatorTest {
    private fun design(
        widthUnits: Int,
        heightUnits: Int,
        stitchCount: Int = 1000,
        endFound: Boolean = true
    ): EmbroideryDesign =
        EmbroideryDesign(
            fileName =
                "teste.pes",
            format =
                "PES",
            label =
                "Teste",
            points =
                listOf(
                    EmbroideryPoint(
                        xUnits = 0,
                        yUnits = 0,
                        command =
                            StitchCommand.STITCH,
                        colorIndex = 0
                    ),
                    EmbroideryPoint(
                        xUnits =
                            widthUnits,
                        yUnits =
                            heightUnits,
                        command =
                            StitchCommand.END,
                        colorIndex = 0
                    )
                ),
            bounds =
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits =
                        widthUnits,
                    minYUnits = 0,
                    maxYUnits =
                        heightUnits
                ),
            stitchCount =
                stitchCount,
            jumpCount = 0,
            colorChanges = 0,
            endFound =
                endFound,
            sourceBytes =
                byteArrayOf(
                    1
                )
        )

    @Test
    fun validMatrixCanBeSent() {
        val result =
            MachineTransferValidator
                .validate(
                    design =
                        design(
                            800,
                            800
                        ),
                    format =
                        "PES",
                    hoop =
                        HoopProfile
                            .H100X100
                )

        assertTrue(
            result.ready
        )
    }

    @Test
    fun matrixOutsidePhysicalHoopAreaIsBlocked() {
        val result =
            MachineTransferValidator
                .validate(
                    design =
                        design(
                            1001,
                            984
                        ),
                    format =
                        "PES",
                    hoop =
                        HoopProfile
                            .H100X100
                )

        assertFalse(
            result.ready
        )

        assertTrue(
            result
                .blockingIssues
                .any {
                    it.message
                        .contains(
                            "não cabe"
                        )
                }
        )
    }

    @Test
    fun unsupportedFormatIsBlocked() {
        val result =
            MachineTransferValidator
                .validate(
                    design =
                        design(
                            800,
                            800
                        ),
                    format =
                        "XXX",
                    hoop =
                        HoopProfile
                            .H100X100
                )

        assertFalse(
            result.ready
        )
    }

    @Test
    fun portraitMatrixFitsRectangularHoopWhenRotated() {
        val result =
            MachineTransferValidator
                .validate(
                    design =
                        design(
                            widthUnits = 692,
                            heightUnits = 1784
                        ),
                    format = "PES",
                    hoop =
                        HoopProfile
                            .H140X200
                )

        assertTrue(
            "69,2 × 178,4 mm deve caber no bastidor 140 × 200 mm quando o bastidor é girado.",
            result.ready
        )
    }

    @Test
    fun portraitMatrixFitsNominal130x180Hoop() {
        val result =
            MachineTransferValidator
                .validate(
                    design =
                        design(
                            widthUnits = 692,
                            heightUnits = 1784
                        ),
                    format = "PES",
                    hoop =
                        HoopProfile
                            .H130X180
                )

        assertTrue(
            "69,2 × 178,4 mm deve caber fisicamente no bastidor nominal 130 × 180 mm.",
            result.ready
        )
    }

    @Test
    fun recommendedHoopUsesRotatedFitForPortraitMatrix() {
        val result =
            MachineTransferValidator
                .recommendedHoop(
                    design(
                        widthUnits = 692,
                        heightUnits = 1784
                    )
                )

        assertTrue(
            "O primeiro bastidor físico compatível deve ser 130 × 180 mm.",
            result ==
                HoopProfile.H130X180
        )
    }

}
