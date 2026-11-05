package com.timachado.brothermatrizes.desktop

import java.awt.image.BufferedImage
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DesktopUiSmokeTest {
    @Test
    fun `limites de pontadas ignoram rótulos e regiões fora do canvas`() {
        val image = BufferedImage(500, 300, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, 0x14273B)
            }
        }
        assertNull(DesktopUiSmoke.syntheticStitchBounds(image))
        image.setRGB(420, 100, 0xE6BE70)
        assertNull(DesktopUiSmoke.syntheticStitchBounds(image))
        for (y in 90..140) {
            for (x in 100..180) {
                image.setRGB(x, y, 0xE6BE70)
            }
        }
        assertEquals(
            Rectangle(100, 90, 81, 51),
            DesktopUiSmoke.syntheticStitchBounds(image)
        )
    }

    @Test
    fun `zoom real em janela baixa pode cortar altura sem invalidar ampliacao`() {
        assertTrue(
            DesktopUiSmoke.visiblyZoomed(
                Rectangle(0, 0, 204, 203), Rectangle(0, 0, 296, 211)
            )
        )
        assertFalse(
            DesktopUiSmoke.visiblyZoomed(
                Rectangle(0, 0, 204, 203), Rectangle(12, 7, 204, 203)
            )
        )
        assertFalse(
            DesktopUiSmoke.visiblyZoomed(
                Rectangle(0, 0, 204, 203), Rectangle(0, 0, 211, 206)
            )
        )
        assertFalse(
            DesktopUiSmoke.visiblyZoomed(
                Rectangle(0, 0, 204, 203), Rectangle(0, 0, 300, 125)
            )
        )
    }

    @Test
    fun `smoke gráfico detecta pontos dourados somente na região da prévia`() {
        val image = BufferedImage(500, 300, BufferedImage.TYPE_INT_RGB)
        val background = 0x14273B
        val stitch = 0xE6BE70
        val text = 0xE6E0E9
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, background)
            }
        }
        assertEquals(0, DesktopUiSmoke.countSyntheticStitchPixels(image))
        // Um rótulo claro, mas não dourado, não equivale a pontadas.
        image.setRGB(100, 100, text)
        // Uma marca fora do canvas também não valida a prévia.
        image.setRGB(420, 100, stitch)
        assertEquals(0, DesktopUiSmoke.countSyntheticStitchPixels(image))
        for (y in 100 until 110) {
            for (x in 100 until 110) {
                image.setRGB(x, y, stitch)
            }
        }
        assertEquals(100, DesktopUiSmoke.countSyntheticStitchPixels(image))
    }
}
