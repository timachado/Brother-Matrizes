package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopLetterPickerTest {
    private val project = PreviewBounds(0, 0, 200, 100)
    private val sample = listOf(
        DesktopLetterRegion(0, PreviewBounds(10, 10, 70, 90)),
        DesktopLetterRegion(1, PreviewBounds(130, 10, 190, 90))
    )

    @Test
    fun `seleciona objeto em coordenadas reais e ignora fundo`() {
        // 440x240 -> (440-40)/200 = 2 px por unidade.
        assertEquals(
            0, DesktopLetterPicker.pick(
                project, sample, 440f, 240f, 1f, Offset.Zero, Offset(100f, 100f)
            )
        )
        assertEquals(
            1, DesktopLetterPicker.pick(
                project, sample, 440f, 240f, 1f, Offset.Zero, Offset(340f, 100f)
            )
        )
        assertNull(
            DesktopLetterPicker.pick(
                project, sample, 440f, 240f, 1f, Offset.Zero, Offset(220f, 100f)
            )
        )
    }

    @Test
    fun `pan e zoom mudam hitbox sem mudar objeto`() {
        val view = DesktopPreviewNavigation.viewport(
            project, 440f, 240f, 2f, Offset(16f, -8f)
        )
        val coord = view.screenPoint(160, 50)
        assertEquals(
            1, DesktopLetterPicker.pick(
                project, sample, 440f, 240f, 2f, Offset(16f, -8f), coord
            )
        )
    }

    @Test
    fun `compilador de letras conserva geometria e regioes na mesma matriz`() {
        val layout = DesktopLetterComposer.start("A B", 12f, TextStitchStyle.RUNNING)
        val result = DesktopLetterComposer.renderDetailed(layout)
        assertEquals(listOf(0, 2), result.regions.map { it.index })
        assertEquals(
            DesktopLetterComposer.render(layout).current.points,
            result.document.current.points
        )
        val bounds = result.document.current.bounds
        assertTrue(result.regions.all { r ->
            r.bounds.minX >= bounds.minXUnits && r.bounds.maxX <= bounds.maxXUnits &&
                r.bounds.minY >= bounds.minYUnits && r.bounds.maxY <= bounds.maxYUnits
        })
    }

    @Test
    fun `rejeita ponteiro e dimensoes invalidos`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterPicker.pick(
                project, sample, 440f, 240f, 1f, Offset.Zero,
                Offset(Float.NaN, 50f)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopLetterPicker.pick(
                project, sample, 0f, 240f, 1f, Offset.Zero, Offset.Zero
            )
        }
    }
}
