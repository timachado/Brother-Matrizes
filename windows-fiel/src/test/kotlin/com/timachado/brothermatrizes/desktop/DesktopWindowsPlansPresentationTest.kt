package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopServerAccountSnapshot
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsPlansPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** No external auth, billing, Supabase request, license creation or purchase. */
class DesktopWindowsPlansPresentationTest {
    private val id = "11111111-2222-4333-8444-555555555555"
    private val email = "conta-qa@example.invalid"

    private fun server(
        code: String = "free",
        pro: Boolean = false,
        status: String = "active",
        expiry: String? = null,
        configured: Boolean = true,
        trial: String = "eligible"
    ) = DesktopServerAccountSnapshot(
        userId = id,
        email = email,
        planCode = code,
        hasServerPro = pro,
        subscriptionStatus = status,
        currentPeriodEnd = expiry,
        trialStatus = trial,
        commercialConfigured = configured
    )

    @Test
    fun unverifiedOrUnconfiguredNeverShowsCommercialAccount() {
        assertNull(DesktopWindowsPlansPresentation.displayOnly(null))
        assertNull(DesktopWindowsPlansPresentation.displayOnly(
            server("pro_lifetime", pro = true, configured = false)
        ))
        assertNull(DesktopWindowsPlansPresentation.displayOnly(
            DesktopServerAccountSnapshot(
                userId = "not-verified", email = email,
                planCode = "pro_lifetime", hasServerPro = true,
                subscriptionStatus = "active", commercialConfigured = true
            )
        ))
    }

    @Test
    fun onlyServerValidLifetimeCanAppearAsCurrentPlan() {
        val valid = requireNotNull(DesktopWindowsPlansPresentation.displayOnly(
            server("pro_lifetime", pro = true)
        ))
        assertTrue(valid.commercialConfigured)
        assertEquals("pro_lifetime", valid.planCode)
        assertTrue(valid.hasProAccess)
        assertTrue(valid.isLifetime)
        assertEquals(email, valid.email)
        val noGrant = requireNotNull(DesktopWindowsPlansPresentation.displayOnly(
            server("pro_lifetime", pro = false)
        ))
        assertEquals("free", noGrant.planCode)
        assertFalse(noGrant.hasProAccess)
        assertEquals(null, noGrant.manageUrl)
    }

    @Test
    fun recurringAndTrialRequireUnexpiredServerPeriod() {
        for (plan in listOf("pro_monthly", "pro_yearly", "trial")) {
            val active = requireNotNull(DesktopWindowsPlansPresentation.displayOnly(
                server(plan, true, expiry = "2099-01-01T00:00:00Z")
            ))
            assertEquals(plan, active.planCode)
            assertTrue(active.hasProAccess)
            for (invalidExpiry in listOf<String?>(
                null, "2000-01-01T00:00:00Z", "not-a-date"
            )) {
                val denied = requireNotNull(DesktopWindowsPlansPresentation.displayOnly(
                    server(plan, true, expiry = invalidExpiry)
                ))
                assertEquals("free", denied.planCode)
                assertFalse(denied.hasProAccess)
            }
        }
    }

    @Test
    fun suspendedOrUnknownPlanCannotBeDisplayedAsPro() {
        for ((plan, pro, status) in listOf(
            Triple("pro_lifetime", true, "revoked"),
            Triple("pro_monthly", true, "expired"),
            Triple("locally_promoted", true, "active"),
            Triple("free", true, "active")
        )) {
            val snapshot = requireNotNull(DesktopWindowsPlansPresentation.displayOnly(
                server(plan, pro, status, "2099-01-01T00:00:00Z")
            ))
            assertEquals("free", snapshot.planCode)
            assertFalse(snapshot.hasProAccess)
        }
    }
}
