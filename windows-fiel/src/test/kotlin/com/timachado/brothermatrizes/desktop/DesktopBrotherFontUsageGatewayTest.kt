package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherFontUsageGateway
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests offline: HttpURLConnection is a fake, never production Supabase. */
class DesktopBrotherFontUsageGatewayTest {
    private val reservation = "usage_12345678-1234-1234-1234-123456789abc"

    private class FakeConnection(
        url: URL,
        private val status: Int = 200,
        private val response: String = "",
        private val mime: String = "application/json"
    ) : HttpURLConnection(url) {
        val written = ByteArrayOutputStream()
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
        override fun getOutputStream(): OutputStream = written

        fun sentPayload(): String = written.toString(Charsets.UTF_8)
    }

    private fun client(connection: FakeConnection): DesktopBrotherFontUsageGateway =
        DesktopBrotherFontUsageGateway(
            publishableKey = "public-test-key",
            appVersionCode = 196
        ) { url ->
            assertEquals(
                "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage",
                url.toString()
            )
            connection
        }

    @Test
    fun `reserva confirma contrato e headers autenticados`() = runBlocking {
        val fake = FakeConnection(
            URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
            response = """{"allowed":true,"operation":"import_font","request_key":"$reservation"}"""
        )
        val result = client(fake).reserveFont("verified-access-token")
        assertEquals(reservation, result)
        assertEquals("""{"action":"reserve","operation":"import_font"}""", fake.sentPayload())
        assertEquals("POST", fake.requestMethod)
        assertEquals("Bearer verified-access-token", fake.getRequestProperty("Authorization"))
        assertEquals("public-test-key", fake.getRequestProperty("apikey"))
        assertEquals("196", fake.getRequestProperty("X-Brother-App-Version-Code"))
        assertEquals(false, fake.instanceFollowRedirects)
        assertEquals(20_000, fake.connectTimeout)
        assertEquals(20_000, fake.readTimeout)
        assertTrue(fake.disconnected)
    }

    @Test
    fun `finaliza reserva do servidor com sucesso verdadeiro e falso`() = runBlocking {
        for (success in listOf(true, false)) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                response = """{"ok":true}"""
            )
            client(fake).finalizeFont("verified-access-token", reservation, success)
            assertEquals(
                """{"action":"finalize","operation":"import_font","request_key":"$reservation","success":$success}""",
                fake.sentPayload()
            )
            assertTrue(fake.disconnected)
        }
    }

    @Test
    fun `nega reservas invalidas ou do tipo errado`() = runBlocking {
        val cases = listOf(
            """{"allowed":false,"operation":"import_font","request_key":"$reservation"}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"$reservation"}""",
            """{"allowed":true,"operation":"import_font","request_key":"beta_local"}""",
            """{"allowed":true,"operation":"import_font"}"""
        )
        for (response in cases) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                response = response
            )
            assertFailsWith<IllegalStateException> {
                client(fake).reserveFont("verified-access-token")
            }
            assertTrue(fake.disconnected)
        }
    }

    @Test
    fun `nega respostas HTTP inseguras, nao JSON e acima do limite`() = runBlocking {
        val cases = listOf(
            Triple(302, "application/json", "{}"),
            Triple(401, "application/json", "{}"),
            Triple(200, "text/html", "<html/>"),
            Triple(200, "application/json", "x".repeat(65_537)),
            Triple(200, "application/json", "[]")
        )
        for ((status, mime, body) in cases) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                status = status, mime = mime, response = body
            )
            assertFailsWith<Exception> {
                client(fake).reserveFont("verified-access-token")
            }
            assertTrue(fake.disconnected)
        }
    }

    @Test
    fun `recusa credenciais ou requestKey malformados antes de enviar`() = runBlocking {
        var calls = 0
        val gateway = DesktopBrotherFontUsageGateway("public-test-key", 196) {
            calls++
            error("Nenhuma rede esperada")
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.reserveFont("token\r\nmalicioso")
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.finalizeFont("verified-access-token", "fake_pro_123", true)
        }
        assertEquals(0, calls)
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherFontUsageGateway("", 196)
        }
    }
}
