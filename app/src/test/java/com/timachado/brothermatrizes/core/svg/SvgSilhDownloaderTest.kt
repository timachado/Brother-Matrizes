package com.timachado.brothermatrizes.core.svg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgSilhDownloaderTest {
    @Test
    fun acceptedPublicSvgSilhDownload() {
        val url = "https://svgsilh.com/svg/1681779.svg"
        assertTrue(SvgSilhDownloader.isDownloadLink(url))
        assertEquals("svg-silh-1681779.svg", SvgSilhDownloader.fileName(url))
    }

    @Test
    fun redirectsToOtherHostsOrHttpAreRejected() {
        val bad = listOf(
            "http://svgsilh.com/svg/1681779.svg",
            "https://svgsilh.com.evil.test/svg/1681779.svg",
            "https://evil.test/?target=svgsilh.com/svg/1681779.svg",
            "https://user:password@svgsilh.com/svg/1681779.svg",
            "https://svgsilh.com:8080/svg/1681779.svg",
            "https://svgsilh.com/svg/1681779.svg?redirect=https://evil.test",
            "https://svgsilh.com/svg/not-an-id.svg",
            "file:///storage/emulated/0/payload.svg",
            "javascript:alert(1)"
        )
        bad.forEach { url ->
            assertFalse("Should reject: " + url, SvgSilhDownloader.isDownloadLink(url))
        }
    }

    @Test
    fun browsingOnlyAllowsHttpsPagesFromOfficialSite() {
        assertTrue(SvgSilhDownloader.isTrustedPage(SvgSilhDownloader.HOME_URL))
        assertTrue(SvgSilhDownloader.isTrustedPage(
            "https://svgsilh.com/pt/tag/flor-1.html"))
        assertFalse(SvgSilhDownloader.isTrustedPage(
            "https://svgsilh.com.evil.test/pt/"))
        assertFalse(SvgSilhDownloader.isTrustedPage(
            "http://svgsilh.com/pt/"))
    }

    @Test
    fun browserOnlyOpensExternalHttpsLinksWithoutCredentialsOrCustomSchemes() {
        val accepted = listOf(
            "https://example.org/",
            "https://example.org:443/galeria/svg",
            "https://github.com/timachado/Brother-Matrizes"
        )
        accepted.forEach { url ->
            assertTrue("HTTPS externo deveria abrir: $url",
                SvgSilhDownloader.isSafeExternalBrowserLink(url))
        }

        val blocked = listOf(
            SvgSilhDownloader.HOME_URL,
            "https://www.svgsilh.com/pt/",
            "http://example.org/",
            "https://example.org:8080/path",
            "https://user:pass@example.org/path",
            "intent://pay/#Intent;scheme=example;end",
            "javascript:alert(1)",
            "file:///data/data/private",
            "content://com.example.provider/private",
            "market://details?id=com.example",
            "mailto:support@example.org",
            "ftp://example.org/file.svg",
            "https://"
        )
        blocked.forEach { url ->
            assertFalse("Esquema externo deveria ser bloqueado: $url",
                SvgSilhDownloader.isSafeExternalBrowserLink(url))
        }
    }

    @Test
    fun svgByteValidationRejectsXmlPayloadsAndScripts() {
        assertTrue(SvgSilhDownloader.isSvgDocument(
            """<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"><path d="M0 0 L1 1"/></svg>"""
                .toByteArray()
        ))
        assertFalse(SvgSilhDownloader.isSvgDocument(
            "<html><body>Not SVG</body></html>".toByteArray()
        ))
        assertFalse(SvgSilhDownloader.isSvgDocument(
            "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///secret'>]><svg/>".toByteArray()
        ))
        assertFalse(SvgSilhDownloader.isSvgDocument(
            "<svg><script>alert('x')</script></svg>".toByteArray()
        ))
        assertFalse(SvgSilhDownloader.isSvgDocument(
            "<svg><foreignObject/></svg>".toByteArray()
        ))
        assertFalse(SvgSilhDownloader.isSvgDocument(
            ByteArray(2_000_001)
        ))
    }
}
