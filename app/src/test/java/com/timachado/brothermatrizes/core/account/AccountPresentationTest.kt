package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPresentationTest {

    @Test
    fun knownPlansHaveFriendlyLabels() {
        assertEquals(
            "Gratuito",
            AccountPresentation
                .planLabel(
                    "free"
                )
        )

        assertEquals(
            "Brother Matrizes Pro",
            AccountPresentation
                .planLabel(
                    "pro"
                )
        )
    }

    @Test
    fun subscriptionStatusesHaveFriendlyLabels() {
        assertEquals(
            "Ativa",
            AccountPresentation
                .statusLabel(
                    "active"
                )
        )

        assertEquals(
            "Pagamento pendente",
            AccountPresentation
                .statusLabel(
                    "past_due"
                )
        )
    }

    @Test
    fun accountKnowsPaidAndUsableState() {
        val paid =
            AccountSnapshot(
                userId = "1",
                email = "teste@example.com",
                displayName = "Teste",
                avatarUrl = null,
                planCode = "pro",
                subscriptionStatus =
                    "active",
                currentPeriodEnd =
                    null
            )

        val free =
            paid.copy(
                planCode = "free",
                subscriptionStatus =
                    "expired"
            )

        assertTrue(
            paid.isPaid
        )

        assertTrue(
            paid.isSubscriptionUsable
        )

        assertFalse(
            free.isPaid
        )

        assertFalse(
            free.isSubscriptionUsable
        )
    }
}
