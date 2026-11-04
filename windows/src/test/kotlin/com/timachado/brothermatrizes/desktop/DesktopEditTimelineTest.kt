package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Regressão não destrutiva: o histórico existe SOMENTE durante a sessão. */
class DesktopEditTimelineTest {
    private fun initial() = DesktopMatrixService.createName("QA", 12f)

    @Test
    fun `desfazer e refazer recompõem exatamente os pontos pelo motor oficial`() {
        val base = initial()
        val originalPoints = base.current.points.toList()
        val shift = EditTransform(scale = 1.1f, offsetXUnits = 20)
        val rotate = EditTransform(rotationDegrees = 15f)

        val shifted = DesktopMatrixService.edit(base, shift)
        val rotated = DesktopMatrixService.edit(shifted, rotate)
        val timeline = DesktopEditTimeline.start(base)
            .record(DesktopEditAction.Transform(shift))
            .record(DesktopEditAction.Transform(rotate))

        assertTrue(timeline.canUndo)
        assertFalse(timeline.canRedo)
        assertEquals(2, timeline.position)
        val undoOne = timeline.undo()
        assertEquals(shifted.current.points, undoOne.document.current.points)
        val undoTwo = undoOne.timeline.undo()
        assertEquals(originalPoints, undoTwo.document.current.points)
        assertFalse(undoTwo.timeline.canUndo)
        assertTrue(undoTwo.timeline.canRedo)

        val redoOne = undoTwo.timeline.redo()
        assertEquals(shifted.current.points, redoOne.document.current.points)
        val redoTwo = redoOne.timeline.redo()
        assertEquals(rotated.current.points, redoTwo.document.current.points)
        assertFalse(redoTwo.timeline.canRedo)

        // A matriz-base e a sequência original não são modificadas.
        assertEquals(originalPoints, base.original.points)
        assertEquals(base.original, redoTwo.document.original)
    }

    @Test
    fun `nova edicao apos desfazer invalida a pilha de refazer`() {
        val base = initial()
        val a = EditTransform(offsetXUnits = 10)
        val b = EditTransform(offsetYUnits = 15)
        val c = EditTransform(rotationDegrees = -15f)
        val history = DesktopEditTimeline.start(base)
            .record(DesktopEditAction.Transform(a))
            .record(DesktopEditAction.Transform(b))
            .undo().timeline
            .record(DesktopEditAction.Transform(c))

        assertEquals(2, history.total)
        assertFalse(history.canRedo)
        assertEquals(
            DesktopMatrixService.edit(DesktopMatrixService.edit(base, a), c).current.points,
            history.undo().timeline.redo().document.current.points
        )
        assertFailsWith<IllegalStateException> { history.redo() }
    }

    @Test
    fun `restaurar original e reversivel e nao apaga as operacoes anteriores`() {
        val base = initial()
        val operation = EditTransform(scale = 1.25f, offsetYUnits = 25)
        val changed = DesktopMatrixService.edit(base, operation)
        val history = DesktopEditTimeline.start(base)
            .record(DesktopEditAction.Transform(operation))
            .record(DesktopEditAction.RestoreOriginal)

        val restored = history.undo().timeline.redo()
        assertEquals(base.original.points, restored.document.current.points)
        val beforeRestore = restored.timeline.undo()
        assertEquals(changed.current.points, beforeRestore.document.current.points)
    }

    @Test
    fun `histórico mantém só oito edições sem duplicar cópias de matrizes`() {
        val base = initial()
        val step = EditTransform(offsetXUnits = 10)
        var history = DesktopEditTimeline.start(base)
        var expected = base
        repeat(11) {
            expected = DesktopMatrixService.edit(expected, step)
            history = history.record(DesktopEditAction.Transform(step))
        }
        assertEquals(DesktopEditTimeline.MAX_ACTIONS, history.total)
        assertEquals(DesktopEditTimeline.MAX_ACTIONS, history.position)
        val final = history.undo().timeline.redo().document
        assertEquals(expected.current.points, final.current.points)

        // Ao esgotar o histórico, a linha de base já contém 3 ações.
        repeat(DesktopEditTimeline.MAX_ACTIONS) {
            history = history.undo().timeline
        }
        assertFalse(history.canUndo)
        var afterThree = base
        repeat(3) { afterThree = DesktopMatrixService.edit(afterThree, step) }
        assertEquals(afterThree.current.points, history.redo().timeline.undo().document.current.points)
    }

    @Test
    fun `abrir outro documento inicia historico separado`() {
        val first = initial()
        val changed = DesktopEditTimeline.start(first)
            .record(DesktopEditAction.Transform(EditTransform(offsetXUnits = 10)))
        assertTrue(changed.canUndo)
        val newSession = DesktopEditTimeline.start(DesktopMatrixService.createName("OUTRO", 15f))
        assertFalse(newSession.canUndo)
        assertFalse(newSession.canRedo)
        assertFailsWith<IllegalStateException> { newSession.undo() }
    }
}
