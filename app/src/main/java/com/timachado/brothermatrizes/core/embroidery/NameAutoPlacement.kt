package com.timachado.brothermatrizes.core.embroidery

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/**
 * Busca o maior nome que cabe na área livre da matriz, usando o percurso
 * REAL das pontadas. Não estica a fonte artificialmente e não desloca
 * flores, folhas ou borboletas. A mesma validação protege o arquivo final.
 */
object NameAutoPlacement {
    data class Fit(
        val options: TextMatrixOptions,
        val centerXUnits: Int,
        val centerYUnits: Int,
        val design: EmbroideryDesign
    )

    private const val SEARCH_BUDGET_NANOS = 9_000_000_000L

    // Priorizar a região central; procurar até 12 mm em cada direção.
    // Amostragem finita e cancelável, sem trabalho pesado na thread da UI.
    private val offsets: List<Pair<Int, Int>> = buildList {
        add(0 to 0)
        for (radius in listOf(30, 60, 90, 120)) {
            add(0 to -radius)
            add(0 to radius)
            add(-radius to 0)
            add(radius to 0)
            if (radius <= 90) {
                add(-radius to -radius)
                add(radius to -radius)
                add(-radius to radius)
                add(radius to radius)
            }
        }
    }

    private fun orientations(
        requested: TextMatrixOptions,
        source: EmbroideryDesign
    ): List<Float> {
        // Nunca alterar uma rotação já escolhida manualmente.
        if (abs(requested.rotationDegrees) > 0.01f) {
            return listOf(requested.rotationDegrees)
        }
        val w = source.bounds.widthMm
        val h = source.bounds.heightMm
        return if (h > w * 1.2f) {
            // Texto acompanha o eixo longo do bastidor vertical.
            listOf(90f, -90f, 0f)
        } else {
            listOf(0f, 90f, -90f)
        }
    }

    suspend fun find(
        original: EmbroideryDesign,
        requested: TextMatrixOptions,
        centerXUnits: Int = (original.bounds.minXUnits.toLong() +
            original.bounds.maxXUnits.toLong()).div(2).toInt(),
        centerYUnits: Int = (original.bounds.minYUnits.toLong() +
            original.bounds.maxYUnits.toLong()).div(2).toInt(),
        importedGenerator: ((TextMatrixOptions) -> Result<EmbroideryDesign>)? = null
    ): Result<Fit> {
        if (requested.text.isBlank()) {
            return Result.failure(IllegalArgumentException("Digite um nome para ajustar."))
        }

        // A edição de um nome existente nunca pode incorporá-lo ao fundo.
        val source = original.editableName?.original ?: original
        val xCenter = if (source === original) centerXUnits else
            (source.bounds.minXUnits.toLong() + source.bounds.maxXUnits.toLong())
                .div(2).toInt()
        val yCenter = if (source === original) centerYUnits else
            (source.bounds.minYUnits.toLong() + source.bounds.maxYUnits.toLong())
                .div(2).toInt()

        // O limite do editor já é 30 mm; testar de maior para menor.
        // Em cada altura, conservar a fonte escolhida (inclusive TTF/OTF),
        // pesquisar o eixo e a posição ANTES de reduzir o tamanho.
        val heights = listOf(
            30f, 28f, 26f, 24f, 22f, 20f, 18f, 16f,
            14f, 12f, 10f, 8f, 6f, 4f
        )
        val rotations = orientations(requested, source)
        val started = System.nanoTime()
        var lastError: Throwable? = null
        var timedOut = false

        for (height in heights) {
            currentCoroutineContext().ensureActive()
            for (angle in rotations) {
                val options = requested.copy(
                    heightMm = height,
                    rotationDegrees = angle,
                    enforceHoop = true
                )
                for ((dx, dy) in offsets) {
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() - started > SEARCH_BUDGET_NANOS) {
                        timedOut = true
                        break
                    }
                    val x = xCenter + dx
                    val y = yCenter + dy
                    val safe = NamePersonalization.canPlace(
                        source, options, x, y, importedGenerator
                    )
                    if (safe.isFailure) {
                        lastError = safe.exceptionOrNull()
                        continue
                    }
                    // A prévia só é aceita se a sequência completa, a margem
                    // das folhas e a área segura do bastidor também passarem.
                    val result = NamePersonalization.add(
                        source, options, x, y, importedGenerator
                    )
                    if (result.isSuccess) {
                        return Result.success(
                            Fit(options, x, y, result.getOrThrow())
                        )
                    }
                    lastError = result.exceptionOrNull()
                }
                if (timedOut) break
            }
            if (timedOut) break
        }
        return Result.failure(IllegalArgumentException(
            if (timedOut) {
                "Encaixe interrompido para preservar o desempenho do Android. " +
                    "Reduza o nome ou ajuste a altura manualmente."
            } else {
                "Não foi encontrado espaço seguro para este nome sem cobrir " +
                    "as folhas ou ultrapassar o bastidor. Tente um texto menor."
            },
            lastError
        ))
    }
}
