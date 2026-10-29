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
 * Primeiro expandir a fonte mantendo suas proporções (pela altura).
 * Se a largura do corredor limitar a altura, usar extensão longitudinal
 * moderada no contorno vetorial, recalculando o Satin na escala FINAL.
 * Nunca mover flores, borboletas ou o brasão.
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
        val validCandidates = ArrayList<Candidate>()
        var lastError: Throwable? = null
        var timedOut = false

        // TTF/OTF ornamental: o motor Satin é caro e NÃO pode ser
        // reexecutado a cada altura, rotação e deslocamento da busca.
        // Gerar uma única sequência REAL de pontadas, e usar transformações
        // apenas para o pré-teste de posição. O resultado final sempre
        // passa pelo gerador Satin em tamanho real e validação completa.
        val referenceHeight = 12f
        val referenceOptions = requested.copy(
            heightMm = referenceHeight,
            rotationDegrees = 0f,
            widthScale = 1f,
            heightScale = 1f,
            hoopProfile = null,
            enforceHoop = false,
            outputFormat = source.format.uppercase()
        )
        val reference = if (referenceOptions.importedFontId != null) {
            val generator = importedGenerator ?: return Result.failure(
                IllegalArgumentException("Fonte importada não disponível.")
            )
            generator(referenceOptions)
        } else {
            TextMatrixGenerator.generate(referenceOptions)
        }.getOrElse { error ->
            // Sem repetir a geração com falha por dezenas de offsets.
            return Result.failure(IllegalArgumentException(
                "A fonte selecionada não gerou pontadas Satin válidas: " +
                    (error.message ?: "erro no processamento da fonte") +
                    ". Revise a fonte antes de tentar o encaixe.", error
            ))
        }
        val hoop = source.hoopProfile ?: requested.hoopProfile
        val started = System.nanoTime()

        fun withinBudget(): Boolean {
            if (System.nanoTime() - started <= SEARCH_BUDGET_NANOS) return true
            timedOut = true
            return false
        }

        suspend fun probe(height: Float, wordLengthScale: Float = 1f): Candidate? {
            val options = requested.copy(
                heightMm = height,
                // A largura da palavra corresponde ao eixo longo de um nome
                // rodado 90 graus em molduras verticais. Não comprimir a
                // altura do glifo nem mexer na posição das flores.
                widthScale = wordLengthScale,
                heightScale = 1f,
                enforceHoop = true
            )
            for (angle in rotations) {
                val candidate = options.copy(rotationDegrees = angle)
                for ((dx, dy) in offsets) {
                    currentCoroutineContext().ensureActive()
                    if (!withinBudget()) return null
                    val x = centerX + dx
                    val y = centerY + dy
                    val positioned = MatrixEditor.apply(
                        reference,
                        EditTransform(
                            centerAtOrigin = true,
                            offsetXUnits = x,
                            offsetYUnits = y,
                            rotationDegrees = angle,
                            // A escala aqui só mede a geometria provisória.
                            // NÃO alterar as pontadas Satin da matriz final.
                            scale = height / referenceHeight,
                            scaleX = wordLengthScale,
                            mirrorVertical =
                                source.sourceYAxisDown != reference.sourceYAxisDown
                        )
                    )
                    val clearance = NamePlacementSafety.validate(
                        source, positioned
                    )
                    if (clearance.isFailure) {
                        lastError = clearance.exceptionOrNull()
                        continue
                    }
                    if (hoop != null) {
                        val widthUnits =
                            max(source.bounds.maxXUnits, positioned.bounds.maxXUnits)
                                .toLong() -
                            min(source.bounds.minXUnits, positioned.bounds.minXUnits)
                        val heightUnits =
                            max(source.bounds.maxYUnits, positioned.bounds.maxYUnits)
                                .toLong() -
                            min(source.bounds.minYUnits, positioned.bounds.minYUnits)
                        val insideHoop =
                            widthUnits <= (hoop.usableWidthMm * 10).toLong() &&
                            heightUnits <= (hoop.usableHeightMm * 10).toLong() ||
                            widthUnits <= (hoop.usableHeightMm * 10).toLong() &&
                            heightUnits <= (hoop.usableWidthMm * 10).toLong()
                        if (!insideHoop) {
                            lastError = IllegalArgumentException(
                                "O nome ultrapassa a área útil do bastidor."
                            )
                            continue
                        }
                    }
                    return Candidate(candidate, x, y)
                        .also(validCandidates::add)
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

        // A altura sozinha nem sempre ocupa o corredor central. A Adamiya
        // é cursiva e aumentar o espaçamento entre letras rompe as ligações:
        // alongar levemente o contorno da palavra no eixo da leitura
        // preserva as conexões entre os glifos. Limite 165% para impedir
        // deformações exageradas. Somente candidatos realmente livres
        // (incluindo 1,5 mm de margem) podem ser aplicados.
        //
        // Fazer essa busca DEPOIS da altura mantém a tipografia natural
        // sempre que possível e evita consultas ao motor Satin em loop.
        if (selected != null && withinBudget() &&
            requested.importedFontId != null
        ) {
            val fittedHeight = selected.options.heightMm
            for (stretch in listOf(1.65f, 1.45f, 1.25f)) {
                currentCoroutineContext().ensureActive()
                if (!withinBudget()) break
                val longer = probe(fittedHeight, stretch)
                if (longer != null) {
                    selected = longer
                    break
                }
            }
        }

        // O pré-teste usa geometria escalada; o resultado comercial
        // SEMPRE regenera os pontos no tamanho final e exige margem real.
        // Limitar tentativas caras e testar também a altura original de
        // referência quando o maior candidato não puder ser bordado.
        for (candidate in validCandidates
            .distinctBy { listOf(it.options.heightMm,
                it.options.widthScale, it.x.toFloat(), it.y.toFloat()) }
            .sortedWith(compareByDescending<Candidate> {
                it.options.heightMm * it.options.widthScale
            }.thenByDescending { it.options.heightMm })
            .take(8)
        ) {
            currentCoroutineContext().ensureActive()
            val result = NamePersonalization.add(
                source, candidate.options, candidate.x, candidate.y,
                importedGenerator
            )
            if (result.isSuccess) {
                return Result.success(Fit(
                    candidate.options, candidate.x, candidate.y,
                    result.getOrThrow()
                ))
            }
            lastError = result.exceptionOrNull()
        }
        return Result.failure(IllegalArgumentException(
            if (timedOut && validCandidates.isEmpty()) {
                "A análise de encaixe ultrapassou o limite desta matriz. " +
                    "O bordado ainda não foi liberado para a máquina."
            } else {
                "Não foi possível concluir um encaixe seguro. " +
                    (lastError?.message ?: "Mantenha 1,5 mm de distância da moldura.")
            },
            lastError
        ))
    }
}
