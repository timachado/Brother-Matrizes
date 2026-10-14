package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AccountPlanCatalogTest {
    @Test fun showsApprovedLaunchPricesWithoutGrantingAvailabilityOrEntitlement() {
        val plans = AccountPlanCatalog.forDisplay(emptyList())
        assertEquals(listOf(
            "free", "pro_monthly", "pro_yearly",
            "pro_lifetime", "pro_lifetime_launch"
        ), plans.map { it.code })
        assertFalse(plans.any { it.active })
        assertEquals(0, plans.first().priceCents)
        plans.drop(1).forEach { assertNull(it.priceCents) }
        assertEquals(listOf(1990, 15990, 39990, 24990),
            plans.drop(1).map { BrotherLaunchPriceDisplay.priceCents(it.code) })
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
        val display = AccountPlanCatalog.forDisplay(listOf(launch, annual))
        assertEquals(listOf("pro_yearly", "pro_lifetime_launch"), display.map { it.code })
        display.forEach { assertNull(it.priceCents) }
        assertFalse(display.any { it.active })
    }
}
