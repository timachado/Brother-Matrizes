package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopEditorDragTest {
    private val square = PreviewBounds(0, 0, 100, 100)

    @Test
    fun `converte pixels da previsualizacao para decimos de mm`() {
        // Canvas 240x240, margens 40 -> 200/100 = 2px por unidade.
        assertEquals(
            DesktopEmbroideryOffset(10, -5),
            DesktopEditorDrag.fromPixels(square, 240f, 240f, 1f, Offset(20f, -10f))
        )
    }

    @Test
    fun `mesmo deslocamento em zoom duplo produz metade das unidades`() {
        assertEquals(
            DesktopEmbroideryOffset(5, -3),
            DesktopEditorDrag.fromPixels(square, 240f, 240f, 2f, Offset(20f, -10f))
        )
    }

    @Test
    fun `arrasto neutro nao muda geometria`() {
        assertEquals(
            DesktopEmbroideryOffset(0, 0),
            DesktopEditorDrag.fromPixels(square, 240f, 240f, 1f, Offset.Zero)
        )
    }

    @Test
    fun `movimento excessivo e dimensoes invalidas sao bloqueados`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopEditorDrag.fromPixels(square, 240f, 240f, 1f, Offset(5000f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopEditorDrag.fromPixels(square, 0f, 240f, 1f, Offset(20f, 0f))
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopEditorDrag.fromPixels(square, 240f, 240f, 1f, Offset(Float.NaN, 0f))
        }
    }
}
