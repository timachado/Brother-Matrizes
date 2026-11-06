package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.BrotherFeedbackDelivery
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopCommunityLinksTest {
    @Test
    fun `grupo oficial e pagina do produto sao apenas links autorizados`() {
        assertEquals("https", DesktopCommunityLinks.officialGroup().scheme)
        assertEquals(
            "chat.whatsapp.com", DesktopCommunityLinks.officialGroup().host
        )
        assertTrue(DesktopCommunityLinks.isAllowed(
            DesktopCommunityLinks.officialGroup()
        ))
        assertTrue(DesktopCommunityLinks.isAllowed(
            DesktopCommunityLinks.productPage()
        ))
        assertFalse(DesktopCommunityLinks.isAllowed(
            URI.create("https://chat.whatsapp.com/convite-alterado")
        ))
        assertFalse(DesktopCommunityLinks.isAllowed(
            URI.create("https://timachado.ifree.page.evil.example/aplicativos/brother-matrizes/")
        ))
        assertFalse(DesktopCommunityLinks.isAllowed(
            URI.create("file:///C:/Users/teste/Documentos/privado.txt")
        ))
        assertFalse(DesktopCommunityLinks.isAllowed(
            URI.create("javascript:alert(1)")
        ))
    }

    @Test
    fun `feedback gerado pelo Android aparece somente pre preenchido`() {
        val feedback = "A fonte O não bordou; favor verificar acentuação e linha."
        val url = DesktopCommunityLinks.whatsappFeedback("Problema", feedback)
        assertTrue(DesktopCommunityLinks.isAllowed(url))
        assertEquals("wa.me", url.host)
        assertEquals(
            "/${BrotherFeedbackDelivery.WHATSAPP_PHONE}", url.path
        )
        val decoded = URLDecoder.decode(
            url.rawQuery.removePrefix("text="), StandardCharsets.UTF_8
        )
        assertTrue(decoded.contains("Versão: 0.1.0-qa"))
        assertTrue(decoded.contains("Tipo: Problema"))
        assertTrue(decoded.endsWith(feedback))
        assertEquals(
            "Brother Matrizes — Sugestão",
            BrotherFeedbackDelivery.subject("Sugestão")
        )
        assertEquals(
            BrotherFeedbackDelivery.SUPPORT_EMAIL,
            "contato.timachado@gmail.com"
        )
    }

    @Test
    fun `mailto possui categoria e relato codificados sem envio automatizado`() {
        val url = DesktopCommunityLinks.emailFeedback(
            "Sugestão", "Adicionar busca de matrizes por nome."
        )
        assertTrue(DesktopCommunityLinks.isAllowed(url))
        assertEquals("mailto", url.scheme)
        val content = URLDecoder.decode(
            url.rawSchemeSpecificPart, StandardCharsets.UTF_8
        )
        assertTrue(content.contains(BrotherFeedbackDelivery.SUPPORT_EMAIL))
        assertTrue(content.contains("subject=Brother Matrizes — Sugestão"))
        assertTrue(content.contains("Tipo: Sugestão"))
        assertTrue(content.contains("Adicionar busca de matrizes"))
    }

    @Test
    fun `bloqueia categoria nao prevista texto vazio e feedback enorme`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopCommunityLinks.emailFeedback("admin", "Relato")
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCommunityLinks.whatsappFeedback("Problema", " \n  ")
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopCommunityLinks.whatsappFeedback(
                "Sugestão", "a".repeat(DesktopCommunityLinks.MAX_FEEDBACK_CHARS + 1)
            )
        }
        assertFalse(DesktopCommunityLinks.isAllowed(
            URI.create("https://wa.me/5511999999999?text=roubar")
        ))
        assertFalse(DesktopCommunityLinks.isAllowed(
            URI.create("https://wa.me/${BrotherFeedbackDelivery.WHATSAPP_PHONE}?text=x&next=evil")
        ))
    }
}
