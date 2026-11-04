package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopHoopAdvisorTest {
    private fun design(widthMm: Int, heightMm: Int): EmbroideryDesign {
        return EmbroideryDesign(
            fileName = "synthetic.dst",
            format = "DST",
            label = "fixture",
            points = emptyList(),
            bounds = EmbroideryBounds(
                minXUnits = 0, maxXUnits = widthMm * 10,
                minYUnits = 0, maxYUnits = heightMm * 10
            ),
            stitchCount = 0,
            jumpCount = 0,
            colorChanges = 0,
            endFound = false,
            sourceBytes = byteArrayOf()
        )
    }

    @Test
    fun `usa exatamente perfis e margens internas oficiais Android`() {
        val assessments = DesktopHoopAdvisor.analyze(design(90, 90))
        assertEquals(HoopProfile.entries.size, assessments.size)
        val square = assessments.first { it.hoop == HoopProfile.H100X100 }
        assertEquals(90f, square.usableWidthMm)
        assertEquals(90f, square.usableHeightMm)
        assertEquals(DesktopHoopFit.DIRECT, square.status)
    }

    @Test
    fun `rejeita milimetro que excede margem segura no bastidor de cem`() {
        val square = DesktopHoopAdvisor.analyze(design(91, 90))
            .first { it.hoop == HoopProfile.H100X100 }
        assertEquals(DesktopHoopFit.EXCEEDS, square.status)
        assertEquals(1f, square.directOverflowWidthMm)
        assertEquals(0f, square.directOverflowHeightMm)
    }

    @Test
    fun `avisa de orientacao alternativa sem alterar a matriz`() {
        val matrix = design(115, 160)
        val original = matrix.bounds
        val assessment = DesktopHoopAdvisor.analyze(matrix)
            .first { it.hoop == HoopProfile.H130X180 }
        assertEquals(DesktopHoopFit.ALTERNATE_ORIENTATION, assessment.status)
        assertEquals(original, matrix.bounds)
    }

    @Test
    fun `compara tamanhos maiores sem prometer compatibilidade de maquina`() {
        val all = DesktopHoopAdvisor.analyze(design(400, 350))
        assertTrue(all.all { it.status == DesktopHoopFit.EXCEEDS })
        assertTrue(all.all { it.usableWidthMm > 0f && it.usableHeightMm > 0f })
    }

    @Test
    fun `rejeita dimensoes geometricas invalidas`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopHoopAdvisor.analyze(design(-10, 20))
        }
    }

    @Test
    fun `cada analise corresponde ao documento e nao guarda estado global`() {
        val small = DesktopHoopAdvisor.analyze(design(30, 30))
        val large = DesktopHoopAdvisor.analyze(design(170, 140))
        assertEquals(DesktopHoopFit.DIRECT,
            small.first { it.hoop == HoopProfile.H100X100 }.status)
        assertEquals(DesktopHoopFit.EXCEEDS,
            large.first { it.hoop == HoopProfile.H100X100 }.status)
    }
}
