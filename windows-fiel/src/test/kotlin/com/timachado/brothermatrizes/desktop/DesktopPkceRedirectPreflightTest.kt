package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopPkceHandshake
import com.timachado.brothermatrizes.desktop.platform.DesktopPkceRedirectPreflight
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Offline-only regression tests: no Google login, API request or dashboard edits. */
class DesktopPkceRedirectPreflightTest {
    @Test
    fun generatedDynamicPortRedirectMeetsStrictLocalContract() {
        val redirect = DesktopPkceHandshake.start(49152).redirectUri
        assertTrue(DesktopPkceRedirectPreflight.generatedRedirectHasExpectedShape(redirect))
        assertTrue(redirect.toASCIIString().startsWith("http://127.0.0.1:49152/brother-matrizes/auth?bm_state="))
        assertTrue(
            DesktopPkceRedirectPreflight.SUPABASE_ALLOWLIST_CANDIDATE
                .contains("""/brother-matrizes/auth\?bm_state=*""")
        )
    }

    @Test
    fun rejectsExternalHostsUnexpectedPathsQueryAndCredentialInjection() {
        val good = DesktopPkceHandshake.start(49152).redirectUri.toASCIIString()
        val invalid = listOf(
            good.replace("127.0.0.1", "localhost"),
            good.replace("127.0.0.1", "127.0.0.2"),
            good.replace("http://", "https://"),
            good.replace(":49152/", ":80/"),
            good.replace(":49152/", ":0/"),
            good.replace("/brother-matrizes/auth?", "/other/auth?"),
            good.replace("?bm_state=", "?code=unexpected&bm_state="),
            good + "&code=unexpected",
            good + "#fragment",
            good.replace("http://", "http://user@"),
            good.replace("bm_state=", "bm_state=bad-"),
            good.replace("bm_state=", "not_state=")
        )
        invalid.forEach { value ->
            assertFalse(
                DesktopPkceRedirectPreflight.generatedRedirectHasExpectedShape(URI(value)),
                "Unsafe redirect must not pass preflight"
            )
        }
    }
}
