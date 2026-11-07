package com.timachado.brothermatrizes.desktop.platform

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Read-only, explicit QA check of Supabase GoTrue public provider settings.
 *
 * GET /auth/v1/settings reports external.google, but DOES NOT expose the
 * Supabase Auth Redirect URLs allowlist. Therefore a true result must never
 * be treated as proof that the 127.0.0.1 PKCE callback is approved, and it
 * cannot grant a paid feature or connect DesktopCommercialFontImportGate.
 */
internal class DesktopGoogleProviderReadiness(
    private val publishableKey: String,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    data class Snapshot(
        val googleProviderEnabled: Boolean,
        val redirectAllowlistVerified: Boolean = false
    )

    private val endpoint = URL(
        "https://dwpcddiramxlhavdmmyn.supabase.co/auth/v1/settings"
    )

    init {
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all { c -> !c.isISOControl() && !c.isWhitespace() }) {
            "A consulta exige chave Supabase pública, nunca secreta."
        }
        require(endpoint.protocol == "https" &&
            endpoint.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            endpoint.port == -1 && endpoint.userInfo == null &&
            endpoint.path == "/auth/v1/settings" &&
            endpoint.query == null && endpoint.ref == null) {
            "Endpoint público de autenticação inesperado."
        }
    }

    suspend fun checkPublicGoogleProvider(): Snapshot = withContext(Dispatchers.IO) {
        val conn = connectionFactory(endpoint)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("apikey", publishableKey)
            check(conn.responseCode == HttpURLConnection.HTTP_OK) {
                "Não foi possível consultar a disponibilidade do Google."
            }
            val mime = conn.contentType.orEmpty().substringBefore(';')
                .trim().lowercase()
            check(mime == "application/json" || mime.endsWith("+json")) {
                "Servidor retornou conteúdo inesperado."
            }
            val body = Json.parseToJsonElement(readLimited(conn.inputStream))
                as? JsonObject ?: error("Configuração pública inválida.")
            val external = body["external"] as? JsonObject
                ?: error("Lista pública de provedores indisponível.")
            val google = (external["google"] as? JsonPrimitive)?.booleanOrNull
                ?: error("Status do Google ausente da configuração.")
            Snapshot(googleProviderEnabled = google)
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream): String =
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val output = StringBuilder()
            val buffer = CharArray(1024)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                check(output.length + count <= 16_384) {
                    "A resposta de autenticação excedeu o limite seguro."
                }
                output.append(buffer, 0, count)
            }
            output.toString()
        }
}
