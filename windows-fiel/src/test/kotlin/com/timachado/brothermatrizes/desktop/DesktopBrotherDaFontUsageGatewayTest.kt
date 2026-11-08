package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherDaFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedGoogleTokenSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fully offline fake HTTP. Never calls production or charges user quotas. */
class DesktopBrotherDaFontUsageGatewayTest {
    private val key = "sb_publishable_offline_fake_header_safe_123"
    private val token = "offline_token_google_verified_for_qa_12345"
    private val requestKey = "usage_12345678-1234-4234-8234-123456789abc"

    private class FakeConnection(url: URL, private val response: String) :
        HttpURLConnection(url) {
        val posted = ByteArrayOutputStream()
        var closed = false
        override fun connect(): Unit = Unit
        override fun usingProxy(): Boolean = false
        override fun disconnect() { closed = true }
        override fun getResponseCode(): Int = 200
        override fun getContentType(): String = "application/json"
        override fun getOutputStream(): OutputStream = posted
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    }

    private fun gateway(
        server: FakeConnection,
        source: DesktopVerifiedGoogleTokenSource =
            DesktopVerifiedGoogleTokenSource { token },
        recordUrl: (URL) -> Unit = {}
    ): DesktopBrotherDaFontUsageGateway =
        DesktopBrotherDaFontUsageGateway(
            publishableKey = key,
            versionCode = 196,
            verifiedGoogle = source,
            connectionFactory = {
                recordUrl(it)
                server
            }
        )

    @Test
    fun reservesOnlyDaFontSearchAgainstExistingOfficialSupabase() = runBlocking {
        val url = URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage")
        val conn = FakeConnection(url,
            """{"allowed":true,"operation":"dafont_search","request_key":"$requestKey"}""")
        var called = 0
        val client = gateway(conn, recordUrl = {
            called++
            assertEquals(url.toString(), it.toString())
        })
        assertTrue(client.hasVerifiedGoogleSession())
        assertEquals(requestKey, client.reserveDaFontSearch())
        assertEquals(1, called)
        assertTrue(conn.closed)
        assertFalse(conn.instanceFollowRedirects)
        assertEquals("POST", conn.requestMethod)
        assertEquals(key, conn.getRequestProperty("apikey"))
        assertEquals("Bearer $token", conn.getRequestProperty("Authorization"))
        assertEquals("196", conn.getRequestProperty("X-Brother-App-Version-Code"))
        val payload = Json.parseToJsonElement(
            conn.posted.toString(Charsets.UTF_8)
        ).jsonObject
        assertEquals("reserve", payload["action"]?.jsonPrimitive?.content)
        assertEquals("dafont_search", payload["operation"]?.jsonPrimitive?.content)
        assertEquals(2, payload.keys.size)
        assertFalse(client.toString().contains(token))
    }

    @Test
    fun finalizesExactlyTheSameReservationAndBooleanResult() = runBlocking {
        for (success in listOf(true, false)) {
            val conn = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                """{"ok":true}"""
            )
            gateway(conn).finalizeDaFontSearch(requestKey, success)
            val posted = Json.parseToJsonElement(
                conn.posted.toString(Charsets.UTF_8)
            ).jsonObject
            assertEquals("finalize", posted["action"]?.jsonPrimitive?.content)
            assertEquals("dafont_search", posted["operation"]?.jsonPrimitive?.content)
            assertEquals(requestKey, posted["request_key"]?.jsonPrimitive?.content)
            assertEquals(success.toString(), posted["success"]?.jsonPrimitive?.content)
            assertEquals(4, posted.keys.size)
            assertTrue(conn.closed)
        }
    }

    @Test
    fun deniedOrStringAuthorizationNeverGrantsDaFontSearch() = runBlocking {
        for (flag in listOf("false", "\"true\"", "null")) {
            val conn = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                """{"allowed":$flag,"operation":"dafont_search","request_key":"$requestKey"}"""
            )
            val result = runCatching { gateway(conn).reserveDaFontSearch() }
            assertIs<IllegalStateException>(result.exceptionOrNull())
            assertTrue(conn.closed)
        }
    }

    @Test
    fun missingVerifiedGoogleTokenPreventsAllHttpBeforeConnecting() = runBlocking {
        val url = URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage")
        val conn = FakeConnection(url, """{"ok":true}""")
        var called = 0
        val noLogin = gateway(conn, source = DesktopVerifiedGoogleTokenSource { null },
            recordUrl = { called++ })
        assertFalse(noLogin.hasVerifiedGoogleSession())
        assertIs<IllegalStateException>(
            runCatching { noLogin.reserveDaFontSearch() }.exceptionOrNull()
        )
        assertEquals(0, called)
        assertTrue(conn.posted.size() == 0)
    }

    @Test
    fun unsafePublicKeysOrInvalidReceiptAreRejectedBeforeNetwork() = runBlocking {
        assertIs<IllegalArgumentException>(
            runCatching {
                DesktopBrotherDaFontUsageGateway(
                    "sb_secret_service_role", 196,
                    DesktopVerifiedGoogleTokenSource { token }
                )
            }.exceptionOrNull()
        )
        val conn = FakeConnection(
            URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
            """{"ok":true}"""
        )
        var called = 0
        assertIs<IllegalArgumentException>(
            runCatching {
                gateway(conn, recordUrl = { called++ }).finalizeDaFontSearch(
                    "different_operation_or_bad_key", true
                )
            }.exceptionOrNull()
        )
        assertEquals(0, called)
    }
}
