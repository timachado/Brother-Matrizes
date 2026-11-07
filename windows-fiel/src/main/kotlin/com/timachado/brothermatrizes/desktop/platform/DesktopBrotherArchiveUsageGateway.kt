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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * HTTP adapter for the SAME brother-usage reserve/finalize contract as Android.
 *
 * NOT connected to the desktop application, login or import screen.
 * No constructor side effects. All network I/O occurs only if the
 * authenticated gate explicitly invokes reserve/finalize.
 *
 * A public Supabase key is provided by the caller, never hardcoded here.
 * Authorization decisions are made by server JSON, not locally.
 */
internal class DesktopBrotherArchiveUsageGateway(
    private val publishableKey: String,
    private val versionCode: Int,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopArchiveUsageGateway {
    private val endpoint = URL(
        "https://dwpcddiramxlhavdmmyn.supabase.co/functions/v1/brother-usage"
    )
    private val reservationPattern = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    init {
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "Somente a chave pública Supabase é permitida no cliente Windows."
        }
        require(versionCode in 1..10_000_000) { "Versão Android oficial inválida." }
        check(endpoint.protocol == "https" &&
            endpoint.host == "dwpcddiramxlhavdmmyn.supabase.co" &&
            endpoint.port == -1 && endpoint.userInfo == null &&
            endpoint.path == "/functions/v1/brother-usage" &&
            endpoint.query == null && endpoint.ref == null) {
            "Endpoint de cotas não autorizado."
        }
    }

    override suspend fun reserve(
        accessToken: String,
        category: DesktopArchiveInventory.Category
    ): String {
        val operation = operationFor(category)
        val response = post(
            accessToken,
            buildJsonObject {
                put("action", "reserve")
                put("operation", operation)
            }
        )
        check(response.boolean("allowed") && response.text("operation") == operation) {
            "Servidor não autorizou a importação."
        }
        val key = response.text("request_key")
        check(reservationPattern.matches(key)) {
            "Chave de reserva inválida retornada pelo servidor."
        }
        return key
    }

    override suspend fun finalize(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String,
        success: Boolean
    ) {
        require(reservationPattern.matches(requestKey)) {
            "Reserva inválida para finalizar importação."
        }
        val response = post(
            accessToken,
            buildJsonObject {
                put("action", "finalize")
                put("operation", operationFor(category))
                put("request_key", requestKey)
                put("success", success)
            }
        )
        check(response.boolean("ok")) {
            "Servidor não confirmou a finalização da importação."
        }
    }

    private fun operationFor(category: DesktopArchiveInventory.Category): String =
        when (category) {
            DesktopArchiveInventory.Category.MATRIX -> "import_matrix"
            DesktopArchiveInventory.Category.FONT -> "import_font"
        }

    private suspend fun post(accessToken: String, payload: JsonObject): JsonObject =
        withContext(Dispatchers.IO) {
            require(accessToken.length in 16..8192 &&
                accessToken.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
                "Sessão Google inválida."
            }
            val connection = connectionFactory(endpoint)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("apikey", publishableKey)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Brother-App-Version-Code", versionCode.toString())
                connection.outputStream.use { stream ->
                    stream.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val code = connection.responseCode
                val mime = connection.contentType.orEmpty()
                    .substringBefore(';').trim().lowercase()
                check(mime == "application/json" || mime.endsWith("+json")) {
                    "Resposta do serviço de cotas não é JSON."
                }
                val reply = if (code in 200..299) connection.inputStream
                    else connection.errorStream
                val raw = readBounded(reply)
                val parsed = Json.parseToJsonElement(raw) as? JsonObject
                    ?: error("Resposta de cotas inválida.")
                check(code == HttpURLConnection.HTTP_OK) {
                    "Servidor recusou a operação de importação (HTTP $code)."
                }
                parsed
            } finally {
                connection.disconnect()
            }
        }

    private fun readBounded(input: InputStream?): String {
        requireNotNull(input) { "Servidor não retornou resposta." }
        return input.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(4096)
            val out = StringBuilder()
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                check(out.length + n <= 65_536) { "Resposta de cotas excedeu 64 KiB." }
                out.append(buffer, 0, n)
            }
            out.toString()
        }
    }

    private fun JsonObject.text(field: String): String =
        (this[field] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content.orEmpty()

    private fun JsonObject.boolean(field: String): Boolean =
        (this[field] as? JsonPrimitive)?.booleanOrNull == true

    override fun toString(): String = "DesktopBrotherArchiveUsageGateway(redacted)"
}
