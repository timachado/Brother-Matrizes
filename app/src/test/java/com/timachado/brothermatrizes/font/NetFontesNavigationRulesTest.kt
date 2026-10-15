package com.timachado.brothermatrizes.font

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NetFontesNavigationRulesTest {
    @Test fun pesquisaNoEnderecoOficialComEncodingSeguro() {
        assertEquals("https://www.netfontes.com/search/Balmoral?f=1",
            NetFontesNavigationRules.searchUrl(" Balmoral "))
        assertEquals("https://www.netfontes.com/search/Fonte%20com%20acentua%C3%A7%C3%A3o?f=1",
            NetFontesNavigationRules.searchUrl("Fonte com acentuação"))
        assertEquals(NetFontesNavigationRules.HOME, NetFontesNavigationRules.searchUrl(" "))
    }

    @Test fun officialSiteAndDownload() {
        assertTrue(NetFontesNavigationRules.isOfficialPage("https://www.netfontes.com/escrita/escolar/"))
        assertTrue(NetFontesNavigationRules.isOfficialPage("https://netfontes.com/view/magnolia_sky/"))
        assertTrue(NetFontesNavigationRules.isOfficialDownload("https://www.netfontes.com/dow.php?cod=holidays_homework"))
        assertTrue(FontCatalogSource.NETFONTES.isOfficialDownload("https://www.netfontes.com/dow.php?cod=holidays_homework"))
        assertFalse(FontCatalogSource.DAFONT.isOfficialDownload("https://www.netfontes.com/dow.php?cod=holidays_homework"))
    }

    @Test fun rejectsSpoofingAndExternalDownloads() {
        val invalid = listOf(
            "http://www.netfontes.com/dow.php?cod=x",
            "https://www.netfontes.com.evil.test/dow.php?cod=x",
            "https://user@www.netfontes.com/dow.php?cod=x",
            "https://www.netfontes.com/dow.php",
            "https://www.netfontes.com/dow.php?cod=",
            "https://www.netfontes.com/dow.php?cod=x&redirect=https://evil.test",
            "https://www.netfontes.com/other.php?cod=x",
            "https://other.example/dow.php?cod=x"
        )
        invalid.forEach { assertFalse("Unexpectedly accepted: $it", NetFontesNavigationRules.isOfficialDownload(it)) }
    }
}
