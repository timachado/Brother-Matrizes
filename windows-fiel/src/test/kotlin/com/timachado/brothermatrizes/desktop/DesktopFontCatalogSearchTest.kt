package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogAccess
import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogAccessUnavailable
import com.timachado.brothermatrizes.desktop.platform.DesktopFontCatalogSearch
import com.timachado.brothermatrizes.font.DaFontNavigationRules
import com.timachado.brothermatrizes.font.FontCatalogSource
import com.timachado.brothermatrizes.font.NetFontesNavigationRules
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Does not open the browser, call Google or consume a real server quota. */
class DesktopFontCatalogSearchTest {
    private class FakeAccount(
        var googleVerified: Boolean = true,
        var reservation: String = "usage_12345678-1234-4234-8234-123456789abc"
    ) : DesktopFontCatalogAccess {
        var reserves = 0
        val finalized = mutableListOf<Pair<String, Boolean>>()
        override suspend fun hasVerifiedGoogleSession(): Boolean = googleVerified
        override suspend fun reserveDaFontSearch(): String {
            reserves++
            return reservation
        }
        override suspend fun finalizeDaFontSearch(requestKey: String, success: Boolean) {
            finalized += requestKey to success
        }
    }

    @Test
    fun neverOpensEitherCatalogWithoutGoogleSession() = runBlocking {
        var opened = 0
        for (source in FontCatalogSource.entries) {
            val denied = runCatching {
                DesktopFontCatalogSearch.browse(
                    source, "Maria", DesktopFontCatalogAccessUnavailable
                ) { opened++ }
            }
            assertIs<IllegalStateException>(denied.exceptionOrNull())
        }
        assertEquals(0, opened)
        val fake = FakeAccount(googleVerified = false)
        assertIs<IllegalStateException>(
            runCatching {
                DesktopFontCatalogSearch.browse(
                    FontCatalogSource.DAFONT, "Maria", fake
                ) { opened++ }
            }.exceptionOrNull()
        )
        assertEquals(0, fake.reserves)
        assertTrue(fake.finalized.isEmpty())
    }

    @Test
    fun netFontesUsesIdenticalAndroidSearchUrlWithoutDaFontQuota() = runBlocking {
        val account = FakeAccount()
        val opened = mutableListOf<URI>()
        val result = DesktopFontCatalogSearch.browse(
            FontCatalogSource.NETFONTES, " Letras azuis ", account
        ) { opened += it }
        assertEquals(NetFontesNavigationRules.searchUrl("Letras azuis"), result.destination.toString())
        assertEquals(1, opened.size)
        assertEquals("https", opened.single().scheme)
        assertTrue(result.quotaConfirmationReceived)
        assertEquals(0, account.reserves)
        assertTrue(account.finalized.isEmpty())
    }

    @Test
    fun daFontUsesOriginalAndroidUrlAndExactlyOneReservationAndFinalization() = runBlocking {
        val account = FakeAccount()
        val opened = mutableListOf<URI>()
        val result = DesktopFontCatalogSearch.browse(
            FontCatalogSource.DAFONT, " Sofia Script ", account
        ) { opened += it }
        assertEquals(DaFontNavigationRules.searchUrl("Sofia Script"), result.destination.toString())
        assertEquals(listOf(result.destination), opened)
        assertEquals(1, account.reserves)
        assertEquals(listOf(account.reservation to true), account.finalized)
    }

    @Test
    fun failedDaFontLaunchFinalizesFalseWithoutRetrying() = runBlocking {
        val account = FakeAccount()
        val failure = runCatching {
            DesktopFontCatalogSearch.browse(
                FontCatalogSource.DAFONT, "Maria", account
            ) { throw IllegalStateException("Navegador indisponível.") }
        }
        assertIs<IllegalStateException>(failure.exceptionOrNull())
        assertEquals(1, account.reserves)
        assertEquals(listOf(account.reservation to false), account.finalized)
    }

    @Test
    fun invalidReservationNeverOpensDaFontOrFinalizesUnrecognizedReceipt() = runBlocking {
        val account = FakeAccount(reservation = "forged_admin_receipt")
        var launched = false
        val result = runCatching {
            DesktopFontCatalogSearch.browse(
                FontCatalogSource.DAFONT, "Maria", account
            ) { launched = true }
        }
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertFalse(launched)
        assertEquals(1, account.reserves)
        assertTrue(account.finalized.isEmpty())
    }

    @Test
    fun homeNavigationDoesNotDebitToolQuotaButStillRequiresGoogle() = runBlocking {
        val account = FakeAccount()
        for (source in FontCatalogSource.entries) {
            val result = DesktopFontCatalogSearch.browse(
                source, "   ", account
            ) { url ->
                assertTrue(source.isOfficialPage(url.toString()))
                assertEquals(source.homeUrl, url.toString())
            }
            assertEquals(source.homeUrl, result.destination.toString())
        }
        assertEquals(0, account.reserves)
        assertTrue(account.finalized.isEmpty())
    }

    @Test
    fun officialAndroidCatalogRulesRejectNonOfficialHostsAndMixedSchemes() {
        assertFalse(DaFontNavigationRules.isOfficialPage("https://dafont.com.evil.example/"))
        assertFalse(NetFontesNavigationRules.isOfficialPage("http://www.netfontes.com/"))
        assertFalse(NetFontesNavigationRules.isOfficialPage("https://evil@www.netfontes.com/"))
        assertFalse(DaFontNavigationRules.isOfficialPage("file:///tmp/font.ttf"))
    }
}
