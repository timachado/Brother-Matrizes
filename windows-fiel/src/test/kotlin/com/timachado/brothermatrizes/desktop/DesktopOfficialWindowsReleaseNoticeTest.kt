package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialWindowsReleaseNotice
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * All network replies synthetic; no release is published, downloaded,
 * installed, executed, signed or opened by these tests.
 */
class DesktopOfficialWindowsReleaseNoticeTest {
    private val published = """
        {
          "applicationId":"com.timachado.brothermatrizes",
          "platform":"windows",
          "channel":"stable",
          "status":"published",
          "versionName":"1.0.0",
          "versionCode":100,
          "releaseTag":"windows-v1.0.0",
          "releaseNotes":"Exportação nativa de matrizes.;Melhorias na biblioteca e fontes.;Correções de estabilidade."
        }
    """.trimIndent()

    private class FakeResponse(
        url: URL,
        private val status: Int = 200,
        private val mime: String = "text/plain; charset=utf-8",
        private val body: String = ""
    ) : HttpURLConnection(url) {
        var disconnected = false
        var reads = 0
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getContentLengthLong() = body.length.toLong()
        override fun getInputStream(): InputStream {
            reads++
            return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun unpublishedWindowsChannelReturnsNoReleaseWithoutOpeningAndroidManifest() = runBlocking {
        val requested = mutableListOf<String>()
        lateinit var reply: FakeResponse
        val result = DesktopOfficialWindowsReleaseNotice { url ->
            requested += url.toString()
            FakeResponse(url, status = 404).also { reply = it }
        }.checkPublishedWindowsRelease()
        assertNull(result)
        assertEquals(listOf(DesktopOfficialWindowsReleaseNotice.WINDOWS_MANIFEST), requested)
        assertFalse(requested.single().endsWith("/updates/stable.json"))
        assertEquals(0, reply.reads)
        assertTrue(reply.disconnected)
        assertFalse(reply.instanceFollowRedirects)
        assertEquals("GET", reply.requestMethod)
        assertNull(reply.getRequestProperty("Authorization"))
    }

    @Test
    fun acceptsOnlyExplicitOfficialWindowsStableReleaseMetadata() = runBlocking {
        val reply = FakeResponse(
            URL(DesktopOfficialWindowsReleaseNotice.WINDOWS_MANIFEST),
            body = published
        )
        val notice = DesktopOfficialWindowsReleaseNotice { reply }
            .checkPublishedWindowsRelease()
        requireNotNull(notice)
        assertEquals("1.0.0", notice.versionName)
        assertEquals(100, notice.versionCode)
        assertEquals("windows-v1.0.0", notice.releaseTag)
        assertEquals(3, notice.highlights.size)
        assertTrue(notice.displayText().contains("Versão oficial Windows 1.0.0"))
        assertTrue(notice.displayText().contains("nenhum executável será baixado"))
        assertFalse(notice.displayText().contains("APK"))
        assertEquals(1, reply.reads)
        assertTrue(reply.disconnected)
    }

    @Test
    fun rejectsAndroidOrQaOrForgedWindowsVersionBeforeShowingAsPublished() = runBlocking {
        for (invalid in listOf(
            published.replace("\"windows\"", "\"android\""),
            published.replace("\"stable\"", "\"qa\""),
            published.replace("\"published\"", "\"draft\""),
            published.replace("com.timachado.brothermatrizes", "com.other.app"),
            published.replace("windows-v1.0.0", "v1.0.0"),
            published.replace("windows-v1.0.0", "windows-v1.0.1"),
            published.replace("\"versionCode\":100", "\"versionCode\":\"100\""),
            published.replace("\"versionCode\":100", "\"versionCode\":-1"),
            published.replace("1.0.0", "1.0.0-beta"),
            "{bad",
            "A".repeat(12_289)
        )) {
            assertFailsWith<Exception> {
                DesktopOfficialWindowsReleaseNotice { url ->
                    FakeResponse(url, body = invalid)
                }.checkPublishedWindowsRelease()
            }
        }
    }

    @Test
    fun refusesHtmlRedirectOrServerFailureInsteadOfClaimingNoRelease() = runBlocking {
        for ((code, mime) in listOf(
            200 to "text/html",
            302 to "application/json",
            503 to "application/json"
        )) {
            assertFailsWith<Exception> {
                DesktopOfficialWindowsReleaseNotice { url ->
                    FakeResponse(url, status = code, mime = mime, body = published)
                }.checkPublishedWindowsRelease()
            }
        }
    }

    @Test
    fun stripsUntrustedReleaseNotesUrlsAndLimitsHighlightedItems() = runBlocking {
        val body = published.replace(
            "Correções de estabilidade.",
            "http://malicious.example/installer.exe;" +
                "Melhoria adicional na exportação.;Outra melhoria de edição."
        )
        val notice = DesktopOfficialWindowsReleaseNotice { url ->
            FakeResponse(url, mime = "application/json", body = body)
        }.checkPublishedWindowsRelease()
        requireNotNull(notice)
        assertEquals(3, notice.highlights.size)
        assertFalse(notice.displayText().contains("malicious.example"))
        assertFalse(notice.displayText().contains("installer.exe"))
    }
}
