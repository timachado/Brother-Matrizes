package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopPreviewViewportTest {
    private val bounds = PreviewBounds(0, 0, 100, 100)

    @Test
    fun `ajustar cabe matriz na area centralizada sem alterar pontos`() {
        val transform = DesktopPreviewNavigation.viewport(
            bounds, 200f, 200f, 1f, Offset.Zero
        )
        assertEquals(1.6f, transform.scale, 0.0001f)
        assertEquals(Offset(20f, 20f), transform.screenPoint(0, 0))
        assertEquals(Offset(100f, 100f), transform.screenPoint(50, 50))
        assertEquals(Offset(180f, 180f), transform.screenPoint(100, 100))
    }

    @Test
    fun `zoom preserva o centro e arrastar desloca somente a visualizacao`() {
        val zoomed = DesktopPreviewNavigation.viewport(
            bounds, 200f, 200f, 2f, Offset.Zero
        )
        assertEquals(3.2f, zoomed.scale, 0.0001f)
        assertEquals(Offset(100f, 100f), zoomed.screenPoint(50, 50))
        val panned = DesktopPreviewNavigation.viewport(
            bounds, 200f, 200f, 2f, Offset(23f, -9f)
        )
        assertEquals(Offset(123f, 91f), panned.screenPoint(50, 50))
        assertEquals(bounds, this.bounds)
    }

    @Test
    fun `previa com limites grandes nao sofre overflow de inteiros`() {
        val extremes = PreviewBounds(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val view = DesktopPreviewNavigation.viewport(
            extremes, 800f, 600f, 1f, Offset.Zero
        )
        assertTrue(view.scale.isFinite() && view.scale > 0f)
        val middle = view.screenPoint(0, 0)
        assertTrue(middle.x.isFinite() && middle.y.isFinite())
    }

    @Test
    fun `recusa zoom e medidas invalidas`() {
        for (badZoom in listOf(0f, 0.5f, 9f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopPreviewNavigation.viewport(bounds, 200f, 200f, badZoom, Offset.Zero)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopPreviewNavigation.viewport(bounds, 0f, 200f, 1f, Offset.Zero)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopPreviewNavigation.viewport(bounds, 200f, 200f, 1f, Offset(Float.NaN, 0f))
        }
    }
}
