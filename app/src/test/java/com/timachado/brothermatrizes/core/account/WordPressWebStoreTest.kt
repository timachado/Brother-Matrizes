package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordPressWebStoreTest {
    @Test fun opensOnlyOfficialWooCommerceAccount() {
        assertEquals(
            "https://timachado.ifree.page/minha-conta/",
            WordPressWebStore.accountUrl("https://timachado.ifree.page")
        )
        assertEquals(
            "https://timachado.ifree.page/minha-conta/",
            WordPressWebStore.accountUrl("https://timachado.ifree.page/")
        )
    }

    @Test fun rejectsUnexpectedHostsAndUnsafeUrls() {
        for (candidate in listOf(
            "http://timachado.ifree.page",
            "https://fake.example",
            "https://timachado.ifree.page.evil.test",
            "https://timachado.ifree.page@evil.test",
            "https://user@timachado.ifree.page",
            "https://timachado.ifree.page:8443",
            "https://timachado.ifree.page/redirect",
            "https://timachado.ifree.page/?next=https://evil.test",
            "https://timachado.ifree.page/#token"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                WordPressWebStore.accountUrl(candidate)
            }
        }
    }
}
