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
internal interface DesktopArchiveIdempotentUsageGateway {
    // Current HTTP adapter only implements Free V2. Future Pro V2 must
    // explicitly declare PRO_V2 BEFORE the first network request.
    val reservationProtocol: DesktopArchiveQuotaJournal.ReservationProtocol
        get() = DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2

    /**
     * Client request ID MUST be the UUIDv4 persisted by the journal before
     * the first reserve attempt. A new ID is a NEW quota attempt.
     */
    suspend fun reserveOnce(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        clientRequestId: String
    ): String
}

internal interface DesktopArchiveQuotaStatusReader {
    /** Diagnostic ONLY. Never settles a quota or deletes a journal marker. */
    suspend fun lookupKnown(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String
    ): DesktopArchiveQuotaStatus
}

internal data class DesktopArchiveQuotaStatus(
    val requestKey: String,
    val category: DesktopArchiveInventory.Category,
    val state: State
) {
    enum class State { RESERVED, COMPLETED, FAILED, NOT_FOUND }
}

internal class DesktopBrotherArchiveUsageGateway(
    private val publishableKey: String,
    private val versionCode: Int,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val projectRef: String = "dwpcddiramxlhavdmmyn"
) : DesktopArchiveUsageGateway, DesktopArchiveQuotaStatusReader,
    DesktopArchiveIdempotentUsageGateway {
    constructor(
        publishableKey: String,
        versionCode: Int,
        connectionFactory: (URL) -> HttpURLConnection
    ) : this(publishableKey, versionCode, connectionFactory, "dwpcddiramxlhavdmmyn")

    private val endpoint = URL(
        "https://$projectRef.supabase.co/functions/v1/brother-usage"
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
        require(projectRef.matches(Regex("[a-z0-9]{20}"))) {
            "Referência Supabase inválida."
        }
        check(endpoint.protocol == "https" &&
            endpoint.host == "$projectRef.supabase.co" &&
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

    /**
     * Staged protocol. The server must have the reviewed SQL RPC and the
     * additive Edge Function deployed before this can succeed. Neither
     * change is activated by compiling this Windows class.
     */
    override suspend fun reserveOnce(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        clientRequestId: String
    ): String {
        require(
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
                .matches(clientRequestId)
        ) { "Identificador idempotente Windows inválido." }
        val operation = operationFor(category)
        val response = post(accessToken, buildJsonObject {
            put("action", "reserve_v2")
            put("operation", operation)
            put("client_request_id", clientRequestId)
        })
        val key = "usage_$clientRequestId"
        check(response.boolean("allowed") &&
            response.text("operation") == operation &&
            response.text("request_key") == key &&
            response.text("state") == "reserved" &&
            response.booleanOptional("replayed") != null
        ) {
            "Servidor não confirmou a reserva idempotente de importação."
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

    /**
     * Read-only reservation diagnostics, available in code but NOT wired to
     * Windows UI. Usage keys call "status"; UUIDv4 Pro V2 keys call
     * "status_pro_v2" (gated/undeployed). Legacy ephemeral Pro V1 keys
     * are NOT queryable.
     *
     * COMPLETED/FAILED/NOT_FOUND never authorize local journal removal:
     * ownership and the persisted finalization intent remain unverified.
     */
    override suspend fun lookupKnown(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String
    ): DesktopArchiveQuotaStatus {
        val proV2 = requestKey.startsWith("pro_")
        val proV2Format = Regex(
            "^pro_[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )
        require(reservationPattern.matches(requestKey) &&
            ((proV2 && proV2Format.matches(requestKey)) ||
                requestKey.startsWith("usage_"))) {
            "Somente reservas persistidas do Gratuito ou Pro V2 podem ser consultadas."
        }
        val operation = operationFor(category)
        val response = post(accessToken, buildJsonObject {
            put("action", if (proV2) "status_pro_v2" else "status")
            put("operation", operation)
            put("request_key", requestKey)
        })
        require(response.text("operation") == operation &&
            response.text("request_key") == requestKey) {
            "Resposta do servidor não corresponde à reserva consultada."
        }
        val found = response.booleanOptional("found")
            ?: error("Resposta do estado de cota sem confirmação.")
        if (!found) {
            require(response["state"] == null) {
                "Estado presente em resposta de reserva inexistente."
            }
            return DesktopArchiveQuotaStatus(
                requestKey, category, DesktopArchiveQuotaStatus.State.NOT_FOUND
            )
        }
        val state = when (response.text("state")) {
            "reserved" -> DesktopArchiveQuotaStatus.State.RESERVED
            "completed" -> DesktopArchiveQuotaStatus.State.COMPLETED
            "failed" -> DesktopArchiveQuotaStatus.State.FAILED
            else -> error("Servidor retornou um estado de cota desconhecido.")
        }
        return DesktopArchiveQuotaStatus(requestKey, category, state)
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

    private fun JsonObject.booleanOptional(field: String): Boolean? {
        val value = this[field] as? JsonPrimitive ?: return null
        // JSON string "true" must never be treated as a server boolean.
        return if (value.isString) null else value.booleanOrNull
    }

    private fun JsonObject.boolean(field: String): Boolean =
        booleanOptional(field) == true

    override fun toString(): String = "DesktopBrotherArchiveUsageGateway(redacted)"
}
