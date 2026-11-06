package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSvgObjectCollectionTest {
    private fun drawing() = VectorArtwork(
        label = "Colecao QA",
        paths = listOf(
            VectorPath(
                listOf(
                    VectorPoint(0f, 0f), VectorPoint(25f, 0f),
                    VectorPoint(25f, 15f), VectorPoint(0f, 15f)
                ), closed = true
            ),
            VectorPath(
                listOf(
                    VectorPoint(40f, 30f), VectorPoint(65f, 40f),
                    VectorPoint(45f, 55f)
                ), closed = true
            ),
            VectorPath(
                listOf(
                    VectorPoint(80f, 60f), VectorPoint(100f, 80f)
                )
            )
        )
    )

    @Test
    fun `duplicacao insere apos selecionado e desloca apenas a nova copia`() {
        val source = drawing()
        val result = DesktopSvgObjectCollection.duplicate(source, 1)
        assertEquals(2, result.selectedIndex)
        assertEquals(4, result.artwork.paths.size)
        assertEquals(source.paths[0], result.artwork.paths[0])
        assertEquals(source.paths[1], result.artwork.paths[1])
        assertEquals(source.paths[2], result.artwork.paths[3])
        assertEquals(
            listOf(
                VectorPoint(50f, 40f), VectorPoint(75f, 50f),
                VectorPoint(55f, 65f)
            ),
            result.artwork.paths[2].points
        )
        assertTrue(result.artwork.paths[2].closed)
        assertEquals(source.label, result.artwork.label)
        assertEquals(3, source.paths.size)
        DesktopSvgImport.validate(result.artwork)
    }

    @Test
    fun `excluir remove somente selecionado e seleciona proximo ou ultimo`() {
        val source = drawing()
        val firstRemoved = DesktopSvgObjectCollection.delete(source, 0)
        assertEquals(0, firstRemoved.selectedIndex)
        assertEquals(listOf(source.paths[1], source.paths[2]), firstRemoved.artwork.paths)
        val lastRemoved = DesktopSvgObjectCollection.delete(source, 2)
        assertEquals(1, lastRemoved.selectedIndex)
        assertEquals(listOf(source.paths[0], source.paths[1]), lastRemoved.artwork.paths)
        assertEquals(3, source.paths.size)
    }

    @Test
    fun `historico reverte duplicacao e exclusao e nova edicao invalida redo`() {
        val original = drawing()
        val cloned = DesktopSvgObjectCollection.duplicate(original, 0)
        val withoutClone = DesktopSvgObjectCollection.delete(
            cloned.artwork, cloned.selectedIndex
        )
        assertEquals(original, withoutClone.artwork)
        val history = DesktopSvgEditSession.start(original)
            .record(cloned.artwork).record(withoutClone.artwork)
        assertEquals(cloned.artwork, history.undo().current)
        assertEquals(original, history.undo().undo().current)
        assertEquals(withoutClone.artwork, history.undo().redo().current)
        val alternative = DesktopSvgObjectCollection.delete(original, 1).artwork
        val replaced = history.undo().undo().record(alternative)
        assertFalse(replaced.canRedo)
        assertEquals(original, replaced.undo().current)
    }

    @Test
    fun `ultima forma nao pode ser excluida e indices invalidos falham`() {
        val single = drawing().copy(paths = listOf(drawing().paths[0]))
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectCollection.delete(single, 0)
        }
        for (badIndex in listOf(-1, 3)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgObjectCollection.duplicate(drawing(), badIndex)
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgObjectCollection.delete(drawing(), badIndex)
            }
        }
    }

    @Test
    fun `recusa duplicacao alem dos limites de caminhos pontos e coordenadas`() {
        val manyPaths = VectorArtwork(
            label = "Limite de caminhos",
            paths = List(DesktopSvgImport.MAX_PATHS) { i ->
                VectorPath(
                    listOf(
                        VectorPoint(i.toFloat(), 0f),
                        VectorPoint((i + 1).toFloat(), 10f)
                    )
                )
            }
        )
        DesktopSvgImport.validate(manyPaths)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectCollection.duplicate(manyPaths, 0)
        }

        val manyPoints = VectorArtwork(
            label = "Limite de pontos",
            paths = List(8) { i ->
                VectorPath(List(750) { j ->
                    VectorPoint(j.toFloat(), i.toFloat())
                })
            }
        )
        DesktopSvgImport.validate(manyPoints)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectCollection.duplicate(manyPoints, 0)
        }

        val atEdge = VectorArtwork(
            label = "Borda",
            paths = listOf(
                VectorPath(listOf(
                    VectorPoint(999_995f, 10f),
                    VectorPoint(1_000_000f, 20f)
                )),
                drawing().paths[0]
            )
        )
        DesktopSvgImport.validate(atEdge)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectCollection.duplicate(atEdge, 0)
        }
        assertEquals(2, atEdge.paths.size)
    }

    @Test
    fun `exclusao que deixa somente linha sem altura e recusada sem mutacao`() {
        val source = VectorArtwork(
            label = "Geometria obrigatoria",
            paths = listOf(
                VectorPath(listOf(VectorPoint(0f, 0f), VectorPoint(100f, 0f))),
                VectorPath(listOf(VectorPoint(0f, 10f), VectorPoint(50f, 20f)))
            )
        )
        DesktopSvgImport.validate(source)
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgObjectCollection.delete(source, 1)
        }
        assertEquals(2, source.paths.size)
    }

    @Test
    fun `reordenacao move somente o caminho selecionado e segue selecionado`() {
        val source = drawing()
        val movedUp = DesktopSvgObjectCollection.reorder(source, 1, -1)
        assertEquals(0, movedUp.selectedIndex)
        assertEquals(
            listOf(source.paths[1], source.paths[0], source.paths[2]),
            movedUp.artwork.paths
        )
        assertEquals(source.paths, drawing().paths)
        val restored = DesktopSvgObjectCollection.reorder(movedUp.artwork, 0, 1)
        assertEquals(1, restored.selectedIndex)
        assertEquals(source, restored.artwork)
        val movedDown = DesktopSvgObjectCollection.reorder(source, 1, 1)
        assertEquals(2, movedDown.selectedIndex)
        assertEquals(
            listOf(source.paths[0], source.paths[2], source.paths[1]),
            movedDown.artwork.paths
        )
        val history = DesktopSvgEditSession.start(source).record(movedDown.artwork)
        assertEquals(source, history.undo().current)
        assertEquals(movedDown.artwork, history.undo().redo().current)
    }

    @Test
    fun `reordenacao rejeita ultrapassar extremidades e saltos nao autorizados`() {
        val source = drawing()
        for ((index, direction) in listOf(
            0 to -1, 2 to 1, -1 to 1, 3 to -1,
            1 to 0, 1 to 2, 1 to -2
        )) {
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgObjectCollection.reorder(source, index, direction)
            }
        }
        assertEquals(3, source.paths.size)
    }
}
