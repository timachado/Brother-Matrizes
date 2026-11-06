package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopPreviewGuideGeometryTest {
    @Test
    fun `grade de 10 mm acompanha coordenadas oficiais e centro da matriz`() {
        val bounds = PreviewBounds(-100, -200, 100, 200)
        val result = DesktopPreviewGuideGeometry.calculate(bounds, 10)
        assertEquals(listOf(-100, 0, 100), result.verticalUnits)
        assertEquals(listOf(-200, -100, 0, 100, 200), result.horizontalUnits)
        assertEquals(0, result.centerXUnits)
        assertEquals(0, result.centerYUnits)
        assertEquals(100L, result.effectiveSpacingUnits)
        assertEquals(PreviewBounds(-100, -200, 100, 200), bounds)
    }

    @Test
    fun `grade de 5 mm alinha negativos e positivos sem deslocar pontos`() {
        val bounds = PreviewBounds(-125, -90, 95, 70)
        val result = DesktopPreviewGuideGeometry.calculate(bounds, 5)
        assertEquals(listOf(-100, -50, 0, 50), result.verticalUnits)
        assertEquals(listOf(-50, 0, 50), result.horizontalUnits)
        assertEquals(-15, result.centerXUnits)
        assertEquals(-10, result.centerYUnits)
        assertEquals(50L, result.effectiveSpacingUnits)
    }

    @Test
    fun `matrizes pequenas sem cruzamento nao produzem linhas falsas`() {
        val result = DesktopPreviewGuideGeometry.calculate(
            PreviewBounds(1, 1, 49, 49), 5
        )
        assertTrue(result.verticalUnits.isEmpty())
        assertTrue(result.horizontalUnits.isEmpty())
        assertEquals(25, result.centerXUnits)
        assertEquals(25, result.centerYUnits)
    }

    @Test
    fun `limites inteiros extremos nao geram overflow ou linhas ilimitadas`() {
        val bounds = PreviewBounds(
            Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE
        )
        val result = DesktopPreviewGuideGeometry.calculate(bounds, 5)
        assertTrue(result.verticalUnits.size <= DesktopPreviewGuideGeometry.MAX_LINES_PER_AXIS)
        assertTrue(result.horizontalUnits.size <= DesktopPreviewGuideGeometry.MAX_LINES_PER_AXIS)
        assertTrue(result.verticalUnits.all { it in bounds.minX..bounds.maxX })
        assertTrue(result.horizontalUnits.all { it in bounds.minY..bounds.maxY })
        assertTrue(result.effectiveSpacingUnits >= 50L)
        assertTrue(result.verticalUnits.all {
            it.toLong() % result.effectiveSpacingUnits == 0L
        })
    }

    @Test
    fun `falha fechada para intervalo ou espacamento invalidos`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopPreviewGuideGeometry.calculate(PreviewBounds(10, 0, 5, 10), 10)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopPreviewGuideGeometry.calculate(PreviewBounds(0, 0, 10, 10), 1)
        }
    }
}
