package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopSvgObjectMirrorTest {
    private fun artwork() = VectorArtwork(
        label = "Espelho SVG QA",
        paths = listOf(
            VectorPath(
                listOf(
                    VectorPoint(0f, 0f), VectorPoint(30f, 5f),
                    VectorPoint(20f, 40f), VectorPoint(0f, 35f)
                ), closed = true
            ),
            VectorPath(listOf(VectorPoint(60f, 10f), VectorPoint(90f, 15f)))
        )
    )

    @Test
    fun `espelhar horizontal reflete so coordenada x do objeto selecionado`() {
        val source = artwork()
        val changed = DesktopSvgObjectTransform.apply(
            source, 0, DesktopSvgObjectAction.MIRROR_HORIZONTAL
        )
        assertEquals(
            listOf(
                VectorPoint(30f, 0f), VectorPoint(0f, 5f),
                VectorPoint(10f, 40f), VectorPoint(30f, 35f)
            ), changed.paths[0].points
        )
        assertEquals(source.paths[1], changed.paths[1])
        assertTrue(changed.paths[0].closed)
        assertEquals(source.label, changed.label)
        assertEquals(VectorPoint(0f, 0f), source.paths[0].points.first())
        DesktopSvgImport.validate(changed)
    }

    @Test
    fun `espelhar vertical reflete so coordenada y do objeto selecionado`() {
        val source = artwork()
        val changed = DesktopSvgObjectTransform.apply(
            source, 0, DesktopSvgObjectAction.MIRROR_VERTICAL
        )
        assertEquals(
            listOf(
                VectorPoint(0f, 40f), VectorPoint(30f, 35f),
                VectorPoint(20f, 0f), VectorPoint(0f, 5f)
            ), changed.paths[0].points
        )
        assertEquals(source.paths[1], changed.paths[1])
        assertEquals(source.paths[0].closed, changed.paths[0].closed)
    }

    @Test
    fun `dois espelhamentos iguais recuperam coordenadas e undo redo funciona`() {
        val original = artwork()
        for (action in listOf(
            DesktopSvgObjectAction.MIRROR_HORIZONTAL,
            DesktopSvgObjectAction.MIRROR_VERTICAL
        )) {
            val reflected = DesktopSvgObjectTransform.apply(original, 0, action)
            val restored = DesktopSvgObjectTransform.apply(reflected, 0, action)
            assertEquals(original, restored)
            val session = DesktopSvgEditSession.start(original).record(reflected)
            assertEquals(original, session.undo().current)
            assertEquals(reflected, session.undo().redo().current)
        }
    }

    @Test
    fun `objeto inexistente e geometria invalida sao recusados sem alterar original`() {
        val original = artwork()
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectTransform.apply(original, -1,
                DesktopSvgObjectAction.MIRROR_HORIZONTAL)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectTransform.apply(original, 9,
                DesktopSvgObjectAction.MIRROR_VERTICAL)
        }
        val invalid = original.copy(paths = listOf(
            VectorPath(listOf(
                VectorPoint(Float.NaN, 0f), VectorPoint(20f, 40f)
            )),
            original.paths[1]
        ))
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectTransform.apply(invalid, 0,
                DesktopSvgObjectAction.MIRROR_HORIZONTAL)
        }
        assertEquals(VectorPoint(0f, 0f), original.paths[0].points.first())
    }
}
