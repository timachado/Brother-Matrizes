package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherCommunityLinks
import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialCommunityGroupResolver
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All HTTP replies are synthetic; no Internet, browser or WhatsApp opened. */
class DesktopOfficialCommunityGroupResolverTest {
    private val github = DesktopOfficialCommunityGroupResolver.GITHUB_SOURCE
    private val wordpress = DesktopOfficialCommunityGroupResolver.WORDPRESS_SOURCE
    private val original = DesktopBrotherCommunityLinks.group()
    private val alternate = "https://chat.whatsapp.com/ABCDEFGHIJKLMNOPQRSTUV"
    private val validGitHub =
        """{"product":"brother-matrizes","groupUrl":"$alternate"}"""
    private val validWordPress = """{"groupUrl":"$alternate"}"""

    private class FakeResponse(
        url: URL,
        private val status: Int = 200,
        private val mime: String = "application/json; charset=utf-8",
        private val payload: String
    ) : HttpURLConnection(url) {
        var disconnected = false
        override fun disconnect() { disconnected = true }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int = status
        override fun getContentType(): String = mime
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun githubIsPreferredAndNeverRequestsWordPressWhenValid() = runBlocking {
        val requested = mutableListOf<String>()
        val fake = FakeResponse(URL(github), payload = validGitHub)
        val result = DesktopOfficialCommunityGroupResolver { url ->
            requested += url.toString()
            fake
        }.resolve()
        assertEquals(URI(alternate), result)
        assertEquals(listOf(github), requested)
        assertFalse(fake.instanceFollowRedirects)
        assertEquals("GET", fake.requestMethod)
        assertEquals("application/json", fake.getRequestProperty("Accept"))
        assertNull(fake.getRequestProperty("Authorization"))
        assertTrue(fake.disconnected)
    }

    @Test
    fun failsOverToOfficialWordPressIfGitHubIsAbsent() = runBlocking {
        val requested = mutableListOf<String>()
        val responses = mutableListOf<FakeResponse>()
        val result = DesktopOfficialCommunityGroupResolver { url ->
            requested += url.toString()
            FakeResponse(
                url, if (url.toString() == github) 404 else 200,
                payload = if (url.toString() == github) "{}" else validWordPress
            ).also { responses += it }
        }.resolve()
        assertEquals(URI(alternate), result)
        assertEquals(listOf(github, wordpress), requested)
        assertTrue(responses.all { it.disconnected })
    }

    @Test
    fun invalidJsonUrlsMimeAndRedirectFallBackToAndroidInvite() = runBlocking {
        for ((status, mime, body) in listOf(
            Triple(200, "application/json", """{"product":"other","groupUrl":"$alternate"}"""),
            Triple(200, "application/json", """{"product":"brother-matrizes","groupUrl":"https://evil.example"}"""),
            Triple(200, "application/json", """{"product":"brother-matrizes","groupUrl":"https://chat.whatsapp.com.evil.example/ABCDEFGHIJKLMNOPQRSTUV"}"""),
            Triple(200, "application/json", """{"product":"brother-matrizes","groupUrl":"https://chat.whatsapp.com/ABCDEFGHIJKLMNOPQRSTUV?token=foo"}"""),
            Triple(200, "application/json", """{"product":"brother-matrizes","groupUrl":true}"""),
            Triple(200, "application/json", "{}"),
            Triple(200, "application/json", "x".repeat(4_097)),
            Triple(302, "application/json", validGitHub),
            Triple(200, "text/html", "<html>blocked</html>")
        )) {
            val result = DesktopOfficialCommunityGroupResolver { url ->
                FakeResponse(url, status, mime,
                    if (url.toString() == github) body else "{}")
            }.resolve()
            assertEquals(original, result)
        }
    }

    @Test
    fun uriValidatorBlocksPortUserInfoQueryAndHostSpoofing() {
        for (url in listOf(
            "https://chat.whatsapp.com:444/ABCDEFGHIJKLMNOPQRSTUV",
            "https://user@chat.whatsapp.com/ABCDEFGHIJKLMNOPQRSTUV",
            "http://chat.whatsapp.com/ABCDEFGHIJKLMNOPQRSTUV",
            "https://chat.whatsapp.com/ABCDEFGHIJKLMNOPQRSTUV#anchor",
            "https://chat.whatsapp.com.evil.example/ABCDEFGHIJKLMNOPQRSTUV",
            "https://chat.whatsapp.com/ABC",
            "https://chat.whatsapp.com/ABCDEFGHIJKLMNOPQRSTUV?next=1"
        )) {
            assertNull(DesktopBrotherCommunityLinks.verifiedGroup(url))
        }
        assertEquals(URI(alternate), DesktopBrotherCommunityLinks.verifiedGroup(alternate))
        assertEquals(original, DesktopBrotherCommunityLinks.verifiedGroup(original.toString()))
    }
}
