package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialReleaseNotice
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The network is a fake. No APK downloads, Windows release or tokens. */
class DesktopOfficialReleaseNoticeTest {
    private val json = """
        {
          "applicationId":"com.timachado.brothermatrizes",
          "channel":"stable",
          "versionName":"1.11.8",
          "versionCode":195,
          "releaseNotes":"Melhorias para o editor de matrizes.;Ajustes nas fontes e letras.;Salvar o arquivo com segurança.;Detalhes em https://example.org"
        }
    """.trimIndent()

    private class FakeConnection(
        url: URL, val jsonText: String,
        private val code: Int = 200,
        private val mime: String = "application/json; charset=utf-8"
    ) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int = code
        override fun getContentType(): String = mime
        override fun getContentLengthLong(): Long = jsonText.length.toLong()
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(jsonText.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun readsRealManifestShapeAndNeverClaimsWindowsRelease() = runBlocking {
        val requests = mutableListOf<String>()
        lateinit var response: FakeConnection
        val notice = DesktopOfficialReleaseNotice { url ->
            requests += url.toString()
            FakeConnection(url, json).also { response = it }
        }.checkAndroidAnnouncements()
        assertEquals(listOf(DesktopOfficialReleaseNotice.ANDROID_MANIFEST), requests)
        assertEquals("1.11.8", notice.versionName)
        assertEquals(195, notice.versionCode)
        assertEquals(3, notice.highlights.size)
        assertTrue(notice.displayText().contains("Android"))
        assertTrue(notice.displayText().contains("NÃO é um instalador"))
        assertFalse(notice.displayText().contains("https://example.org"))
        assertFalse(response.instanceFollowRedirects)
        assertEquals("GET", response.requestMethod)
        assertEquals(null, response.getRequestProperty("Authorization"))
        assertTrue(response.disconnected)
    }

    @Test
    fun wrongProductOrPlatformAndBogusFieldsFailClosed() = runBlocking {
        for (bad in listOf(
            json.replace("com.timachado.brothermatrizes", "com.evil"),
            json.replace("\"stable\"", "\"beta\""),
            json.replace("\"versionCode\":195", "\"versionCode\":\"195\""),
            json.replace("\"versionCode\":195", "\"versionCode\":-1"),
            json.replace("1.11.8", "Windows-1.11.8"),
            "{ invalid",
            "x".repeat(12_289)
        )) {
            assertFailsWith<Exception> {
                DesktopOfficialReleaseNotice { url ->
                    FakeConnection(url, bad)
                }.checkAndroidAnnouncements()
            }
        }
    }

    @Test
    fun htmlResponseAndRedirectCannotBecomeReleaseNotice() = runBlocking {
        for ((status, mime) in listOf(
            200 to "text/html",
            302 to "application/json",
            503 to "application/json"
        )) {
            assertFailsWith<Exception> {
                DesktopOfficialReleaseNotice { url ->
                    FakeConnection(url, json, status, mime)
                }.checkAndroidAnnouncements()
            }
        }
    }
}
