package com.timachado.brothermatrizes.ui.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherPostUpdatePolicyTest {
    @Test fun mostraPrimeiraAberturaAposAtualizacao() {
        assertTrue(BrotherPostUpdatePolicy.shouldDisplay(184, 0))
        assertTrue(BrotherPostUpdatePolicy.shouldDisplay(185, 184))
    }

    @Test fun naoMostraNovamenteNaMesmaVersao() {
        assertFalse(BrotherPostUpdatePolicy.shouldDisplay(184, 184))
        assertFalse(BrotherPostUpdatePolicy.shouldDisplay(184, 185))
    }

    @Test fun naoInterfereEmVersoesAnteriores() {
        assertFalse(BrotherPostUpdatePolicy.shouldDisplay(183, 0))
    }
}
