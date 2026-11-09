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
 * Offline/staging QA transport for the NOT-YET-DEPLOYED Pro V2 receipts.
 *
 * This class is intentionally NOT connected to the Windows application.
 * It REFUSES the official production Supabase project in its constructor.
 * After dedicated staging and ownership/entitlement QA, production rollout
 * requires a separate reviewed code change; never toggle a flag here.
 *
 * No service_role, Pro grant, payment, session persistence, automatic retry
 * or local entitlement can be performed by this adapter.
 */
internal class DesktopBrotherProV2QaGateway(
    private val publishableKey: String,
    private val versionCode: Int,
    stagingProjectRef: String,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : DesktopArchiveUsageGateway, DesktopArchiveIdempotentUsageGateway,
    DesktopArchiveQuotaStatusReader, DesktopUsageV2Preflight {

    override val reservationProtocol =
        DesktopArchiveQuotaJournal.ReservationProtocol.PRO_V2

    private val requestId = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    )
    private val endpoint: URL

    init {
        require(stagingProjectRef.matches(Regex("^[a-z0-9]{20}$")) &&
            stagingProjectRef != DesktopBrotherBackendTarget.OFFICIAL_REF) {
            "Recibos Pro V2 só podem ser testados em projeto staging distinto."
        }
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all {
                it.code in 0x21..0x7e && it != '"' && it != '\\'
            }) { "Use somente chave Supabase publicável de staging." }
        require(versionCode in 1..10_000_000) { "Versão QA inválida." }
        endpoint = URL("https://" + stagingProjectRef +
            ".supabase.co/functions/v1/brother-usage")
        check(endpoint.protocol == "https" &&
            endpoint.host == stagingProjectRef + ".supabase.co" &&
            endpoint.port == -1 && endpoint.userInfo == null &&
            endpoint.path == "/functions/v1/brother-usage" &&
            endpoint.query == null && endpoint.ref == null) {
            "Endpoint staging inesperado."
        }
    }

    /** Read-only staging gate: never create a Pro journal on legacy servers. */
    override suspend fun ensureV2Ready(accessToken: String, operation: String) {
        require(operation == "import_font" || operation == "import_matrix") {
            "A operação Pro V2 para diagnóstico é inválida."
        }
        val response = post(accessToken, buildJsonObject {
            put("action", "preflight_v2")
            put("operation", operation)
        })
        check(response.text("operation") == operation &&
            response.text("protocol") == "pro_v2" &&
            response.bool("ready") == true) {
            "O servidor staging ainda não confirmou Pro V2. " +
                "Nenhuma cota nem arquivo foi criado."
        }
    }

    /** Legacy Pro V1 has no durable receipt and is NEVER a fallback. */
    override suspend fun reserve(
        accessToken: String, category: DesktopArchiveInventory.Category
    ): String = error("Pro V2 exige UUID persistido antes de reservar.")

    override suspend fun reserveOnce(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        clientRequestId: String
    ): String {
        require(requestId.matches(clientRequestId)) {
            "UUIDv4 da tentativa Pro V2 inválido."
        }
        val operation = operationFor(category)
        val result = post(accessToken, buildJsonObject {
            put("action", "reserve_pro_v2")
            put("operation", operation)
            put("client_request_id", clientRequestId)
        })
        val key = "pro_" + clientRequestId
        check(result.bool("allowed") == true &&
            result.bool("pro") == true &&
            result.text("operation") == operation &&
            result.text("request_key") == key &&
            result.text("state") == "reserved" &&
            result.bool("replayed") != null) {
            "Servidor staging não confirmou recibo Pro V2."
        }
        return key
    }

    override suspend fun finalize(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String,
        success: Boolean
    ) {
        require(validProV2Key(requestKey)) {
            "Finalização exige recibo Pro V2 persistido."
        }
        val operation = operationFor(category)
        val result = post(accessToken, buildJsonObject {
            put("action", "finalize_pro_v2")
            put("operation", operation)
            put("request_key", requestKey)
            put("success", success)
        })
        check(result.bool("ok") == true &&
            result.text("operation") == operation &&
            result.text("request_key") == requestKey &&
            result.text("state") == (if (success) "completed" else "failed")) {
            "Finalização Pro V2 não confirmada com estado esperado."
        }
        check(result.bool("replayed") != null) {
            "Recibo Pro V2 sem sinal de repetição válido."
        }
    }

    /** Diagnostic only: this does NOT reconcile/delete any local journal. */
    override suspend fun lookupKnown(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String
    ): DesktopArchiveQuotaStatus {
        require(validProV2Key(requestKey)) {
            "Consulta exige recibo Pro V2 persistido."
        }
        val operation = operationFor(category)
        val result = post(accessToken, buildJsonObject {
            put("action", "status_pro_v2")
            put("operation", operation)
            put("request_key", requestKey)
        })
        check(result.text("operation") == operation &&
            result.text("request_key") == requestKey) {
            "Estado Pro V2 não corresponde ao recibo consultado."
        }
        val found = result.bool("found")
            ?: error("Estado Pro V2 sem confirmação.")
        val state = if (!found) {
            check(result["state"] == null) {
                "Recibo inexistente não pode retornar estado."
            }
            DesktopArchiveQuotaStatus.State.NOT_FOUND
        } else when (result.text("state")) {
            "reserved" -> DesktopArchiveQuotaStatus.State.RESERVED
            "completed" -> DesktopArchiveQuotaStatus.State.COMPLETED
            "failed" -> DesktopArchiveQuotaStatus.State.FAILED
            else -> error("Estado Pro V2 desconhecido.")
        }
        return DesktopArchiveQuotaStatus(requestKey, category, state)
    }

    private fun validProV2Key(key: String): Boolean =
        key.startsWith("pro_") && requestId.matches(key.removePrefix("pro_"))

    private fun operationFor(category: DesktopArchiveInventory.Category): String =
        when (category) {
            DesktopArchiveInventory.Category.FONT -> "import_font"
            DesktopArchiveInventory.Category.MATRIX -> "import_matrix"
        }

    private suspend fun post(
        accessToken: String, payload: JsonObject
    ): JsonObject = withContext(Dispatchers.IO) {
        require(accessToken.length in 16..8192 &&
            accessToken.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "Sessão Google QA inválida."
        }
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
            conn.setRequestProperty("Authorization", "Bearer " + accessToken)
            conn.setRequestProperty("X-Brother-App-Version-Code", versionCode.toString())
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            check(status == HttpURLConnection.HTTP_OK) {
                "Pro V2 staging indisponível (HTTP " + status + ")."
            }
            val mime = conn.contentType.orEmpty().substringBefore(';')
                .trim().lowercase()
            check(mime == "application/json" || mime.endsWith("+json")) {
                "Recibo Pro V2 não é JSON."
            }
            val parsed = Json.parseToJsonElement(readBounded(conn.inputStream))
            check(parsed is JsonObject) { "Recibo Pro V2 inválido." }
            parsed
        } finally {
            conn.disconnect()
        }
    }

    private fun readBounded(input: InputStream): String =
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buf = CharArray(1024)
            while (true) {
                val count = reader.read(buf)
                if (count < 0) break
                check(out.length + count <= 16_384) {
                    "Recibo Pro V2 excedeu limite seguro."
                }
                out.append(buf, 0, count)
            }
            out.toString()
        }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

    override fun toString(): String = "DesktopBrotherProV2QaGateway(redacted)"
}
