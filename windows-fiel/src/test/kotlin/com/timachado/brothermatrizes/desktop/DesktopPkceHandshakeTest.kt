package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopPkceHandshake
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Offline PKCE/CSRF tests. No browser, real accounts, tokens or Supabase calls. */
class DesktopPkceHandshakeTest {
    private val fixed = Instant.parse("2026-11-06T21:00:00Z")
    private val clock = Clock.fixed(fixed, ZoneOffset.UTC)

    @Test
    fun validatesPkceS256AndSingleUseCallback() {
        val flow = DesktopPkceHandshake.start(53124, clock)
        val redirect = flow.redirectUri
        assertEquals("http", redirect.scheme)
        assertEquals("127.0.0.1", redirect.host)
        assertEquals(53124, redirect.port)
        assertEquals("/brother-matrizes/auth", redirect.path)
        assertEquals("bm_state=" + flow.state, redirect.rawQuery)
        assertEquals(43, flow.state.length)
        assertEquals(43, flow.codeChallenge.length)
        assertFalse(flow.toString().contains(flow.state))
        val valid = URI(redirect.toString() + "&code=returned_qa_code")
        val exchange = flow.acceptCallback(valid, fixed.plusSeconds(60))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                exchange.codeVerifier.toByteArray(StandardCharsets.US_ASCII)
            )
        )
        assertEquals(flow.codeChallenge, expected)
        assertEquals("returned_qa_code", exchange.authorizationCode)
        assertFalse(exchange.toString().contains(exchange.codeVerifier))
        assertFalse(exchange.toString().contains(exchange.authorizationCode))
        assertFailsWith<IllegalStateException> {
            flow.acceptCallback(valid, fixed.plusSeconds(90))
        }
    }

    @Test
    fun rejectsHostPortSchemePathCsrfAndDuplicates() {
        val flow = DesktopPkceHandshake.start(53222, clock)
        val good = flow.redirectUri.toString()
        val cases = listOf(
            good.replace("127.0.0.1", "localhost") + "&code=abc",
            good.replace(":53222/", ":53223/") + "&code=abc",
            good.replace("http://", "https://") + "&code=abc",
            good.replace("/brother-matrizes/auth", "/other") + "&code=abc",
            good.replace(flow.state, "invalid") + "&code=abc",
            good + "&code=abc&code=def",
            good + "&code=abc&unexpected=1",
            good + "&code=abc#fragment",
            good + "&code=%0Ainject",
            good + "&code=",
            good
        )
        cases.forEach { invalid ->
            assertFailsWith<IllegalArgumentException>(invalid) {
                flow.acceptCallback(URI(invalid), fixed.plusSeconds(1))
            }
        }
        // Invalid callbacks must not consume the legitimate one.
        assertEquals(
            "good_code",
            flow.acceptCallback(URI(good + "&code=good_code"), fixed.plusSeconds(1))
                .authorizationCode
        )
    }

    @Test
    fun expiresAfterFiveMinutesAndRejectsClockRollback() {
        val first = DesktopPkceHandshake.start(53333, clock)
        val second = DesktopPkceHandshake.start(53333, clock)
        assertTrue(first.state != second.state)
        val callback = URI(first.redirectUri.toString() + "&code=abc")
        assertFailsWith<IllegalStateException> {
            first.acceptCallback(callback, fixed.plusSeconds(301))
        }
        assertFailsWith<IllegalStateException> {
            first.acceptCallback(callback, fixed.minusSeconds(1))
        }
        assertEquals(
            "abc", first.acceptCallback(callback, fixed.plusSeconds(300)).authorizationCode
        )
    }

    @Test
    fun disallowsNonEphemeralOrInvalidPortValues() {
        listOf(0, 80, 1023, 65536).forEach { port ->
            assertFailsWith<IllegalArgumentException> {
                DesktopPkceHandshake.start(port, clock)
            }
        }
    }
}
