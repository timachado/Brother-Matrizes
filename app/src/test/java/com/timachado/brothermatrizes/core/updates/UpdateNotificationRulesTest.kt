package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotificationRulesTest {
    @Test fun naoAviseSeAplicativoEstaAtualizado() {
        assertFalse(UpdateNotificationRules.shouldNotify(171, 171, 0))
    }

    @Test fun aviseQuandoNovaVersaoForPublicada() {
        assertTrue(UpdateNotificationRules.shouldNotify(170, 171, 0))
    }

    @Test fun naoAviseNovamenteParaMesmaVersao() {
        assertFalse(UpdateNotificationRules.shouldNotify(170, 171, 171))
    }

    @Test fun voltaAAvisarQuandoForPublicadaOutraVersao() {
        assertTrue(UpdateNotificationRules.shouldNotify(170, 172, 171))
    }

    @Test fun versaoMaisAntigaNuncaGeraNotificacao() {
        assertFalse(UpdateNotificationRules.shouldNotify(171, 170, 0))
    }
}
