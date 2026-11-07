package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopExternalBrowser
import com.timachado.brothermatrizes.desktop.platform.DesktopGooglePkceLoginCoordinator
import com.timachado.brothermatrizes.desktop.platform.DesktopSupabaseGooglePkceClient
import com.timachado.brothermatrizes.desktop.platform.WindowsExternalBrowser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** End-to-end local socket and fake token endpoint; no real network calls. */
class DesktopGooglePkceLoginCoordinatorTest {
    private val testKey = "sb_publishable_offline_test_key_1234567890"
    private val access = "offline_access_token_1234567890123"
    private val refresh = "offline_refresh_token_1234567890123"

    private class FakeTokenEndpoint(url: URL, private val json: String) :
        HttpURLConnection(url) {
        val body = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "application/json"
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
        override fun getOutputStream(): OutputStream = body
    }

    @Test
    fun browserReceivesOnlyAuthorizationUrlAndCallbackIsExchangedOffline() = runBlocking {
        var openerUrl: URI? = null
        val endpoint = FakeTokenEndpoint(
            URL("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/token?grant_type=pkce"),
            """{"access_token":"$access","refresh_token":"$refresh","expires_in":3600,"token_type":"bearer"}"""
        )
        val client = DesktopSupabaseGooglePkceClient(testKey) { url ->
            assertEquals("https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/token?grant_type=pkce",
                url.toString())
            endpoint
        }
        val senderErrors = mutableListOf<Throwable>()
        var sender: Thread? = null
        val browser = DesktopExternalBrowser { url ->
            openerUrl = url
            assertEquals("https", url.scheme)
            assertEquals("/auth/v1/authorize", url.path)
            val args = url.rawQuery.split("&").associate {
                val split = it.split("=", limit = 2)
                split[0] to URLDecoder.decode(split[1], StandardCharsets.UTF_8)
            }
            assertEquals("google", args["provider"])
            assertEquals("s256", args["code_challenge_method"])
            val destination = URI(requireNotNull(args["redirect_to"]))
            sender = thread(start = true, name = "qa-google-callback", isDaemon = true) {
                try {
                    Socket("127.0.0.1", destination.port).use { socket ->
                        socket.soTimeout = 4000
                        val target = destination.rawPath + "?" + destination.rawQuery + "&code=mock_google_auth_code"
                        val request = "GET " + target + " HTTP/1.1\r\n" +
                            "Host: 127.0.0.1:" + destination.port + "\r\n" +
                            "Connection: close\r\n\r\n"
                        socket.getOutputStream().write(
                            request.toByteArray(StandardCharsets.US_ASCII)
                        )
                        val response = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
                        assertFalse(response.contains("mock_google_auth_code"))
                    }
                } catch (error: Throwable) {
                    synchronized(senderErrors) { senderErrors.add(error) }
                }
            }
        }
        val session = DesktopGooglePkceLoginCoordinator(
            client, browser, Duration.ofSeconds(8)
        ).signInForIsolatedQa()
        sender?.join(5000)
        assertTrue(senderErrors.isEmpty(), senderErrors.firstOrNull()?.message ?: "Sender failed")
        assertEquals(access, session.accessToken)
        assertEquals(refresh, session.refreshToken)
        assertEquals(3600, session.expiresInSeconds)
        assertEquals("dwpcddiramxlhavdmmyn.supabase.co", openerUrl?.host)
        assertTrue(endpoint.body.toString(StandardCharsets.UTF_8)
            .contains("mock_google_auth_code"))
        assertFalse(session.toString().contains(access))
        assertFalse(session.toString().contains(refresh))
    }

    @Test
    fun blocksNonSupabaseBrowserDestinationBeforeOpeningBrowser() {
        assertFailsWith<IllegalArgumentException> {
            WindowsExternalBrowser.open(URI("https://evil.example/authorize"))
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsExternalBrowser.open(URI("http://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/authorize"))
        }
        assertFailsWith<IllegalArgumentException> {
            WindowsExternalBrowser.open(URI("https://dwpcddiramxlhavdmmyn.supabase.co/other"))
        }
    }
}
