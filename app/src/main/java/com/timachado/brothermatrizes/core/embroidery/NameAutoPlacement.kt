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
        val positions = listOf(
            0 to 0,
            0 to -30, 0 to 30,
            -30 to 0, 30 to 0,
            0 to -65, 0 to 65,
            -65 to 0, 65 to 0
        )
        val maxHeight = minOf(17f, maxOf(8f,
            minOf(original.bounds.widthMm, original.bounds.heightMm) / 7f))
        val heights = buildList {
            var h = maxHeight
            while (h >= 4f) {
                add((h * 2f).roundToInt() / 2f)
                h -= 1f
            }
            if (lastOrNull() != 4f) add(4f)
        }.distinct()
        // Nunca substituir uma fonte importada por uma BM sem autorização.
        val fonts = if (requested.importedFontId != null) listOf(requested.font)
            else listOf(requested.font, EmbroideryFontPreset.COMPACT).distinct()
        var lastError: Throwable? = null
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
                    val x = centerXUnits + dx
                    val y = centerYUnits + dy
                    val result = NamePersonalization.add(
                        original, options, x, y, importedGenerator
                    )
                    if (result.isSuccess) return Result.success(
                        Fit(options, x, y, result.getOrThrow())
                    )
                    lastError = result.exceptionOrNull()
                }
            }
        }
        return Result.failure(IllegalArgumentException(
            "Não há espaço seguro para este nome nesta matriz. " +
                "Experimente abreviar o nome ou escolher outra posição.",
            lastError
        ))
    }
}
