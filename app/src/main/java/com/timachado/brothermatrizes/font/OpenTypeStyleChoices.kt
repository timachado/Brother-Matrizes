package com.timachado.brothermatrizes.font

import java.io.File

/**
 * Uma tag OpenType validada, nunca fornecida livremente pelo usuário.
 * A presença de uma tag em GSUB não garante que ela afete a palavra atual:
 * apenas o processamento de texto no Android pode comprovar essa diferença.
 */
internal data class OpenTypeStyleChoice(val tag: String?, val label: String) {
    val settings: String?
        get() = tag?.let { "'$it' 1" }
}

internal object OpenTypeStyleChoices {
    private val supported = setOf("aalt", "salt", "swsh", "cswh", "dlig", "hlig")
    fun choices(file: File): List<OpenTypeStyleChoice> =
        fromTags(OpenTypeFeatureCatalog.readOptionalFeatures(file))

    fun fromTags(tags: Set<String>): List<OpenTypeStyleChoice> {
        val options = mutableListOf(OpenTypeStyleChoice(null, "Padrão"))
        val alternates = tags
            .filter { isAllowedTag(it) }
            .sortedWith(compareBy<String>({ !it.startsWith("ss") }, { it }))
        alternates.forEach { tag ->
            val label = when {
                tag.startsWith("ss") -> "Estilo " + tag.removePrefix("ss").toInt()
                tag == "aalt" -> "Alternativas"
                tag == "salt" -> "Alternativa tipográfica"
                tag == "swsh" || tag == "cswh" -> "Floreios"
                tag == "dlig" -> "Ligaturas especiais"
                else -> "Ligaturas opcionais"
            }
            options.add(OpenTypeStyleChoice(tag, "$label · $tag"))
        }
        return options
    }

    fun isAllowedTag(tag: String): Boolean = tag in supported ||
        (tag.length == 4 && tag.startsWith("ss") &&
            tag.substring(2).all { it in '0'..'9' } &&
            tag.substring(2) != "00")

    fun verifiedTag(requested: String?, available: List<OpenTypeStyleChoice>): String? =
        requested?.takeIf { tag -> isAllowedTag(tag) && available.any { it.tag == tag } }

    fun fontSettings(tag: String?): String? =
        tag?.takeIf(::isAllowedTag)?.let { "'$it' 1" }
}
