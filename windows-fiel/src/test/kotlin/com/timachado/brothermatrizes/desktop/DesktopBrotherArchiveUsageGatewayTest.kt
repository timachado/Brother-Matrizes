package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherArchiveUsageGateway
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Fake HttpURLConnection: zero DNS, zero network, no Supabase credentials. */
class DesktopBrotherArchiveUsageGatewayTest {
    private val publishable = "sb_publishable_fake_qa_only_1234567890"
    private val token = "mock-verified-google-token-123456789"
    private val validReservation = "usage_12345678-1234-1234-1234-123456789abc"
    private val expectedEndpoint = URL(
        "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"
    )

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val mime: String,
        private val body: String
    ) : HttpURLConnection(url) {
        var disconnected = false
        val requestBytes = ByteArrayOutputStream()
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentType(): String = mime
        override fun getOutputStream(): OutputStream = requestBytes
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getErrorStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

        fun request() = Json.parseToJsonElement(
            requestBytes.toString(Charsets.UTF_8)
        ).jsonObject
    }

    private fun client(connection: FakeConnection) = DesktopBrotherArchiveUsageGateway(
        publishableKey = publishable,
        versionCode = 195
    ) { url ->
        assertEquals(expectedEndpoint.toString(), url.toString())
        connection
    }

    @Test
    fun reservesMatrixAndFontUsingRealAndroidRequestShapeAndHeaders() = runBlocking {
        for ((category, operation) in listOf(
            DesktopArchiveInventory.Category.MATRIX to "import_matrix",
            DesktopArchiveInventory.Category.FONT to "import_font"
        )) {
            val connection = FakeConnection(
                expectedEndpoint, 200, "application/json; charset=utf-8",
                """{"allowed":true,"operation":"$operation","request_key":"$validReservation"}"""
            )
            val gateway = client(connection)
            assertEquals(validReservation, gateway.reserve(token, category))
            assertEquals("POST", connection.requestMethod)
            assertEquals("reserve", connection.request()["action"]?.jsonPrimitive?.content)
            assertEquals(operation, connection.request()["operation"]?.jsonPrimitive?.content)
            assertEquals("Bearer $token", connection.getRequestProperty("Authorization"))
            assertEquals(publishable, connection.getRequestProperty("apikey"))
            assertEquals("195", connection.getRequestProperty("X-Brother-App-Version-Code"))
            assertEquals("application/json", connection.getRequestProperty("Accept"))
            assertTrue(connection.doOutput)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
            assertFalse(gateway.toString().contains(token))
            assertFalse(gateway.toString().contains(publishable))
        }
    }

    @Test
    fun finalizesExactReservationAndHonorsSuccessFalse() = runBlocking {
        for (success in listOf(true, false)) {
            val connection = FakeConnection(
                expectedEndpoint, 200, "application/json", """{"ok":true}"""
            )
            client(connection).finalize(
                token, DesktopArchiveInventory.Category.FONT,
                validReservation, success
            )
            assertEquals("finalize", connection.request()["action"]?.jsonPrimitive?.content)
            assertEquals("import_font", connection.request()["operation"]?.jsonPrimitive?.content)
            assertEquals(validReservation, connection.request()["request_key"]?.jsonPrimitive?.content)
            assertEquals(success.toString(), connection.request()["success"]?.jsonPrimitive?.content)
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun rejectedQuotaSpoofedOperationAndMalformedReservationFailClosed() = runBlocking {
        val responses = listOf(
            """{"allowed":false,"operation":"import_matrix","request_key":"$validReservation"}""",
            """{"allowed":true,"operation":"import_font","request_key":"$validReservation"}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"pro_local_override"}""",
            """{"allowed":true,"operation":"import_matrix","request_key":123}""",
            """{"allowed":true,"operation":"import_matrix"}"""
        )
        for (response in responses) {
            val connection = FakeConnection(expectedEndpoint, 200, "application/json", response)
            assertFailsWith<Exception> {
                client(connection).reserve(token, DesktopArchiveInventory.Category.MATRIX)
            }
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun refusesNonJsonMalformedOversizedRedirectAndHttpErrors() = runBlocking {
        val responses = listOf(
            Triple(302, "application/json", """{"allowed":true}"""),
            Triple(403, "application/json", """{"message":"denied"}"""),
            Triple(200, "text/html", "<html>not a JSON response</html>"),
            Triple(200, "application/json", "[]"),
            Triple(200, "application/json", "not json"),
            Triple(200, "application/json", "x".repeat(65_537))
        )
        for ((status, mime, reply) in responses) {
            val connection = FakeConnection(expectedEndpoint, status, mime, reply)
            assertFailsWith<Exception> {
                client(connection).reserve(token, DesktopArchiveInventory.Category.FONT)
            }
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
        }
    }

    @Test
    fun invalidPublicKeyTokenAndReservationNeverInitiateNetwork() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherArchiveUsageGateway("sb_secret_production_key", 195)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherArchiveUsageGateway(publishable, -1)
        }

        var opened = 0
        val gateway = DesktopBrotherArchiveUsageGateway(publishable, 195) {
            opened++
            error("No real network request allowed in test")
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.reserve("short", DesktopArchiveInventory.Category.MATRIX)
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.reserve("Bearer injected\r\nHeader: bad", DesktopArchiveInventory.Category.FONT)
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.finalize(
                token, DesktopArchiveInventory.Category.MATRIX,
                "locally_signed_forged_pro", true
            )
        }
        assertEquals(0, opened)
    }
}
