package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopQaStagingProject
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAccountQaOptIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Only validated URL assembly and developer opt-in; no live Supabase calls. */
class DesktopQaStagingProjectTest {
    private val staging = "stagingonlyqa1234567"
    private val publicKey = "sb_publishable_fake_offline_key_for_staging"

    @Test
    fun productionAndUnrelatedProjectsNeverQualifyAsWindowsQaStaging() {
        for (ref in listOf(
            "dwpcddiramxlhavdmmyn",
            "likurzkruaznsvvidhun",
            "",
            "supabase.com",
            "https://a.supabase.co",
            "STAGINGONLYQA1234567",
            "stagingonlyqa1234567/",
            "stagingonlyqa1234567@bad.example",
            "stagingonlyqa1234567.example",
            "stagingonlyqa12345678",
            "stagingonlyqa123456"
        )) {
            assertFailsWith<IllegalArgumentException> {
                DesktopQaStagingProject.isolated(ref)
            }
        }
    }

    @Test
    fun noImplicitProductionFallbackOrCredentialOptInWithoutStaging() {
        val enabled = mapOf(
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled",
            "BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to publicKey
        )
        assertNull(DesktopWindowsAccountQaOptIn.stagingTargetFromEnvironment(enabled))
        assertNull(DesktopWindowsAccountQaOptIn.stagingTargetFromEnvironment(
            enabled + ("BROTHER_WINDOWS_QA_STAGING_PROJECT_REF" to
                "dwpcddiramxlhavdmmyn")
        ))
        assertNull(DesktopWindowsAccountQaOptIn.stagingTargetFromEnvironment(
            enabled + ("BROTHER_WINDOWS_QA_STAGING_PROJECT_REF" to
                "likurzkruaznsvvidhun")
        ))
        val disabled = enabled +
            ("BROTHER_WINDOWS_QA_STAGING_PROJECT_REF" to staging) +
            ("BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "disabled")
        assertNull(DesktopWindowsAccountQaOptIn.stagingTargetFromEnvironment(disabled))
        assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(disabled))
        val allowed = DesktopWindowsAccountQaOptIn.stagingTargetFromEnvironment(
            enabled + ("BROTHER_WINDOWS_QA_STAGING_PROJECT_REF" to staging)
        )
        assertEquals(staging, allowed?.ref)
    }

    @Test
    fun onlyHttpsSupabaseHostAndFixedEndpointsAreAccepted() {
        val target = DesktopQaStagingProject.isolated(staging)
        assertEquals("https://$staging.supabase.co", target.origin)
        for (path in listOf(
            "/auth/v1/user",
            "/auth/v1/settings",
            "/auth/v1/token?grant_type=pkce",
            "/auth/v1/token?grant_type=refresh_token",
            "/functions/v1/brother-entitlement",
            "/functions/v1/brother-usage"
        )) {
            val endpoint = target.endpoint(path)
            assertEquals("$staging.supabase.co", endpoint.host)
            assertEquals("https", endpoint.protocol)
            assertEquals(-1, endpoint.port)
            assertNull(endpoint.userInfo)
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
        assertTrue(!target.toString().contains(staging))
    }

    @Test
    fun rejectsKeysNotSafeForHttpHeadersBeforeOptIn() {
        val enabled = mapOf(
            "BROTHER_WINDOWS_QA_GOOGLE_AUTH" to "enabled",
            "BROTHER_WINDOWS_QA_STAGING_PROJECT_REF" to staging
        )
        for (key in listOf(
            "sb_secret_not_allowed",
            "sb_publishable_invalid key with space",
            "sb_publishable_bad\\"quoted",
            "sb_publishable_bad\\\\slash",
            "sb_publishable_unicode_ç",
            "sb_publishable_newline\\r\\nInjected",
            "sb_publishable_short"
        )) {
            val env = enabled + ("BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to key)
            if (key == "sb_publishable_short") {
                // Length check below the minimum, just like commercial client.
                assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(env))
            } else {
                assertNull(DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(env))
            }
        }
        assertEquals(publicKey, DesktopWindowsAccountQaOptIn.publicKeyFromEnvironment(
            enabled + ("BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY" to publicKey)
        ))
    }
}
