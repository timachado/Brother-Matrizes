package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsWordPressHandoff
import com.timachado.brothermatrizes.core.account.WordPressWebStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** No browser, checkout, WooCommerce API, purchase, payment or network. */
class DesktopWindowsWordPressHandoffTest {
    @Test
    fun usesExactOfficialAndroidAccountAndCheckoutDestinations() {
        val site = "https://timachado.ifree.page"
        assertEquals(
            WordPressWebStore.accountUrl(site),
            DesktopWindowsWordPressHandoff.account().toString()
        )
        for (plan in listOf(
            "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"
        )) {
            val windows = DesktopWindowsWordPressHandoff.plan(plan)
            assertEquals(WordPressWebStore.planUrl(site, plan), windows.toString())
            assertEquals("https", windows.scheme)
            assertEquals("timachado.ifree.page", windows.host)
            assertEquals(null, windows.userInfo)
            assertEquals(null, windows.fragment)
        }
    }

    @Test
    fun cannotMintLocalPlanOrOpenUnapprovedCheckoutCodes() {
        for (bad in listOf(
            "", "free", "trial", "pro", "admin", "pro_lifetime_launch?redirect=evil",
            "pro_monthly#fragment", "../wp-admin", "pro_monthly\n"
        )) {
            assertFailsWith<IllegalArgumentException> {
                DesktopWindowsWordPressHandoff.plan(bad)
            }
        }
        assertTrue(DesktopWindowsWordPressHandoff.account().toString()
            .endsWith("/minha-conta/meus-aplicativos/"))
    }
}
