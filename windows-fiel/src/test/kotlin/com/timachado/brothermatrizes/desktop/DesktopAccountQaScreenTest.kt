package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopServerAccountSnapshot
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Account presentation and QA switch are tested without Google, Supabase,
 * Windows registry, device credentials, remote payment services or networking.
 */
class DesktopAccountQaScreenTest {
    private val publicKey = "sb_publishable_offline_qa_key_1234567890"

    @Test
    fun normalWindowsLaunchNeverOptsIntoGoogleAutomatically() {
        assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(emptyMap()))
        assertNull(
            DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
                mapOf("BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to publicKey)
            )
        )
        assertNull(
            DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
                mapOf("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "false",
                    "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to publicKey)
            )
        )
        assertNull(
            DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
                mapOf("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled")
            )
        )
    }

    @Test
    fun onlyExplicitQaAndPublicKeyAllowAccountUiToRequestSignIn() {
        val qa = mapOf(
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to publicKey
        )
        assertEquals(
            publicKey,
            DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(qa)
        )
        for (disallowed in listOf(
            "sb_secret_never_use_in_windows",
            "service_role",
            "sb_publishable_bad\nnew-line",
            "sb_publishable_bad\twith-tab",
            "sb_publishable_bad with_space",
            "sb_publishable_short"
        )) {
            assertNull(
                DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
                    qa + ("BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to disallowed)
                )
            )
        }
    }

    @Test
    fun serverValidatedPlansHaveHumanReadableAndroidCommercialLabels() {
        val expected = mapOf(
            "pro_monthly" to "Brother Matrizes Pro mensal",
            "pro_yearly" to "Brother Matrizes Pro anual",
            "pro_lifetime" to "Brother Matrizes Pro vitalício",
            "pro_lifetime_launch" to "Brother Matrizes Pro vitalício lançamento",
            "trial" to "Teste Pro"
        )
        for ((plan, label) in expected) {
            val ui = DesktopAccountPresentation.verified(
                DesktopServerAccountSnapshot(
                    userId = "11111111-2222-4333-8444-555555555555",
                    planCode = plan,
                    hasServerPro = true,
                    subscriptionStatus = "active"
                )
            )
            assertEquals(label, ui.planName)
            assertTrue(ui.isPaidPlan)
            assertTrue(ui.verifiedOnServer)
        }
    }

    @Test
    fun neverDisplaysPaidAccessFromInactiveUntrustedOrUnknownPlan() {
        for ((plan, pro, status) in listOf(
            Triple("pro_yearly", false, "active"),
            Triple("pro_lifetime", true, "expired"),
            Triple("pro_lifetime_launch", true, "unavailable"),
            Triple("future_plan", true, "active"),
            Triple("free", false, "active")
        )) {
            val ui = DesktopAccountPresentation.verified(
                DesktopServerAccountSnapshot(
                    "11111111-2222-4333-8444-555555555555",
                    plan,
                    pro,
                    status
                )
            )
            assertEquals("Plano gratuito", ui.planName)
            assertFalse(ui.planName.contains("Pro"))
            // An unknown plan may be server-marked true, but it must never
            // unlock commercial operations: UI is explicitly read-only.
            if (!pro || status != "active") assertFalse(ui.isPaidPlan)
        }
    }
}
