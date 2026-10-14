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
    fun portraitMatrixOutsideSafe130x180AreaIsBlocked() {
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

        assertFalse(
            "69,2 × 178,4 mm ultrapassa a área segura de 120 × 170 mm do bastidor 130 × 180.",
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
            "Com margem segura, o primeiro bastidor compatível deve ser 140 × 200 mm.",
            result ==
                HoopProfile.H140X200
        )
    }

    @Test
    fun nonPesFormatWarnsForBrotherDestination() {
        val result =
            MachineTransferValidator
                .validate(
                    design =
                        design(
                            800,
                            800
                        ),
                    format =
                        "DST",
                    hoop =
                        HoopProfile.H100X100
                )

        assertTrue(
            result.warnings
                .any {
                    it.message
                        .contains(
                            "Brother"
                        ) &&
                    it.message
                        .contains(
                            "PES"
                        )
                }
        )

        assertTrue(
            result.warnings
                .any {
                    it.message
                        .contains(
                            "cores"
                        )
                }
        )
    }

}
