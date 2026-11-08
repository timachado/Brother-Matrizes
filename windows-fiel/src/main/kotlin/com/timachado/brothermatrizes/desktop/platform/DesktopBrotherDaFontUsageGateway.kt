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
 * The deployed Brother Matrizes 'brother-usage' v7 supports dafont_search.
 * This adapter preserves the SAME Google identity + quota ledger as Android.
 *
 * Not wired to the default Windows UI: a trusted session provider can only
 * be installed after the Desktop Google OAuth redirect is fully homologated.
 * No constructor requests, account creation, local quota or secret keys.
 */
internal fun interface DesktopVerifiedGoogleTokenSource {
    suspend fun verifiedToken(): String?
}

internal class DesktopBrotherDaFontUsageGateway(
    private val publishableKey: String,
    private val versionCode: Int,
    private val verifiedGoogle: DesktopVerifiedGoogleTokenSource,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopFontCatalogAccess {
    private val endpoint =
        DesktopBrotherBackendTarget.official().endpoint("/functions/v1/brother-usage")

    private val keyPattern = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    init {
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all { c ->
                c.code in 0x21..0x7e && c != '"' && c != '\\'
            }) { "É necessária uma chave publicável do Supabase oficial." }
        require(versionCode in 1..10_000_000) {
            "Versão Brother Matrizes inválida."
        }
    }

    override suspend fun hasVerifiedGoogleSession(): Boolean =
        usableTokenOrNull(verifiedGoogle.verifiedToken()) != null

    override suspend fun reserveDaFontSearch(): String {
        val body = post(requireVerifiedToken(),
            """{"action":"reserve","operation":"dafont_search"}""")
        check(body.trueBoolean("allowed") &&
            body.typedText("operation") == "dafont_search") {
            "Pesquisa DaFont não autorizada pelas cotas do servidor."
        }
        val key = body.typedText("request_key")
        check(keyPattern.matches(key)) {
            "Reserva de pesquisa DaFont inválida."
        }
        return key
    }

    override suspend fun finalizeDaFontSearch(
        requestKey: String, success: Boolean
    ) {
        require(keyPattern.matches(requestKey)) {
            "Reserva DaFont inválida para finalização."
        }
        val json = """{"action":"finalize","operation":"dafont_search","request_key":"$requestKey","success":$success}"""
        val body = post(requireVerifiedToken(), json)
        check(body.trueBoolean("ok")) {
            "O Supabase não confirmou o registro da pesquisa DaFont."
        }
    }

    private fun usableTokenOrNull(token: String?): String? =
        token?.takeIf { it.length in 20..8192 &&
            it.all { c ->
                c.code in 0x21..0x7e && c != '"' && c != '\\'
            }
        }

    private suspend fun requireVerifiedToken(): String =
        usableTokenOrNull(verifiedGoogle.verifiedToken())
            ?: error("Conta Google não validada no Brother Matrizes.")

    private suspend fun post(token: String, payload: String): JsonObject =
        withContext(Dispatchers.IO) {
            val conn = connectionFactory(endpoint)
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
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("X-Brother-App-Version-Code", versionCode.toString())
                conn.outputStream.use {
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }
                val status = conn.responseCode
                check(status == HttpURLConnection.HTTP_OK) {
                    "Serviço de pesquisa DaFont recusou a operação (HTTP $status)."
                }
                val mime = conn.contentType.orEmpty().substringBefore(';')
                    .trim().lowercase()
                check(mime == "application/json" || mime.endsWith("+json")) {
                    "Resposta inesperada do serviço de cotas."
                }
                val json = conn.inputStream.use(::readBoundedJson)
                val parsed = Json.parseToJsonElement(json)
                check(parsed is JsonObject) {
                    "Resposta de pesquisa DaFont inválida."
                }
                parsed
            } finally {
                conn.disconnect()
            }
        }

    private fun readBoundedJson(stream: InputStream): String {
        val result = ByteArray(65_537)
        var count = 0
        while (count < result.size) {
            val n = stream.read(result, count, result.size - count)
            if (n < 0) break
            count += n
        }
        check(count in 2..65_536) {
            "Resposta de cotas ausente ou maior que 64 KiB."
        }
        return String(result, 0, count, Charsets.UTF_8)
    }

    private fun JsonObject.trueBoolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.let {
            !it.isString && it.booleanOrNull == true
        } == true

    private fun JsonObject.typedText(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }
            ?.content.orEmpty()

    override fun toString() = "DesktopBrotherDaFontUsageGateway(redacted)"
}
