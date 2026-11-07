package com.timachado.brothermatrizes.desktop.platform

import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Adaptador Windows isolado para o mesmo contrato brother-usage do Android.
 *
 * Não autentica, não armazena tokens, não libera funcionalidades na UI.
 * Só pode ser ligado ao gate após homologação do OAuth/PKCE do desktop.
 * Publicável (anon/publishable) não é service_role nem segredo do servidor.
 */
internal class DesktopBrotherFontUsageGateway(
    private val publishableKey: String,
    private val appVersionCode: Int,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopFontUsageGateway {
    private val endpoint = URL(
        "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"
    )
    private val keyPattern = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    init {
        require(publishableKey.isNotBlank() &&
            publishableKey.length <= 4096 &&
            publishableKey.none { it.isISOControl() }) {
            "Chave publicável inválida."
        }
        require(appVersionCode > 0) { "Versão do aplicativo inválida." }
        require(endpoint.protocol == "https" &&
            endpoint.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            endpoint.port == -1 &&
            endpoint.userInfo == null &&
            endpoint.query == null &&
            endpoint.ref == null &&
            endpoint.path == "/functions/v1/brother-usage") {
            "Servidor de cotas inesperado."
        }
    }

    override suspend fun reserveFont(accessToken: String): String {
        val body = post(accessToken,
            """{"action":"reserve","operation":"import_font"}""")
        check(body.bool("allowed") && body.string("operation") == "import_font") {
            "A importação de fonte não foi autorizada pelo servidor."
        }
        return body.string("request_key").also {
            check(keyPattern.matches(it)) { "Reserva de fonte inválida." }
        }
    }

    override suspend fun finalizeFont(
        accessToken: String,
        requestKey: String,
        success: Boolean
    ) {
        require(keyPattern.matches(requestKey)) { "Reserva de fonte inválida." }
        val payload = """{"action":"finalize","operation":"import_font","request_key":"$requestKey","success":$success}"""
        val body = post(accessToken, payload)
        check(body.bool("ok")) {
            "A confirmação de uso da fonte não foi aceita pelo servidor."
        }
    }

    private suspend fun post(accessToken: String, jsonBody: String): JsonObject =
        withContext(Dispatchers.IO) {
            require(accessToken.isNotBlank() && accessToken.length <= 8192 &&
                accessToken.none { it.isISOControl() }) {
                "Sessão de autenticação inválida."
            }
            val conn = openConnection(endpoint)
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("apikey", publishableKey)
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("X-Brother-App-Version-Code", appVersionCode.toString())
                conn.outputStream.use {
                    it.write(jsonBody.toByteArray(Charsets.UTF_8))
                }
                val status = conn.responseCode
                check(status == HttpURLConnection.HTTP_OK) {
                    "Servidor de cotas indisponível (HTTP $status)."
                }
                val mime = conn.contentType.orEmpty()
                    .substringBefore(';').trim().lowercase()
                check(mime == "application/json" || mime.endsWith("+json")) {
                    "Servidor de cotas retornou conteúdo inesperado."
                }
                val raw = conn.inputStream.use(::readBoundedJson)
                val parsed = Json.parseToJsonElement(raw)
                check(parsed is JsonObject) { "Resposta de cotas inválida." }
                parsed
            } finally {
                conn.disconnect()
            }
        }

    private fun readBoundedJson(stream: InputStream): String =
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(4096)
            val result = StringBuilder()
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                check(result.length + read <= 65_536) {
                    "Resposta do servidor excedeu o limite seguro."
                }
                result.append(buffer, 0, read)
            }
            result.toString()
        }

    private fun JsonObject.bool(field: String): Boolean =
        (this[field] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.string(field: String): String =
        (this[field] as? JsonPrimitive)?.let {
            if (it.isString) it.content else ""
        }.orEmpty()
}
