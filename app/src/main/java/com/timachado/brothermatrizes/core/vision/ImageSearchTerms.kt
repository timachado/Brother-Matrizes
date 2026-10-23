package com.timachado.brothermatrizes.core.vision

/**
 * Converte rótulos visuais escolhidos pelo usuário em termos de pesquisa.
 * Nenhuma foto ou dado do Telegram é indexado ou enviado a um serviço remoto.
 * Os resultados são sugestões, não uma identificação da matriz PES/DST/JEF.
 */
object ImageSearchTerms {
    private val translations = mapOf(
        "flower" to "flor", "flowers" to "flores", "rose" to "rosa",
        "butterfly" to "borboleta", "bird" to "pássaro", "dog" to "cachorro",
        "cat" to "gato", "horse" to "cavalo", "fish" to "peixe",
        "baby" to "bebê", "heart" to "coração", "star" to "estrela",
        "tree" to "árvore", "leaf" to "folha", "leaves" to "folhas",
        "car" to "carro", "crown" to "coroa", "angel" to "anjo",
        "animal" to "animal", "text" to "texto", "letter" to "letra",
        "cartoon" to "desenho", "logo" to "logo", "embroidery" to "bordado"
    )

    fun fromLabels(labels: List<Pair<String, Float>>): List<String> =
        labels.asSequence()
            .filter { it.second >= 0.45f }
            .sortedByDescending { it.second }
            .map { it.first.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotBlank() && it.length <= 50 }
            .map { translations[it] ?: it }
            .distinct()
            .take(3)
            .toList()
}
