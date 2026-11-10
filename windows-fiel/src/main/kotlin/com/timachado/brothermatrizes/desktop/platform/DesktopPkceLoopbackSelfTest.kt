package com.timachado.brothermatrizes.desktop.platform

import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Self-test for the Windows part of OAuth redirect handling.
 *
 * Only 127.0.0.1 is contacted, with a synthetic authorization code and a
 * generated PKCE state. Never opens Google, Supabase, a browser, DPAPI, or the
 * commercial import gate. A PASS does NOT verify Supabase Redirect URLs.
 * Run only after the user explicitly presses the diagnostics button.
 */
internal object DesktopPkceLoopbackSelfTest {
    data class Result(
        val localListenerAvailable: Boolean,
        val forgedStateRejected: Boolean,
        val validLocalRoundTrip: Boolean
    )

    suspend fun verify(): Result = withContext(Dispatchers.IO) {
        DesktopPkceLoopbackListener.open().use { listener ->
            check(listener.redirectUri.scheme == "http" &&
                listener.redirectUri.host == "127.0.0.1" &&
                listener.redirectUri.port == listener.port &&
                listener.port in 1024..65535) {
                "O retorno não ficou restrito ao loopback local."
            }

            check(DesktopPkceRedirectPreflight.generatedRedirectHasExpectedShape(
                listener.redirectUri
            )) {
                "O retorno local não corresponde ao contrato OAuth Windows."
            }

            val syntheticCode = "bm-local-qa-code"
            val target = listener.redirectUri.rawPath + "?" +
                listener.redirectUri.rawQuery
            val worker = Executors.newSingleThreadExecutor { task ->
                Thread(task, "bm-local-oauth-qa").apply { isDaemon = true }
            }
            try {
                val receiver = worker.submit<String> {
                    listener.awaitCallback(Duration.ofSeconds(12)).authorizationCode
                }
                // First a forged-state callback must be refused WITHOUT ending
                // the legitimate one-shot listener.
                val invalid = target.replace(
                    listener.handshake.state, "invalid-qa-state"
                ) + "&code=" + syntheticCode
                val rejection = sendSyntheticCallback(listener.port, invalid)
                check(rejection.startsWith("HTTP/1.1 400 Bad Request")) {
                    "O retorno local aceitou um estado OAuth incorreto."
                }
                check(!receiver.isDone) {
                    "Um retorno inválido encerrou a escuta OAuth."
                }

                val acceptance = sendSyntheticCallback(
                    listener.port, target + "&code=" + syntheticCode
                )
                check(acceptance.startsWith("HTTP/1.1 200 OK") &&
                    acceptance.contains("Cache-Control: no-store") &&
                    !acceptance.contains(syntheticCode) &&
                    !acceptance.contains(listener.handshake.state)) {
                    "O retorno local não confirmou a segurança da resposta."
                }
                check(receiver.get(5, TimeUnit.SECONDS) == syntheticCode) {
                    "O código sintético do callback não foi entregue corretamente."
                }
                Result(
                    localListenerAvailable = true,
                    forgedStateRejected = true,
                    validLocalRoundTrip = true
                )
            } finally {
                // Always stop the loopback socket and synthetic executor.
                listener.close()
                worker.shutdownNow()
            }
        }
    }

    private fun sendSyntheticCallback(port: Int, target: String): String {
        val address = InetAddress.getByName("127.0.0.1")
        check(address.isLoopbackAddress) { "Destino local inesperado." }
        Socket(address, port).use { socket ->
            socket.soTimeout = 5_000
            val request = (
                "GET " + target + " HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:" + port + "\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII)
            socket.getOutputStream().write(request)
            socket.getOutputStream().flush()
            // Receiver responds with generic text only, never actual tokens.
            return socket.getInputStream().readBytes()
                .toString(Charsets.UTF_8)
        }
    }
}
