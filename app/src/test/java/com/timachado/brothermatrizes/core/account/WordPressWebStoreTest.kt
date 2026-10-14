package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WordPressWebStoreTest {
    @Test fun opensOnlyOfficialWooCommerceAccount() {
        assertEquals(
            "https://timachado.ifree.page/minha-conta/meus-aplicativos/",
            WordPressWebStore.accountUrl("https://timachado.ifree.page")
        )
        assertEquals(
            "https://timachado.ifree.page/minha-conta/meus-aplicativos/",
            WordPressWebStore.accountUrl("https://timachado.ifree.page/")
        )
    }

    @Test fun purchaseLinksUseExistingWooCommerceFlowWithoutCollectingCardData() {
        assertEquals(
            "https://timachado.ifree.page/minha-conta/?tiac_auth=confirm&tiac_resume_app=307&tiac_resume_plan=monthly",
            WordPressWebStore.planUrl("https://timachado.ifree.page", "pro_monthly")
        )
        assertEquals(
            "https://timachado.ifree.page/minha-conta/?tiac_auth=confirm&tiac_resume_app=307&tiac_resume_plan=yearly",
            WordPressWebStore.planUrl("https://timachado.ifree.page", "pro_yearly")
        )
        assertEquals(
            "https://timachado.ifree.page/minha-conta/?tiac_auth=confirm&tiac_resume_app=307&tiac_resume_plan=lifetime",
            WordPressWebStore.planUrl("https://timachado.ifree.page", "pro_lifetime")
        )
        assertEquals(
            "https://timachado.ifree.page/?post_type=product&p=308",
            WordPressWebStore.planUrl("https://timachado.ifree.page", "pro_lifetime_launch")
        )
        assertThrows(IllegalArgumentException::class.java) {
            WordPressWebStore.planUrl("https://timachado.ifree.page", "free")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WordPressWebStore.planUrl("https://evil.test", "pro_monthly")
        }
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
