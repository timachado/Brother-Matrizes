package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSvgObjectTransformTest {
    private fun drawing() = VectorArtwork(
        label = "Transformacao QA",
        paths = listOf(
            VectorPath(listOf(
                VectorPoint(0f, 0f), VectorPoint(20f, 0f),
                VectorPoint(20f, 10f), VectorPoint(0f, 10f)
            ), closed = true),
            VectorPath(listOf(VectorPoint(40f, 40f), VectorPoint(60f, 50f)))
        )
    )

    @Test
    fun `escala selecionada preserva centro e outros caminhos`() {
        val original = drawing()
        val grown = DesktopSvgObjectTransform.apply(
            original, 0, DesktopSvgObjectAction.SCALE_UP
        )
        assertEquals(original.paths[1], grown.paths[1])
        assertTrue(grown.paths[0].closed)
        assertEquals(-1f, grown.paths[0].points[0].x, 0.0001f)
        assertEquals(-0.5f, grown.paths[0].points[0].y, 0.0001f)
        assertEquals(21f, grown.paths[0].points[2].x, 0.0001f)
        assertEquals(10.5f, grown.paths[0].points[2].y, 0.0001f)
        assertEquals(original.paths[0].points[0], VectorPoint(0f, 0f))
        DesktopSvgImport.validate(grown)

        val shrunk = DesktopSvgObjectTransform.apply(
            original, 0, DesktopSvgObjectAction.SCALE_DOWN
        )
        assertEquals(1f, shrunk.paths[0].points[0].x, 0.0001f)
        assertEquals(0.5f, shrunk.paths[0].points[0].y, 0.0001f)
    }

    @Test
    fun `rotacao aplica quinze graus sem mudar o centro ou vizinhos`() {
        val original = drawing()
        val turned = DesktopSvgObjectTransform.apply(
            original, 0, DesktopSvgObjectAction.ROTATE_RIGHT
        )
        assertEquals(original.paths[1], turned.paths[1])
        assertTrue(turned.paths[0].closed)
        val a = turned.paths[0].points[0]
        val c = turned.paths[0].points[2]
        assertEquals(10f, (a.x + c.x) / 2f, 0.0001f)
        assertEquals(5f, (a.y + c.y) / 2f, 0.0001f)
        assertTrue(a.x < 2f && a.y < 0f)
        assertEquals(original.label, turned.label)
        val reversed = DesktopSvgObjectTransform.apply(
            turned, 0, DesktopSvgObjectAction.ROTATE_LEFT
        )
        for ((before, after) in original.paths[0].points.zip(reversed.paths[0].points)) {
            assertEquals(before.x, after.x, 0.0001f)
            assertEquals(before.y, after.y, 0.0001f)
        }
    }

    @Test
    fun `historico admite desfazer refazer sem reescrever matriz`() {
        val original = drawing()
        val turned = DesktopSvgObjectTransform.apply(
            original, 1, DesktopSvgObjectAction.ROTATE_RIGHT
        )
        val history = DesktopSvgEditSession.start(original).record(turned)
        assertTrue(history.canUndo)
        assertEquals(original, history.undo().current)
        assertEquals(turned, history.undo().redo().current)
        assertFalse(history.canRedo)
    }

    @Test
    fun `parametros invalidos e limite de um milhao falham fechados`() {
        val original = drawing()
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectTransform.apply(original, -1, DesktopSvgObjectAction.SCALE_UP)
        }
        val edge = original.copy(paths = listOf(
            VectorPath(listOf(
                VectorPoint(999_995f, 0f), VectorPoint(1_000_000f, 0f),
                VectorPoint(1_000_000f, 10f)
            )),
            original.paths[1]
        ))
        DesktopSvgImport.validate(edge)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectTransform.apply(edge, 0, DesktopSvgObjectAction.SCALE_UP)
        }
        assertEquals(VectorPoint(1_000_000f, 0f), edge.paths[0].points[1])
    }
}
