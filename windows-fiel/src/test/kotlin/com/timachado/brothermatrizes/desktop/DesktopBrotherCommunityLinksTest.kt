package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.BrotherFeedbackDelivery
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherCommunityLinks
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Official Android community destinations, local-only Windows regression. */
class DesktopBrotherCommunityLinksTest {
    @Test
    fun groupAndUpdatePageMatchOfficialAndroidSource() {
        val root = Path.of("").toAbsolutePath().normalize()
        val androidCommunity = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/BrotherCommunityScreen.kt"
        ))
        assertTrue(androidCommunity.contains(
            "\""+DesktopBrotherCommunityLinks.OFFICIAL_GROUP+"\""
        ))
        assertEquals(
            "https://chat.whatsapp.com",
            "${DesktopBrotherCommunityLinks.group().scheme}://" +
                DesktopBrotherCommunityLinks.group().host
        )
        assertEquals("https", DesktopBrotherCommunityLinks.updates().scheme)
        assertEquals("timachado.ifree.page", DesktopBrotherCommunityLinks.updates().host)
        assertEquals("/aplicativos/brother-matrizes/", DesktopBrotherCommunityLinks.updates().path)
    }

    @Test
    fun whatsappAndEmailDraftPreserveAndroidTextAndRequireUserToSend() {
        val content = "Falha na importação do nome Maria.\nTestei no Windows."
        val wa = DesktopBrotherCommunityLinks.whatsappFeedback("Problema", content, "Windows QA")
        assertEquals("wa.me", wa.host)
        assertEquals("/${BrotherFeedbackDelivery.WHATSAPP_PHONE}", wa.path)
        val decoded = URLDecoder.decode(
            wa.rawQuery.substringAfter("text="), StandardCharsets.UTF_8
        )
        assertEquals(BrotherFeedbackDelivery.message("Problema", content, "Windows QA"), decoded)
        val mail = DesktopBrotherCommunityLinks.emailFeedback("Problema", content, "Windows QA")
        assertEquals("mailto", mail.scheme)
        assertTrue(mail.schemeSpecificPart.startsWith(BrotherFeedbackDelivery.SUPPORT_EMAIL))
        assertTrue(mail.rawSchemeSpecificPart.contains("subject="))
        assertTrue(mail.rawSchemeSpecificPart.contains("body="))
        // The links are only drafts; nothing is sent by building the URI.
    }

    @Test
    fun invalidFeedbackAndExternalWebsiteMustBeRejectedBeforeOpening() {
        for (txt in listOf("", "  ", "a".repeat(2501), "texto\u0000segredo")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopBrotherCommunityLinks.whatsappFeedback("Sugestão", txt, "Windows QA")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherCommunityLinks.emailFeedback("Outra categoria", "teste", "Windows QA")
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherCommunityLinks.openWebsite(URI("https://example.org"))
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopBrotherCommunityLinks.openWebsite(
                URI("https://chat.whatsapp.com.evil.example/BXbLoaQXC9dFIJdEzwmwB2")
            )
        }
    }

    @Test
    fun sharedAndroidFeedbackUtilityMatchesExactSourceByteForByte() {
        val root = Path.of("").toAbsolutePath().normalize()
        val source = Files.readAllBytes(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/BrotherFeedbackDelivery.kt"
        ))
        val desktop = Files.readAllBytes(root.resolve(
            "build/generated/android-community/kotlin/" +
                "com/timachado/brothermatrizes/BrotherFeedbackDelivery.kt"
        ))
        kotlin.test.assertContentEquals(source, desktop)
    }
}
