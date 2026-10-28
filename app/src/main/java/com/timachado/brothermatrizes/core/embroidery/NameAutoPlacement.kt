package com.timachado.brothermatrizes.core.embroidery

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Encontra o maior nome que cabe entre as pontadas REAIS da moldura.
 *
 * Uma busca que começa em 30 mm pode consumir todo o orçamento com
 * candidatos inviáveis (por exemplo, a coroa deslocada de um brasão).
 * Primeiro encontrar uma altura utilizável, depois aumentá-la.
 *
 * Fontes TTF/OTF mantêm a sua proporção original: ajustar a altura não é
 * esticar a tipografia. Nunca mover flores, borboletas ou o brasão.
 */
object NameAutoPlacement {
    data class Fit(
        val options: TextMatrixOptions,
        val centerXUnits: Int,
        val centerYUnits: Int,
        val design: EmbroideryDesign
    )

    private data class Candidate(
        val options: TextMatrixOptions,
        val x: Int,
        val y: Int
    )

    private const val SEARCH_BUDGET_NANOS = 10_000_000_000L

    /**
     * O centro da caixa total não é necessariamente o centro da região útil.
     * Em brasões largos a ornamentação inferior desloca a caixa para baixo.
     * Primeiro procurar na linha do centro e para CIMA, sem considerar
     * saltos do PES como bordados. O teste final usa 1,5 mm de margem real.
     */
    internal fun candidateOffsets(source: EmbroideryDesign): List<Pair<Int, Int>> {
        val portrait = source.bounds.heightMm > source.bounds.widthMm * 1.2f
        val span = min(
            source.bounds.maxXUnits.toLong() - source.bounds.minXUnits,
            source.bounds.maxYUnits.toLong() - source.bounds.minYUnits
        ).coerceAtLeast(1L).toFloat()
        val offsets = linkedSetOf(0 to 0)
        val primaryRadii = listOf(.14f, .20f, .08f, .28f, .04f)
            .map { (span * it).roundToInt().coerceAtLeast(10) }
            .distinct()
        fun primary(delta: Int): Pair<Int, Int> =
            if (portrait) delta to 0 else 0 to delta
        fun secondary(delta: Int): Pair<Int, Int> =
            if (portrait) 0 to delta else delta to 0

        // Paisagem: tentar o centro do escudo acima do ornamento inferior.
        // Retrato: priorizar o corredor central estreito, depois laterais.
        for (r in primaryRadii) {
            offsets += primary(-r)
            offsets += primary(r)
        }
        for (r in listOf(.07f, .14f, .22f)) {
            val value = (span * r).roundToInt().coerceAtLeast(10)
            offsets += secondary(-value)
            offsets += secondary(value)
        }
        val diag = (span * .11f).roundToInt().coerceAtLeast(10)
        for (dx in listOf(-diag, diag)) {
            for (dy in listOf(-diag, diag)) {
                offsets += dx to dy
            }
        }
        return offsets.toList()
    }

    private fun orientations(
        requested: TextMatrixOptions,
        source: EmbroideryDesign
    ): List<Float> {
        // Nunca alterar uma rotação explicitamente escolhida.
        if (abs(requested.rotationDegrees) > .01f) {
            return listOf(requested.rotationDegrees)
        }
        return if (source.bounds.heightMm > source.bounds.widthMm * 1.2f) {
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

        val source = original.editableName?.original ?: original
        val centerX = if (source === original) centerXUnits else
            (source.bounds.minXUnits.toLong() + source.bounds.maxXUnits.toLong())
                .div(2).toInt()
        val centerY = if (source === original) centerYUnits else
            (source.bounds.minYUnits.toLong() + source.bounds.maxYUnits.toLong())
                .div(2).toInt()
        val rotations = orientations(requested, source)
        val offsets = candidateOffsets(source)
        val started = System.nanoTime()
        val validCandidates = ArrayList<Candidate>()
        var lastError: Throwable? = null
        var timedOut = false

        fun withinBudget(): Boolean {
            if (System.nanoTime() - started <= SEARCH_BUDGET_NANOS) return true
            timedOut = true
            return false
        }

        suspend fun probe(height: Float): Candidate? {
            val options = requested.copy(
                heightMm = height,
                widthScale = 1f,
                heightScale = 1f,
                enforceHoop = true
            )
            for (angle in rotations) {
                for ((dx, dy) in offsets) {
                    currentCoroutineContext().ensureActive()
                    if (!withinBudget()) return null
                    val candidate = options.copy(rotationDegrees = angle)
                    val x = centerX + dx
                    val y = centerY + dy
                    val safe = NamePersonalization.canPlace(
                        source, candidate, x, y, importedGenerator
                    )
                    if (safe.isSuccess) {
                        return Candidate(candidate, x, y).also(validCandidates::add)
                    }
                    lastError = safe.exceptionOrNull()
                }
            }
            return null
        }

        // Encaixe inicial utilizável ANTES da pesquisa de preenchimento.
        // Se 30 mm encostar nos ornamentos, 14/10/7/4 mm são testados
        // sem desperdiçar o orçamento nos 30 mm inviáveis.
        val start = min(14f, max(4f, requested.heightMm))
        var selected: Candidate? = null
        for (height in listOf(start, 10f, 7f, 4f).distinct().filter { it <= start }) {
            currentCoroutineContext().ensureActive()
            if (!withinBudget()) break
            selected = probe(height)
            if (selected != null) break
        }

        if (selected != null && withinBudget()) {
            var low = selected.options.heightMm
            var high = 31f
            // Busca binária por um nome maior, usando as mesmas pontadas
            // da fonte e procurando todos os centros necessários.
            while (high - low >= 1.5f && withinBudget()) {
                currentCoroutineContext().ensureActive()
                val middle = ((low + high) / 2f).coerceIn(4f, 30f)
                val larger = probe(middle)
                if (larger != null) {
                    selected = larger
                    low = middle
                } else {
                    if (timedOut) break
                    high = middle
                }
            }
        }

        // canPlace() é rápido; add() gera e valida a matriz completa.
        // Tentar do maior para o menor também protege fontes TTF/OTF
        // cujo Satin no tamanho final difere do pré-teste leve.
        for (candidate in validCandidates.sortedByDescending { it.options.heightMm }) {
            currentCoroutineContext().ensureActive()
            val result = NamePersonalization.add(
                source, candidate.options, candidate.x, candidate.y, importedGenerator
            )
            if (result.isSuccess) {
                return Result.success(Fit(
                    candidate.options, candidate.x, candidate.y, result.getOrThrow()
                ))
            }
            lastError = result.exceptionOrNull()
        }
        return Result.failure(IllegalArgumentException(
            if (timedOut && validCandidates.isEmpty()) {
                "O ajuste automático não terminou nesta matriz. " +
                    "Tente um nome menor ou uma fonte menos larga."
            } else {
                "Não existe encaixe seguro para este nome na área livre da matriz. " +
                    "Ajuste o tamanho ou o bastidor, mantendo 1,5 mm das pontadas existentes."
            },
            lastError
        ))
    }
}
