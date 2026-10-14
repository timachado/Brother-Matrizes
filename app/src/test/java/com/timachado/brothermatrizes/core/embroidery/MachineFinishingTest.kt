package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineFinishingTest {

    private fun design(
        points: List<EmbroideryPoint>
    ): EmbroideryDesign {
        val coordinates =
            points.filter {
                it.command !=
                    StitchCommand.END
            }

        return EmbroideryDesign(
            fileName =
                "teste.dst",
            format =
                "DST",
            label =
                "Teste",
            points =
                points,
            bounds =
                EmbroideryBounds(
                    minXUnits =
                        coordinates
                            .minOf {
                                it.xUnits
                            },
                    maxXUnits =
                        coordinates
                            .maxOf {
                                it.xUnits
                            },
                    minYUnits =
                        coordinates
                            .minOf {
                                it.yUnits
                            },
                    maxYUnits =
                        coordinates
                            .maxOf {
                                it.yUnits
                            }
                ),
            stitchCount =
                points.count {
                    it.command ==
                        StitchCommand.STITCH
                },
            jumpCount =
                points.count {
                    it.command ==
                        StitchCommand.JUMP
                },
            colorChanges =
                points.count {
                    it.command ==
                        StitchCommand.COLOR_CHANGE
                },
            endFound = true,
            sourceBytes =
                ByteArray(0)
        )
    }

    @Test
    fun addsTieInAndTieOffAroundColorBlock() {
        val source =
            design(
                listOf(
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        30,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        60,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        60,
                        0,
                        StitchCommand.END,
                        0
                    )
                )
            )

        val result =
            MachineFinishing
                .apply(source)

        assertTrue(
            result.design.stitchCount >
                source.stitchCount
        )

        assertEquals(
            1,
            result.report
                .tieInCount
        )

        assertEquals(
            1,
            result.report
                .tieOffCount
        )

        assertEquals(
            source.stitchCount +
                4,
            result.design
                .stitchCount
        )
    }

    @Test
    fun insertsTrimBeforeLongJump() {
        val source =
            design(
                listOf(
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        200,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        220,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        220,
                        0,
                        StitchCommand.END,
                        0
                    )
                )
            )

        val result =
            MachineFinishing
                .apply(
                    source,
                    MachineFinishingSettings(
                        trimJumpThresholdMm =
                            8f
                    )
                )

        assertTrue(
            result.design.points
                .any {
                    it.command ==
                        StitchCommand.TRIM
                }
        )

        assertEquals(
            1,
            result.report
                .autoTrimCount
        )
    }

    @Test
    fun reportsLongStitchesAndJumps() {
        val source =
            design(
                listOf(
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        100,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        300,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        300,
                        0,
                        StitchCommand.END,
                        0
                    )
                )
            )

        val report =
            MachineFinishing
                .analyze(
                    source,
                    MachineFinishingSettings(
                        longStitchWarningMm =
                            7f,
                        longJumpWarningMm =
                            12f
                    )
                )

        assertEquals(
            1,
            report.longStitchCount
        )

        assertEquals(
            1,
            report.longJumpCount
        )

        assertTrue(
            report.longestStitchMm >=
                10f
        )

        assertTrue(
            report.longestJumpMm >=
                20f
        )
    }

    @Test
    fun removesRedundantTravelCommands() {
        val source =
            design(
                listOf(
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.TRIM,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.TRIM,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.END,
                        0
                    )
                )
            )

        val result =
            MachineFinishing
                .apply(source)

        assertTrue(
            result.report
                .redundantCommandsRemoved >=
                2
        )
    }

    @Test
    fun preservesColorChanges() {
        val source =
            design(
                listOf(
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        0,
                        StitchCommand.COLOR_CHANGE,
                        1
                    ),
                    EmbroideryPoint(
                        50,
                        0,
                        StitchCommand.JUMP,
                        1
                    ),
                    EmbroideryPoint(
                        70,
                        0,
                        StitchCommand.STITCH,
                        1
                    ),
                    EmbroideryPoint(
                        70,
                        0,
                        StitchCommand.END,
                        1
                    )
                )
            )

        val result =
            MachineFinishing
                .apply(source)

        assertEquals(
            1,
            result.design.colorChanges
        )

        assertEquals(
            1,
            result.design.points
                .count {
                    it.command ==
                        StitchCommand.COLOR_CHANGE
                }
        )
    }
}
