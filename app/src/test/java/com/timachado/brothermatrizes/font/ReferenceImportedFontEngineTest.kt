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
                        0f to 18f,
                        8f to 9f,
                        16f to 14f,
                        24f to 40f,
                        34f to 15f,
                        50f to 8f,
                        70f to 35f,
                        84f to 12f,
                        96f to 20f
                    )
                )

        assertEquals(
            "O início visual deve ficar no pé inferior esquerdo do primeiro traço, não na extremidade esquerda do laço.",
            24f,
            start?.first
        )

        assertEquals(
            40f,
            start?.second
        )
    }

    @Test
    fun cursiveVisualStartSkipsIsolatedLeadingFlourishCluster() {
        val start =
            ReferenceImportedFontEngine
                .debugGlyphVisualStartPoint(
                    listOf(
                        0f to 12f,
                        5f to 40f,
                        10f to 13f,
                        16f to 8f,
                        24f to 38f,
                        32f to 10f,
                        45f to 8f,
                        64f to 35f,
                        74f to 10f,
                        90f to 18f
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
            40f,
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

        val firstStitch =
            points.first {
                it.command ==
                    StitchCommand.STITCH
            }

        assertEquals(
            "O JUMP inicial deve posicionar diretamente no ponto em que a primeira pontada começa.",
            first.xUnits,
            firstStitch.xUnits
        )

        assertEquals(
            first.yUnits,
            firstStitch.yUnits
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


    @Test
    fun structuralSatinStartSkipsCurvedLeadingFlourish() {
        val x =
            ReferenceImportedFontEngine
                .debugStructuralSatinStartX()

        assertTrue(
            "O início Satin estrutural não pode ser nulo.",
            x !=
                null
        )

        assertEquals(
            "O início Satin deve cair no primeiro traço estrutural após o floreio curvo.",
            24f,
            x
                ?: Float.NaN,
            1.5f
        )
    }


    @Test
    fun firstSatinColumnStartsAtVisualLowerFootAndRunsUpward() {
        val points =
            ReferenceImportedFontEngine
                .debugFirstColumnInsetPath()

        val firstJump =
            points.first()

        val firstStitch =
            points.first {
                it.command ==
                    StitchCommand.STITCH
            }

        assertEquals(
            StitchCommand.JUMP,
            firstJump.command
        )

        assertEquals(
            "A primeira coluna deve manter o eixo central do traço selecionado.",
            10,
            firstJump.xUnits
        )

        assertEquals(
            "A primeira coluna deve começar pelo pé visual inferior (menor yUnits para sourceYAxisDown=false).",
            0,
            firstJump.yUnits
        )

        assertEquals(
            firstJump.xUnits,
            firstStitch.xUnits
        )

        assertEquals(
            firstJump.yUnits,
            firstStitch.yUnits
        )

        val stitches =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        val farIndex =
            stitches.indexOfFirst {
                it.yUnits >=
                    70
            }

        assertTrue(
            "O center-run deve subir do pé até o extremo superior antes de iniciar a cobertura Satin.",
            farIndex >
                0
        )

        assertTrue(
            "Depois de alcançar o extremo superior, a cobertura Satin deve retornar em direção ao pé.",
            stitches
                .drop(
                    farIndex +
                        1
                )
                .any {
                    it.yUnits <=
                        10
                }
        )

        assertTrue(
            "A cobertura Satin deve voltar até a extremidade original; o floreio não pode ser removido.",
            points.any {
                it.command ==
                    StitchCommand.STITCH &&
                    it.yUnits ==
                    0
            }
        )
    }


    @Test
    fun greatVibesLikeInvertedCoordinatesChooseFirstStructuralFoot() {
        val start =
            ReferenceImportedFontEngine
                .debugGlyphVisualStartPoint(
                    listOf(
                        18f to -328f,
                        73f to -313f,
                        142f to -139f,
                        226f to -139f,
                        398f to 43f,
                        427f to 66f,
                        481f to 36f,
                        627f to -290f,
                        789f to 10f,
                        821f to 34f,
                        883f to -20f,
                        1206f to -49f,
                        1365f to 115f,
                        1403f to 105f,
                        1461f to -614f
                    )
                )

        assertTrue(
            "Com Y interno invertido, o primeiro pé estrutural do M deve ficar perto de x=398..430, não no laço esquerdo.",
            start !=
                null &&
                start.first in
                    390f..435f
        )
    }

}
