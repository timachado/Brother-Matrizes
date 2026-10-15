package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedGlyphTopologyTest {
    private fun v(x: Float, y: Float) = CleanRoomSatinEngine.V(x, y)
    private fun rect(x: Float, y: Float, w: Float, h: Float) = listOf(
        v(x, y), v(x + w, y), v(x + w, y + h), v(x, y + h)
    )
    private fun rail(ax: Float, ay: Float, bx: Float, by: Float) =
        CleanRoomSatinEngine.Rail(v(ax, ay), v(bx, by))

    @Test
    fun shapedRunKeepsInnerCounterWithItsOwnOutline() {
        val boxes = listOf(
            ImportedOpenTypeContourGroups.Box(0f, 0f, 62f, 80f),
            ImportedOpenTypeContourGroups.Box(45f, 20f, 53f, 55f),
            ImportedOpenTypeContourGroups.Box(68f, 0f, 93f, 75f)
        )
        // O centro do furo aproxima-se da segunda letra, mas pertence
        // ao contorno exterior da primeira letra.
        val owners = ImportedOpenTypeContourGroups.owners(boxes, listOf(12f, 58f, 82f))
        assertEquals(owners[0], owners[1])
        assertEquals(2, owners[2])
    }

    @Test
    fun emptyCounterIsNotAValidSatinTravelConnector() {
        val plan = CleanRoomSatinEngine.planContours(
            listOf(rect(0f, 0f, 125f, 115f), rect(35f, 30f, 55f, 55f).asReversed()),
            densityMm = 0.4f, pullMm = 0f
        )
        val ink = plan.connectorInsideInk ?: error("Sem verificação do contorno")
        assertFalse("Um deslocamento atravessando o furo requer JUMP.",
            ink(v(20f, 55f), v(104f, 55f)))
        assertTrue("Uma barra inteiramente dentro da tinta continua Satin.",
            ink(v(15f, 14f), v(105f, 14f)))
    }

    @Test
    fun blockedConnectorBecomesJumpButBothSatinBarsRemain() {
        // O erro anterior costurava de B(30,50) para C(90,50) pela
        // região vazia, produzindo o cruzamento em forma de teia.
        val left = rail(10f, 50f, 30f, 50f)
        val right = rail(90f, 50f, 110f, 50f)
        val plan = CleanRoomSatinEngine.PlannedGlyph(
            rails = listOf(listOf(left, right)), sampledEdges = 2,
            duplicateRailsDiscarded = 0,
            connectorInsideInk = { a, b ->
                !(a.x < 40f && b.x > 80f)
            }
        )
        val points = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
        assertTrue("A segunda barra deve continuar presente.",
            points.any { it.command == StitchCommand.STITCH && it.xUnits == 110 })
        assertTrue("A primeira barra deve continuar presente.",
            points.any { it.command == StitchCommand.STITCH && it.xUnits == 30 })
        assertTrue("A ligação vazia precisa ser JUMP em vez de STITCH.",
            points.any { it.command == StitchCommand.JUMP && it.xUnits == 90 })
        assertFalse("Não pode existir costura no meio do furo.",
            points.any { it.command == StitchCommand.STITCH &&
                it.xUnits in 48..82 && it.yUnits == 50 })
    }

    @Test
    fun twoLegsOfMKeepTopAndBottomAfterRoutePlanningAndSewing() {
        // M geométrico com quatro traços e junções reais. Não é um
        // desenho inventado para um nome específico.
        val contours = listOf(
            rect(0f, 0f, 23f, 155f),
            listOf(v(15f, 0f), v(29f, 0f), v(86f, 83f), v(72f, 101f)),
            listOf(v(70f, 83f), v(121f, 0f), v(135f, 0f), v(86f, 101f)),
            rect(124f, 0f, 23f, 155f)
        )
        val plan = CleanRoomSatinEngine.planContours(
            contours, densityMm = 0.4f, pullMm = 0.2f
        )
        val rails = plan.rails.flatten()
        assertTrue("A primeira haste superior foi perdida.",
            rails.any { it.middle.x < 26f && it.middle.y < 25f })
        assertTrue("A primeira haste inferior foi perdida.",
            rails.any { it.middle.x < 26f && it.middle.y > 125f })
        assertTrue("A SEGUNDA haste superior foi perdida.",
            rails.any { it.middle.x > 122f && it.middle.y < 25f })
        assertTrue("A SEGUNDA haste inferior foi perdida.",
            rails.any { it.middle.x > 122f && it.middle.y > 125f })

        val sewn = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
            .filter { it.command == StitchCommand.STITCH }
        assertTrue("O arquivo de pontos precisa bordar a base da haste direita.",
            sewn.any { it.xUnits >= 130 && it.yUnits >= 126 })
        assertTrue("O arquivo de pontos precisa bordar o topo da haste direita.",
            sewn.any { it.xUnits >= 130 && it.yUnits <= 25 })
        assertTrue("O arquivo de pontos precisa bordar a base da haste esquerda.",
            sewn.any { it.xUnits <= 15 && it.yUnits >= 126 })
    }

    @Test
    fun shortValidSatinBarsNeverTurnIntoEmptyTravelMoves() {
        val plan = CleanRoomSatinEngine.planContours(
            listOf(rect(0f, 0f, 18f, 110f)),
            densityMm = 0.4f
        )
        val sewn = CleanRoomSatinEngine.stitchPlannedGlyphForTest(plan)
        assertTrue(sewn.count { it.command == StitchCommand.STITCH } >= 20)
        assertTrue("Não deve inserir JUMPs entre todas as barras contíguas.",
            sewn.count { it.command == StitchCommand.JUMP } <= 3)
    }
}
