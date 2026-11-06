package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSvgNodeEditorTest {
    private fun drawing() = VectorArtwork(
        label = "Nós QA",
        paths = listOf(
            VectorPath(listOf(
                VectorPoint(0f, 0f), VectorPoint(20f, 0f),
                VectorPoint(20f, 20f), VectorPoint(0f, 20f)
            ), closed = true),
            VectorPath(listOf(
                VectorPoint(30f, 30f), VectorPoint(50f, 40f),
                VectorPoint(60f, 60f)
            ))
        )
    )

    @Test
    fun `mover apenas um no preserva demais caminhos e historico reversivel`() {
        val source = drawing()
        val edited = DesktopSvgNodeEditor.apply(
            source, 0, 1, DesktopSvgNodeAction.RIGHT
        )
        assertEquals(1, edited.selectedNodeIndex)
        assertEquals(VectorPoint(21f, 0f), edited.artwork.paths[0].points[1])
        assertEquals(source.paths[0].points[0], edited.artwork.paths[0].points[0])
        assertEquals(source.paths[1], edited.artwork.paths[1])
        assertTrue(edited.artwork.paths[0].closed)
        assertEquals(VectorPoint(20f, 0f), source.paths[0].points[1])
        val movedAgain = DesktopSvgNodeEditor.apply(
            edited.artwork, 0, 1, DesktopSvgNodeAction.LEFT
        )
        assertEquals(source, movedAgain.artwork)
        val history = DesktopSvgEditSession.start(source).record(edited.artwork)
        assertEquals(source, history.undo().current)
        assertEquals(edited.artwork, history.undo().redo().current)
        assertFalse(history.canRedo)
    }

    @Test
    fun `inserir meio do segmento e remover restaura geometria original`() {
        val source = drawing()
        val created = DesktopSvgNodeEditor.apply(
            source, 1, 0, DesktopSvgNodeAction.INSERT_AFTER
        )
        assertEquals(1, created.selectedNodeIndex)
        assertEquals(
            listOf(
                VectorPoint(30f, 30f), VectorPoint(40f, 35f),
                VectorPoint(50f, 40f), VectorPoint(60f, 60f)
            ),
            created.artwork.paths[1].points
        )
        assertEquals(source.paths[0], created.artwork.paths[0])
        val removed = DesktopSvgNodeEditor.apply(
            created.artwork, 1, created.selectedNodeIndex, DesktopSvgNodeAction.DELETE
        )
        assertEquals(source, removed.artwork)
    }

    @Test
    fun `caminho fechado permite criar no entre ultimo e primeiro`() {
        val source = drawing()
        val added = DesktopSvgNodeEditor.apply(
            source, 0, 3, DesktopSvgNodeAction.INSERT_AFTER
        )
        assertEquals(4, added.selectedNodeIndex)
        assertEquals(VectorPoint(0f, 10f), added.artwork.paths[0].points.last())
        assertTrue(added.artwork.paths[0].closed)
        DesktopSvgImport.validate(added.artwork)
    }

    @Test
    fun `limites de 2 nos abertos e 3 nos fechados sao obrigatorios`() {
        val source = drawing()
        val open2 = source.copy(paths = listOf(
            source.paths[0], VectorPath(source.paths[1].points.take(2))
        ))
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(open2, 1, 0, DesktopSvgNodeAction.DELETE)
        }
        val closed3 = source.copy(paths = listOf(
            source.paths[0].copy(points = source.paths[0].points.take(3)),
            source.paths[1]
        ))
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(closed3, 0, 0, DesktopSvgNodeAction.DELETE)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(source, 1, 2, DesktopSvgNodeAction.INSERT_AFTER)
        }
    }

    @Test
    fun `indices inexistentes e limite coordenada recusados`() {
        val source = drawing()
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(source, 9, 0, DesktopSvgNodeAction.DOWN)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(source, 0, -1, DesktopSvgNodeAction.DOWN)
        }
        val edge = source.copy(paths = listOf(
            VectorPath(listOf(
                VectorPoint(1_000_000f, 0f),
                VectorPoint(999_990f, 10f),
                VectorPoint(999_990f, 20f)
            )),
            source.paths[1]
        ))
        DesktopSvgImport.validate(edge)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(edge, 0, 0, DesktopSvgNodeAction.RIGHT)
        }
        assertEquals(VectorPoint(1_000_000f, 0f), edge.paths[0].points[0])
    }

    @Test
    fun `protege limites globais de pontos e pontos por caminho`() {
        val original = drawing()
        val tooManyPerPath = original.copy(paths = listOf(
            VectorPath(List(800) { i ->
                VectorPoint(i.toFloat(), if (i == 799) 1f else 0f)
            }),
            original.paths[1]
        ))
        DesktopSvgImport.validate(tooManyPerPath)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(
                tooManyPerPath, 0, 0, DesktopSvgNodeAction.INSERT_AFTER
            )
        }

        val fullTotal = VectorArtwork(
            label = "Limite global",
            paths = List(8) { row ->
                VectorPath(List(750) { col ->
                    VectorPoint(col.toFloat(), row.toFloat())
                })
            }
        )
        DesktopSvgImport.validate(fullTotal)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(
                fullTotal, 0, 0, DesktopSvgNodeAction.INSERT_AFTER
            )
        }
    }

    @Test
    fun `edicao que remove largura ou altura do desenho e bloqueada`() {
        val original = VectorArtwork(
            label = "Nao achatar",
            paths = listOf(
                VectorPath(listOf(
                    VectorPoint(0f, 0f), VectorPoint(0f, 0f),
                    VectorPoint(10f, 10f)
                ))
            )
        )
        DesktopSvgImport.validate(original)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodeEditor.apply(original, 0, 2, DesktopSvgNodeAction.DELETE)
        }
        assertEquals(3, original.paths.first().points.size)
    }
}
