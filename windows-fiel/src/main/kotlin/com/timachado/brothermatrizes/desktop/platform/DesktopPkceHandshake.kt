package com.timachado.brothermatrizes.desktop.platform

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * PKCE S256 + CSRF state for a native Windows login, RFC 7636 / RFC 8252.
 *
 * Isolated building block only: does not open the browser, listen on a port,
 * exchange the code with Supabase, persist sessions, or unlock licensed UI.
 * The caller must bind a listener ONLY to 127.0.0.1, with an ephemeral port,
 * and register/verify its redirect URI in the Supabase redirect allow list.
 * A browser-based login must use an external browser, not an embedded WebView.
 */
internal class DesktopPkceHandshake private constructor(
    val redirectUri: URI,
    val codeChallenge: String,
    val state: String,
    private val verifier: String,
    private val startedAt: Instant
) {
    private var used = false

    /** Only a validated, single-use code may carry the secret verifier onward. */
    @Synchronized
    fun acceptCallback(callback: URI, now: Instant = Instant.now()): DesktopPkceExchange {
        check(!used) { "Login já concluído; inicie uma nova autenticação." }
        check(!now.isBefore(startedAt) &&
            Duration.between(startedAt, now) <= Duration.ofMinutes(5)) {
            "A autorização expirou; inicie um novo login."
        }
        require(callback.scheme == "http" &&
            callback.host == "127.0.0.1" &&
            callback.port == redirectUri.port &&
            callback.rawUserInfo == null &&
            callback.path == redirectUri.path &&
            callback.rawFragment == null) {
            "Destino de retorno OAuth inválido."
        }
        val params = parseQuery(callback.rawQuery)
        require(params.size == 2 && params.keys == setOf("bm_state", "code")) {
            "Parâmetros de retorno OAuth inválidos."
        }
        val receivedState = params.getValue("bm_state")
        require(MessageDigest.isEqual(
            receivedState.toByteArray(StandardCharsets.US_ASCII),
            state.toByteArray(StandardCharsets.US_ASCII)
        )) { "Estado OAuth não corresponde à solicitação iniciada." }
        val code = params.getValue("code")
        require(code.length in 1..2048 &&
            code.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
            "Código OAuth inválido."
        }
        used = true
        return DesktopPkceExchange(code, verifier)
    }

    /**
     * A matching OAuth error terminates this one-shot attempt without consuming
     * authorization codes or exposing provider-supplied descriptions. Wrong
     * state, unknown keys and malformed callbacks cannot terminate the flow.
     *
     * Only query-string errors reach a loopback HTTP server; URI fragments
     * are never sent in HTTP requests and may instead lead to timeout.
     */
    @Synchronized
    fun acceptErrorCallback(
        callback: URI,
        now: Instant = Instant.now()
    ): Boolean {
        check(!used) { "Login já concluído; inicie uma nova autenticação." }
        check(!now.isBefore(startedAt) &&
            Duration.between(startedAt, now) <= Duration.ofMinutes(5)) {
            "A autorização expirou; inicie um novo login."
        }
        require(callback.scheme == "http" &&
            callback.host == "127.0.0.1" &&
            callback.port == redirectUri.port &&
            callback.rawUserInfo == null &&
            callback.path == redirectUri.path &&
            callback.rawFragment == null) {
            "Destino de retorno OAuth inválido."
        }
        val query = callback.rawQuery ?: return false
        if (!query.split('&').any { it.startsWith("error=") }) return false
        require(query.length <= 4096) { "Retorno OAuth grande demais." }
        val fields = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val at = pair.indexOf('=')
            require(at > 0) { "Parâmetro de erro OAuth inválido." }
            val key = pair.substring(0, at)
            require(key in setOf("bm_state", "error", "error_code", "error_description") &&
                key !in fields) {
                "Parâmetros de erro OAuth inválidos."
            }
            val value = URLDecoder.decode(
                pair.substring(at + 1), StandardCharsets.UTF_8
            )
            require(value.length in 1..2048 &&
                value.none { it.isISOControl() }) {
                "Valor de erro OAuth inválido."
            }
            fields[key] = value
        }
        val actual = fields["bm_state"].orEmpty()
        require(MessageDigest.isEqual(
            actual.toByteArray(StandardCharsets.UTF_8),
            state.toByteArray(StandardCharsets.UTF_8)
        )) { "Estado OAuth não corresponde à solicitação iniciada." }
        require(fields.getValue("error").matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
            "Código de erro OAuth inválido."
        }
        used = true
        return true
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        require(!raw.isNullOrBlank() && raw.length <= 4096) {
            "Retorno OAuth sem parâmetros ou grande demais."
        }
        val result = mutableMapOf<String, String>()
        for (entry in raw.split('&')) {
            val index = entry.indexOf('=')
            require(index > 0) { "Parâmetro de retorno inválido." }
            val key = entry.substring(0, index)
            require(key == "code" || key == "bm_state") {
                "Parâmetro OAuth desconhecido."
            }
            require(key !in result) { "Parâmetro OAuth duplicado." }
            val value = URLDecoder.decode(
                entry.substring(index + 1), StandardCharsets.UTF_8
            )
            require(value.isNotBlank() && value.length <= 2048 &&
                value.none { it.isISOControl() }) {
                "Valor do parâmetro OAuth inválido."
            }
            result[key] = value
        }
        return result
    }

    companion object {
        /** The caller binds an ephemeral loopback listener before calling start. */
        fun start(
            boundLoopbackPort: Int,
            clock: Clock = Clock.systemUTC(),
            random: SecureRandom = SecureRandom()
        ): DesktopPkceHandshake {
            require(boundLoopbackPort in 1024..65535) {
                "Use uma porta efêmera vinculada exclusivamente a 127.0.0.1."
            }
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            random.nextBytes(bytes)
            val state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(
                    verifier.toByteArray(StandardCharsets.US_ASCII)
                )
            )
            val redirect = URI(
                "http", null, "127.0.0.1", boundLoopbackPort,
                "/brother-matrizes/auth", "bm_state=$state", null
            )
            return DesktopPkceHandshake(
                redirect, challenge, state, verifier, Instant.now(clock)
            )
        }
    }

    override fun toString(): String = "DesktopPkceHandshake(redacted)"
}

/** A valid callback; must be exchanged once and never logged or persisted. */
internal class DesktopPkceExchange(
    val authorizationCode: String,
    val codeVerifier: String
) {
    override fun toString(): String = "DesktopPkceExchange(redacted)"
}
