package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AccountPlanCatalogTest {
    @Test fun restoresAllFivePlanCardsWithoutInventingPricesOrAvailability() {
        val plans = AccountPlanCatalog.forDisplay(emptyList())
        assertEquals(listOf(
            "free", "pro_monthly", "pro_yearly",
            "pro_lifetime", "pro_lifetime_launch"
        ), plans.map { it.code })
        assertFalse(plans.any { it.active })
        assertEquals(0, plans.first().priceCents)
        plans.drop(1).forEach { assertNull(it.priceCents) }
    }

    @Test fun serverProductsRemainVisibleEvenWhileInactive() {
        val annual = AccountPlanOption(
            code = "pro_yearly", name = "Pro Anual",
            billingType = "yearly", isPaid = true,
            isLifetime = false, isPromotional = false,
            description = "", priceCents = null,
            currency = "BRL", active = false,
            availableFrom = null, availableUntil = null, displayOrder = 2
        )
        val launch = annual.copy(
            code = "pro_lifetime_launch", name = "Vitalício Lançamento",
            isLifetime = true, isPromotional = true, displayOrder = 4
        )
        assertEquals(listOf("pro_yearly", "pro_lifetime_launch"),
            AccountPlanCatalog.forDisplay(listOf(launch, annual)).map { it.code })
    }
}
