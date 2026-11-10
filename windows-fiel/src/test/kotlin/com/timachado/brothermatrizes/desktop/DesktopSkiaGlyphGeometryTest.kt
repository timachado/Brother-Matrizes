package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopSkiaGlyphGeometry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Port da amostragem Android: testes geométricos, NÃO testes de
 * bordado satin/underlay e NÃO equivalência entre tipos de fontes.
 */
class DesktopSkiaGlyphGeometryTest {
    private fun systemFont(): Path {
        val system = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            system.resolve("arial.ttf"),
            system.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Não há TTF do sistema para testes geométricos.")
    }

    @Test
    fun `letra O mantem buracos internos e perimetros independentes`() {
        val result = DesktopSkiaGlyphGeometry.sample(
            systemFont(), "OO", 120f, 12f
        )
        assertEquals(2, result.glyphCount)
        assertTrue(result.contoursByGlyph.all { it.size >= 2 },
            "A letra O precisa manter interior e exterior como contornos separados.")
        assertTrue(result.contoursByGlyph.all { glyph ->
            glyph.all { contour -> contour.size >= 8 }
        })
        assertTrue(result.pointCount > 50)
        assertTrue(result.left < 0f && result.right > 0f)
        assertTrue(result.top < 0f && result.bottom > 0f)
        assertTrue(abs(result.left + result.right) < 0.01f)
        assertTrue(abs(result.top + result.bottom) < 0.01f)
        assertTrue(result.contoursByGlyph.flatten().flatten().all {
            it.x.isFinite() && it.y.isFinite() &&
                abs(it.x) < 10000f && abs(it.y) < 10000f
        })
    }

    @Test
    fun `nome Maria decompõe em 5 glifos e preserva a ordem original`() {
        val result = DesktopSkiaGlyphGeometry.sample(
            systemFont(), "Maria", 120f, 3f
        )
        assertEquals(5, result.glyphCount)
        assertTrue(result.contourCount >= 5)
        assertTrue(result.pointCount > 80)
        val firstX = result.contoursByGlyph.first().flatten().minOf { it.x }
        val lastX = result.contoursByGlyph.last().flatten().maxOf { it.x }
        assertTrue(firstX < lastX)
        assertTrue(result.right - result.left > 100f)
    }

    @Test
    fun `espacamento e altura alteram limites sem reordenar contornos`() {
        val font = systemFont()
        val normal = DesktopSkiaGlyphGeometry.sample(font, "OO", 100f, 0f)
        val spaced = DesktopSkiaGlyphGeometry.sample(font, "OO", 100f, 25f)
        assertEquals(normal.glyphCount, spaced.glyphCount)
        assertEquals(normal.contoursByGlyph.map { it.size },
            spaced.contoursByGlyph.map { it.size })
        assertTrue(spaced.right - spaced.left > normal.right - normal.left)
        val larger = DesktopSkiaGlyphGeometry.sample(font, "OO", 160f, 0f)
        assertTrue(larger.bottom - larger.top > normal.bottom - normal.top)
    }

    @Test
    fun `recusa fonte ausente texto inseguro e escala fora dos limites`() {
        val font = systemFont()
        for (text in listOf("", "   ", "Mar\nia", "x".repeat(65), "\uD800")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopSkiaGlyphGeometry.sample(font, text, 120f, 3f)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSkiaGlyphGeometry.sample(font, "Maria", Float.NaN, 3f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSkiaGlyphGeometry.sample(font, "Maria", 120f, Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSkiaGlyphGeometry.sample(font, "Maria", 2500f, 3f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSkiaGlyphGeometry.sample(font.parent.resolve("missing.ttf"), "M", 120f, 3f)
        }
    }
}
