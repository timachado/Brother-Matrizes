package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeDesignImportedFontEngineTest {

    @Test
    fun samplerChoosesNarrowerAxisForWideRectangle() {
        val widths =
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
    fun productionScriptMStartsAtInnerFootBeforeOuterFlourish() {
        assertEquals(
            "A entrada do M cursivo deve usar o vale interno do glifo e não o laço externo.",
            24f to 40f,
            PeDesignImportedFontEngine.debugProductionFlourishEntry()
        )
    }

    @Test
    fun productionGeneratorUsesStructuralFirstGlyphEntry() {
        val (first, continuesFromNeedle) =
            PeDesignImportedFontEngine.debugProductionStartHint()

        assertEquals(
            "O pé estrutural, não o laço da borda esquerda, deve iniciar a primeira letra.",
            40f to 64f,
            first
        )
        assertTrue(
            "As letras seguintes devem continuar da posição atual da agulha.",
            continuesFromNeedle
        )
    }

    @Test
    fun firstSatinRegionStartsNearestToTheGlyphStartHint() {
        val points =
            PeDesignImportedFontEngine
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
    fun nearbyConnectedColumnsContinueWithStitchesWithoutJumpOrTrim() {
        val points =
            PeDesignImportedFontEngine
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
            "Trechos adjacentes dentro do mesmo glifo devem manter apenas o JUMP inicial de posicionamento.",
            1,
            points.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )

        assertTrue(
            "Transição curta e interna entre colunas não deve cortar a linha.",
            points.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }

    @Test
    fun disconnectedColumnsJumpInsteadOfCrossingEmptyArea() {
        val points =
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        val with =
            PeDesignImportedFontEngine
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
    fun adjacentSatinColumnsContinueFromNearestEndWithoutJumpingBackToTop() {
        val points =
            PeDesignImportedFontEngine
                .debugReferencePath(
                    connected =
                        true,
                    includeUnderlay =
                        false
                )

        assertEquals(
            "Trechos adjacentes e conectados devem manter somente o JUMP inicial.",
            1,
            points.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )

        val entry =
            PeDesignImportedFontEngine
                .debugSerpentineSecondColumnEntry()

        assertEquals(
            "Ao continuar na segunda coluna, a entrada deve permanecer no lado A da linha Satin.",
            30,
            entry?.first
        )

        assertEquals(
            "Terminando a primeira coluna embaixo, a próxima deve entrar pelo extremo inferior.",
            20,
            entry?.second
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
            PeDesignImportedFontEngine
                .debugReadingOrderColumnLeftEdges()
        )
    }

    @Test
    fun disconnectedShapesCreateMoreThanOneColumn() {
        val count =
            PeDesignImportedFontEngine
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
    fun largeAdamiyaLikeFlourishFindsLowerCurveNotLeftOuterEdge() {
        // Curva sintetica com a mesma escala do floreio Adamiya;
        // nao redistribui o arquivo de fonte enviado pelo usuario.
        val loop = (0 until 720).map { index ->
            val angle = index * (2.0 * Math.PI / 720.0)
            val x = 260f + 230f * kotlin.math.cos(angle).toFloat()
            val y = -220f + 350f * kotlin.math.sin(angle).toFloat()
            x to y
        }

        val start = PeDesignImportedFontEngine
            .debugGlyphVisualStartPoint(loop)

        assertTrue(
            "O inicio do floreio deve ficar no pe inferior, nao na lateral esquerda.",
            start != null &&
                start.first in 250f..270f &&
                start.second in 120f..135f
        )
    }

    @Test
    fun cursiveVisualStartPrefersLowerEntryStrokeInsteadOfLeftmostLoopExtremity() {
        val start =
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
                .debugFirstColumnChosenByAnchor()
        )
    }


    @Test
    fun structuralSatinStartSkipsCurvedLeadingFlourish() {
        val x =
            PeDesignImportedFontEngine
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
            PeDesignImportedFontEngine
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
            "Em coordenadas Android a base da coluna e o MAIOR valor de Y.",
            140,
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
                it.yUnits <=
                    70
            }

        assertTrue(
            "O center-run deve subir do pé até o extremo superior antes de iniciar a cobertura Satin.",
            farIndex >
                0
        )

        assertTrue(
            "Depois de alcancar o topo, a cobertura Satin deve retornar ao pe.",
            stitches
                .drop(
                    farIndex +
                        1
                )
                .any {
                    it.yUnits >=
                        130
                }
        )

        assertTrue(
            "A cobertura Satin tambem deve preservar o extremo inferior.",
            points.any {
                it.command ==
                    StitchCommand.STITCH &&
                    it.yUnits ==
                    140
            }
        )
    }


    @Test
    fun greatVibesLikeInvertedCoordinatesChooseFirstStructuralFoot() {
        val start =
            PeDesignImportedFontEngine
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


    @Test
    fun adaptiveSatinRotatesRowsWithStrokeDirection() {
        val (
            horizontal,
            vertical
        ) =
            PeDesignImportedFontEngine
                .debugAdaptiveFlowOrientationCounts()

        assertTrue(
            "O motor adaptativo deve produzir linhas Satin horizontais em trechos verticais do traço.",
            horizontal >
                0
        )

        assertTrue(
            "O motor adaptativo deve girar as linhas Satin e produzir linhas verticais em trechos horizontais.",
            vertical >
                0
        )
    }


    @Test
    fun adaptiveSatinReachesStrokeEndCaps() {
        val (
            minY,
            maxY
        ) =
            PeDesignImportedFontEngine
                .debugAdaptiveFlowEnvelope()

        assertTrue(
            "O fluxo adaptativo deve alcançar o cap inferior do traço.",
            minY <=
                2f
        )

        assertTrue(
            "O fluxo adaptativo deve alcançar o cap superior do traço.",
            maxY >=
                98f
        )
    }

    @Test
    fun axisReferenceUnderlayRunsToFarCenterAndBackBeforeSatin() {
        val points =
            PeDesignImportedFontEngine
                .debugAxisReferenceEmissionPath(
                    includeUnderlay =
                        true
                )

        assertEquals(
            StitchCommand.JUMP,
            points.first()
                .command
        )

        assertEquals(
            0,
            points.first()
                .xUnits
        )

        assertEquals(
            0,
            points.first()
                .yUnits
        )

        val stitches =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertEquals(
            "A primeira passada central deve sair do rail A para o centro da primeira row.",
            10 to 0,
            stitches[0]
                .let {
                    it.xUnits to
                        it.yUnits
                }
        )

        assertEquals(
            "O center-run deve alcançar o centro da última row.",
            10 to 30,
            stitches[1]
                .let {
                    it.xUnits to
                        it.yUnits
                }
        )

        assertEquals(
            "O underlay da referência retorna pelo centro antes de iniciar o Satin.",
            10 to 0,
            stitches[2]
                .let {
                    it.xUnits to
                        it.yUnits
                }
        )
    }

    @Test
    fun axisReferenceSatinKeepsForwardRowsAndRailAForLocks() {
        val points =
            PeDesignImportedFontEngine
                .debugAxisReferenceEmissionPath(
                    includeUnderlay =
                        false
                )

        val stitches =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        val coordinates =
            stitches.map {
                it.xUnits to
                    it.yUnits
            }

        assertTrue(
            "A trava inicial precisa usar A -> A+0,6 mm -> A.",
            coordinates
                .take(
                    3
                ) ==
                listOf(
                    0 to 0,
                    6 to 0,
                    0 to 0
                )
        )

        /*
         * O primeiro A coincide com a última perfuração da trava e, por isso,
         * não deve ser emitido novamente. A cobertura segue diretamente para B.
         */
        assertEquals(
            "A primeira row Satin deve seguir para B sem martelar A duas vezes.",
            20 to 0,
            coordinates[
                3
            ]
        )

        assertTrue(
            "A última row deve ser alcançada em ordem direta.",
            coordinates.windowed(
                2
            ).any {
                    pair ->
                pair ==
                    listOf(
                        0 to 30,
                        20 to 30
                    )
            }
        )
    }


    @Test
    fun connectedObjectFinishesBeforeCloserSeparatedLeg() {
        val selectedLeftEdge =
            PeDesignImportedFontEngine
                .debugConnectedObjectWinsOverCloserUnrelatedLeg()

        org.junit.Assert.assertEquals(
            "O motor PE-DESIGN-style deve continuar no mesmo objeto antes de puxar uma perna separada só porque ela está mais perto.",
            20f,
            selectedLeftEdge,
            0.01f
        )
    }


    @Test
    fun junctionKeepsStraightSatinColumnTogetherBeforeSideBranch() {
        val paths =
            PeDesignImportedFontEngine
                .debugFlowJunctionContinuity()

        assertEquals(
            "Um entroncamento em T deve resultar em uma coluna contínua mais um ramo lateral.",
            2,
            paths.size
        )

        val horizontal =
            paths.firstOrNull {
                    path ->
                val endpoints =
                    setOf(
                        path.firstOrNull(),
                        path.lastOrNull()
                    )

                endpoints ==
                    setOf(
                        10 to 0,
                        10 to 20
                    )
            }

        assertTrue(
            "Os lados esquerdo e direito do mesmo traço devem permanecer na mesma coluna Satin.",
            horizontal !=
                null
        )

        assertTrue(
            "A coluna contínua precisa atravessar o nó central sem ser interrompida.",
            horizontal?.contains(
                10 to 10
            ) ==
                true
        )

        val branch =
            paths.firstOrNull {
                    path ->
                (0 to 10) in
                    path
            }

        assertTrue(
            "O ramo lateral deve permanecer separado para ser trabalhado somente depois.",
            branch !=
                null
        )
    }


    @Test
    fun curvedYJunctionKeepsMainSatinFlowTogetherBeforeSideBranch() {
        val paths =
            PeDesignImportedFontEngine
                .debugCurvedYJunctionContinuity()

        assertEquals(
            "Uma bifurcação curva em Y deve resultar em uma coluna principal contínua e um ramo lateral.",
            2,
            paths.size
        )

        val main =
            paths.firstOrNull {
                    path ->
                val endpoints =
                    setOf(
                        path.firstOrNull(),
                        path.lastOrNull()
                    )

                endpoints ==
                    setOf(
                        20 to 0,
                        0 to 20
                    )
            }

        assertTrue(
            "Os dois lados curvos do mesmo traço precisam permanecer na mesma coluna Satin.",
            main !=
                null
        )

        assertTrue(
            "A coluna curva deve atravessar o nó central antes de liberar o ramo lateral.",
            main?.contains(
                10 to 10
            ) ==
                true
        )
    }


    @Test
    fun activeImportedSatinSamplerUsesCompactGeometryBudget() {
        val (
            compactRows,
            _
        ) =
            PeDesignImportedFontEngine
                .debugCompactVsAdaptiveRowCounts()

        assertTrue(
            "A rota ativa compacta precisa produzir linhas Satin válidas.",
            compactRows >
                0
        )

        assertTrue(
            "O traço sintético em L não pode explodir em dezenas de rows redundantes.",
            compactRows <=
                32
        )
    }


    @Test
    fun generatedGeometryCentersVisibleStitchesInsteadOfJumpTravel() {
        val (
            center,
            jump
        ) =
            PeDesignImportedFontEngine
                .debugCenteredVisibleGeometry()

        assertEquals(
            "O centro dos pontos realmente costurados deve ficar em 0,0.",
            0 to 0,
            center
        )

        assertTrue(
            "O JUMP pode continuar fora da área visível sem deslocar o centro do bordado.",
            jump.first <
                -100
        )
    }

    @Test
    fun emitterDoesNotRepeatConsecutiveStitchAtSameCoordinate() {
        val stitches =
            PeDesignImportedFontEngine
                .debugAxisReferenceEmissionPath(
                    includeUnderlay =
                        false
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        val repeated =
            stitches.windowed(
                2
            ).any {
                    pair ->
                pair[0].xUnits ==
                    pair[1].xUnits &&
                pair[0].yUnits ==
                    pair[1].yUnits
            }

        assertTrue(
            "O motor não deve perfurar duas vezes seguidas exatamente a mesma coordenada.",
            !repeated
        )
    }


    @Test
    fun skeletonCornerDoesNotCreateRedundantDiagonalFlowPath() {
        val pathCount =
            PeDesignImportedFontEngine
                .debugSkeletonCornerPathCount()

        assertEquals(
            "Uma quina do skeleton deve continuar como um único caminho, sem triângulo diagonal redundante.",
            1,
            pathCount
        )
    }

}
