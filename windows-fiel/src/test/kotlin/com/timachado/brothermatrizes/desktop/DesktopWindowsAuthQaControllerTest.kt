package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopQaSignIn
import com.timachado.brothermatrizes.desktop.platform.DesktopSessionRefresher
import com.timachado.brothermatrizes.desktop.platform.DesktopTransientAuthSession
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsAuthQaController
import com.timachado.brothermatrizes.desktop.platform.DesktopSupabaseGoogleIdentityVerifier
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherEntitlementReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No Google login, Supabase requests, or production tokens. */
class DesktopWindowsAuthQaControllerTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T02:00:00Z"), ZoneOffset.UTC)
    private val token = "qa_access_token_123456789012345"
    private val refresh = "qa_refresh_token_123456789012345"
    private fun withFolder(block: suspend (Path) -> Unit) = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", true))
        val folder = Files.createTempDirectory("bm-qa-controller-")
        try {
            block(folder)
        } finally {
            Files.walk(folder).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun loginRecoveryLogoutAndExclusiveOwnership() = withFolder { folder ->
        var logins = 0
        val signIn = DesktopQaSignIn {
            logins++
            DesktopTransientAuthSession(token, refresh, 3600)
        }
        val refreshAdapter = DesktopSessionRefresher {
            error("No refresh expected")
        }
        DesktopWindowsAuthQaController.open(signIn, refreshAdapter, clock, folder).use { first ->
            assertFalse(first.recover())
            assertFailsWith<IllegalStateException> { first.accessTokenForQa() }
            first.login()
            assertEquals(1, logins)
            assertEquals(token, first.accessTokenForQa())
            assertFailsWith<IllegalStateException> {
                DesktopWindowsAuthQaController.open(signIn, refreshAdapter, clock, folder)
            }
        }
        DesktopWindowsAuthQaController.open(signIn, refreshAdapter, clock, folder).use { next ->
            assertTrue(next.recover())
            assertEquals(token, next.accessTokenForQa())
            next.logout()
            assertFailsWith<IllegalStateException> { next.accessTokenForQa() }
            assertFalse(next.recover())
        }
        assertEquals(1, logins)
        assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
    }

    @Test
    fun failedLoginNeverReusesPreviousAccountSession() = withFolder { folder ->
        val adapter = DesktopSessionRefresher { error("Unexpected refresh") }
        DesktopWindowsAuthQaController.open(
            DesktopQaSignIn { DesktopTransientAuthSession(token, refresh, 3600) },
            adapter, clock, folder
        ).use { first ->
            first.login()
            assertEquals(token, first.accessTokenForQa())
        }
        DesktopWindowsAuthQaController.open(
            DesktopQaSignIn { error("Google cancelled") },
            adapter, clock, folder
        ).use { second ->
            assertTrue(second.recover())
            assertFailsWith<IllegalStateException> { second.login() }
            assertFailsWith<IllegalStateException> { second.accessTokenForQa() }
            assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
        }
    }

    @Test
    fun onlyServerConfirmedGoogleIdentityPassesQaValidation() = withFolder { folder ->
        val id = "11111111-2222-4333-8444-555555555555"
        val email = "verified-qa@example.invalid"
        fun verifier(provider: String): DesktopSupabaseGoogleIdentityVerifier {
            val json = """{"id":"$id","email":"$email","app_metadata":{"provider":"$provider"}}"""
            return DesktopSupabaseGoogleIdentityVerifier(
                "sb_publishable_offline_test_key_1234567890"
            ) { url ->
                assertEquals(
                    "https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/user",
                    url.toString()
                )
                object : HttpURLConnection(url) {
                    override fun connect() = Unit
                    override fun disconnect() = Unit
                    override fun usingProxy() = false
                    override fun getResponseCode() = 200
                    override fun getContentType() = "application/json"
                    override fun getInputStream(): InputStream =
                        ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
        DesktopWindowsAuthQaController.open(
            DesktopQaSignIn { DesktopTransientAuthSession(token, refresh, 3600) },
            DesktopSessionRefresher { error("No network refresh expected") },
            clock, folder
        ).use { controller ->
            assertFailsWith<IllegalStateException> {
                controller.loginAndVerifyGoogleForQa(verifier("email"))
            }
            assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
            assertFailsWith<IllegalStateException> { controller.accessTokenForQa() }
            val identity = controller.loginAndVerifyGoogleForQa(verifier("google"))
            assertEquals(id, identity.userId)
            assertEquals(email, identity.email)
            assertFalse(identity.toString().contains(email))
            assertEquals(token, controller.accessTokenForQa())
            assertEquals(token, controller.verifiedAccessTokenForQa(verifier("google")))
            assertTrue(Files.exists(folder.resolve("session.v1.dpapi")))
            assertEquals(id, controller.verifyGoogleAccountForQa(verifier("google")).userId)
            assertFailsWith<IllegalStateException> {
                controller.verifiedAccessTokenForQa(verifier("email"))
            }
            assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
            assertFailsWith<IllegalStateException> { controller.accessTokenForQa() }
            controller.logout()
            assertFailsWith<IllegalStateException> {
                controller.verifyGoogleAccountForQa(verifier("google"))
            }
        }
    }


    @Test
    fun accountPreviewNeverGrantsLocalRightsAndInvalidResponseClearsDpapi() = withFolder { folder ->
        val id = "11111111-2222-4333-8444-555555555555"
        val email = "test-account@example.invalid"
        fun mockGet(
            url: URL,
            response: String,
            status: Int = 200
        ): HttpURLConnection = object : HttpURLConnection(url) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getContentType() = "application/json"
            override fun getInputStream(): InputStream =
                ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))
        }
        val key = "sb_publishable_offline_test_key_1234567890"
        val verifier = DesktopSupabaseGoogleIdentityVerifier(key) { url ->
            assertEquals("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/user", url.toString())
            mockGet(
                url,
                """{"id":"$id","email":"$email","app_metadata":{"provider":"google"}}"""
            )
        }
        var wrongSubject = false
        val reader = DesktopBrotherEntitlementReader(key) { url ->
            assertEquals(
                "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-entitlement",
                url.toString()
            )
            val sub = if (wrongSubject) "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee" else id
            mockGet(
                url,
                """{"authenticated":true,"user":{"id":"$sub","email":"$email"},"planCode":"pro_lifetime","status":"active","pro":true}"""
            )
        }
        DesktopWindowsAuthQaController.open(
            DesktopQaSignIn { DesktopTransientAuthSession(token, refresh, 3600) },
            DesktopSessionRefresher { error("No token refresh expected") },
            clock, folder
        ).use { controller ->
            controller.loginAndVerifyGoogleForQa(verifier)
            val preview = controller.accountPreviewForQa(verifier, reader)
            assertTrue(preview.hasServerPro)
            assertEquals("pro_lifetime", preview.planCode)
            // Preview deliberately does NOT implement commercial entitlement gate.
            assertFalse(preview.toString().contains(token))
            assertTrue(Files.exists(folder.resolve("session.v1.dpapi")))
            wrongSubject = true
            assertFailsWith<IllegalStateException> {
                controller.accountPreviewForQa(verifier, reader)
            }
            assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
            assertFailsWith<IllegalStateException> { controller.accessTokenForQa() }
        }
    }

}
