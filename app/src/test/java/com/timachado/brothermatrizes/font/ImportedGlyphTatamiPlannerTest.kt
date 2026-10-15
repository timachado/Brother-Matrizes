package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Testes de regressão SEM fontes de terceiros e sem alterar o simulador. */
class ImportedGlyphTatamiPlannerTest {
    private fun box(x0: Float, y0: Float, x1: Float, y1: Float, reversed: Boolean = false):
        List<ImportedGlyphTatamiPlanner.P> {
        val p = ImportedGlyphTatamiPlanner
        val ring = listOf(p.P(x0, y0), p.P(x1, y0), p.P(x1, y1), p.P(x0, y1))
        return if (reversed) ring.reversed() else ring
    }

    @Test
    fun overlappingInkRemainsOneSpan() {
        val spans = ImportedGlyphTatamiPlanner.scanlineSpans(
            listOf(box(0f, 0f, 10f, 10f), box(5f, 0f, 15f, 10f)), 5f
        )
        assertEquals(1, spans.size)
        assertEquals(0f, spans[0].first, 0.001f)
        assertEquals(15f, spans[0].second, 0.001f)
    }

    @Test
    fun holeInGlyphRemainsEmptyAndUsesJump() {
        val outside = box(0f, 0f, 140f, 120f)
        val hole = box(40f, 40f, 100f, 80f, reversed = true)
        val path = ImportedGlyphTatamiPlanner.plan(listOf(outside, hole))
        assertTrue(path.any { it.move == ImportedGlyphTatamiPlanner.Move.STITCH })
        assertTrue(path.count { it.move == ImportedGlyphTatamiPlanner.Move.JUMP } > 1)
        var previous: ImportedGlyphTatamiPlanner.P? = null
        for (move in path) {
            if (previous != null && move.move == ImportedGlyphTatamiPlanner.Move.STITCH) {
                val length = hypot(
                    (move.point.x - previous.x).toDouble(),
                    (move.point.y - previous.y).toDouble()
                ).toFloat()
                assertTrue("Ponto superior a 2,7mm: $length", length <= 27.001f)
                for (i in 1..9) {
                    val t = i / 10f
                    val x = previous.x + (move.point.x - previous.x) * t
                    val y = previous.y + (move.point.y - previous.y) * t
                    assertFalse("Costura invadiu o vazado em $x,$y",
                        x > 40.5f && x < 99.5f && y > 40.5f && y < 79.5f)
                }
            }
            previous = move.point
        }
    }

    @Test
    fun adapterDoesNotInjectMachineFinishingCommands() {
        val v = { x: Float, y: Float -> CleanRoomSatinEngine.V(x, y) }
        val stitches = ImportedGlyphTatamiAdapter.digitize(listOf(
            listOf(v(0f, 0f), v(110f, 0f), v(110f, 65f), v(0f, 65f))
        ))
        assertTrue(stitches.isNotEmpty())
        assertEquals(StitchCommand.JUMP, stitches.first().command)
        assertTrue(stitches.any { it.command == StitchCommand.STITCH })
        assertTrue(stitches.all { it.command == StitchCommand.STITCH ||
            it.command == StitchCommand.JUMP })
        assertFalse("Não ativar o QA sem homologação", ImportedGlyphTatamiAdapter.QA_ONLY_ENABLED)
    }
}
