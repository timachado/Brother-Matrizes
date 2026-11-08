package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.BrotherFeedbackDelivery
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Windows handoff for the SAME public community/feedback actions as Android.
 * Only known official destinations are opened; no silent feedback submission.
 * The Android BrotherFeedbackDelivery.kt is compiled into the desktop app
 * unchanged so contact details and formatting cannot diverge.
 */
internal object DesktopBrotherCommunityLinks {
    // Official group as published by BrotherCommunityScreen.kt in Android main.
    internal const val OFFICIAL_GROUP = "https://chat.whatsapp.com/BXbLoaQXC9dFIJdEzwmwB2"
    internal const val PRODUCT_PAGE = "https://timachado.ifree.page/aplicativos/brother-matrizes/"
    private val approvedGroup = Regex("^https://chat\\.whatsapp\\.com/[A-Za-z0-9]{16,32}$")

    fun group(): URI = URI(OFFICIAL_GROUP).also {
        require(approvedGroup.matches(it.toString()))
    }

    fun updates(): URI = URI(PRODUCT_PAGE)

    fun whatsappFeedback(category: String, message: String, version: String): URI {
        require(category in setOf("Sugestão", "Problema"))
        require(BrotherFeedbackDelivery.canSend(message) && message.length <= 2500 &&
            message.none { it == '\u0000' }) { "Feedback inválido." }
        val encoded = encode(BrotherFeedbackDelivery.message(category, message, version))
        return URI(
            "https://wa.me/${BrotherFeedbackDelivery.WHATSAPP_PHONE}?text=$encoded"
        )
    }

    fun emailFeedback(category: String, message: String, version: String): URI {
        require(category in setOf("Sugestão", "Problema"))
        require(BrotherFeedbackDelivery.canSend(message) && message.length <= 2500 &&
            message.none { it == '\u0000' }) { "Feedback inválido." }
        val subject = encode(BrotherFeedbackDelivery.subject(category))
        val body = encode(BrotherFeedbackDelivery.message(category, message, version))
        return URI("mailto:${BrotherFeedbackDelivery.SUPPORT_EMAIL}?subject=$subject&body=$body")
    }

    private fun encode(text: String): String =
        URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20")

    fun openWebsite(uri: URI) {
        require(uri.scheme == "https" && uri.rawUserInfo == null &&
            uri.port == -1 && uri.fragment == null &&
            (uri == group() || uri == updates() ||
                (uri.host == "wa.me" &&
                    uri.path == "/${BrotherFeedbackDelivery.WHATSAPP_PHONE}" &&
                    uri.rawQuery?.startsWith("text=") == true))) {
            "Destino externo não autorizado."
        }
        check(Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "O Windows não disponibilizou um navegador."
        }
        Desktop.getDesktop().browse(uri)
    }

    fun openEmailDraft(uri: URI) {
        require(uri.scheme == "mailto" && uri.schemeSpecificPart.startsWith(
            "${BrotherFeedbackDelivery.SUPPORT_EMAIL}?subject="
        )) { "E-mail de suporte inesperado." }
        check(Desktop.isDesktopSupported() &&
            Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            "O Windows não tem aplicativo de e-mail configurado."
        }
        Desktop.getDesktop().mail(uri)
    }
}
