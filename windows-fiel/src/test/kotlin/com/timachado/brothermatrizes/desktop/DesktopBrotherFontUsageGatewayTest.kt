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
    private val publishableKey = "sb_publishable_offline_qa_test_0123456789"

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
            publishableKey = publishableKey,
            appVersionCode = 196
        ) { url ->
            assertEquals(
                "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage",
                url.toString()
            )
            connection
        }

    @Test
    fun `preflight da fonte não consome cota e bloqueia contrato antigo`() = runBlocking {
        val endpoint = URL(
            "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"
        )
        val success = FakeConnection(endpoint,
            response = """{"operation":"import_font","protocol":"free_v2","ready":true}""")
        client(success).ensureV2Ready("verified-access-token", "import_font")
        assertEquals(
            """{"action":"preflight_v2","operation":"import_font"}""",
            success.sentPayload()
        )
        assertTrue(success.disconnected)

        for (body in listOf(
            """{"operation":"import_font","protocol":"free_v2","ready":false}""",
            """{"operation":"import_font","protocol":"free_v2","ready":"true"}""",
            """{"operation":"import_matrix","protocol":"free_v2","ready":true}""",
            """{"operation":"import_font","protocol":"legacy_v1","ready":true}"""
        )) {
            val fake = FakeConnection(endpoint, response = body)
            assertFailsWith<IllegalStateException> {
                client(fake).ensureV2Ready("verified-access-token", "import_font")
            }
            assertEquals(
                """{"action":"preflight_v2","operation":"import_font"}""",
                fake.sentPayload()
            )
            assertTrue(fake.disconnected)
        }

        val oldServer = FakeConnection(endpoint, status = 400,
            response = """{"message":"unsupported action"}""")
        assertFailsWith<IllegalStateException> {
            client(oldServer).ensureV2Ready(
                "verified-access-token", "import_font")
        }
        assertTrue(oldServer.disconnected)
        var connections = 0
        val gateway = DesktopBrotherFontUsageGateway(publishableKey, 196) {
            connections++
            error("Never connect")
        }
        assertFailsWith<IllegalArgumentException> {
            gateway.ensureV2Ready("verified-access-token", "import_matrix")
        }
        assertEquals(0, connections)
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
        assertEquals(publishableKey, fake.getRequestProperty("apikey"))
        assertEquals("196", fake.getRequestProperty("X-Brother-App-Version-Code"))
        assertEquals(false, fake.instanceFollowRedirects)
        assertEquals(20_000, fake.connectTimeout)
        assertEquals(20_000, fake.readTimeout)
        assertTrue(fake.disconnected)
    }

    @Test
    fun `reserva Free V2 usa ID persistido e exige recibo idempotente`() = runBlocking {
        val id = "12345678-1234-4234-9234-123456789abc"
        for (replayed in listOf(false, true)) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                response = """{"allowed":true,"operation":"import_font","request_key":"usage_$id","state":"reserved","replayed":$replayed}"""
            )
            val key = client(fake).reserveFontOnce("verified-access-token", id)
            assertEquals("usage_$id", key)
            assertEquals(
                """{"action":"reserve_v2","operation":"import_font","client_request_id":"$id"}""",
                fake.sentPayload()
            )
            assertTrue(fake.disconnected)
        }
    }

    @Test
    fun `V2 recusa resposta legada ou Pro antes de importar e nunca baixa fonte`() = runBlocking {
        val id = "12345678-1234-4234-9234-123456789abc"
        val cases = listOf(
            """{"allowed":true,"operation":"import_font","request_key":"usage_$id"}""",
            """{"allowed":true,"operation":"import_font","request_key":"pro_$id","state":"reserved","replayed":false}""",
            """{"allowed":true,"operation":"import_matrix","request_key":"usage_$id","state":"reserved","replayed":false}""",
            """{"allowed":true,"operation":"import_font","request_key":"usage_$id","state":"reserved","replayed":"false"}""",
            """{"allowed":true,"operation":"import_font","request_key":"usage_$id","state":"completed","replayed":false}""",
            """{"allowed":false,"operation":"import_font","request_key":"usage_$id","state":"reserved","replayed":false}"""
        )
        for (body in cases) {
            val fake = FakeConnection(
                URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
                response = body
            )
            assertFailsWith<IllegalStateException> {
                client(fake).reserveFontOnce("verified-access-token", id)
            }
            assertTrue(fake.disconnected)
        }
        val legacyServer = FakeConnection(
            URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
            status = 400, response = """{"message":"invalid"}"""
        )
        assertFailsWith<IllegalStateException> {
            client(legacyServer).reserveFontOnce("verified-access-token", id)
        }
        assertEquals(
            """{"action":"reserve_v2","operation":"import_font","client_request_id":"$id"}""",
            legacyServer.sentPayload()
        )
    }

    @Test
    fun `reserva V2 nega UUID nao versionado antes de conectar`() = runBlocking {
        var requests = 0
        val gateway = DesktopBrotherFontUsageGateway(publishableKey, 196) {
            requests++
            error("Never connect with invalid client request key")
        }
        for (id in listOf("12345678-1234-1234-1234-123456789abc",
            "wrong", "12345678-1234-4234-1234-123456789abc")) {
            assertFailsWith<IllegalArgumentException> {
                gateway.reserveFontOnce("verified-access-token", id)
            }
        }
        assertEquals(0, requests)
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
            """{"allowed":"true","operation":"import_font","request_key":"$reservation"}""",
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
    fun `finalizacao nunca aceita texto true como confirmacao do servidor`() = runBlocking {
        val fake = FakeConnection(
            URL("https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"),
            response = """{"ok":"true"}"""
        )
        assertFailsWith<IllegalStateException> {
            client(fake).finalizeFont("verified-access-token", reservation, true)
        }
        assertTrue(fake.disconnected)
    }

    @Test
    fun `chave secreta nao pode ser usada no cliente Windows nem abrir rede`() {
        var calls = 0
        for (key in listOf(
            "sb_secret_restricted_private_key_1234567890",
            "service_role_private_key_1234567890",
            "eyJhbGciOiJIUzI1NiJ9.fake.signature",
            "public-test-key",
            "sb_publishable_x",
            "sb_publishable_unsafe key_1234567890",
            "sb_publishable_unsafe\r\nheader_1234567890"
        )) {
            assertFailsWith<IllegalArgumentException> {
                DesktopBrotherFontUsageGateway(key, 196) {
                    calls++
                    error("Nunca conectar com chave proibida.")
                }
            }
        }
        assertEquals(0, calls)
    }

    @Test
    fun `recusa credenciais ou requestKey malformados antes de enviar`() = runBlocking {
        var calls = 0
        val gateway = DesktopBrotherFontUsageGateway(publishableKey, 196) {
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
