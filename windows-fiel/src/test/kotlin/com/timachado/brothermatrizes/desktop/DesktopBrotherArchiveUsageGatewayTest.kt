package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatus
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

    @Test
    fun knownReservationStatusIsReadOnlyAndScopedToCategoryAndKey() = runBlocking {
        for ((category, operation) in listOf(
            DesktopArchiveInventory.Category.MATRIX to "import_matrix",
            DesktopArchiveInventory.Category.FONT to "import_font"
        )) {
            for ((wireState, expected) in listOf(
                "reserved" to DesktopArchiveQuotaStatus.State.RESERVED,
                "completed" to DesktopArchiveQuotaStatus.State.COMPLETED,
                "failed" to DesktopArchiveQuotaStatus.State.FAILED
            )) {
                val reply = """{"found":true,"operation":"$operation","request_key":"$validReservation","state":"$wireState"}"""
                val connection = FakeConnection(expectedEndpoint, 200, "application/json", reply)
                val gateway = client(connection)
                val status = gateway.lookupKnown(token, category, validReservation)
                assertEquals(expected, status.state)
                assertEquals(category, status.category)
                assertEquals(validReservation, status.requestKey)
                val request = connection.request()
                assertEquals("status", request["action"]?.jsonPrimitive?.content)
                assertEquals(operation, request["operation"]?.jsonPrimitive?.content)
                assertEquals(validReservation, request["request_key"]?.jsonPrimitive?.content)
                assertEquals(3, request.keys.size)
                assertTrue(connection.disconnected)
            }
        }
    }

    @Test
    fun unknownReservationIsNotMistakenForConfirmedFailureOrPermission() = runBlocking {
        val connection = FakeConnection(
            expectedEndpoint, 200, "application/json",
            """{"found":false,"operation":"import_matrix","request_key":"$validReservation"}"""
        )
        val result = client(connection).lookupKnown(
            token, DesktopArchiveInventory.Category.MATRIX, validReservation
        )
        assertEquals(DesktopArchiveQuotaStatus.State.NOT_FOUND, result.state)
    }

    @Test
    fun statusRefusesProAndForgedKeysBeforeAnyHttpConnection() = runBlocking {
        var opened = 0
        val gateway = DesktopBrotherArchiveUsageGateway(publishable, 195) {
            opened++
            error("No network requests accepted")
        }
        for (key in listOf(
            "pro_12345678-1234-1234-1234-123456789abc",
            "usage_12345678-1234-1234-1234-123456789abx",
            "usage_00000000-0000-0000-0000-000000000000" + "\r\nX: a",
            "usage_trusted_local",
            ""
        )) {
            assertFailsWith<IllegalArgumentException> {
                gateway.lookupKnown(token, DesktopArchiveInventory.Category.MATRIX, key)
            }
        }
        assertEquals(0, opened)
    }

    @Test
    fun statusRejectsMismatchedOwnerEvidenceStateOrFailedNetwork() = runBlocking {
        val invalid = listOf(
            Triple(200, "application/json",
                """{"found":true,"operation":"import_font","request_key":"$validReservation","state":"completed"}"""),
            Triple(200, "application/json",
                """{"found":true,"operation":"import_matrix","request_key":"usage_00000000-0000-0000-0000-000000000000","state":"completed"}"""),
            Triple(200, "application/json",
                """{"found":true,"operation":"import_matrix","request_key":"$validReservation","state":"charged_unverified"}"""),
            Triple(200, "application/json",
                """{"found":false,"operation":"import_matrix","request_key":"$validReservation","state":"completed"}"""),
            Triple(200, "application/json",
                """{"operation":"import_matrix","request_key":"$validReservation","state":"completed"}"""),
            Triple(503, "application/json", """{"message":"service unavailable"}"""),
            Triple(200, "text/html", "<html>not a status</html>"),
            Triple(302, "application/json", """{"found":true}""")
        )
        for ((code, mime, body) in invalid) {
            val connection = FakeConnection(expectedEndpoint, code, mime, body)
            assertFailsWith<Exception> {
                client(connection).lookupKnown(
                    token, DesktopArchiveInventory.Category.MATRIX, validReservation
                )
            }
            assertTrue(connection.disconnected)
            assertFalse(connection.instanceFollowRedirects)
        }
    }

    @Test
    fun reserveV2SendsPersistedClientUuidAndReturnsIdenticalQuotaKey() = runBlocking {
        val id = "12345678-1234-4234-8234-123456789abc"
        for ((category, operation) in listOf(
            DesktopArchiveInventory.Category.MATRIX to "import_matrix",
            DesktopArchiveInventory.Category.FONT to "import_font"
        )) {
            for (replay in listOf(false, true)) {
                val connection = FakeConnection(
                    expectedEndpoint, 200, "application/json",
                    """{"allowed":true,"operation":"$operation","request_key":"usage_$id","state":"reserved","replayed":$replay,"remaining":null}"""
                )
                val result = client(connection).reserveOnce(token, category, id)
                assertEquals("usage_$id", result)
                val request = connection.request()
                assertEquals("reserve_v2", request["action"]?.jsonPrimitive?.content)
                assertEquals(operation, request["operation"]?.jsonPrimitive?.content)
                assertEquals(id, request["client_request_id"]?.jsonPrimitive?.content)
                assertEquals(3, request.keys.size)
                assertFalse(connection.instanceFollowRedirects)
                assertTrue(connection.disconnected)
            }
        }
    }

    @Test
    fun reserveV2RejectsMalformedIdsBeforeAnyNetwork() = runBlocking {
        var attempts = 0
        val gateway = DesktopBrotherArchiveUsageGateway(publishable, 195) {
            attempts++
            error("No real or simulated call allowed")
        }
        for (id in listOf(
            "", "usage_12345678-1234-4234-8234-123456789abc",
            "12345678-1234-1234-8234-123456789abc",
            "12345678-1234-4234-8234-123456789abc\nInjected: true",
            "12345678-1234-4234-8234-123456789abx"
        )) {
            assertFailsWith<IllegalArgumentException> {
                gateway.reserveOnce(token, DesktopArchiveInventory.Category.MATRIX, id)
            }
        }
        assertEquals(0, attempts)
    }

    @Test
    fun reserveV2CannotTrustForgedOrUnconfirmedServerReceipt() = runBlocking {
        val id = "12345678-1234-4234-8234-123456789abc"
        val badReplies = listOf(
            """{"allowed":true,"operation":"import_font","request_key":"usage_$id","state":"reserved","replayed":false}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"usage_00000000-0000-4000-8000-000000000000","state":"reserved","replayed":false}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"pro_$id","state":"reserved","replayed":false}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"usage_$id","state":"completed","replayed":true}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"usage_$id","state":"reserved"}""",
            """{"allowed":false,"operation":"import_matrix","request_key":"usage_$id","state":"reserved","replayed":true}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"usage_$id","state":"reserved","replayed":"true"}"""
        )
        for (body in badReplies) {
            val conn = FakeConnection(expectedEndpoint, 200, "application/json", body)
            assertFailsWith<Exception> {
                client(conn).reserveOnce(token, DesktopArchiveInventory.Category.MATRIX, id)
            }
            assertTrue(conn.disconnected)
        }
        for (code in listOf(400, 403, 409, 503)) {
            val conn = FakeConnection(
                expectedEndpoint, code, "application/json",
                """{"message":"not authorized"}"""
            )
            assertFailsWith<Exception> {
                client(conn).reserveOnce(token, DesktopArchiveInventory.Category.MATRIX, id)
            }
        }
    }
}
