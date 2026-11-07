package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopPkceExchange
import com.timachado.brothermatrizes.desktop.platform.DesktopPkceHandshake
import com.timachado.brothermatrizes.desktop.platform.DesktopSupabaseGooglePkceClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Supabase/Google network is never used; every HTTP response comes from a fake. */
class DesktopSupabaseGooglePkceClientTest {
    private val key = "sb_publishable_offline_test_key_1234567890"
    private val access = "eyJ.test_mock_access_token_AAAAA"
    private val refresh = "offline_mock_refresh_token_BBBBBB"

    private class FakeConnection(
        url: URL,
        private val status: Int = 200,
        private val mime: String = "application/json",
        private val response: String = ""
    ) : HttpURLConnection(url) {
        private val buffer = ByteArrayOutputStream()
        var closed = false

        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))
        override fun getOutputStream(): OutputStream = buffer
        fun body() = buffer.toString(StandardCharsets.UTF_8)
    }

    @Test
    fun buildsGoogleAuthorizationUrlWithExpectedChallengeAndRedirect() {
        val flow = DesktopPkceHandshake.start(53421)
        val client = DesktopSupabaseGooglePkceClient(key) {
            error("Authorization URL builder must not use HTTP.")
        }
        val uri = client.authorizationUrl(flow)
        assertEquals("https", uri.scheme)
        assertEquals("dwpcddiramxlhavdmmyn.supabase.co", uri.host)
        assertEquals("/auth/v1/authorize", uri.path)
        val args = uri.rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
        }
        assertEquals("google", args["provider"])
        assertEquals(flow.redirectUri.toASCIIString(), args["redirect_to"])
        assertEquals(flow.codeChallenge, args["code_challenge"])
        assertEquals("s256", args["code_challenge_method"])
        assertEquals(4, args.size)
        assertFalse(uri.toString().contains("code_verifier"))
    }

    @Test
    fun exchangesCodeForEphemeralSessionWithoutLoggingSecrets() = runBlocking {
        val fake = FakeConnection(
            URL("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/token?grant_type=pkce"),
            response = """{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"token_type":"bearer"}"""
        )
        val client = DesktopSupabaseGooglePkceClient(key) { url ->
            assertEquals(
                "https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/token?grant_type=pkce",
                url.toString()
            )
            fake
        }
        val session = client.exchangeCode(
            DesktopPkceExchange("mock_auth_code_123", "a".repeat(43))
        )
        assertEquals(access, session.accessToken)
        assertEquals(refresh, session.refreshToken)
        assertEquals(3600, session.expiresInSeconds)
        assertFalse(session.toString().contains(access))
        assertFalse(session.toString().contains(refresh))
        assertTrue(fake.closed)
        assertEquals("POST", fake.requestMethod)
        assertEquals(key, fake.getRequestProperty("apikey"))
        assertEquals(false, fake.instanceFollowRedirects)
        val json = Json.parseToJsonElement(fake.body()) as JsonObject
        assertEquals("mock_auth_code_123", json["auth_code"]?.jsonPrimitive?.content)
        assertEquals("a".repeat(43), json["code_verifier"]?.jsonPrimitive?.content)
        assertFalse(fake.body().contains(refresh))
    }

    @Test
    fun rejectsBadResponseCodesAndFormatsWithoutLeakingSession() = runBlocking {
        val cases = listOf(
            Triple(302, "application/json", """{"access_token":"$access"}"""),
            Triple(403, "application/json", """{"access_token":"$access"}"""),
            Triple(200, "text/html", "<html>not an auth session</html>"),
            Triple(200, "application/json", "x".repeat(65_537)),
            Triple(200, "application/json", "[]"),
            Triple(200, "application/json", """{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"token_type":"mac"}"""),
            Triple(200, "application/json", """{"access_token":"$access","refresh_token":"$refresh","expires_in":0,"token_type":"bearer"}"""),
            Triple(200, "application/json", """{"access_token":"$access","refresh_token":"short","expires_in":3600,"token_type":"bearer"}"""),
            Triple(200, "application/json", """{"access_token":"$access","expires_in":3600,"token_type":"bearer"}""")
        )
        for ((status, mime, body) in cases) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/token?grant_type=pkce"),
                status, mime, body
            )
            val client = DesktopSupabaseGooglePkceClient(key) { fake }
            val error = assertFailsWith<Exception> {
                client.exchangeCode(DesktopPkceExchange("test_auth_code", "c".repeat(43)))
            }
            assertTrue(fake.closed)
            assertFalse(error.message.orEmpty().contains(access))
            assertFalse(error.message.orEmpty().contains(refresh))
        }
    }

    @Test
    fun rejectsSecretKeysAndInvalidVerifierBeforeAnyNetworkRequest() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            DesktopSupabaseGooglePkceClient("sb_secret_should_never_be_in_desktop")
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSupabaseGooglePkceClient("service_role")
        }
        var calls = 0
        val client = DesktopSupabaseGooglePkceClient(key) {
            calls++
            error("Network must never be called")
        }
        assertFailsWith<IllegalArgumentException> {
            client.exchangeCode(DesktopPkceExchange("test_auth_code", "bad"))
        }
        assertFailsWith<IllegalArgumentException> {
            client.exchangeCode(DesktopPkceExchange("bad\r\ncode", "a".repeat(43)))
        }
        assertEquals(0, calls)
    }
}
