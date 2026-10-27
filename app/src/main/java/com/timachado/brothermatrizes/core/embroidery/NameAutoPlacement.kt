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
        // Deslocamentos proporcionais à matriz, priorizando o centro.
        // A malha cobre eixos e diagonais; a validação usa pontadas reais.
        val minSideUnits = minOf(
            original.bounds.maxXUnits.toLong() - original.bounds.minXUnits,
            original.bounds.maxYUnits.toLong() - original.bounds.minYUnits
        ).coerceAtLeast(0L)
        val shortStep = (minSideUnits / 30L).coerceIn(15L, 45L).toInt()
        val longStep = (shortStep * 2).coerceAtMost(90)
        val positions = buildList {
            add(0 to 0)
            for (step in listOf(shortStep, longStep)) {
                add(0 to -step); add(0 to step)
                add(-step to 0); add(step to 0)
                add(-step to -step); add(step to -step)
                add(-step to step); add(step to step)
            }
        }.distinct()
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
            // Priorizar centro: primeiro todas as alturas nele. Se necessário,
            // explorar a malha com altura no laço externo para gerar a fonte
            // UMA vez por altura, e não uma vez por posição.
            for (group in listOf(positions.take(1), positions.drop(1))) {
                for (height in heights) {
                    currentCoroutineContext().ensureActive()
                    val options = requested.copy(
                        font = font, heightMm = height, enforceHoop = true
                    )
                    for ((dx, dy) in group) {
                        currentCoroutineContext().ensureActive()
                        val x = centerXUnits + dx
                        val y = centerYUnits + dy
                        val check = NamePersonalization.checkPlacement(
                            original, options, x, y, importedGenerator
                        )
                        if (check.isFailure) {
                            lastError = check.exceptionOrNull()
                            continue
                        }
                        // Só montar, normalizar e gerar os comandos finais
                        // depois que geometria e bastidor tiverem passado.
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
        }
        return Result.failure(IllegalArgumentException(
            "Não há espaço seguro para este nome nesta matriz. " +
                "Experimente abreviar o nome ou escolher outra posição.",
            lastError
        ))
    }
}
