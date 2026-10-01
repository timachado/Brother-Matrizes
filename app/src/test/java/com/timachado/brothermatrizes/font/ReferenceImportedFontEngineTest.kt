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
    fun emitterPreservesTheColumnOrderProducedByTheSampler() {
        val points =
            ReferenceImportedFontEngine
                .debugVisualStartPath()

        assertTrue(
            points.isNotEmpty()
        )

        val first =
            points.first()

        assertEquals(
            StitchCommand.JUMP,
            first.command
        )

        assertEquals(
            "O emitter não pode reordenar as colunas recebidas do sampler.",
            70,
            first.xUnits
        )
    }

    @Test
    fun adjacentColumnsUseReferenceJumpWithoutForcedTrim() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        assertEquals(
            StitchCommand.JUMP,
            points.first()
                .command
        )

        assertTrue(
            "Cada coluna começa com o travel do motor de referência.",
            points.count {
                it.command ==
                    StitchCommand.JUMP
            } >=
                2
        )

        assertTrue(
            "Colunas próximas não devem forçar corte de linha.",
            points.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }

    @Test
    fun disconnectedColumnsJumpInsteadOfCrossingEmptyArea() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        false,
                    includeUnderlay =
                        false
                )

        assertTrue(
            points.count {
                it.command ==
                    StitchCommand.JUMP
            } >=
                2
        )
    }

    @Test
    fun satinUsesReferenceLocksAndBothEdgesOfEverySampleRow() {
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

        // O motor de referência usa trava inicial/final e costura A/B
        // em cada linha Satin.
        assertTrue(
            stitches >=
                20
        )
    }

    @Test
    fun centerUnderlayMakesTheReferenceForwardAndReturnPass() {
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
            "O underlay central de referência precisa fazer ida e retorno antes da cobertura.",
            xSequence
                .zipWithNext()
                .any {
                        pair ->
                    pair.second <
                        pair.first -
                            3
                }
        )
    }

    @Test
    fun centerUnderlayStartsOnColumnCenterInsteadOfWalkingEdges() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        true
                )

        val firstStitch =
            points.first {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "Underlay central deve começar no centro da coluna, não na borda.",
            firstStitch.xUnits in
                11..14
        )
    }

    @Test
    fun centerUnderlayAddsAFoundationPass() {
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
    fun adjacentSatinColumnsFollowSamplerOrientationWithoutSerpentineRewrite() {
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
