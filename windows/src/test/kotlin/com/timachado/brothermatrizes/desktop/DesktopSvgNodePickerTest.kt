package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DesktopSvgNodePickerTest {
    private val artwork = VectorArtwork(
        label = "Selecao de nos QA",
        paths = listOf(
            VectorPath(listOf(
                VectorPoint(0f, 0f), VectorPoint(100f, 0f),
                VectorPoint(100f, 100f)
            )),
            VectorPath(listOf(
                VectorPoint(100f, 0f), VectorPoint(0f, 100f)
            ))
        )
    )
    private val width = 440f
    private val height = 340f

    private fun screen(point: VectorPoint) =
        DesktopSvgPathEditor.viewport(artwork, width, height).point(point)

    @Test
    fun `clique direto seleciona nó e caminho corretos sem modificar matriz`() {
        val target = screen(VectorPoint(0f, 0f))
        val originalPaths = artwork.paths.toList()
        assertEquals(
            DesktopSvgNodeHit(0, 0),
            DesktopSvgNodePicker.pick(artwork, width, height, target)
        )
        assertEquals(originalPaths, artwork.paths)
        assertEquals(
            DesktopSvgNodeHit(0, 2),
            DesktopSvgNodePicker.pick(artwork, width, height,
                screen(VectorPoint(100f, 100f)))
        )
    }

    @Test
    fun `na interseção exata o caminho ja selecionado tem preferencia`() {
        val cursor = screen(VectorPoint(100f, 0f))
        assertEquals(
            DesktopSvgNodeHit(0, 1),
            DesktopSvgNodePicker.pick(artwork, width, height, cursor)
        )
        assertEquals(
            DesktopSvgNodeHit(1, 0),
            DesktopSvgNodePicker.pick(artwork, width, height, cursor, 1)
        )
        assertEquals(
            DesktopSvgNodeHit(0, 1),
            DesktopSvgNodePicker.pick(artwork, width, height, cursor, 0)
        )
        // Referência obsoleta não provoca exceção nem seleção fora dos limites.
        assertEquals(
            DesktopSvgNodeHit(0, 1),
            DesktopSvgNodePicker.pick(artwork, width, height, cursor, 99)
        )
    }

    @Test
    fun `usa distancia em pixels e rejeita clique longe de qualquer nó`() {
        val target = screen(VectorPoint(0f, 0f))
        val inside = Offset(target.x + DesktopSvgNodePicker.RADIUS_PX - 0.5f, target.y)
        val outside = Offset(target.x + DesktopSvgNodePicker.RADIUS_PX + 1f, target.y)
        assertEquals(
            DesktopSvgNodeHit(0, 0),
            DesktopSvgNodePicker.pick(artwork, width, height, inside)
        )
        assertNull(DesktopSvgNodePicker.pick(artwork, width, height, outside))
        assertNull(DesktopSvgNodePicker.pick(
            artwork, width, height, screen(VectorPoint(50f, 50f))
        ))
    }

    @Test
    fun `clique próximo ainda escolhe nó mais próximo e não somente preferido`() {
        val start = screen(VectorPoint(100f, 100f))
        assertEquals(
            DesktopSvgNodeHit(0, 2),
            DesktopSvgNodePicker.pick(
                artwork, width, height, Offset(start.x - 2f, start.y - 2f),
                preferredPathIndex = 1
            )
        )
    }

    @Test
    fun `posição ou viewport inválida é recusada`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodePicker.pick(artwork, width, height, Offset(Float.NaN, 1f))
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodePicker.pick(
                artwork, width, height, Offset(Float.POSITIVE_INFINITY, 1f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgNodePicker.pick(artwork, 0f, height, Offset.Zero)
        }
    }
}
