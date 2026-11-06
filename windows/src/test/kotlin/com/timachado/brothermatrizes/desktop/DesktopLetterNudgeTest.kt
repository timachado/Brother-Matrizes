package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopLetterNudgeTest {
    private fun start(text: String = "AB") =
        DesktopLetterComposer.start(text, 12f, TextStitchStyle.RUNNING)

    @Test
    fun `deslocamento de meia unidade milimetrica preserva transformacoes da letra`() {
        val base = DesktopLetterComposer.update(
            start(), 1, 2.3f, -1.2f, 1.5f, 18f
        )
        val moved = DesktopLetterNudge.move(base, 1, 5, 0)
        val actual = moved.objects[1]
        assertEquals(2.8f, actual.moveXmm, 0.0001f)
        assertEquals(-1.2f, actual.moveYmm, 0.0001f)
        assertEquals(1.5f, actual.scale)
        assertEquals(18f, actual.rotationDegrees)
        assertEquals(base.objects[0], moved.objects[0])
        val shifted = DesktopLetterNudge.move(moved, 1, 0, -5)
        assertEquals(-1.7f, shifted.objects[1].moveYmm, 0.0001f)
    }

    @Test
    fun `vinte passos de meia unidade formam dez milimetros sem deriva`() {
        var layout = start()
        repeat(20) { layout = DesktopLetterNudge.move(layout, 0, 5, 0) }
        assertEquals(10f, layout.objects[0].moveXmm)
        repeat(20) { layout = DesktopLetterNudge.move(layout, 0, -5, 0) }
        assertEquals(0f, layout.objects[0].moveXmm)
    }

    @Test
    fun `pontos da outra camada e undo redo continuam validos`() {
        val original = start()
        val firstPoints = DesktopLetterComposer.render(start("A")).current.points.dropLast(1)
        val modified = DesktopLetterNudge.move(original, 1, 5, 0)
        val rendered = DesktopLetterComposer.render(modified).current
        assertEquals(firstPoints, rendered.points.take(firstPoints.size))
        assertTrue(rendered.endFound)
        assertEquals(1, rendered.points.count { it.command == StitchCommand.END })
        val session = DesktopLetterSession.start(original).record(modified)
        assertEquals(original, session.undo().current)
        assertEquals(modified, session.undo().redo().current)
    }

    @Test
    fun `recusa espacos passos arbitrarios e ultrapassar cem milimetros`() {
        val base = start("A B")
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterNudge.move(base, 1, 5, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterNudge.move(base, 2, 3, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterNudge.move(base, 0, 5, 5)
        }
        val atLimit = DesktopLetterComposer.update(base, 0, 100f, -100f, 1f, 0f)
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterNudge.move(atLimit, 0, 5, 0)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterNudge.move(atLimit, 0, 0, -5)
        }
        assertEquals(99.5f, DesktopLetterNudge.move(atLimit, 0, -5, 0)
            .objects[0].moveXmm, 0.0001f)
    }
}
