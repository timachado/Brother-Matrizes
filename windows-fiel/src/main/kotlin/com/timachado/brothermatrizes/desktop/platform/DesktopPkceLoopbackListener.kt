package com.timachado.brothermatrizes.desktop.platform

import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Isolated callback receiver for a future Windows browser-based PKCE flow.
 *
 * Binds ONLY to IPv4 loopback on an ephemeral port. No external interfaces,
 * auth API, browser launch, token exchange, persistent credentials or UI wiring.
 * Validates Host and callback; invalid local requests cannot consume PKCE.
 *
 * Call awaitCallback from a background worker (never the Compose UI thread).
 * Caller must close this receiver on cancellation.
 */
/** No secrets, provider error codes or personally identifying data in logs. */
internal class DesktopOAuthLoginRejected :
    IllegalStateException("Autenticação Google cancelada ou recusada.")

internal class DesktopPkceLoopbackListener private constructor(
    private val server: ServerSocket,
    val handshake: DesktopPkceHandshake
) : Closeable {

    val port: Int get() = server.localPort
    val redirectUri: URI get() = handshake.redirectUri

    fun awaitCallback(timeout: Duration = Duration.ofMinutes(5)): DesktopPkceExchange {
        require(!timeout.isNegative && !timeout.isZero &&
            timeout <= Duration.ofMinutes(5)) {
            "Tempo limite OAuth inválido."
        }
        val budgetNanos = timeout.toNanos()
        val started = System.nanoTime()
        while (true) {
            val remaining = budgetNanos - (System.nanoTime() - started)
            check(remaining > 0) { "Tempo de autenticação esgotado." }
            server.soTimeout = min(
                3_000L, maxOf(1L, TimeUnit.NANOSECONDS.toMillis(remaining))
            ).toInt()
            val peer = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            peer.use { socket ->
                socket.soTimeout = min(
                    3_000L, maxOf(1L, TimeUnit.NANOSECONDS.toMillis(remaining))
                ).toInt()
                val result = runCatching { parseRequest(socket) }
                val callback = result.getOrNull()
                if (callback != null) {
                    // A verified error callback stops the browser wait
                    // immediately; forged or mismatched state still gets 400.
                    val rejected = runCatching {
                        handshake.acceptErrorCallback(callback)
                    }.getOrDefault(false)
                    if (rejected) {
                        sendResponse(socket, 200,
                            "Autenticacao Google cancelada ou recusada. Volte ao Brother Matrizes.")
                        throw DesktopOAuthLoginRejected()
                    }
                    val exchange = runCatching { handshake.acceptCallback(callback) }
                        .getOrNull()
                    if (exchange != null) {
                        sendResponse(socket, 200, "Autenticacao recebida. Volte ao Brother Matrizes.")
                        return exchange
                    }
                }
                sendResponse(socket, 400, "Retorno de autenticacao invalido.")
            }
        }
    }

    private fun parseRequest(socket: Socket): URI {
        require(socket.inetAddress.isLoopbackAddress) {
            "Conexão não local rejeitada."
        }
        val input = BufferedInputStream(socket.getInputStream())
        val startLine = readLineLimited(input, 4096)
        val tokens = startLine.split(' ')
        require(tokens.size == 3 && tokens[0] == "GET" &&
            tokens[2] == "HTTP/1.1") {
            "Método HTTP não autorizado."
        }
        val target = tokens[1]
        require(target.startsWith("/brother-matrizes/auth?") &&
            !target.startsWith("//") &&
            target.length <= 4096) {
            "Caminho OAuth não autorizado."
        }

        var host: String? = null
        var consumed = startLine.length + 2
        while (true) {
            val header = readLineLimited(input, 4096)
            consumed += header.length + 2
            require(consumed <= 8192) { "Cabeçalhos HTTP grandes demais." }
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            require(colon > 0 && !header.contains('\u0000')) {
                "Cabeçalho HTTP inválido."
            }
            val headerName = header.substring(0, colon).trim()
            val value = header.substring(colon + 1).trim()
            if (headerName.equals("Host", true)) {
                require(host == null) { "Host HTTP duplicado." }
                host = value
            }
            require(!headerName.equals("Transfer-Encoding", true)) {
                "Corpo HTTP não autorizado."
            }
            if (headerName.equals("Content-Length", true)) {
                require(value == "0") { "Corpo HTTP não autorizado." }
            }
        }
        require(host == "127.0.0.1:" + port) {
            "Host HTTP inesperado."
        }
        return URI("http://127.0.0.1:" + port + target)
    }

    private fun readLineLimited(input: BufferedInputStream, maxChars: Int): String {
        val out = StringBuilder()
        while (out.length <= maxChars) {
            val next = input.read()
            require(next >= 0) { "Conexão HTTP incompleta." }
            if (next == '\n'.code) {
                require(out.isNotEmpty() && out.last() == '\r') {
                    "Cabeçalho HTTP sem CRLF."
                }
                return out.dropLast(1).toString()
            }
            require(next in 0x20..0x7e || next == '\r'.code) {
                "Caractere HTTP inválido."
            }
            out.append(next.toChar())
        }
        throw IllegalArgumentException("Linha HTTP muito longa.")
    }

    private fun sendResponse(socket: Socket, status: Int, message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        val title = if (status == 200) "OK" else "Bad Request"
        val headers = ("HTTP/1.1 " + status + " " + title + "\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: " + bytes.size + "\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'\r\n" +
            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        runCatching {
            socket.getOutputStream().apply {
                write(headers)
                write(bytes)
                flush()
            }
        }
    }

    override fun close() {
        server.close()
    }

    companion object {
        fun open(
            clock: Clock = Clock.systemUTC(),
            random: SecureRandom = SecureRandom()
        ): DesktopPkceLoopbackListener {
            val server = ServerSocket()
            try {
                server.reuseAddress = false
                server.bind(
                    InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
                    4
                )
                val handshake = DesktopPkceHandshake.start(server.localPort, clock, random)
                return DesktopPkceLoopbackListener(server, handshake)
            } catch (error: Exception) {
                server.close()
                throw error
            }
        }
    }

    override fun toString(): String = "DesktopPkceLoopbackListener(redacted)"
}
