package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.account.BrotherPlanBenefits
import com.timachado.brothermatrizes.core.account.WordPressWebStore
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopAccountLinksTest {
    @Test
    fun `conta usa exclusivamente rota oficial definida no Android`() {
        val url = DesktopAccountLinks.account()
        assertEquals(
            WordPressWebStore.accountUrl("https://timachado.ifree.page"),
            url.toString()
        )
        assertTrue(DesktopAccountLinks.isAllowed(url))
        assertEquals("Não vinculada no Windows QA", DesktopAccountLinks.accountStatus)
        assertFalse(DesktopAccountLinks.proAccessVerified)
    }

    @Test
    fun `ofertas conservam destinos oficiais sem inventar preços ou acesso`() {
        val expectedCodes = setOf(
            "pro_monthly", "pro_yearly",
            "pro_lifetime", "pro_lifetime_launch"
        )
        assertEquals(expectedCodes, DesktopAccountLinks.plans.map { it.code }.toSet())
        for (offer in DesktopAccountLinks.plans) {
            assertEquals(
                WordPressWebStore.planUrl(
                    "https://timachado.ifree.page", offer.code
                ),
                DesktopAccountLinks.plan(offer.code).toString()
            )
            assertEquals(BrotherPlanBenefits.headline(offer.code), offer.headline)
            assertEquals(BrotherPlanBenefits.billing(offer.code), offer.billing)
            assertTrue(DesktopAccountLinks.isAllowed(DesktopAccountLinks.plan(offer.code)))
            assertTrue(offer.title.isNotBlank())
        }
        assertFalse(DesktopAccountLinks.proAccessVerified)
    }

    @Test
    fun `bloqueia planos estranhos e nao gera acesso por redirecionamento`() {
        for (code in listOf("free", "admin", "", "pro_monthly#redirect")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopAccountLinks.plan(code)
            }
        }
        for (uri in listOf(
            "http://timachado.ifree.page/minha-conta/meus-aplicativos/",
            "https://timachado.ifree.page.evil.example/minha-conta/",
            "https://timachado.ifree.page/minha-conta/?redirect=https://evil.example",
            "https://timachado.ifree.page@evil.example/minha-conta/",
            "https://timachado.ifree.page:444/minha-conta/meus-aplicativos/",
            "file:///C:/Users/Public/token.txt",
            "https://timachado.ifree.page/minha-conta/meus-aplicativos/#secret"
        )) {
            assertFalse(DesktopAccountLinks.isAllowed(URI.create(uri)), uri)
        }
    }

    @Test
    fun `loja exige dominio oficial para qualquer link de compra`() {
        assertFailsWith<IllegalArgumentException> {
            WordPressWebStore.accountUrl("https://evil.example")
        }
        assertFailsWith<IllegalArgumentException> {
            WordPressWebStore.planUrl(
                "https://timachado.ifree.page.evil.example", "pro_monthly"
            )
        }
    }
}
