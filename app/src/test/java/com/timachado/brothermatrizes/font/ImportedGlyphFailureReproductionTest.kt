package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Reproduz o relato visual de 15/10/2026 sem modificar o motor ou simulador.
 * Diagnóstico: o texto completo tem uma capital M mais alta que o pingo do i.
 * O resgate de pontos isolados usa a região maior como referência.
 * Este teste imprime métricas de ambos os cenários para o log da CI.
 */
class ImportedGlyphFailureReproductionTest {
    private fun rect(x: Float, y: Float, w: Float, h: Float) = listOf(
        CleanRoomSatinEngine.V(x, y),
        CleanRoomSatinEngine.V(x + w, y),
        CleanRoomSatinEngine.V(x + w, y + h),
        CleanRoomSatinEngine.V(x, y + h)
    )

    @Test
    fun compareSeparatedIDotWithSameDotUnderTallerMInCompleteRun() {
        val capitalM = rect(0f, -55f, 28f, 150f)
        val iStem = rect(90f, 0f, 15f, 90f)
        val iDot = rect(92f, -27f, 11f, 9f)
        val local = CleanRoomSatinEngine.planContours(
            listOf(iStem, iDot), densityMm = 0.4f, pullMm = 0.2f
        )
        val global = CleanRoomSatinEngine.planContours(
            listOf(capitalM, iStem, iDot), densityMm = 0.4f, pullMm = 0.2f
        )

        fun rails(plan: CleanRoomSatinEngine.PlannedGlyph) =
            plan.rails.flatten().filter { it.middle.x in 88f..107f &&
                it.middle.y in -29f..-16f }
        fun dotStitches(plan: CleanRoomSatinEngine.PlannedGlyph) =
            CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
                .filter { it.command == StitchCommand.STITCH &&
                    it.xUnits in 88..107 && it.yUnits in -29..-16 }
        val localRails = rails(local)
        val globalRails = rails(global)
        val localStitches = dotStitches(local)
        val globalStitches = dotStitches(global)
        println("DIAG-I-DOT local_rails=${localRails.size} " +
            "full_run_rails=${globalRails.size} " +
            "local_heights=${localRails.map { it.middle.y.roundToInt() }.distinct().size} " +
            "full_run_heights=${globalRails.map { it.middle.y.roundToInt() }.distinct().size} " +
            "local_stitches=${localStitches.size} " +
            "full_run_stitches=${globalStitches.size}")
        assertTrue("Dot must be present in separated positive control.",
            localRails.isNotEmpty() && localStitches.isNotEmpty())
        assertTrue("Dot must not disappear in full run.",
            globalRails.isNotEmpty() && globalStitches.isNotEmpty())
        assertTrue("Pingo do i precisa de pelo menos 3 faixas em alturas diferentes no texto completo.",
            globalRails.map { it.middle.y.roundToInt() }.distinct().size >= 3)
    }

    @Test
    fun motorStitchesBetweenFarApartRailsInsideSameRouteWithoutJump() {
        val first = CleanRoomSatinEngine.Rail(
            CleanRoomSatinEngine.V(10f, 50f), CleanRoomSatinEngine.V(30f, 50f)
        )
        val second = CleanRoomSatinEngine.Rail(
            CleanRoomSatinEngine.V(90f, 50f), CleanRoomSatinEngine.V(110f, 50f)
        )
        val stitched = CleanRoomSatinEngine.stitchPlannedGlyphForTest(
            CleanRoomSatinEngine.PlannedGlyph(
                rails = listOf(listOf(first, second)),
                sampledEdges = 2,
                duplicateRailsDiscarded = 0,
                // O raster de uma faixa isolada à esquerda e outra à direita
                // contém um vão de 60 unidades entre 30 e 90.
                connectorInsideInk = { a, b ->
                    !(a.x <= 30f && b.x >= 90f)
                }
            )
        )
        val stitchedConnector = stitched.any {
            it.command == StitchCommand.STITCH && it.xUnits == 90 && it.yUnits == 50
        }
        val jumpedConnector = stitched.any {
            it.command == StitchCommand.JUMP && it.xUnits == 90 && it.yUnits == 50
        }
        println("DIAG-VOID-CONNECTOR stitched=${stitchedConnector} " +
            "jumped=${jumpedConnector} " +
            "commands=${stitched.joinToString { it.command.toString() + ':' + it.xUnits }}")
        // Critério de homologação: não deve costurar atravessando o vazio.
        // O código comercial atualmente FALHA este requisito. O teste
        // permanece vermelho até a correção ser autorizada e validada.
        assertTrue("Não costurar travessia vazia: usar JUMP.",
            jumpedConnector && !stitchedConnector)
    }
    @Test
    fun realMaskDetectsGapThroughHoleWithoutDiscardingSatinRails() {
        // Vazado interior: o caminho de deslocamento não deve costurar o furo.
        val contours = listOf(
            rect(0f, 0f, 125f, 115f),
            rect(35f, 30f, 55f, 55f).asReversed()
        )
        val plan = CleanRoomSatinEngine.planContours(
            contours, densityMm = 0.4f, pullMm = 0f
        )
        val inside = plan.connectorInsideInk ?: error("Sem verificador de tinta")
        assertTrue("Percurso dentro da letra deve continuar costurado.",
            inside(CleanRoomSatinEngine.V(15f, 14f),
                CleanRoomSatinEngine.V(105f, 14f)))
        org.junit.Assert.assertFalse("Percurso sobre vazado requer JUMP.",
            inside(CleanRoomSatinEngine.V(20f, 55f),
                CleanRoomSatinEngine.V(104f, 55f)))
    }

    @Test
    fun nearSatinBarsStayStitchedWithoutExcessiveJumpCommands() {
        val plan = CleanRoomSatinEngine.planContours(
            listOf(rect(0f, 0f, 18f, 110f)), densityMm = 0.4f
        )
        val points = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
        assertTrue(points.count { it.command == StitchCommand.STITCH } >= 20)
        assertTrue("O guard não deve inserir saltos a cada barra Satin.",
            points.count { it.command == StitchCommand.JUMP } <= 3)
    }

}
