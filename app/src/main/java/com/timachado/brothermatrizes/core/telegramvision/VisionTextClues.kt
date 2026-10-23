package com.timachado.brothermatrizes.core.telegramvision

/** Pure, testable search term normalization. Raw OCR is never sent to our servers. */
object VisionTextClues {
    private val ignored = setOf(
        "imagem", "foto", "photo", "image", "border", "decor", "item", "design",
        "sample", "preview", "download", "telegram", "whatsapp", "www", "http",
        "bordado", "matriz", "matrizes", "art", "graphic", "text", "label",
        "floral arrangement", "flower arrangement", "tableware", "plant",
        "object", "pattern", "product", "font", "label"
    )
    private val locale = java.util.Locale.ROOT
    private val labelToPortuguese = mapOf(
        "rose" to "rosa", "butterfly" to "borboleta",
        "sunflower" to "girassol", "flower" to "flor",
        "flowers" to "flores", "dog" to "cachorro",
        "cat" to "gato", "bird" to "pássaro",
        "heart" to "coração", "tree" to "árvore",
        "horse" to "cavalo", "star" to "estrela"
    )
    private val word = Regex("[\\p{L}\\p{N}]{4,32}")
    private val likelyCode = Regex("[a-zA-Z]{1,8}[-_]?[0-9]{3,10}")

    fun fromOcr(raw: String): List<String> {
        val lines = raw.lines().map { it.trim() }
        val candidates = lines.asSequence()
            .flatMap { line ->
                sequenceOf(line).plus(word.findAll(line).map { it.value }.asSequence())
            }
            .map { it.trim().lowercase(locale).take(48) }
            .filter { it.length in 4..48 && it !in ignored }
            .filter { it.any(Char::isLetter) }
            .filterNot { it.contains("http") || it.contains("@") || it.contains("www") }
            .distinct()
            .sortedWith(compareByDescending<String> { likelyCode.matches(it) }.thenByDescending { it.length })
            .take(3)
            .toList()
        return candidates
    }

    fun fromVisualLabels(labels: List<Pair<String, Float>>): List<String> =
        labels.asSequence()
            .filter { it.second >= 0.74f }
            .sortedByDescending { it.second }
            .map { it.first.trim().lowercase(locale) }
            .map { labelToPortuguese[it] ?: it }
            .filter { it.length in 4..36 && it !in ignored }
            .filter { it.all { char -> char.isLetter() || char == ' ' || char == '-' } }
            .distinct()
            .take(2)
            .toList()

    fun combine(ocrTerms: List<String>, visualTerms: List<String>): List<String> =
        (ocrTerms + visualTerms).distinct().take(4)
}
