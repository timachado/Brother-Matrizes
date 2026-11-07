package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopPkceLoopbackListener
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Local loopback-only tests. No Supabase, Google, browser or account contacted.
 */
class DesktopPkceLoopbackListenerTest {
    private fun request(
        port: Int,
        target: String,
        method: String = "GET",
        host: String = "127.0.0.1:" + port,
        extraHeaders: String = ""
    ): String = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 5_000
        val message = method + " " + target + " HTTP/1.1\r\n" +
            "Host: " + host + "\r\n" +
            extraHeaders +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().write(message.toByteArray(StandardCharsets.US_ASCII))
        socket.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun receivesOnlyValidSingleUseLocalCallback() {
        DesktopPkceLoopbackListener.open().use { listener ->
            assertTrue(listener.port > 1023)
            assertEquals("127.0.0.1", listener.redirectUri.host)
            assertFalse(listener.toString().contains(listener.handshake.state))
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<String> {
                    val exchange = listener.awaitCallback(Duration.ofSeconds(15))
                    exchange.authorizationCode
                }
                val target = listener.redirectUri.rawPath + "?" +
                    listener.redirectUri.rawQuery + "&code=qa_code_123"
                val result = request(listener.port, target)
                assertTrue(result.startsWith("HTTP/1.1 200 OK"))
                assertTrue(result.contains("Cache-Control: no-store"))
                assertTrue(result.contains("Referrer-Policy: no-referrer"))
                assertTrue(result.contains("Content-Security-Policy: default-src 'none'"))
                assertFalse(result.contains("qa_code_123"))
                assertEquals("qa_code_123", future.get(5, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun ignoresWrongHostMethodStateAndDuplicateCodeBeforeValidLogin() {
        DesktopPkceLoopbackListener.open().use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<String> {
                    listener.awaitCallback(Duration.ofSeconds(15)).authorizationCode
                }
                val target = listener.redirectUri.rawPath + "?" +
                    listener.redirectUri.rawQuery + "&code=valid_abc"
                val attacks = listOf(
                    Triple(target, "POST", "127.0.0.1:" + listener.port),
                    Triple(target, "GET", "malicious.example"),
                    Triple(target.replace(listener.handshake.state, "wrong"), "GET", "127.0.0.1:" + listener.port),
                    Triple(target + "&code=duplicate", "GET", "127.0.0.1:" + listener.port),
                    Triple("/other?" + listener.redirectUri.rawQuery + "&code=bad", "GET", "127.0.0.1:" + listener.port)
                )
                for ((path, method, host) in attacks) {
                    assertTrue(request(listener.port, path, method, host)
                        .startsWith("HTTP/1.1 400 Bad Request"))
                }
                assertEquals(false, future.isDone)
                assertTrue(request(listener.port, target).startsWith("HTTP/1.1 200 OK"))
                assertEquals("valid_abc", future.get(5, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun blocksUnexpectedHttpRequestBodyBeforeValidCallback() {
        DesktopPkceLoopbackListener.open().use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<String> {
                    listener.awaitCallback(Duration.ofSeconds(15)).authorizationCode
                }
                val target = listener.redirectUri.rawPath + "?" +
                    listener.redirectUri.rawQuery + "&code=valid_abc"
                assertTrue(request(
                    listener.port, target,
                    extraHeaders = "Content-Length: 10\r\n"
                ).startsWith("HTTP/1.1 400 Bad Request"))
                assertTrue(request(
                    listener.port, target,
                    extraHeaders = "Transfer-Encoding: chunked\r\n"
                ).startsWith("HTTP/1.1 400 Bad Request"))
                assertTrue(request(listener.port, target).startsWith("HTTP/1.1 200 OK"))
                assertEquals("valid_abc", future.get(5, TimeUnit.SECONDS))
            } finally {
                listener.close()
                executor.shutdownNow()
            }
        }
    }
}
