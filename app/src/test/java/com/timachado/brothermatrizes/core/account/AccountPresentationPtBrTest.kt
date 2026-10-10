package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountPresentationPtBrTest {
    @Test fun everyCommercialBillingCodeIsUserFriendlyPortuguese() {
        assertEquals("Mensal", AccountPresentation.billingLabel("monthly"))
        assertEquals("Anual", AccountPresentation.billingLabel("yearly"))
        assertEquals("Pagamento único", AccountPresentation.billingLabel("one_time"))
        assertEquals("Gratuito", AccountPresentation.billingLabel("free"))
        assertEquals("Não informado", AccountPresentation.billingLabel("unexpected_code"))
    }

    @Test fun unknownSubscriptionCodesNeverAppearOnScreen() {
        assertEquals("Aguardando confirmação", AccountPresentation.statusLabel("unavailable"))
        assertEquals("Aguardando pagamento", AccountPresentation.statusLabel("pending"))
        assertEquals("Situação não informada", AccountPresentation.statusLabel("gateway_internal_code"))
        assertEquals("Mudança para plano superior", AccountPresentation.eventLabel("upgraded"))
        assertEquals("Atualização da assinatura", AccountPresentation.eventLabel("code_not_supported"))
    }

    @Test fun unavailableWooCommercePlansAreDisplayOnly() {
        val cards = AccountPlanCatalog.forDisplay(emptyList())
        assertEquals(5, cards.size)
        assertFalse(cards.any { it.active })
        assertFalse(cards.drop(1).any { it.priceCents != null })
    }
}
