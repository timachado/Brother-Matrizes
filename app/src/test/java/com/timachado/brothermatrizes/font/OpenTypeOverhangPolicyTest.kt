package com.timachado.brothermatrizes.font

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTypeOverhangPolicyTest {
    private fun e(advance: Float, left: Float, right: Float) =
        OpenTypeOverhangPolicy.GlyphExtent(advance, left, right)

    @Test fun bertildaSs01CannotDecorateEveryInternalA() {
        // Bertilda real, unidades em 1000/em: a.001 avança 293 mas o
        // contorno esquerdo chega a -926 (floreio de letra inicial).
        assertTrue(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(293f, -926f, 306f), 1, 5
        ))
    }

    @Test fun bertildaSs02CannotDecorateInternalRAndI() {
        // r.002 avança 250, porém o floreio direito termina em 1160.
        assertTrue(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(250f, 0f, 1160f), 2, 5
        ))
        // i.002 avança 142 e o floreio ultrapassa a posição 1077.
        assertTrue(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(142f, -3f, 1077f), 3, 5
        ))
    }

    @Test fun allowsTerminalFlourishesWithoutNeighbour() {
        assertFalse(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(293f, -4f, 1224f), 4, 5
        ))
        assertFalse(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(293f, -926f, 306f), 0, 5
        ))
    }

    @Test fun regularTextIsNotBlocked() {
        for (n in 0 until 5) {
            assertFalse(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
                e(293f, -4f, 306f), n, 5
            ))
        }
    }

    @Test fun loveHeartAlternativesWithMatchingAdvanceRemainAvailable() {
        // Love.ttf a.alt1: advance 811, contorno horizontal 50..830.
        // A variante aumenta o advance junto com o desenho do coração.
        // Não bloquear por ser apenas maior, desde que não invada vizinhos.
        assertFalse(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(811f, 50f, 830f), 1, 5
        ))
        // Love.ttf i.alt1: advance 551, contorno 50..570.
        assertFalse(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(551f, 50f, 570f), 3, 5
        ))
    }

    @Test fun singleLetterCanUseItsOwnOrnaments() {
        assertFalse(OpenTypeOverhangPolicy.isUnsafeForWholeWord(
            e(142f, -950f, 1077f), 0, 1
        ))
    }
}
