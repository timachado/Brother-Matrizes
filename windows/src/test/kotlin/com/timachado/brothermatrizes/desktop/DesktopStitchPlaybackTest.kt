package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopStitchPlaybackTest {
    @Test
    fun `seek e passo unitario preservam indices em matrizes grandes`() {
        for (total in listOf(1, 7, 731, 999_999, 2_000_000)) {
            for (target in listOf(0, 1, total / 3, total / 2, total - 1, total).distinct()) {
                val position = target.coerceIn(0, total)
                val p = DesktopStitchPlayback.seek(total, position)
                assertEquals(position, DesktopStitchPlayback.visibleCount(total, p),
                    "A prévia deve representar exatamente o comando $position/$total.")
            }
            assertEquals(1, DesktopStitchPlayback.visibleCount(total,
                DesktopStitchPlayback.advance(total, 0f, 1)))
            assertEquals(total - 1, DesktopStitchPlayback.visibleCount(total,
                DesktopStitchPlayback.advance(total, 1f, -1)))
            assertEquals(total, DesktopStitchPlayback.visibleCount(total,
                DesktopStitchPlayback.advance(total, 1f, 2_000_001)))
            assertEquals(0, DesktopStitchPlayback.visibleCount(total,
                DesktopStitchPlayback.advance(total, 0f, -2_000_001)))
        }
    }

    @Test
    fun `velocidades visuais geram passos limitados por ciclo`() {
        assertEquals(listOf(100, 500, 2_000), DesktopStitchPlayback.availableRates)
        assertEquals(20, DesktopStitchPlayback.commandsPerTick(100))
        assertEquals(100, DesktopStitchPlayback.commandsPerTick(500))
        assertEquals(400, DesktopStitchPlayback.commandsPerTick(2_000))
        assertFailsWith<IllegalArgumentException> {
            DesktopStitchPlayback.commandsPerTick(100_000)
        }
    }

    @Test
    fun `posicoes invalidas nunca passam para tela`() {
        assertEquals(0, DesktopStitchPlayback.visibleCount(0, 0f))
        assertEquals(0f, DesktopStitchPlayback.seek(0, 0))
        assertFailsWith<IllegalArgumentException> {
            DesktopStitchPlayback.visibleCount(10, Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStitchPlayback.visibleCount(10, -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStitchPlayback.seek(12, 13)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopStitchPlayback.seek(2_000_001, 1)
        }
        assertTrue(DesktopStitchPlayback.advance(100, 0.995f, 100) <= 1f)
    }
}
