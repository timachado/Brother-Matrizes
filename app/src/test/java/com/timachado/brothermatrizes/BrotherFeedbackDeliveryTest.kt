package com.timachado.brothermatrizes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrotherFeedbackDeliveryTest {
    @Test fun direcionaAosContatosComerciaisOficiais() {
        assertEquals("5511987814783", BrotherFeedbackDelivery.WHATSAPP_PHONE)
        assertEquals("contato.timachado@gmail.com", BrotherFeedbackDelivery.SUPPORT_EMAIL)
    }

    @Test fun naoPermiteEnviarMensagemVazia() {
        assertFalse(BrotherFeedbackDelivery.canSend(""))
        assertFalse(BrotherFeedbackDelivery.canSend("  \n "))
        assertTrue(BrotherFeedbackDelivery.canSend("Teste"))
    }

    @Test fun formataFeedbackComVersaoTipoEMensagemSemAlterarConteudo() {
        val text = BrotherFeedbackDelivery.message(
            "Problema", "  A fonte não abriu corretamente.  ", "1.10.4"
        )
        assertTrue(text.contains("Versão: 1.10.4"))
        assertTrue(text.contains("Tipo: Problema"))
        assertTrue(text.endsWith("A fonte não abriu corretamente."))
        assertFalse(text.contains("A fonte não abriu corretamente.  "))
    }

    @Test fun criaAssuntoDeEmailComCategoria() {
        assertEquals(
            "Brother Matrizes — Sugestão",
            BrotherFeedbackDelivery.subject("Sugestão")
        )
    }
}
