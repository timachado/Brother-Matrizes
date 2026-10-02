package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceImportedFontEngineTest {

    @Test
    fun samplerChoosesNarrowerAxisForWideRectangle() {
        val widths =
            ReferenceImportedFontEngine
                .debugColumnWidths(
                    polygon =
                        listOf(
                            0f to 0f,
                            100f to 0f,
                            100f to 20f,
                            0f to 20f
                        )
                )

        assertTrue(
            widths.isNotEmpty()
        )

        assertTrue(
            widths.maxOrNull()!! <=
                21f
        )
    }

    @Test
    fun wideSatinBandIsSplitBelowMaximumWidth() {
        val widths =
            ReferenceImportedFontEngine
                .debugColumnWidths(
                    polygon =
                        listOf(
                            0f to 0f,
                            100f to 0f,
                            100f to 100f,
                            0f to 100f
                        ),
                    maxWidthMm =
                        7f
                )

        assertTrue(
            widths.isNotEmpty()
        )

        assertTrue(
            widths.maxOrNull()!! <=
                70.1f
        )
    }

    @Test
    fun emitterKeepsSamplerColumnOrder() {
        val points =
            ReferenceImportedFontEngine
                .debugVisualStartPath()

        assertTrue(
            points.isNotEmpty()
        )

        assertEquals(
            StitchCommand.JUMP,
            points.first()
                .command
        )

        // A lista sintética é [right, left]. O emitter não deve
        // reordenar pela posição atual/startHint.
        assertEquals(
            70,
            points.first()
                .xUnits
        )
    }

    @Test
    fun everyColumnStartsWithReferenceTravel() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        assertEquals(
            2,
            points.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )

        assertTrue(
            points.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }

    @Test
    fun connectedAndDisconnectedGeometryDoNotRewriteColumnTravel() {
        val connected =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        val disconnected =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        false,
                    includeUnderlay =
                        false
                )

        assertEquals(
            connected.map {
                it.command
            },
            disconnected.map {
                it.command
            }
        )
    }

    @Test
    fun satinEmitsBothAAndBForEverySampleRow() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        val stitches =
            points.count {
                it.command ==
                    StitchCommand.STITCH
            }

        /*
         * Duas colunas com quatro rows:
         * por coluna = lock 3 + (A,B)*4 + lock 3 = 14 STITCH.
         * Total exato = 28 STITCH.
         */
        assertEquals(
            28,
            stitches
        )
    }

    @Test
    fun centerUnderlayRunsForwardAndBackBeforeCoverage() {
        val stitches =
            ReferenceImportedFontEngine
                .debugProgressiveCenterUnderlayPath()
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        val xSequence =
            stitches.map {
                it.xUnits
            }

        assertTrue(
            "O center-run precisa alcançar a extremidade.",
            xSequence.any {
                it >=
                    31
            }
        )

        assertTrue(
            "O center-run de referência precisa retornar antes do Satin.",
            xSequence
                .zipWithNext()
                .any {
                        pair ->
                    pair.second <
                        pair.first -
                            10
                }
        )
    }

    @Test
    fun centerUnderlayAddsFoundationWithoutExtraTrim() {
        val without =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        val with =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        true
                )

        assertTrue(
            with.count {
                it.command ==
                    StitchCommand.STITCH
            } >
                without.count {
                    it.command ==
                        StitchCommand.STITCH
                }
        )

        assertTrue(
            with.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }

    @Test
    fun fixedColumnOrderDoesNotApplySerpentineRewrite() {
        val jumps =
            ReferenceImportedFontEngine
                .debugSerpentineTransitionJumpTargets()

        assertTrue(
            jumps.size >=
                2
        )

        val transition =
            jumps.last()

        assertEquals(
            30,
            transition.first
        )

        assertEquals(
            0,
            transition.second
        )
    }

    @Test
    fun satinColumnsKeepStableLeftToRightReadingOrder() {
        assertEquals(
            listOf(
                0f,
                40f,
                70f
            ),
            ReferenceImportedFontEngine
                .debugReadingOrderColumnLeftEdges()
        )
    }

    @Test
    fun disconnectedShapesCreateMoreThanOneColumn() {
        val count =
            ReferenceImportedFontEngine
                .debugColumnCount(
                    polygons =
                        listOf(
                            listOf(
                                0f to 0f,
                                20f to 0f,
                                20f to 80f,
                                0f to 80f
                            ),
                            listOf(
                                40f to 0f,
                                60f to 0f,
                                60f to 80f,
                                40f to 80f
                            )
                        )
                )

        assertTrue(
            count >=
                2
        )
    }
}
