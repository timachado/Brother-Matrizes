package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidBaseline
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherTelegramUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopTelegramMatrixSearch
import com.timachado.brothermatrizes.desktop.platform.DesktopTelegramSearchAccess
import com.timachado.brothermatrizes.desktop.platform.DesktopTelegramSearchUnavailable
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedGoogleTokenSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No browser, Supabase network, Telegram login or real quota consumption. */
class DesktopTelegramMatrixSearchTest {
    private val request = "usage_12345678-1234-1234-1234-123456789abc"

    private class FakeAccess : DesktopTelegramSearchAccess {
        var allowed = true
        var reserved = 0
        val settled = mutableListOf<Boolean>()
        var badKey = false
        override suspend fun hasVerifiedGoogleSession() = allowed
        override suspend fun reserveSearch(): String {
            reserved++
            return if (badKey) "local_bypass" else
                "usage_12345678-1234-1234-1234-123456789abc"
        }
        override suspend fun finalizeSearch(requestKey: String, success: Boolean) {
            check(requestKey.startsWith("usage_"))
            settled += success
        }
    }

    @Test
    fun requiresVerifiedGoogleBeforeOpeningOrChargingQuota() = runBlocking {
        val gateway = FakeAccess().also { it.allowed = false }
        var launched = false
        assertFailsWith<IllegalStateException> {
            DesktopTelegramMatrixSearch.browse("flores", gateway) { launched = true }
        }
        assertFalse(launched)
        assertEquals(0, gateway.reserved)
        assertTrue(gateway.settled.isEmpty())
        assertFailsWith<IllegalStateException> {
            DesktopTelegramMatrixSearch.browse("flores", DesktopTelegramSearchUnavailable) {}
        }
    }

    @Test
    fun reservesBeforeLaunchAndFinalizesSuccessOrFailureExactlyOnce() = runBlocking {
        val gateway = FakeAccess()
        val order = mutableListOf<String>()
        val proxy = object : DesktopTelegramSearchAccess {
            override suspend fun hasVerifiedGoogleSession() = gateway.hasVerifiedGoogleSession()
            override suspend fun reserveSearch(): String {
                order += "reserve"
                return gateway.reserveSearch()
            }
            override suspend fun finalizeSearch(requestKey: String, success: Boolean) {
                order += "finalize:$success"
                gateway.finalizeSearch(requestKey, success)
            }
        }
        val destination = DesktopTelegramMatrixSearch.browse("matriz flor", proxy) {
            order += "launch"
            assertEquals("tg", it.scheme)
            assertEquals("search", it.host)
            assertTrue(it.rawQuery.orEmpty().contains("matriz%20flor"))
        }
        assertEquals("tg://search?query=matriz%20flor", destination.toASCIIString())
        assertEquals(listOf("reserve", "launch", "finalize:true"), order)
        assertEquals(listOf(true), gateway.settled)
        assertFailsWith<IllegalArgumentException> {
            DesktopTelegramMatrixSearch.browse("teste", proxy) {
                throw IllegalArgumentException("Telegram indisponível no PC")
            }
        }
        assertEquals(listOf(true, false), gateway.settled)
        assertEquals(2, gateway.reserved)
    }

    @Test
    fun rejectsFalseReceiptAndEncodedControlCharacters() = runBlocking {
        val gateway = FakeAccess().also { it.badKey = true }
        var launched = false
        assertFailsWith<IllegalStateException> {
            DesktopTelegramMatrixSearch.browse("rosa", gateway) { launched = true }
        }
        assertFalse(launched)
        assertTrue(gateway.settled.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            DesktopTelegramMatrixSearch.destination("uma\npesquisa")
        }
        assertEquals("tg://search", DesktopTelegramMatrixSearch.destination(" ").toString())
        val accented = DesktopTelegramMatrixSearch.destination("árvore azul")
        assertTrue(accented.rawQuery.orEmpty().contains("%C3%A1rvore%20azul"))
    }

    private class FakeConnection(url: URL, private val body: String) :
        HttpURLConnection(url) {
        val payload = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getOutputStream() = payload
        override fun getResponseCode(): Int = 200
        override fun getContentType(): String = "application/json"
        override fun getInputStream() =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun usesExactOfficialServerOperationAndPublicKeyWithFakeHttp() = runBlocking {
        val queue = ArrayDeque(
            listOf(
                """{"allowed":true,"operation":"telegram_search","request_key":"$request"}""",
                """{"ok":true}"""
            )
        )
        val opened = mutableListOf<FakeConnection>()
        val gateway = DesktopBrotherTelegramUsageGateway(
            publishableKey = "sb_publishable_12345678901234567890",
            versionCode = DesktopAndroidBaseline.VERSION_CODE,
            verifiedGoogle = DesktopVerifiedGoogleTokenSource {
                "verified-google-token-1234567890"
            },
            connectionFactory = { u ->
                FakeConnection(u, queue.removeFirst()).also { opened += it }
            }
        )
        assertTrue(gateway.hasVerifiedGoogleSession())
        assertEquals(request, gateway.reserveSearch())
        gateway.finalizeSearch(request, true)
        assertEquals(2, opened.size)
        for (conn in opened) {
            assertEquals("dwpcddiramxlhavdmmyn.supabase.co", conn.url.host)
            assertEquals("/functions/v1/brother-usage", conn.url.path)
            assertEquals(
                DesktopAndroidBaseline.VERSION_CODE.toString(),
                conn.getRequestProperty("X-Brother-App-Version-Code")
            )
            assertEquals("Bearer verified-google-token-1234567890",
                conn.getRequestProperty("Authorization"))
        }
        assertTrue(opened.first().payload.toString("UTF-8")
            .contains("\"operation\":\"telegram_search\""))
        assertTrue(opened.last().payload.toString("UTF-8")
            .contains("\"success\":true"))
        assertTrue(queue.isEmpty())
    }
}
