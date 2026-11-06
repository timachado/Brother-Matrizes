package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopDrawingServiceTest {
    private val square = listOf(listOf(
        VectorPoint(0.1f, 0.1f),
        VectorPoint(0.9f, 0.1f),
        VectorPoint(0.9f, 0.9f),
        VectorPoint(0.1f, 0.9f),
        VectorPoint(0.1f, 0.1f)
    ))

    @Test
    fun `cor selecionada chega ao gerador de pontadas oficial`() {
        val rgb = 0xE63946
        val result = DesktopDrawingService.generate(
            square, 40f, TextStitchStyle.RUNNING,
            HoopProfile.H100X100, rgb
        )
        assertEquals(rgb, result.current.threadColors.first())
        assertEquals(result.original.threadColors, result.current.threadColors)
        assertTrue(result.current.stitchCount > 0)
    }

    @Test
    fun `traço livre gera matriz real pelo motor Android com END unico`() {
        val doc = DesktopDrawingService.generate(
            square, 40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
        )
        assertEquals("DST", doc.current.format)
        assertTrue(doc.current.stitchCount > 0)
        assertEquals(1, doc.current.points.count { it.command == StitchCommand.END })
        assertTrue(doc.current.bounds.widthMm in 35f..45f)
        assertTrue(doc.current.endFound)
        assertEquals(doc.original.points, doc.current.points)
    }

    @Test
    fun `desenho pode ser convertido e relido sem modificar original`() {
        val source = DesktopDrawingService.generate(
            square, 40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
        )
        val converted = DesktopMatrixService.convert(source, "DST")
        val reopened = DesktopMatrixService.open(converted.fileName, converted.bytes)
        assertEquals(source.current.stitchCount, reopened.current.stitchCount)
        assertEquals(source.original.points, source.current.points)
    }

    @Test
    fun `bloqueia tamanhos e geometria invalida`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(emptyList(), 40f,
                TextStitchStyle.RUNNING, HoopProfile.H100X100)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(square, Float.NaN,
                TextStitchStyle.RUNNING, HoopProfile.H100X100)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(square, 120f,
                TextStitchStyle.RUNNING, HoopProfile.H100X100)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(
                listOf(listOf(VectorPoint(-1f, 0.1f), VectorPoint(0.5f, 0.5f))),
                40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(
                listOf(listOf(VectorPoint(0.5f, 0.5f), VectorPoint(0.5f, 0.5f))),
                40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
            )
        }
    }

    @Test
    fun `limita quantidade de traços e coordenadas`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(
                List(DesktopDrawingService.MAX_STROKES + 1) { square.first() },
                30f, TextStitchStyle.RUNNING, HoopProfile.H100X100
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopDrawingService.generate(
                listOf(List(DesktopDrawingService.MAX_POINTS_PER_STROKE + 1) {
                    VectorPoint((it % 2).toFloat(), 0.5f)
                }),
                30f, TextStitchStyle.RUNNING, HoopProfile.H100X100
            )
        }
    }
}
