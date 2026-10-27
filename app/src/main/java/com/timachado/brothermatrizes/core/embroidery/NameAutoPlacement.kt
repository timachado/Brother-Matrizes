package com.timachado.brothermatrizes.core.embroidery

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/**
 * Procura um encaixe usando pontadas reais, não uma caixa de texto fictícia.
 * Todos os candidatos passam pelos mesmos validadores do exportador.
 * Execução exclusiva no dispatcher Default; cancelável ao digitar outro nome.
 */
object NameAutoPlacement {
    data class Fit(
        val options: TextMatrixOptions,
        val centerXUnits: Int,
        val centerYUnits: Int,
        val design: EmbroideryDesign
    )

    suspend fun find(
        original: EmbroideryDesign,
        requested: TextMatrixOptions,
        centerXUnits: Int = (original.bounds.minXUnits.toLong() +
            original.bounds.maxXUnits.toLong()).div(2).toInt(),
        centerYUnits: Int = (original.bounds.minYUnits.toLong() +
            original.bounds.maxYUnits.toLong()).div(2).toInt(),
        importedGenerator: ((TextMatrixOptions) -> Result<EmbroideryDesign>)? = null
    ): Result<Fit> {
        if (requested.text.isBlank()) return Result.failure(
            IllegalArgumentException("Digite um nome para ajustar.")
        )
        // Primeiro testar o centro e o estilo solicitado. Mudar fonte é o
        // último recurso, antes de retornar um erro de espaço insuficiente.
        // Priorizar o centro e variações discretas. Uma varredura grande
        // monopoliza a CPU em aparelhos modestos e em fontes ornamentadas.
        val positions = listOf(
            0 to 0, 0 to -40, 0 to 40, -40 to 0, 40 to 0
        )
        val maxHeight = minOf(17f, maxOf(8f,
            minOf(original.bounds.widthMm, original.bounds.heightMm) / 7f))
        // Testar no máximo 6 tamanhos por fonte, do desejado ao mínimo.
        val heights = listOf(
            maxHeight, maxHeight * 0.85f, maxHeight * 0.70f,
            maxHeight * 0.55f, maxHeight * 0.40f, 4f
        ).map { (it.coerceAtLeast(4f) * 2f).roundToInt() / 2f }
            .distinct()
        // Nunca substituir uma fonte importada por uma BM sem autorização.
        val fonts = if (requested.importedFontId != null) listOf(requested.font)
            else listOf(requested.font, EmbroideryFontPreset.COMPACT).distinct()
        var lastError: Throwable? = null
        val startedNanos = System.nanoTime()
        val maxSearchNanos = 6_000_000_000L
        var timedOut = false
        for (font in fonts) {
            // A extração de contornos TTF/OTF pode ser cara. Testar todas as
            // posições mantendo a mesma altura reaproveita o cache Satin.
            // Se a fonte não couber, reduzir a altura; nunca trocá-la por BM.
            for (height in heights) {
                currentCoroutineContext().ensureActive()
                val options = requested.copy(
                    font = font, heightMm = height, enforceHoop = true
                )
                for ((dx, dy) in positions) {
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() - startedNanos > maxSearchNanos) {
                        timedOut = true
                        break
                    }
                    val x = centerXUnits + dx
                    val y = centerYUnits + dy
                    // O caminho rápido NÃO duplica a matriz inteira, apenas
                    // verifica bordas reais, margem Satin e bastidor.
                    val check = NamePersonalization.canPlace(
                        original, options, x, y, importedGenerator
                    )
                    if (check.isSuccess) {
                        currentCoroutineContext().ensureActive()
                        val final = NamePersonalization.add(
                            original, options, x, y, importedGenerator
                        )
                        if (final.isSuccess) return Result.success(
                            Fit(options, x, y, final.getOrThrow())
                        )
                        lastError = final.exceptionOrNull()
                    } else {
                        lastError = check.exceptionOrNull()
                    }
                }
                if (timedOut) break
            }
            if (timedOut) break
        }
        return Result.failure(IllegalArgumentException(
            if (timedOut) "Encaixe automático interrompido para manter o aplicativo responsivo. " +
                "Tente ajustar a altura manualmente ou use uma fonte mais simples."
            else "Não há espaço seguro para este nome nesta matriz. " +
                "Experimente abreviar o nome ou escolher outra posição.",
            lastError
        ))
    }
}
