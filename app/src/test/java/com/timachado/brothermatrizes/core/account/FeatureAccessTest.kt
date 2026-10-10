package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAccessTest {
    private fun account(
        planCode: String,
        status: String,
        expires: String? = null
    ): AccountSnapshot =
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
                planCode,
            subscriptionStatus =
                status,
            currentPeriodEnd =
                expires
        )

    @Test
    fun coreRemainsAvailableOnFreePlan() {
        assertTrue(
            account(
                "free",
                "active"
            ).canUse(
                BrotherMatrizesFeature.CORE
            )
        )
    }

    @Test
    fun proOnlyRequiresPaidUsablePlan() {
        assertFalse(
            account(
                "free",
                "active"
            ).canUse(
                BrotherMatrizesFeature.PRO_ONLY
            )
        )

        assertTrue(
            account(
                "pro_monthly",
                "active",
                "2099-12-31T23:59:59Z"
            ).canUse(
                BrotherMatrizesFeature.PRO_ONLY
            )
        )

        assertFalse(
            account(
                "pro_monthly",
                "expired"
            ).canUse(
                BrotherMatrizesFeature.PRO_ONLY
            )
        )

        assertFalse(
            account("pro_monthly", "active").canUse(BrotherMatrizesFeature.PRO_ONLY)
        )
        assertFalse(
            account("pro_monthly", "active", "2020-01-01T00:00:00Z")
                .canUse(BrotherMatrizesFeature.PRO_ONLY)
        )
        assertFalse(
            account("pro_annual", "active", "invalid")
                .canUse(BrotherMatrizesFeature.PRO_ONLY)
        )

        assertTrue(
            account(
                "lifetime",
                "active"
            ).canUse(
                BrotherMatrizesFeature.PRO_ONLY
            )
        )
    }
    @Test
    fun wordpressTrialIsTimeBoundAndNotPurchased() {
        val activeTrial = account("trial", "active", "2099-12-31T23:59:59Z")
        assertTrue(activeTrial.canUse(BrotherMatrizesFeature.PRO_ONLY))
        assertFalse(activeTrial.isPaid)
        // A trial is not a paid subscription and never extends itself.
        assertFalse(account("trial", "active").hasProAccess)
        assertFalse(account("trial", "pending", "2099-12-31T23:59:59Z").hasProAccess)
        assertFalse(account("trial", "active", "not-a-date").hasProAccess)
        assertFalse(
            account("trial", "active", "2020-01-01T00:00:00Z")
                .canUse(BrotherMatrizesFeature.PRO_ONLY)
        )
    }

    @Test
    fun wordpressLifetimeDoesNotNeedRecurringExpiry() {
        val lifetime = account("pro_lifetime", "active")
        assertTrue(lifetime.isLifetime)
        assertTrue(lifetime.canUse(BrotherMatrizesFeature.PRO_ONLY))
        assertTrue(account("pro_lifetime_launch", "active").isLaunchLifetime)
    }

    @Test
    fun unknownWordpressLicenseDoesNotGrantPro() {
        assertFalse(account("free", "unavailable").canUse(BrotherMatrizesFeature.PRO_ONLY))
    }

}
