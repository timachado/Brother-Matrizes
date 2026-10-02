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
    fun firstSatinRegionKeepsStableSpatialOrderEvenWithMisleadingHint() {
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
            "A primeira coluna precisa permanecer na região espacial esquerda; o startHint só orienta a entrada.",
            first.xUnits <=
                20
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
    fun centerUnderlayCompletesForwardAndReturnBeforeSatin() {
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
            "O underlay de referência precisa completar a ida e o retorno antes da cobertura Satin.",
            xSequence.zipWithNext()
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
}
