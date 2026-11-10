package com.timachado.brothermatrizes.desktop.platform

import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Adaptador Supabase Auth/Google PKCE do Windows, isolado da UI e do licenciamento.
 *
 * Os métodos não efetuam login até que sejam invocados explicitamente. Nenhuma
 * sessão é persistida, nenhum status Pro é inferido e a biblioteca de fontes
 * permanece bloqueada enquanto não houver homologação completa da conta.
 *
 * Requer habilitação do redirect no Supabase Auth > URL Configuration, sem
 * alterar configurações de produção automaticamente.
 */
internal class DesktopSupabaseGooglePkceClient(
    private val publishableKey: String,
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val projectRef: String = "dwpcddiramxlhavdmmyn"
) {
    // Preserve the original (key) { fakeHttp } test/QA call sites.
    constructor(
        publishableKey: String,
        openConnection: (URL) -> HttpURLConnection
    ) : this(publishableKey, openConnection, "dwpcddiramxlhavdmmyn")

    private val origin = "https://$projectRef.supabase.co"
    private val tokenEndpoint = URL(origin + "/auth/v1/token?grant_type=pkce")
    private val refreshEndpoint = URL(origin + "/auth/v1/token?grant_type=refresh_token")

    init {
        require(publishableKey.startsWith("sb_publishable_") &&
            publishableKey.length in 20..4096 &&
            publishableKey.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "Use somente uma chave pública Supabase válida, nunca secret/service_role."
        }
        require(projectRef.matches(Regex("[a-z0-9]{20}"))) {
            "Referência Supabase inválida."
        }
        check(tokenEndpoint.protocol == "https" &&
            tokenEndpoint.host == "$projectRef.supabase.co" &&
            tokenEndpoint.port == -1 && tokenEndpoint.userInfo == null) {
            "Servidor de autenticação inesperado."
        }
    }

    /** Apenas constrói a URL. Não abre o navegador ou conecta ao Supabase. */
    fun authorizationUrl(handshake: DesktopPkceHandshake): URI {
        val callback = handshake.redirectUri
        require(callback.scheme == "http" &&
            callback.host == "127.0.0.1" &&
            callback.port in 1024..65535 &&
            callback.path == "/brother-matrizes/auth" &&
            callback.rawQuery == "bm_state=" + handshake.state) {
            "Retorno OAuth Windows inválido."
        }
        require(handshake.codeChallenge.matches(Regex("[A-Za-z0-9_-]{43}"))) {
            "Desafio PKCE inválido."
        }
        val query = listOf(
            "provider" to "google",
            // Google account chooser must appear even when Chrome has a session.
            // Supabase forwards this provider query parameter to Google OAuth.
            "prompt" to "select_account",
            "redirect_to" to callback.toASCIIString(),
            "code_challenge" to handshake.codeChallenge,
            "code_challenge_method" to "s256"
        ).joinToString("&") { (key, value) ->
            key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)
        }
        return URI(origin + "/auth/v1/authorize?" + query)
    }

    /**
     * Troca de código PKCE em memória; NÃO vincular o retorno diretamente à
     * autorização comercial. O direito de uso permanece sob controle do servidor.
     * Uma falha exige novo fluxo de login: não reutilizar o mesmo código.
     */
    suspend fun exchangeCode(proof: DesktopPkceExchange): DesktopTransientAuthSession =
        withContext(Dispatchers.IO) {
            val code = proof.authorizationCode
            val verifier = proof.codeVerifier
            require(code.length in 1..2048 &&
                code.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
                "Código de autorização inválido."
            }
            require(verifier.matches(Regex("[A-Za-z0-9_-]{43,128}"))) {
                "Verificador PKCE inválido."
            }

            // JSON string escaping is intentionally limited to strict ASCII
            // code/verifier inputs validated above. No untrusted free-text.
            val payload = JsonObject(
                mapOf(
                    "auth_code" to JsonPrimitive(code),
                    "code_verifier" to JsonPrimitive(verifier)
                )
            ).toString()

            requestToken(tokenEndpoint, payload)
        }

    /**
     * Roda a rotação oficial do refresh token; devolve NOVA sessão transitória.
     * Cabe ao futuro gerenciador de sessão serializar rotações e persistir
     * apenas com armazenamento Windows protegido, nunca em texto puro.
     */
    suspend fun refreshSession(previous: DesktopTransientAuthSession): DesktopTransientAuthSession {
        require(previous.refreshToken.isValidToken()) { "Refresh token inválido." }
        return requestToken(
            refreshEndpoint,
            JsonObject(mapOf("refresh_token" to JsonPrimitive(previous.refreshToken))).toString()
        )
    }

    private suspend fun requestToken(
        endpoint: URL,
        payload: String
    ): DesktopTransientAuthSession = withContext(Dispatchers.IO) {
        // Fixed trusted Supabase host and exact approved token grant types.
        require(endpoint.protocol == "https" &&
            endpoint.host == "$projectRef.supabase.co" &&
            endpoint.port == -1 &&
            endpoint.userInfo == null &&
            endpoint.path == "/auth/v1/token" &&
            endpoint.query in setOf("grant_type=pkce", "grant_type=refresh_token")) {
            "Servidor de sessão inesperado."
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
            conn.outputStream.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            }
            check(conn.responseCode == HttpURLConnection.HTTP_OK) {
                "Supabase não confirmou a autenticação."
            }
            val mime = conn.contentType.orEmpty()
                .substringBefore(';').trim().lowercase()
            check(mime == "application/json" || mime.endsWith("+json")) {
                "Resposta de autenticação inesperada."
            }
            val json = Json.parseToJsonElement(readBounded(conn.inputStream))
            require(json is JsonObject) { "Resposta de autenticação inválida." }
            val access = json.text("access_token")
            val refresh = json.text("refresh_token")
            val tokenType = json.text("token_type")
            val expires = (json["expires_in"] as? JsonPrimitive)?.intOrNull
            check(tokenType.equals("bearer", ignoreCase = true) &&
                expires != null && expires in 1..86_400 &&
                access.isValidToken() && refresh.isValidToken()) {
                "Supabase retornou uma sessão inválida."
            }
            DesktopTransientAuthSession(access, refresh, expires)
        } finally {
            conn.disconnect()
        }
    }

    private fun String.isValidToken(): Boolean =
        length in 16..8192 && all { it.code in 0x21..0x7e && it != '"' && it != '\\' }

    private fun JsonObject.text(field: String): String =
        (this[field] as? JsonPrimitive)?.let {
            if (it.isString) it.content else ""
        }.orEmpty()

    private fun readBounded(stream: InputStream): String =
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                check(out.length + count <= 65_536) {
                    "Resposta de autenticação grande demais."
                }
                out.append(buffer, 0, count)
            }
            out.toString()
        }
}

/**
 * Dados transitórios apenas. Não representa entitlement, licença Pro
 * ou sessão persistida. Não imprimir tokens no log nem em exceções.
 */
internal class DesktopTransientAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int
) {
    override fun toString(): String = "DesktopTransientAuthSession(redacted)"
}
