package com.timachado.brothermatrizes.core.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class BrotherApkSourceRulesTest {
    @Test fun extractsDynamicWordPressDownloadWithoutHardcodedNonce() {
        val html = """<html><a class="download" href="https://timachado.ifree.page/?ti_app_download=307&amp;_wpnonce=a14cd8bf21">Baixar</a></html>"""
        val resolved = BrotherApkSourceRules.findDownloadUrl(html)
        assertEquals("https://timachado.ifree.page/?ti_app_download=307&_wpnonce=a14cd8bf21", resolved.toString())
    }

    @Test fun rejectsHtmlWithOnlyPageLinksOrForeignHosts() {
        assertNull(BrotherApkSourceRules.findDownloadUrl("""<a href="/aplicativos/brother-matrizes/">Abrir site</a>"""))
        assertNull(BrotherApkSourceRules.findDownloadUrl("""<a href="https://evil.example/?ti_app_download=307&amp;_wpnonce=a14cd8bf21">APK</a>"""))
        assertNull(BrotherApkSourceRules.findDownloadUrl("""<a href="http://timachado.ifree.page/?ti_app_download=307&amp;_wpnonce=a14cd8bf21">APK</a>"""))
        assertNull(BrotherApkSourceRules.findDownloadUrl("""<a href="https://timachado.ifree.page/?ti_app_download=307">APK sem nonce</a>"""))
    }

    @Test fun officialDomainOnlyAndHttps() {
        assertTrue(BrotherApkSourceRules.isOfficial(URL("https://timachado.ifree.page/")))
        assertTrue(!BrotherApkSourceRules.isOfficial(URL("https://timachado.ifree.page.attacker.test/")))
        assertTrue(!BrotherApkSourceRules.isOfficial(URL("http://timachado.ifree.page/")))
    }
}
