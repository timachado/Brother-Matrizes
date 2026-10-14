package com.timachado.brothermatrizes.core.updates

/**
 * O cartão de novidades deve ser útil para o usuário, não um relatório técnico.
 * A lista completa permanece no site; na Home, mostrar apenas pontos curtos.
 */
internal object UpdateInAppNoticeRules {
    fun shouldShow(
        installedCode: Long,
        publishedCode: Long,
        dismissedVersionCode: Long
    ): Boolean = installedCode > 0L &&
        publishedCode > installedCode &&
        dismissedVersionCode != publishedCode

    private val technical = Regex(
        "(?i)(apk|sha.?256|versioncode|firebase|fcm|wordpress|endpoint|github|json|" +
            "servidor|hospedagem|download direto|http|play protect|instalador|cache|certificado)"
    )

    fun highlights(notes: String): List<String> {
        val human = notes.lines()
            .map { it.trim().trimStart('-', '•', '*').trim() }
            .filter { it.length in 12..90 }
            .filterNot {
                technical.containsMatchIn(it) || it.contains("://") ||
                    it.lowercase().startsWith("novidades desta versão")
            }
            .distinct().take(3)
        return if (human.isNotEmpty()) human else listOf(
            "Experiência mais fluida",
            "Melhorias para fontes e matrizes",
            "Correções e estabilidade"
        )
    }

    fun notes(text: String): String = highlights(text).joinToString("\n")
}
