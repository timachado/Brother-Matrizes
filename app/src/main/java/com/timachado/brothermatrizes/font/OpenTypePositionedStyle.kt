package com.timachado.brothermatrizes.font

/**
 * Estilo opcional APENAS no primeiro ou último glifo latino do nome.
 *
 * Mantém as letras internas no estilo padrão. A visualização tipográfica e a
 * extração para Satin usam esta mesma divisão e a mesma geometria.
 * Dividir um run de texto pode alterar ligaturas/kerning entre os limites:
 * essa é uma limitação explícita da primeira versão experimental.
 */
data class OpenTypePositionedStyle(
    val initialTag: String? = null,
    val finalTag: String? = null
) {
    val isEnabled: Boolean get() = initialTag != null || finalTag != null

    data class TextRun(val text: String, val featureTag: String?)

    fun runs(text: String): List<TextRun> {
        require(text.isNotBlank() && text.length <= 24) {
            "Texto de fonte ornamental inválido."
        }
        val total = text.codePointCount(0, text.length)
        require(total in 2..9) {
            "Floreios individuais exigem um nome de 2 a 9 letras."
        }
        require(initialTag == null || OpenTypeStyleChoices.isAllowedTag(initialTag))
        require(finalTag == null || OpenTypeStyleChoices.isAllowedTag(finalTag))
        fun validBoundary(cp: Int) =
            Character.isLetter(cp) &&
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN
        require(validBoundary(text.codePointAt(0)) &&
            validBoundary(text.codePointBefore(text.length))) {
            "Floreios individuais exigem letras latinas no início e no fim."
        }
        if (!isEnabled) return listOf(TextRun(text, null))
        val firstEnd = text.offsetByCodePoints(0, 1)
        val lastStart = text.offsetByCodePoints(0, total - 1)
        val result = mutableListOf<TextRun>()
        if (initialTag != null) result += TextRun(text.substring(0, firstEnd), initialTag)
        val middleStart = if (initialTag != null) firstEnd else 0
        val middleEnd = if (finalTag != null) lastStart else text.length
        if (middleEnd > middleStart)
            result += TextRun(text.substring(middleStart, middleEnd), null)
        if (finalTag != null)
            result += TextRun(text.substring(lastStart), finalTag)
        return result
    }
}
