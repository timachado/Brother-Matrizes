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
    fun firstSatinRegionStartsNearestToTheGlyphStartHint() {
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
            "A primeira coluna precisa começar na região mais próxima do startHint do glifo.",
            first.xUnits >=
                70
        )
    }

    @Test
    fun nearbyColumnsUseRealJumpWithoutForcedTrim() {
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
            "Além do salto inicial, a transição entre colunas precisa continuar sendo JUMP real.",
            points.count {
                it.command ==
                    StitchCommand.JUMP
            } >=
                2
        )

        assertTrue(
            "Transição curta entre colunas não deve cortar a linha.",
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
    fun satinUsesBothEdgesOfEverySampleRow() {
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

        // 2 colunas x 4 linhas x 2 lados + conector contínuo.
        assertTrue(
            "Cada linha Satin precisa costurar A e B para concluir visualmente a coluna.",
            stitches >=
                17
        )
    }

    @Test
    fun centerUnderlayRunsOnceToFarEndBeforeReverseSatin() {
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

        val farIndex =
            xSequence.indexOfFirst {
                it >=
                    31
            }

        assertTrue(
            "A passada central precisa alcançar o extremo da coluna.",
            farIndex >=
                1
        )

        assertTrue(
            "Antes de chegar ao extremo, a passada central não pode retornar.",
            xSequence
                .take(
                    farIndex +
                        1
                )
                .zipWithNext()
                .all {
                        pair ->
                    pair.second >=
                        pair.first
                }
        )

        assertTrue(
            "Depois do extremo, a cobertura Satin deve retornar pela coluna.",
            xSequence
                .drop(
                    farIndex +
                        1
                )
                .any {
                    it <
                        25
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
    fun adjacentSatinColumnsContinueFromNearestEndInsteadOfJumpingBackToTop() {
        val jumps =
            ReferenceImportedFontEngine
                .debugSerpentineTransitionJumpTargets()

        assertEquals(
            "Deve existir o JUMP inicial e o JUMP de transição para a segunda coluna.",
            2,
            jumps.size
        )

        assertEquals(
            "Ao inverter o sentido, a entrada continua no lado A da linha Satin.",
            30,
            jumps.last()
                .first
        )

        assertEquals(
            "Terminando a primeira coluna embaixo, a próxima deve entrar pelo extremo inferior.",
            20,
            jumps.last()
                .second
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
    fun cursiveVisualStartPrefersLowerEntryStrokeInsteadOfLeftmostLoopExtremity() {
        val start =
            ReferenceImportedFontEngine
                .debugGlyphVisualStartPoint(
                    listOf(
                        0f to 22f,
                        8f to 12f,
                        24f to 1f,
                        70f to 0f,
                        96f to 18f
                    )
                )

        assertEquals(
            "O início visual deve ficar no pé inferior esquerdo do primeiro traço, não na extremidade esquerda do laço.",
            24f,
            start?.first
        )

        assertEquals(
            1f,
            start?.second
        )
    }

    @Test
    fun cursiveVisualStartSkipsIsolatedLeadingFlourishCluster() {
        val start =
            ReferenceImportedFontEngine
                .debugGlyphVisualStartPoint(
                    listOf(
                        0f to 0f,
                        4f to 12f,
                        10f to 24f,
                        18f to 13f,
                        24f to 6f,
                        30f to 20f,
                        42f to 30f,
                        54f to 18f,
                        64f to 9f,
                        72f to 22f,
                        84f to 28f,
                        96f to 16f
                    )
                )

        assertEquals(
            "Com três agrupamentos inferiores, o primeiro isolado é floreio; a entrada deve começar no primeiro traço principal.",
            24f,
            start?.first
        )
    }

    @Test
    fun simpleGlyphWithoutLeadingFlourishKeepsItsNaturalLowerLeftStart() {
        val start =
            ReferenceImportedFontEngine
                .debugGlyphVisualStartPoint(
                    listOf(
                        0f to 0f,
                        0f to 40f,
                        20f to 40f,
                        20f to 0f
                    )
                )

        assertEquals(
            0f,
            start?.first
        )

        assertEquals(
            0f,
            start?.second
        )
    }

    @Test
    fun firstNeedleJumpUsesVisualStartBeforeEnteringCenterUnderlay() {
        val points =
            ReferenceImportedFontEngine
                .debugReferencePath(
                    connected = true,
                    includeUnderlay = true
                )

        val first =
            points.first()

        assertEquals(
            StitchCommand.JUMP,
            first.command
        )

        assertEquals(
            "A agulha deve nascer no início visual do traço.",
            5,
            first.xUnits
        )

        assertEquals(
            5,
            first.yUnits
        )

        val firstStitch =
            points.first {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "Depois de nascer no início visual, a entrada no underlay deve ser costurada para dentro da coluna.",
            firstStitch.xUnits >
                first.xUnits
        )
    }


    @Test
    fun firstSatinColumnIsTheOneContainingTheVisualStartAnchor() {
        assertEquals(
            "O startHint dentro do primeiro traço principal deve selecionar essa coluna, não o laço vizinho.",
            24f,
            ReferenceImportedFontEngine
                .debugFirstColumnChosenByAnchor()
        )
    }

}
