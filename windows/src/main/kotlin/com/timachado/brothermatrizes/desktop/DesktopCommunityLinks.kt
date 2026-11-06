package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.BrotherFeedbackDelivery
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Adaptador de Comunidade Windows: apenas URLs oficiais e feedback explícito.
 * Não consulta contas, não armazena relatos, não envia mensagens por conta
 * própria e não reutiliza FCM/tokens Android.
 */
internal object DesktopCommunityLinks {
    const val OFFICIAL_GROUP =
        "https://chat.whatsapp.com/BXbLoaQXC9dFIJdEzwmwB2"
    const val PRODUCT_PAGE =
        "https://timachado.ifree.page/aplicativos/brother-matrizes/"
    const val MAX_FEEDBACK_CHARS = 2500
    const val WINDOWS_QA_VERSION = "0.1.0-qa"
    val categories = listOf("Sugestão", "Problema")

    fun officialGroup(): URI = URI.create(OFFICIAL_GROUP)
    fun productPage(): URI = URI.create(PRODUCT_PAGE)

    private fun verifiedMessage(category: String, message: String): String {
        require(category in categories) {
            "Selecione Sugestão ou Problema."
        }
        require(message.isNotBlank() && message.length <= MAX_FEEDBACK_CHARS) {
            "Descreva o feedback com até 2500 caracteres."
        }
        return BrotherFeedbackDelivery.message(
            category, message, WINDOWS_QA_VERSION
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    fun whatsappFeedback(category: String, message: String): URI {
        val content = verifiedMessage(category, message)
        return URI.create(
            "https://wa.me/${BrotherFeedbackDelivery.WHATSAPP_PHONE}" +
                "?text=${encode(content)}"
        )
    }

    fun emailFeedback(category: String, message: String): URI {
        val content = verifiedMessage(category, message)
        val subject = BrotherFeedbackDelivery.subject(category)
        return URI.create(
            "mailto:${BrotherFeedbackDelivery.SUPPORT_EMAIL}" +
                "?subject=${encode(subject)}&body=${encode(content)}"
        )
    }

    /**
     * Somente destinos fixos do Android oficial ou mensagens criadas no
     * serviço são elegíveis. Mesmo assim é preciso clicar na UI do Windows.
     */
    fun isAllowed(uri: URI): Boolean {
        if (uri.fragment != null || uri.userInfo != null) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> {
                if (uri.port != -1) return false
                when (uri.host?.lowercase()) {
                    "chat.whatsapp.com" -> uri.toString() == OFFICIAL_GROUP
                    "timachado.ifree.page" -> uri.toString() == PRODUCT_PAGE
                    "wa.me" -> uri.path ==
                        "/${BrotherFeedbackDelivery.WHATSAPP_PHONE}" &&
                        uri.rawQuery?.startsWith("text=") == true &&
                        uri.rawQuery.length <= 12_000 &&
                        !uri.rawQuery.contains('&')
                    else -> false
                }
            }
            "mailto" -> {
                val raw = uri.rawSchemeSpecificPart ?: return false
                raw.startsWith(
                    "${BrotherFeedbackDelivery.SUPPORT_EMAIL}?subject="
                ) && raw.contains("&body=") && raw.length <= 15_000
            }
            else -> false
        }
    }

    /** Abre cliente externo somente por clique do usuário; não envia dados. */
    fun openByUserAction(uri: URI) {
        require(isAllowed(uri)) {
            "Destino externo não autorizado para a Comunidade."
        }
        check(Desktop.isDesktopSupported()) {
            "O Windows não disponibilizou abertura de aplicativos externos."
        }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.BROWSE)) {
            "Nenhum navegador/cliente externo foi encontrado."
        }
        desktop.browse(uri)
    }
}
