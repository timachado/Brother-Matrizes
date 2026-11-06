package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFreehandEditSessionTest {
    private fun stroke(i: Int) = listOf(
        VectorPoint(0.1f, (i % 10) / 10f), VectorPoint(0.8f, (i % 10) / 10f)
    )

    @Test
    fun `desfazer refazer e limpar sao reversiveis`() {
        val a = stroke(1)
        val b = stroke(2)
        val start = DesktopFreehandEditSession.start()
        val two = start.record(listOf(a)).record(listOf(a, b))
        assertEquals(listOf(a, b), two.current)
        assertEquals(listOf(a), two.undo().current)
        assertEquals(listOf(a, b), two.undo().redo().current)
        val cleared = two.record(emptyList())
        assertEquals(emptyList(), cleared.current)
        assertEquals(listOf(a, b), cleared.undo().current)
        assertFalse(start.canUndo)
    }

    @Test
    fun `nova edicao apos desfazer descarta o ramo futuro`() {
        val a = stroke(1)
        val b = stroke(2)
        val c = stroke(3)
        val branch = DesktopFreehandEditSession.start()
            .record(listOf(a)).record(listOf(a, b))
            .undo().record(listOf(a, c))
        assertEquals(listOf(a, c), branch.current)
        assertFalse(branch.canRedo)
    }

    @Test
    fun `oito revisoes e sessao reaberta limpa`() {
        var session = DesktopFreehandEditSession.start()
        repeat(15) { index ->
            session = session.record(session.current + listOf(stroke(index)))
        }
        repeat(DesktopFreehandEditSession.MAX_UNDO) { session = session.undo() }
        assertFalse(session.canUndo)
        assertTrue(session.canRedo)
        val reopened = DesktopFreehandEditSession.start(listOf(stroke(2)))
        assertFalse(reopened.canUndo)
        assertFalse(reopened.canRedo)
    }

    @Test
    fun `rejeita excesso e coordenadas invalidas`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopFreehandEditSession.start(
                List(DesktopDrawingService.MAX_STROKES + 1) { stroke(it) }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopFreehandEditSession.start(
                listOf(listOf(VectorPoint(Float.NaN, 0f), VectorPoint(1f, 1f)))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopFreehandEditSession.start(listOf(listOf(VectorPoint(0f, 0f))))
        }
    }
}
