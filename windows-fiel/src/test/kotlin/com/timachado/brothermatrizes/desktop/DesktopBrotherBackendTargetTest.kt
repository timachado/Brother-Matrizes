package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherBackendTarget
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogAccessUnavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Routing only, without network. Existing Android account and server remain
 * the single authority; no Windows-only Supabase user or quota schema.
 */
class DesktopBrotherBackendTargetTest {
    private val ref = DesktopBrotherBackendTarget.OFFICIAL_REF
    private val key = "sb_publishable_fake_offline_test_key_for_qa"

    @Test
    fun windowsUsesIdenticalOfficialSupabaseProjectWithoutStaging() {
        val target = DesktopBrotherBackendTarget.official()
        assertEquals("dwpcddiramxlhavdmmyn", ref)
        assertEquals(ref, target.ref)
        assertEquals("https://$ref.supabase.co", target.origin)
        assertFalse(target.origin.contains("likurzkruaznsvvidhun"))
        assertFalse(target.toString().contains(ref))
        val enabled = mapOf(
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to key
        )
        assertEquals(ref, DesktopWindowsAccountQaOptIn.targetForQa(enabled)?.ref)
        assertEquals(key, DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(enabled))
        assertNull(DesktopWindowsAccountQaOptIn.targetForQa(
            enabled + ("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "disabled")
        ))
        assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
            enabled + ("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "disabled")
        ))
    }

    @Test
    fun daFontAccessStaysDisabledWithoutExplicitQaSession() {
        val optedIn = mapOf(
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to key
        )
        assertEquals(
            DesktopFontCatalogAccessUnavailable,
            DesktopWindowsAccountQaOptIn.fontCatalogAccessForQa(
                qaEnvironment = null, environment = optedIn
            )
        )
        assertEquals(
            DesktopFontCatalogAccessUnavailable,
            DesktopWindowsAccountQaOptIn.fontCatalogAccessForQa(
                qaEnvironment = null,
                environment = optedIn +
                    ("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "disabled")
            )
        )
    }

    @Test
    fun fixedEndpointAllowlistDoesNotAcceptRedirectedOrAdminCalls() {
        val target = DesktopBrotherBackendTarget.official()
        for (path in listOf(
            "/auth/v1/user",
            "/auth/v1/settings",
            "/auth/v1/token?grant_type=pkce",
            "/auth/v1/token?grant_type=refresh_token",
            "/functions/v1/brother-entitlement",
            "/functions/v1/brother-usage"
        )) {
            val url = target.endpoint(path)
            assertEquals("$ref.supabase.co", url.host)
            assertEquals("https", url.protocol)
            assertEquals(-1, url.port)
            assertNull(url.userInfo)
        }
        for (path in listOf(
            "/auth/v1/admin/users",
            "/auth/v1/token?grant_type=password",
            "/functions/v1/other",
            "/functions/v1/brother-usage?bad=1",
            "//attacker.example/auth/v1/user",
            "https://example.com/auth/v1/user",
            "/auth/v1/user#exfil"
        )) {
            assertFailsWith<IllegalArgumentException> { target.endpoint(path) }
        }
    }

    @Test
    fun publicKeyRequiredAndUnsafeHeadersRejectedBeforeQaCanOpen() {
        val env = mapOf("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled")
        assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(env))
        for (bad in listOf(
            "sb_secret_fake_secret_role",
            "service_role_1234567890",
            "sb_publishable_bad key",
            "sb_publishable_bad\\\"quote",
            "sb_publishable_bad\\\\slash",
            "sb_publishable_bad_ç",
            "sb_publishable_bad\\r\\nHeader",
            "sb_publishable_x"
        )) {
            assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
                env + ("BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to bad)
            ))
        }
        assertTrue(DesktopBrotherBackendTarget.official().origin.startsWith("https://"))
    }
}
