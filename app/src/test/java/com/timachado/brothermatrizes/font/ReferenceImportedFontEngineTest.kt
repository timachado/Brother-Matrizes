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

        assertTrue(
            "A primeira coluna deve preservar a ordem do sampler, mas entrar pelo extremo mais próximo do start hint.",
            first.xUnits >=
                80
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

        assertEquals(
            "Regiões conectadas devem ter apenas o salto inicial; a transição fica escondida em STITCH dentro do glifo.",
            1,
            points.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )

        assertTrue(
            "Colunas próximas e conectadas não devem forçar corte de linha.",
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
            "A segunda coluna deve ser percorrida em serpentina, entrando pelo extremo mais próximo.",
            20,
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
    @Test
    fun connectedRegionIsCompletedBeforeDistantRegion() {
        val points =
            ReferenceImportedFontEngine
                .debugConnectedPriorityPath()

        val firstFarJump =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.JUMP &&
                it.xUnits >=
                    70
            }

        assertTrue(
            "A região distante precisa ser acessada por JUMP somente depois que a região conectada for concluída.",
            firstFarJump >
                0
        )

        assertTrue(
            "Antes do salto distante deve existir costura na coluna conectada.",
            points
                .take(
                    firstFarJump
                )
                .any {
                    it.command ==
                        StitchCommand.STITCH &&
                    it.xUnits in
                        18..35
                }
        )

        assertEquals(
            "A ligação para a coluna conectada deve ficar escondida em STITCH, sem salto extra.",
            2,
            points.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )
    }


}
