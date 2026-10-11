package com.timachado.brothermatrizes.font

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DaFontNavigationRulesTest {
    @Test fun pesquisaUsaRotaDoDaFont() {
        assertEquals("https://www.dafont.com/search.php?q=Balmoral",
            DaFontNavigationRules.searchUrl(" Balmoral "))
        assertEquals("https://www.dafont.com/search.php?q=Fonte+com+acentua%C3%A7%C3%A3o",
            DaFontNavigationRules.searchUrl("Fonte com acentuação"))
        assertEquals(DaFontNavigationRules.HOME, DaFontNavigationRules.searchUrl(" "))
    }

    @Test fun enderecoOficialSemSubdominiosFalsos() {
        assertTrue(DaFontNavigationRules.isOfficialPage("https://www.dafont.com/search.php?q=abc"))
        assertFalse(DaFontNavigationRules.isOfficialPage("https://www.dafont.com.evil.test/"))
        assertFalse(DaFontNavigationRules.isOfficialPage("http://www.dafont.com/"))
        assertFalse(DaFontNavigationRules.isOfficialPage("https://user@www.dafont.com/"))
    }

    @Test fun downloadApenasDaOrigemOficial() {
        assertTrue(DaFontNavigationRules.isOfficialDownload("https://dl.dafont.com/dl/?f=my_font"))
        assertFalse(DaFontNavigationRules.isOfficialDownload("https://dl.dafont.com.evil.test/dl/?f=x"))
        assertFalse(DaFontNavigationRules.isOfficialDownload("https://www.dafont.com/dl/?f=x"))
        assertFalse(DaFontNavigationRules.isOfficialDownload("https://dl.dafont.com/dl/"))
        assertFalse(DaFontNavigationRules.isOfficialDownload("http://dl.dafont.com/dl/?f=x"))
    }

    @Test fun rejeitaPaginaHtmlNoLugarDeZip() {
        assertTrue(DaFontNavigationRules.looksLikeZip(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))
        assertFalse(DaFontNavigationRules.looksLikeZip("<html>".toByteArray()))
        assertFalse(DaFontNavigationRules.looksLikeZip(byteArrayOf()))
    }
}
