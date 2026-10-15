package com.timachado.brothermatrizes.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherPlanBenefitsTest {
    @Test fun vitalicioPadraoEPromocionalTemMesmosRecursosPro() {
        val normal = BrotherPlanBenefits.benefits("pro_lifetime")
        val promo = BrotherPlanBenefits.benefits("pro_lifetime_launch")
        assertEquals(normal, promo)
        assertTrue(normal.any { it.contains("sem a cota mensal") })
    }

    @Test fun gratisMantemCotasDoServidor() {
        val benefits = BrotherPlanBenefits.benefits("free")
        assertTrue(benefits.any { it.contains("3 importações") })
        assertTrue(benefits.any { it.contains("5 importações") })
        assertFalse(benefits.any { it.contains("sem a cota mensal") })
    }

    @Test fun nenhumaModalidadePrometeBetaComoDireitoAdquirido() {
        for (code in listOf("free","pro_monthly","pro_yearly","pro_lifetime","pro_lifetime_launch")) {
            assertFalse(BrotherPlanBenefits.benefits(code).any { it.contains("Beta") })
        }
    }

    @Test fun cardsSaoCurtosESemPromessasDeBeta() {
        assertEquals(3, BrotherPlanBenefits.cardHighlights("free").size)
        for (code in listOf(
            "pro_monthly", "pro_yearly", "pro_lifetime", "pro_lifetime_launch"
        )) {
            val summaries = BrotherPlanBenefits.cardHighlights(code)
            assertEquals(2, summaries.size)
            assertTrue(summaries.all { it.length < 55 })
            assertFalse(summaries.any { it.contains("Beta", ignoreCase = true) })
        }
        assertEquals(
            BrotherPlanBenefits.cardHighlights("pro_lifetime_launch").size,
            BrotherPlanBenefits.cardHighlights("pro_lifetime").size
        )
    }

    @Test fun limitesGratuitosReproduzemPoliticaComCompartilhamento() {
        val rows = BrotherPlanBenefits.monthlyFreeLimits.toMap()
        assertEquals(8, rows.size)
        assertEquals("5/mês", rows["Abrir/importar matrizes DST, JEF e PES"])
        assertEquals("Compartilha as 5 importações", rows["Abrir matrizes pelo WinRAR"])
        assertEquals("Compartilha as 5 importações", rows["Importar matrizes do Telegram ou Arquivos"])
        assertEquals("5 envios/mês", rows["Enviar pacote ao RAR"])
        assertEquals("10 pesquisas/mês", rows["Buscar matrizes no Telegram"])
        assertEquals("3 fontes/mês", rows["Importar fontes TTF, OTF ou ZIP"])
        assertEquals("10 pesquisas/mês", rows["Pesquisar fontes no DaFont"])
        assertEquals("Busca no site; importar usa 3 fontes/mês", rows["Pesquisar fontes no NetFontes"])
        assertTrue(BrotherPlanBenefits.comparison.any { it.name == "Pesquisar fontes no NetFontes" && it.free.contains("3 fontes/mês") })
        assertTrue(BrotherPlanBenefits.comparison.any {
            it.name == "Buscar matrizes no Telegram" && it.pro.contains("Sem cota")
        })
    }

    @Test fun economiaAnualUsaPrecoRealOuReferenciaValida() {
        assertEquals(7890, BrotherPlanBenefits.yearlySavings(1990,15990))
        assertNull(BrotherPlanBenefits.yearlySavings(null,15990))
        assertNull(BrotherPlanBenefits.yearlySavings(1000,15990))
    }
}
