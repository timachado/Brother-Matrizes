package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopGoogleProviderReadiness
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** No user, OAuth browser, Supabase network, subscription or quota is touched. */
class DesktopGoogleProviderReadinessTest {
    private val key = "sb_publishable_offline_fake_key_0123456789"
    private val endpoint =
        "https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/settings"

    private class FakeSettings(
        url: URL,
        private val status: Int = 200,
        private val mime: String = "application/json; charset=utf-8",
        private val payload: String
    ) : HttpURLConnection(url) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun readsOnlyGoogleProviderWithoutLoggingInOrCheckingEntitlements() = runBlocking {
        for ((enabled, expected) in listOf(true to true, false to false)) {
            val fake = FakeSettings(
                URL(endpoint), payload = """{"external":{"google":$enabled}}"""
            )
            val readiness = DesktopGoogleProviderReadiness(key) { url ->
                assertEquals(endpoint, url.toString())
                fake
            }
            val snapshot = readiness.checkPublicGoogleProvider()
            assertEquals(expected, snapshot.googleProviderEnabled)
            assertFalse(snapshot.redirectAllowlistVerified)
            assertTrue(fake.closed)
            assertEquals("GET", fake.requestMethod)
            assertEquals(key, fake.getRequestProperty("apikey"))
            assertNull(fake.getRequestProperty("Authorization"))
            assertFalse(fake.instanceFollowRedirects)
        }
    }

    @Test
    fun rejectsSecretsAndInvalidPublishableKeysBeforeAnyNetwork() {
        for (unsafe in listOf(
            "sb_secret_never_publish",
            "service_role_1234567890",
            "anon",
            "sb_publishable_short",
            "sb_publishable_broken\nvalue",
            "sb_publishable_broken with spaces"
        )) {
            var opened = false
            assertFailsWith<IllegalArgumentException> {
                DesktopGoogleProviderReadiness(unsafe) {
                    opened = true
                    error("Unexpected network for invalid public key")
                }
            }
            assertFalse(opened)
        }
    }

    @Test
    fun missingProviderInvalidBodyHtmlRedirectAndOversizedJsonAllFailClosed() = runBlocking {
        val cases = listOf(
            Triple(200, "application/json", """{"external":{"google":"true"}}"""),
            Triple(200, "application/json", """{"external":{}}"""),
            Triple(200, "application/json", "{}"),
            Triple(200, "application/json", "[]"),
            Triple(200, "application/json", """{"external":{"google":null}}"""),
            Triple(200, "application/json", "x".repeat(16_385)),
            Triple(200, "text/html", """<html>not auth config</html>"""),
            Triple(302, "application/json", """{"external":{"google":true}}"""),
            Triple(403, "application/json", """{"external":{"google":true}}""")
        )
        for ((status, mime, body) in cases) {
            val fake = FakeSettings(URL(endpoint), status, mime, body)
            val preflight = DesktopGoogleProviderReadiness(key) { fake }
            val failure = assertFailsWith<Exception> {
                preflight.checkPublicGoogleProvider()
            }
            assertFalse(failure.message.orEmpty().contains(key))
            assertTrue(fake.closed)
        }
    }
}
