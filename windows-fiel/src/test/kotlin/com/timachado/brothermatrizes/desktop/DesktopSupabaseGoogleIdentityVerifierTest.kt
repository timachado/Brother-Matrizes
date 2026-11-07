package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopSupabaseGoogleIdentityVerifier
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 100% offline. HTTP responses are fabricated; no real account or backend. */
class DesktopSupabaseGoogleIdentityVerifierTest {
    private val key = "sb_publishable_offline_test_key_1234567890"
    private val token = "qa_opaque_supabase_access_token_1234567890"
    private val id = "11111111-2222-4333-8444-555555555555"
    private val email = "qa-google@example.invalid"

    private class FakeConnection(
        url: URL,
        private val status: Int = 200,
        private val mime: String = "application/json",
        private val json: String = ""
    ) : HttpURLConnection(url) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun verifiesGoogleProviderAgainstTrustedSupabaseUserEndpoint() = runBlocking {
        for (app in listOf(
            """{"provider":"google","providers":["google"]}""",
            """{"provider":"email","providers":["email","google"]}"""
        )) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/user"),
                json = """{"id":"$id","email":"$email","app_metadata":$app}"""
            )
            val client = DesktopSupabaseGoogleIdentityVerifier(key) { url ->
                assertEquals(
                    "https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/user",
                    url.toString()
                )
                fake
            }
            val verified = client.verifyGoogleIdentity(token)
            assertEquals(id, verified.userId)
            assertEquals(email, verified.email)
            assertEquals("GET", fake.requestMethod)
            assertEquals(key, fake.getRequestProperty("apikey"))
            assertEquals("Bearer $token", fake.getRequestProperty("Authorization"))
            assertFalse(fake.instanceFollowRedirects)
            assertTrue(fake.closed)
            assertFalse(verified.toString().contains(email))
            assertFalse(verified.toString().contains(token))
        }
    }

    @Test
    fun refusesUnverifiedOrUntrustedGoogleClaims() = runBlocking {
        val cases = listOf(
            Triple(401, "application/json",
                """{"id":"$id","email":"$email","app_metadata":{"provider":"google"}}"""),
            Triple(200, "text/html", "<html>Not JSON</html>"),
            Triple(200, "application/json", "{}"),
            Triple(200, "application/json", "x".repeat(65_537)),
            Triple(200, "application/json", "[]"),
            Triple(200, "application/json",
                """{"id":"$id","email":"$email","user_metadata":{"provider":"google"}}"""),
            Triple(200, "application/json",
                """{"id":"$id","email":"$email","app_metadata":{"provider":"email","providers":["email"]}}"""),
            Triple(200, "application/json",
                """{"id":"bad","email":"$email","app_metadata":{"provider":"google"}}"""),
            Triple(200, "application/json",
                """{"id":"$id","email":"","app_metadata":{"provider":"google"}}"""),
            Triple(200, "application/json",
                """{"id":"$id","email":"$email","app_metadata":{"providers":"google"}}""")
        )
        for ((status, type, body) in cases) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/user"),
                status, type, body
            )
            val verifier = DesktopSupabaseGoogleIdentityVerifier(key) { fake }
            val failure = assertFailsWith<Exception> {
                verifier.verifyGoogleIdentity(token)
            }
            assertTrue(fake.closed)
            assertFalse(failure.message.orEmpty().contains(token))
            assertFalse(failure.message.orEmpty().contains(email))
        }
    }

    @Test
    fun rejectsSecretKeyAndMalformedAccessTokenBeforeNetwork() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            DesktopSupabaseGoogleIdentityVerifier("sb_secret_never_ship")
        }
        var requests = 0
        val verifier = DesktopSupabaseGoogleIdentityVerifier(key) {
            requests++
            error("No network should be used")
        }
        assertFailsWith<IllegalArgumentException> {
            verifier.verifyGoogleIdentity("bad")
        }
        assertFailsWith<IllegalArgumentException> {
            verifier.verifyGoogleIdentity("invalid\r\nBearer token")
        }
        assertEquals(0, requests)
    }
}
