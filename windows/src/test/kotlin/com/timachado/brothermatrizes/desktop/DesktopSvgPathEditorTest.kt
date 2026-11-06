package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopSvgPathEditorTest {
    private val artwork = VectorArtwork(
        paths = listOf(
            VectorPath(listOf(VectorPoint(0f, 0f), VectorPoint(100f, 0f))),
            VectorPath(listOf(VectorPoint(10f, 80f), VectorPoint(90f, 80f)))
        ),
        label = "QA"
    )
    private val width = 440f
    private val height = 240f

    @Test
    fun `selecao identifica somente caminho proximo ao ponteiro`() {
        val transform = DesktopSvgPathEditor.viewport(artwork, width, height)
        assertEquals(0, DesktopSvgPathEditor.select(
            artwork, width, height, transform.point(VectorPoint(50f, 0f))
        ))
        assertEquals(1, DesktopSvgPathEditor.select(
            artwork, width, height, transform.point(VectorPoint(50f, 80f))
        ))
        assertNull(DesktopSvgPathEditor.select(
            artwork, width, height, transform.point(VectorPoint(50f, 40f))
        ))
    }

    @Test
    fun `arrasto modifica apenas um caminho sem alterar original`() {
        val transform = DesktopSvgPathEditor.viewport(artwork, width, height)
        val delta = Offset(transform.scale * 7.5f, transform.scale * 5f)
        val changed = DesktopSvgPathEditor.move(
            artwork, 1, width, height, delta
        )
        assertEquals(artwork.paths[0], changed.paths[0])
        assertEquals(17.5f, changed.paths[1].points[0].x)
        assertEquals(85f, changed.paths[1].points[0].y)
        assertEquals(VectorPoint(10f, 80f), artwork.paths[1].points[0])
        DesktopSvgImport.validate(changed)
        val rendered = DesktopDrawingService.generateSvg(
            DesktopImportedSvg("qa.svg", changed),
            40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
        )
        assertTrue(rendered.current.endFound)
        assertTrue(rendered.current.stitchCount > 0)
    }

    @Test
    fun `historico desfaz refaz e invalida futuro apos nova edicao`() {
        val transform = DesktopSvgPathEditor.viewport(artwork, width, height)
        val moved = DesktopSvgPathEditor.move(
            artwork, 0, width, height, Offset(transform.scale * 5f, 0f)
        )
        val session = DesktopSvgEditSession.start(artwork).record(moved)
        assertTrue(session.canUndo)
        assertEquals(artwork, session.undo().current)
        assertEquals(moved, session.undo().redo().current)
        val revised = session.undo().record(DesktopSvgPathEditor.move(
            artwork, 1, width, height, Offset(0f, transform.scale * 3f)
        ))
        assertFalse(revised.canRedo)
        assertEquals(artwork, revised.undo().current)
    }

    @Test
    fun `historico permanece limitado a oito edicoes`() {
        var session = DesktopSvgEditSession.start(artwork)
        repeat(15) {
            val next = DesktopSvgPathEditor.move(
                session.current, 0, width, height, Offset(2f, 0f)
            )
            session = session.record(next)
        }
        repeat(DesktopSvgEditSession.MAX_UNDO) {
            session = session.undo()
        }
        assertFalse(session.canUndo)
    }

    @Test
    fun `coordenadas invalidas e limites bloqueiam edicao`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgPathEditor.move(artwork, 4, width, height, Offset(1f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgPathEditor.move(
                artwork, 0, width, height, Offset(Float.NaN, 0f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgPathEditor.move(
                artwork, 0, width, height, Offset(30_000f, 0f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgPathEditor.viewport(artwork, 0f, height)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgPathEditor.select(
                artwork, width, height, Offset(Float.POSITIVE_INFINITY, 2f)
            )
        }
    }
}
