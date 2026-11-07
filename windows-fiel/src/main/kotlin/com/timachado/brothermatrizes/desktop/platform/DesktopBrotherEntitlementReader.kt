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
 * Read-only account summary from the SAME brother-entitlement endpoint used
 * by official Android BrotherEntitlementClient. Not a commercial permission.
 * Import quotas and all paid grants remain authoritative server-side.
 */
internal class DesktopServerAccountSnapshot(
    val userId: String,
    val planCode: String,
    val hasServerPro: Boolean,
    val subscriptionStatus: String
) {
    override fun toString(): String = "DesktopServerAccountSnapshot(redacted)"
}

/** No POST, no trial activation, no WordPress writes and no local Pro override. */
internal class DesktopBrotherEntitlementReader(
    private val publishableKey: String,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val projectRef: String = "dwpcddiramxlhavdmmyn"
) {
    constructor(
        publishableKey: String,
        connectionFactory: (URL) -> HttpURLConnection
    ) : this(publishableKey, connectionFactory, "dwpcddiramxlhavdmmyn")

    private val endpoint = URL(
        "https://$projectRef.supabase.co/functions/v1/brother-entitlement"
    )
    private val paid = setOf(
        "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch", "trial"
    )

    init {
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "Use somente uma chave pública Supabase."
        }
        require(projectRef.matches(Regex("[a-z0-9]{20}"))) {
            "Referência Supabase inválida."
        }
        check(endpoint.protocol == "https" &&
            endpoint.host == "$projectRef.supabase.co" &&
            endpoint.port == -1 && endpoint.userInfo == null &&
            endpoint.path == "/functions/v1/brother-entitlement" &&
            endpoint.query == null && endpoint.ref == null) {
            "Servidor comercial inesperado."
        }
    }

    suspend fun readOnlyAccount(
        identity: DesktopVerifiedGoogleIdentity,
        accessToken: String
    ): DesktopServerAccountSnapshot = withContext(Dispatchers.IO) {
        require(accessToken.length in 16..8192 &&
            accessToken.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "Sessão inválida."
        }
        require(identity.userId.matches(Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )) && identity.email.contains('@')) {
            "Identidade não verificada."
        }
        val conn = connectionFactory(endpoint)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("apikey", publishableKey)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            check(conn.responseCode == HttpURLConnection.HTTP_OK) {
                "Não foi possível confirmar a assinatura no servidor."
            }
            val mime = conn.contentType.orEmpty()
                .substringBefore(';').trim().lowercase()
            check(mime == "application/json" || mime.endsWith("+json")) {
                "Resposta comercial inesperada."
            }
            val body = Json.parseToJsonElement(readBounded(conn.inputStream))
                as? JsonObject ?: error("Resposta de assinatura inválida.")
            val user = body["user"] as? JsonObject
                ?: error("Resposta sem identidade verificada.")
            // Never accept JSON string "true" or "false" as a server grant.
            val authenticated = body.nativeBoolean("authenticated") == true
            val subject = user.text("id")
            val email = user.text("email")
            check(authenticated && subject.equals(identity.userId, ignoreCase = true) &&
                email.equals(identity.email, ignoreCase = true)) {
                "A assinatura não corresponde à conta Google autenticada."
            }
            val plan = body.text("planCode")
            val active = body.text("status") == "active"
            val serverPro = body.nativeBoolean("pro") == true
            val permitted = serverPro && active && plan in paid
            // This is informational; do not pass the result to commercial gates.
            DesktopServerAccountSnapshot(
                userId = subject,
                planCode = if (permitted) plan else "free",
                hasServerPro = permitted,
                subscriptionStatus = if (active) "active" else "unavailable"
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.nativeBoolean(field: String): Boolean? {
        val value = this[field] as? JsonPrimitive ?: return null
        return if (value.isString) null else value.booleanOrNull
    }

    private fun JsonObject.text(field: String): String =
        (this[field] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content.orEmpty()

    private fun readBounded(stream: InputStream): String =
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val content = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                check(content.length + n <= 65_536) {
                    "Resposta da assinatura excedeu o limite."
                }
                content.append(buffer, 0, n)
            }
            content.toString()
        }
}
