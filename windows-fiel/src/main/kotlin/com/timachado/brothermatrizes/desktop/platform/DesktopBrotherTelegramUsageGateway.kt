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
 * Same brother-usage reserve/finalize telegram_search calls as the Android
 * app. Strict Google verification and server-issued receipt required.
 * This is disabled unless the existing explicit Google Windows QA opt-in
 * is enabled; no local entitlements, quota bypass or background requests.
 */
internal class DesktopBrotherTelegramUsageGateway(
    private val publishableKey: String,
    private val versionCode: Int,
    private val verifiedGoogle: DesktopVerifiedGoogleTokenSource,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopTelegramSearchAccess {
    private val endpoint =
        DesktopBrotherBackendTarget.official().endpoint("/functions/v1/brother-usage")
    private val receipt = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    init {
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "A pesquisa Telegram exige a chave publicável do Supabase oficial."
        }
        require(versionCode in 1..10_000_000) {
            "Versão Brother Matrizes inválida."
        }
    }

    override suspend fun hasVerifiedGoogleSession(): Boolean =
        runCatching { validToken(verifiedGoogle.verifiedToken()) }.getOrNull() != null

    override suspend fun reserveSearch(): String {
        val response = post(
            requiredToken(), """{"action":"reserve","operation":"telegram_search"}"""
        )
        check(response.boolean("allowed") &&
            response.value("operation") == "telegram_search") {
            "O Supabase não autorizou esta pesquisa no Telegram."
        }
        val key = response.value("request_key")
        check(receipt.matches(key)) { "Reserva de pesquisa Telegram inválida." }
        return key
    }

    override suspend fun finalizeSearch(requestKey: String, success: Boolean) {
        require(receipt.matches(requestKey)) {
            "Reserva de pesquisa Telegram inválida para finalização."
        }
        val response = post(
            requiredToken(),
            """{"action":"finalize","operation":"telegram_search","request_key":"$requestKey","success":$success}"""
        )
        check(response.boolean("ok")) {
            "O Supabase não confirmou a finalização da pesquisa Telegram."
        }
    }

    private fun validToken(raw: String?): String? =
        raw?.takeIf { token ->
            token.length in 20..8192 &&
                token.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }
        }

    private suspend fun requiredToken(): String =
        validToken(verifiedGoogle.verifiedToken())
            ?: error("Sessão Google não validada no Brother Matrizes.")

    private suspend fun post(token: String, payload: String): JsonObject =
        withContext(Dispatchers.IO) {
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
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-Brother-App-Version-Code", versionCode.toString())
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                check(status == HttpURLConnection.HTTP_OK) {
                    "A autorização de pesquisa Telegram falhou (HTTP $status)."
                }
                val mime = connection.contentType.orEmpty()
                    .substringBefore(';').trim().lowercase()
                check(mime == "application/json" || mime.endsWith("+json")) {
                    "O servidor retornou conteúdo inesperado."
                }
                val parsed = Json.parseToJsonElement(
                    connection.inputStream.use(::readBounded)
                )
                check(parsed is JsonObject) {
                    "Resposta de autorização de pesquisa inválida."
                }
                parsed
            } finally {
                connection.disconnect()
            }
        }

    private fun readBounded(stream: InputStream): String {
        val out = ByteArray(65_537)
        var count = 0
        while (count < out.size) {
            val read = stream.read(out, count, out.size - count)
            if (read < 0) break
            count += read
        }
        check(count in 2..65_536) {
            "Resposta do servidor de pesquisa ausente ou acima do limite."
        }
        return String(out, 0, count, Charsets.UTF_8)
    }

    private fun JsonObject.boolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.let {
            !it.isString && it.booleanOrNull == true
        } == true

    private fun JsonObject.value(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    override fun toString() = "DesktopBrotherTelegramUsageGateway(redacted)"
}
