package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSvgLayerNudgeTest {
    private fun drawing() = VectorArtwork(
        paths = listOf(
            VectorPath(listOf(VectorPoint(0f, 0f), VectorPoint(20f, 0f))),
            VectorPath(listOf(VectorPoint(10f, 30f), VectorPoint(40f, 50f)))
        ),
        label = "SVG QA"
    )

    @Test
    fun `ajuste altera somente o caminho escolhido sem alterar original`() {
        val original = drawing()
        val altered = DesktopSvgLayerNudge.move(original, 1, 1, 0)
        assertEquals(original.paths[0], altered.paths[0])
        assertEquals(VectorPoint(11f, 30f), altered.paths[1].points.first())
        assertEquals(VectorPoint(41f, 50f), altered.paths[1].points.last())
        assertEquals(VectorPoint(10f, 30f), original.paths[1].points.first())
        assertEquals(original.label, altered.label)
        DesktopSvgImport.validate(altered)
    }

    @Test
    fun `passos sucessivos permanecem exatos e historico svg desfaz e refaz`() {
        val original = drawing()
        var moved = original
        repeat(20) { moved = DesktopSvgLayerNudge.move(moved, 0, 0, 1) }
        assertEquals(VectorPoint(0f, 20f), moved.paths[0].points.first())
        repeat(20) { moved = DesktopSvgLayerNudge.move(moved, 0, 0, -1) }
        assertEquals(original, moved)
        val next = DesktopSvgLayerNudge.move(original, 0, 1, 0)
        val history = DesktopSvgEditSession.start(original).record(next)
        assertTrue(history.canUndo)
        assertEquals(original, history.undo().current)
        assertEquals(next, history.undo().redo().current)
        assertFalse(history.canRedo)
    }

    @Test
    fun `recusa coordenadas ou deslocamento invalidos e falha fechada no limite`() {
        val original = drawing()
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgLayerNudge.move(original, 3, 1, 0)
        }
        for (bad in listOf(0 to 0, 1 to 1, 2 to 0, 0 to -3)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgLayerNudge.move(original, 0, bad.first, bad.second)
            }
        }
        val atLimit = original.copy(paths = listOf(
            VectorPath(listOf(VectorPoint(1_000_000f, 0f), VectorPoint(999_999f, 0f))),
            original.paths[1]
        ))
        DesktopSvgImport.validate(atLimit)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgLayerNudge.move(atLimit, 0, 1, 0)
        }
        assertEquals(VectorPoint(1_000_000f, 0f), atLimit.paths[0].points.first())
    }
}
