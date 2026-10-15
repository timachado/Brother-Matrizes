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
                duplicateRailsDiscarded = 0
            )
        )
        val connectorStitches = stitched.filter {
            it.command == StitchCommand.STITCH &&
                it.yUnits == 50 && it.xUnits in 31..89
        }
        println("DIAG-VOID-CONNECTOR crossings=${connectorStitches.size} " +
            "commands=${stitched.joinToString { it.command.toString() + ':' + it.xUnits }}")
        // A existência da costura de travessia é observação do motor atual;
        // não é um requisito correto. Não publicar como homologação.
        assertTrue("Reprodução do conector indevido na implementação atual.",
            connectorStitches.isNotEmpty())
    }
}
