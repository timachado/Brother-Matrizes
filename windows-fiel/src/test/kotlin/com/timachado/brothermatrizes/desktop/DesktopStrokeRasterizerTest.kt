package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopContourPoint
import com.timachado.brothermatrizes.desktop.platform.DesktopStrokeRasterizer
import com.timachado.brothermatrizes.desktop.platform.DesktopSkiaGlyphGeometry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Testa a máscara de rasterização EVEN_ODD do fluxo principal Android.
 * A comparação pixel a pixel com emulador Android ainda está pendente.
 */
class DesktopStrokeRasterizerTest {
    private fun rect(
        l: Float, t: Float, r: Float, b: Float
    ) = listOf(
        DesktopContourPoint(l, t), DesktopContourPoint(r, t),
        DesktopContourPoint(r, b), DesktopContourPoint(l, b)
    )

    @Test
    fun `máscara EVEN ODD mantém vazado interior e não preenche fundo`() {
        val img = DesktopStrokeRasterizer.rasterize(listOf(
            rect(-50f, -50f, 50f, 50f),
            rect(-25f, -25f, 25f, 25f)
        ))
        val centerX = (0 - img.minX) / img.unitsPerPixel + img.padding
        val centerY = (img.maxY - 0) / img.unitsPerPixel + img.padding
        val x = centerX.toInt()
        val y = centerY.toInt()
        assertTrue(!img.mask[y * img.width + x],
            "Um contorno interior EVEN_ODD precisa ficar vazado.")
        assertTrue(img.mask.any { it })
        assertTrue(img.mask[0].not())
        assertTrue(img.mask[(img.height - 1) * img.width + img.width - 1].not())
        assertTrue(img.width > 100 && img.height > 100)
    }

    @Test
    fun `rasterização e esqueleto executam para contornos TTF reais`() {
        val system = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        val font = listOf(
            system.resolve("arial.ttf"), system.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Fonte real TTF não encontrada.")
        val glyphs = DesktopSkiaGlyphGeometry.sample(font, "O", 120f, 3f)
        assertTrue(glyphs.contoursByGlyph.first().size >= 2)
        val raster = DesktopStrokeRasterizer.rasterize(glyphs.contoursByGlyph.first())
        val original = raster.mask.copyOf()
        val lines = raster.trace()
        assertTrue(lines.isNotEmpty(),
            "O stroke-flow precisa produzir linhas do esqueleto para O.")
        assertTrue(lines.flatten().all {
            it.x in 0 until raster.width && it.y in 0 until raster.height
        })
        assertContentEquals(original, raster.mask,
            "O afinamento não pode modificar a máscara original.")
        assertEquals(lines, raster.trace())
    }

    @Test
    fun `rejeita fonte geometricamente inválida e máscara excessiva`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeRasterizer.rasterize(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeRasterizer.rasterize(
                listOf(rect(Float.NaN, 0f, 30f, 30f))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStrokeRasterizer.rasterize(
                listOf(rect(0f, 0f, 100_000f, 100_000f))
            )
        }
    }
}
