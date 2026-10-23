package com.timachado.brothermatrizes.core.vision

/**
 * Converts OCR or specific visual labels from the user's own photo into a
 * single, conservative Telegram text query. This is NOT reverse image search.
 * Generic classifications never masquerade as the exact embroidery matrix.
 */
object TelegramImageQuery {
    private val locale = java.util.Locale.ROOT
    private val ignore = setOf(
        "matriz", "matrizes", "bordado", "bordados", "embroidery",
        "download", "design", "imagem", "picture", "photo", "foto",
        "telegram", "whatsapp", "tableware", "plant", "object",
        "flower", "flowers", "flor", "flores", "craft", "text", "label"
    )
    private val nameOrCode = Regex("[\\p{L}0-9][\\p{L}0-9_.-]{3,28}")
    private val specificSubjects = mapOf(
        "sunflower" to "girassol",
        "butterfly" to "borboleta",
        "unicorn" to "unicórnio",
        "dolphin" to "golfinho",
        "cat" to "gato",
        "dog" to "cachorro",
        "horse" to "cavalo",
        "bird" to "pássaro",
        "heart" to "coração",
        "rose" to "rosa",
        "elephant" to "elefante",
        "owl" to "coruja",
        "turtle" to "tartaruga",
        "bee" to "abelha",
        "fish" to "peixe",
        "star" to "estrela",
        "princess" to "princesa"
    )

    fun fromOcr(text: String): String? {
        val trimmed = text.replace(Regex("(?i)https?://\\S+|www\\.\\S+|\\S+@\\S+"), " ")
        val candidates = nameOrCode.findAll(trimmed)
            .map { it.value.trim().lowercase(locale) }
            .filter { it !in ignore && it.any(Char::isLetter) }
            .filterNot { it.startsWith("http") || it.startsWith("www") }
            .distinct().toList()
        return candidates.firstOrNull { token ->
            token.length in 5..28 && token.any(Char::isDigit) &&
                token.count(Char::isLetter) >= 2
        } ?: candidates.firstOrNull { it.length in 5..28 }
    }

    fun fromLabels(labels: List<Pair<String, Float>>): String? =
        labels.asSequence()
            .filter { it.second >= 0.80f }
            .sortedByDescending { it.second }
            .mapNotNull { (label, _) -> specificSubjects[label.trim().lowercase(locale)] }
            .firstOrNull()

    fun choose(ocr: String, labels: List<Pair<String, Float>>): String? =
        fromOcr(ocr) ?: fromLabels(labels)
}
