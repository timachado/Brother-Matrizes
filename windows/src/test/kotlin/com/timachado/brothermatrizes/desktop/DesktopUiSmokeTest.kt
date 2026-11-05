package com.timachado.brothermatrizes.desktop

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUiSmokeTest {
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
