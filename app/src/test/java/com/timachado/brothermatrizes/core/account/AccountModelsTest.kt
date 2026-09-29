package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountModelsTest {
    @Test
    fun planLabelsCoverRecurringAndLifetimePlans() {
        assertEquals(
            "Brother Matrizes Pro Mensal",
            AccountPresentation
                .planLabel(
                    "pro_monthly"
                )
        )

        assertEquals(
            "Brother Matrizes Pro Anual",
            AccountPresentation
                .planLabel(
                    "pro_annual"
                )
        )

        assertEquals(
            "Brother Matrizes Vitalício",
            AccountPresentation
                .planLabel(
                    "lifetime"
                )
        )

        assertEquals(
            "Brother Matrizes Vitalício • Lançamento",
            AccountPresentation
                .planLabel(
                    "lifetime_launch"
                )
        )
    }

    @Test
    fun launchPlanIsLifetimeAndPromotional() {
        val launch =
            AccountPlanOption(
                code =
                    "lifetime_launch",
                name =
                    "Brother Matrizes Vitalício • Lançamento",
                billingType =
                    "lifetime",
                isPaid =
                    true,
                isLifetime =
                    true,
                isPromotional =
                    true,
                description =
                    "Oferta",
                priceCents =
                    null,
                currency =
                    "BRL",
                active =
                    true,
                availableFrom =
                    null,
                availableUntil =
                    null,
                displayOrder =
                    40
            )

        val account =
            AccountSnapshot(
                userId =
                    "user",
                email =
                    "user@example.com",
                displayName =
                    "User",
                avatarUrl =
                    null,
                planCode =
                    "lifetime_launch",
                subscriptionStatus =
                    "active",
                currentPeriodEnd =
                    null,
                purchasedAt =
                    null,
                purchasePriceCents =
                    null,
                currency =
                    "BRL",
                provider =
                    null,
                availablePlans =
                    listOf(
                        launch
                    ),
                subscriptionHistory =
                    emptyList()
            )

        assertTrue(
            account.isPaid
        )
        assertTrue(
            account.isLifetime
        )
        assertTrue(
            account.isLaunchLifetime
        )
        assertTrue(
            account
                .isSubscriptionUsable
        )
    }

    @Test
    fun freePlanIsNotPaidOrLifetime() {
        val account =
            AccountSnapshot(
                userId =
                    "user",
                email =
                    "user@example.com",
                displayName =
                    "User",
                avatarUrl =
                    null,
                planCode =
                    "free",
                subscriptionStatus =
                    "active",
                currentPeriodEnd =
                    null,
                purchasedAt =
                    null,
                purchasePriceCents =
                    null,
                currency =
                    "BRL",
                provider =
                    null,
                availablePlans =
                    emptyList(),
                subscriptionHistory =
                    emptyList()
            )

        assertFalse(
            account.isPaid
        )
        assertFalse(
            account.isLifetime
        )
    }
}
