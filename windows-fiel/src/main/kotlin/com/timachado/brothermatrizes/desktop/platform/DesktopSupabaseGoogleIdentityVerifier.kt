package com.timachado.brothermatrizes.desktop.platform

import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Identity confirmed by Supabase Auth, NOT a commercial license or Pro grant.
 * The account is checked against the server, never by decoding a local JWT
 * without signature validation, or by trusting user-editable metadata.
 */
internal class DesktopVerifiedGoogleIdentity(
    val userId: String,
    val email: String
) {
    override fun toString(): String = "DesktopVerifiedGoogleIdentity(redacted)"
}

/**
 * Auth service lookup for the signed-in user, mirroring Android
 * BrotherMatrizesAccountService.verifiedAccessToken()'s Google provider
 * requirement. Only a successful GET /auth/v1/user from the configured
 * Supabase project can confirm the identity.
 *
 * No access to Google, Supabase or commercial quotas until explicitly called.
 */
internal class DesktopSupabaseGoogleIdentityVerifier(
    private val publishableKey: String,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val projectRef: String = "dwpcddiramxlhavdmmyn"
) {
    private val endpoint = URL(
        "https://$projectRef.supabase.co/auth/v1/user"
    )
    private val uuid = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-" +
            "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
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
            endpoint.path == "/auth/v1/user" &&
            endpoint.query == null && endpoint.ref == null) {
            "Serviço de identidade inesperado."
        }
    }

    suspend fun verifyGoogleIdentity(accessToken: String): DesktopVerifiedGoogleIdentity =
        withContext(Dispatchers.IO) {
            require(accessToken.length in 16..8192 &&
                accessToken.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
                "Credencial de acesso inválida."
            }

            val conn = connectionFactory(endpoint)
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("apikey", publishableKey)
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                check(conn.responseCode == HttpURLConnection.HTTP_OK) {
                    "Conta não validada pelo Supabase Auth."
                }
                val type = conn.contentType.orEmpty()
                    .substringBefore(';').trim().lowercase()
                check(type == "application/json" || type.endsWith("+json")) {
                    "Resposta de identidade inesperada."
                }
                val user = Json.parseToJsonElement(readBounded(conn.inputStream))
                    as? JsonObject ?: error("Resposta de identidade inválida.")
                val id = user.stringField("id")
                val email = user.stringField("email")
                val appMetadata = user["app_metadata"] as? JsonObject
                    ?: error("Provedor de identidade ausente.")
                val primary = appMetadata.stringField("provider") == "google"
                val hasGoogle = (appMetadata["providers"] as? JsonArray)
                    ?.any { (it as? JsonPrimitive)?.let { p ->
                        p.isString && p.content == "google"
                    } == true } == true
                check(uuid.matches(id) && email.length in 3..320 &&
                    email.contains('@') && (primary || hasGoogle)) {
                    "É necessário entrar com uma conta Google validada."
                }
                DesktopVerifiedGoogleIdentity(id, email)
            } finally {
                conn.disconnect()
            }
        }

    private fun JsonObject.stringField(name: String): String =
        (this[name] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content.orEmpty()

    private fun readBounded(stream: InputStream): String =
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(4096)
            val result = StringBuilder()
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                check(result.length + n <= 65_536) {
                    "Resposta de identidade excedeu o limite seguro."
                }
                result.append(buffer, 0, n)
            }
            result.toString()
        }
}
