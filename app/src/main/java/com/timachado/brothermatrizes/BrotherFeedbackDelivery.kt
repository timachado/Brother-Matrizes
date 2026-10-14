package com.timachado.brothermatrizes

/**
 * Contatos comerciais públicos da T.I. Machado, verificados no site oficial.
 * Mantém relatos separados do grupo público e da hospedagem WordPress.
 * Não envia automaticamente nada: a confirmação ocorre no WhatsApp/e-mail.
 */
internal object BrotherFeedbackDelivery {
    const val WHATSAPP_PHONE = "5511987814783"
    const val SUPPORT_EMAIL = "contato.timachado@gmail.com"

    fun canSend(message: String): Boolean = message.isNotBlank()

    fun subject(category: String): String =
        "Brother Matrizes — ${category.trim().ifBlank { "Feedback" }}"

    fun message(category: String, feedback: String, version: String): String =
        buildString {
            append("Olá, T.I. Machado!\n\n")
            append("Feedback do Brother Matrizes\n")
            append("Versão: ").append(version.trim()).append('\n')
            append("Tipo: ").append(category.trim()).append("\n\n")
            append(feedback.trim())
        }
}
