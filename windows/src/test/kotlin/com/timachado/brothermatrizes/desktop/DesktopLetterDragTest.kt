package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLetterDragTest {
    private fun original() =
        DesktopLetterComposer.start("AB", 12f, TextStitchStyle.RUNNING)

    @Test
    fun `arrastar segunda letra modifica somente seu objeto e tem desfazer`() {
        val before = original()
        val originalDesign = DesktopLetterComposer.render(before)
        val bounds = DesktopMatrixService.preview(originalDesign).bounds
        val width = 640f
        val height = 480f
        val viewport = DesktopPreviewNavigation.viewport(
            bounds, width, height, 1f, Offset.Zero
        )
        val changed = DesktopLetterDrag.move(
            before, 1, bounds, width, height, 1f,
            Offset(viewport.scale * 40f, viewport.scale * -20f)
        )
        assertEquals(before.objects[0], changed.objects[0])
        assertEquals(4f, changed.objects[1].moveXmm)
        assertEquals(-2f, changed.objects[1].moveYmm)
        val edited = DesktopLetterComposer.render(changed)
        val firstLetter = DesktopLetterComposer.render(
            DesktopLetterComposer.start("A", 12f, TextStitchStyle.RUNNING)
        ).current.points.dropLast(1)
        assertEquals(firstLetter, edited.current.points.take(firstLetter.size))
        assertTrue(originalDesign.current.points != edited.current.points)
        val history = DesktopLetterSession.start(before).record(changed)
        assertEquals(before, history.undo().current)
        assertEquals(changed, history.undo().redo().current)
        assertFalse(history.undo().canUndo)
    }

    @Test
    fun `zoom dobrado reduz pela metade distancia em milimetros`() {
        val base = original()
        val bounds = DesktopMatrixService.preview(
            DesktopLetterComposer.render(base)
        ).bounds
        val view = DesktopPreviewNavigation.viewport(
            bounds, 640f, 480f, 1f, Offset.Zero
        )
        val delta = Offset(view.scale * 40f, 0f)
        val zoomOne = DesktopLetterDrag.move(
            base, 0, bounds, 640f, 480f, 1f, delta
        )
        val zoomTwo = DesktopLetterDrag.move(
            base, 0, bounds, 640f, 480f, 2f, delta
        )
        assertEquals(4f, zoomOne.objects[0].moveXmm)
        assertEquals(2f, zoomTwo.objects[0].moveXmm)
    }

    @Test
    fun `arrasto insignificante nao cria revisao`() {
        val base = original()
        val bounds = DesktopMatrixService.preview(
            DesktopLetterComposer.render(base)
        ).bounds
        val same = DesktopLetterDrag.move(
            base, 0, bounds, 640f, 480f, 1f, Offset.Zero
        )
        assertEquals(base, same)
        assertFalse(DesktopLetterSession.start(base).record(same).canUndo)
    }

    @Test
    fun `bloqueia movimento extremo espacos e valores invalidos`() {
        val base = DesktopLetterComposer.start(
            "A B", 12f, TextStitchStyle.RUNNING
        )
        val bounds = DesktopMatrixService.preview(
            DesktopLetterComposer.render(base)
        ).bounds
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterDrag.move(
                base, 1, bounds, 640f, 480f, 1f, Offset.Zero
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterDrag.move(
                base, 0, bounds, 640f, 480f, 1f, Offset(100000f, 0f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterDrag.move(
                base, 0, bounds, 640f, 480f, 1f, Offset(Float.NaN, 0f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterDrag.move(
                base, 0, bounds, 0f, 480f, 1f, Offset(10f, 0f)
            )
        }
    }
}
