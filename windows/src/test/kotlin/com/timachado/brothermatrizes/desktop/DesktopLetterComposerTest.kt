package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLetterComposerTest {
    private fun start(text: String = "AB"): DesktopLetterLayout =
        DesktopLetterComposer.start(text, 12f, TextStitchStyle.RUNNING)

    @Test
    fun `composicao com duas letras gera pontos validos e um unico END`() {
        val layout = start("AB")
        val document = DesktopLetterComposer.render(layout)
        assertTrue(document.current.stitchCount > 0)
        assertTrue(document.current.endFound)
        assertEquals(1, document.current.points.count { it.command == StitchCommand.END })
        assertEquals(StitchCommand.END, document.current.points.last().command)
        assertEquals(document.current.points, document.original.points)
        assertEquals("AB", document.current.label)
        assertTrue(document.current.jumpCount >= 2)
    }

    @Test
    fun `mover apenas a segunda letra preserva pontadas da primeira`() {
        val first = DesktopLetterComposer.render(start("A")).current.points.dropLast(1)
        val original = DesktopLetterComposer.render(start("AB")).current.points
        assertEquals(first, original.take(first.size))
        val changed = DesktopLetterComposer.update(
            start("AB"), 1, moveXmm = 9f, moveYmm = 4f,
            scale = 1.25f, rotationDegrees = 15f
        )
        val altered = DesktopLetterComposer.render(changed).current.points
        assertEquals(first, altered.take(first.size))
        assertTrue(original.drop(first.size) != altered.drop(first.size))
        assertEquals(1, altered.count { it.command == StitchCommand.END })
    }

    @Test
    fun `historico de parametros desfaz e refaz sem copiar pontos`() {
        val first = start("AB")
        val moved = DesktopLetterComposer.update(first, 0, 12f, -5f, 1.2f, 30f)
        val history = DesktopLetterSession.start(first).record(moved)
        assertTrue(history.canUndo)
        val undone = history.undo()
        assertEquals(first, undone.current)
        assertTrue(undone.canRedo)
        val redone = undone.redo()
        assertEquals(moved, redone.current)
        assertEquals(
            DesktopLetterComposer.render(moved).current.points,
            DesktopLetterComposer.render(redone.current).current.points
        )
        assertFalse(redone.canRedo)
    }

    @Test
    fun `nova edicao depois de desfazer descarta redo e limita historico`() {
        val base = start("AB")
        var history = DesktopLetterSession.start(base)
        repeat(15) { index ->
            val changed = DesktopLetterComposer.update(
                history.current, 0, index + 1f, 0f, 1f, 0f
            )
            history = history.record(changed)
        }
        repeat(DesktopLetterSession.MAX_UNDO) {
            history = history.undo()
        }
        assertFalse(history.canUndo)
        val replaced = DesktopLetterComposer.update(
            history.current, 0, -3f, 0f, 1f, 0f
        )
        history = history.record(replaced)
        assertFalse(history.canRedo)
        assertEquals(replaced, history.current)
    }

    @Test
    fun `recusa caracteres indefinidos e parametros fora dos limites`() {
        for (bad in listOf("", "          ", "ABCDEFGHIJK", "ABC1", "A?", "漢字")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopLetterComposer.start(bad, 12f, TextStitchStyle.RUNNING)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterComposer.start("AB", Float.NaN, TextStitchStyle.RUNNING)
        }
        val layout = start("A B")
        assertEquals(2, layout.editableIndices.size)
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterComposer.update(layout, 1, 0f, 0f, 1f, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterComposer.update(layout, 0, 0f, 0f, 3f, 0f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterComposer.update(layout, 0, 0f, 0f, 1f, Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterComposer.update(layout, 0, 101f, 0f, 1f, 0f)
        }
    }

    @Test
    fun `composicao de letras pode salvar e reabrir FLP sem alterar pontadas`() {
        val original = DesktopLetterComposer.render(
            DesktopLetterComposer.update(start("BOM"), 1, 4f, 0f, 1.1f, -10f)
        )
        val folder = Files.createTempDirectory("brother-letters-qa-")
        val file = folder.resolve("composicao.flp")
        try {
            DesktopProjectService.saveNew(original, file)
            val opened = DesktopProjectService.open(file)
            assertEquals(original.current.points, opened.current.points)
            assertEquals(original.current.threadColors, opened.current.threadColors)
            assertFailsWith<Exception> { DesktopProjectService.saveNew(original, file) }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(folder)
        }
    }
}
