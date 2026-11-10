package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.desktop.platform.DesktopContourPoint
import com.timachado.brothermatrizes.desktop.platform.DesktopCoverageGuardProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Diagnóstico, NÃO substitui o guard Android comercial. */
class DesktopCoverageGuardProbeTest {
    private val glyph = listOf(
        listOf(
            listOf(
                DesktopContourPoint(0f, 0f), DesktopContourPoint(120f, 0f),
                DesktopContourPoint(120f, 80f), DesktopContourPoint(0f, 80f)
            )
        )
    )

    private fun goodStitches(): List<EmbroideryPoint> = buildList {
        for (y in 3..77 step 6) {
            add(EmbroideryPoint(3, y, StitchCommand.JUMP, 0))
            add(EmbroideryPoint(117, y, StitchCommand.STITCH, 0))
        }
    }

    @Test
    fun `aceita linhas de cobertura controladas com precision e coverage reais`() {
        val source = goodStitches()
        val score = DesktopCoverageGuardProbe.check(glyph, source)
        assertTrue(score.precision >= 0.54f)
        assertTrue(score.coverage >= 0.20f)
        assertTrue(score.expectedPixels > 25)
        assertTrue(score.producedPixels > 5)
        assertEquals(score, DesktopCoverageGuardProbe.check(glyph, source),
            "Comparação de silhueta precisa ser determinística.")
    }

    @Test
    fun `bloqueia bordado deslocado e nao concede aprovacao falsa`() {
        val displaced = goodStitches().map {
            it.copy(xUnits = it.xUnits + 300)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCoverageGuardProbe.check(glyph, displaced)
        }
    }

    @Test
    fun `bloqueia desenhos vazios contornos degenerados e matrizes sem pontos`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopCoverageGuardProbe.check(emptyList(), goodStitches())
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCoverageGuardProbe.check(
                listOf(listOf(listOf(
                    DesktopContourPoint(0f, 0f),
                    DesktopContourPoint(1f, 0f),
                    DesktopContourPoint(1f, 0.5f)
                ))), goodStitches()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCoverageGuardProbe.check(glyph, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCoverageGuardProbe.check(glyph, listOf(
                EmbroideryPoint(0, 0, StitchCommand.STITCH, 0)
            ))
        }
    }
}
