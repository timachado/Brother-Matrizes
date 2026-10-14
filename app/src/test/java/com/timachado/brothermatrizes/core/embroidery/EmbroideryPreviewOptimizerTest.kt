package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryPreviewOptimizerTest {
    @Test
    fun smallMatrixIsReturnedUntouched() {
        val points =
            listOf(
                EmbroideryPoint(
                    0,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    10,
                    0,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    10,
                    0,
                    StitchCommand.END,
                    0
                )
            )

        val optimized =
            EmbroideryPreviewOptimizer
                .optimize(
                    points
                )

        assertSame(
            points,
            optimized
        )
    }

    @Test
    fun hugeMatrixKeepsWholeRouteWithoutLeavingRunsOpen() {
        val points =
            buildList {
                repeat(
                    100_000
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                index,
                            yUnits =
                                index %
                                    300,
                            command =
                                StitchCommand.STITCH,
                            colorIndex =
                                0
                        )
                    )
                }

                add(
                    EmbroideryPoint(
                        100_000,
                        0,
                        StitchCommand.JUMP,
                        0
                    )
                )

                repeat(
                    100_000
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                100_000 +
                                    index,
                            yUnits =
                                300 -
                                    index %
                                        300,
                            command =
                                StitchCommand.STITCH,
                            colorIndex =
                                0
                        )
                    )
                }

                add(
                    EmbroideryPoint(
                        199_999,
                        0,
                        StitchCommand.END,
                        0
                    )
                )
            }

        val optimized =
            EmbroideryPreviewOptimizer
                .optimize(
                    points,
                    maxCommands =
                        25_000
                )

        assertTrue(
            optimized.size <
                points.size /
                    4
        )

        assertEquals(
            points.first(),
            optimized.first()
        )

        assertEquals(
            points.last(),
            optimized.last()
        )

        assertTrue(
            optimized.any {
                it.command ==
                    StitchCommand.JUMP
            }
        )

        val jumpIndex =
            optimized.indexOfFirst {
                it.command ==
                    StitchCommand.JUMP
            }

        assertTrue(
            jumpIndex >
                0
        )

        assertEquals(
            points[
                99_999
            ],
            optimized[
                jumpIndex -
                    1
            ]
        )

        assertEquals(
            points[
                100_001
            ],
            optimized[
                jumpIndex +
                    1
            ]
        )
    }

    @Test
    fun uploadedPesSizedMatrixUsesLightweightPreview() {
        val points =
            buildList {
                repeat(
                    21_844
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                index %
                                    858,
                            yUnits =
                                index %
                                    1202,
                            command =
                                StitchCommand.STITCH,
                            colorIndex =
                                index /
                                    3_000
                        )
                    )
                }

                repeat(
                    8
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                100 +
                                    index,
                            yUnits =
                                200 +
                                    index,
                            command =
                                StitchCommand.COLOR_CHANGE,
                            colorIndex =
                                index +
                                    1
                        )
                    )
                }

                repeat(
                    14
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                300 +
                                    index,
                            yUnits =
                                400 +
                                    index,
                            command =
                                StitchCommand.TRIM,
                            colorIndex =
                                8
                        )
                    )
                }

                repeat(
                    8
                ) {
                        index ->
                    add(
                        EmbroideryPoint(
                            xUnits =
                                500 +
                                    index,
                            yUnits =
                                600 +
                                    index,
                            command =
                                StitchCommand.JUMP,
                            colorIndex =
                                8
                        )
                    )
                }

                add(
                    EmbroideryPoint(
                        858,
                        1202,
                        StitchCommand.END,
                        8
                    )
                )
            }

        val optimized =
            EmbroideryPreviewOptimizer
                .optimize(
                    points
                )

        assertTrue(
            "Uma matriz PES com ~21,8 mil pontos deve entrar na prévia leve.",
            optimized.size <
                points.size /
                    2
        )

        assertEquals(
            StitchCommand.END,
            optimized.last()
                .command
        )

        assertEquals(
            8,
            optimized.count {
                it.command ==
                    StitchCommand.COLOR_CHANGE
            }
        )

        assertEquals(
            14,
            optimized.count {
                it.command ==
                    StitchCommand.TRIM
            }
        )

        assertEquals(
            8,
            optimized.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )
    }

}
