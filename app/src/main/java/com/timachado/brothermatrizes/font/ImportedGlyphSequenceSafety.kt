package com.timachado.brothermatrizes.font

import kotlin.math.abs
import kotlin.math.max

/**
 * Trava de segurança da regressão 1.10.12-QA.
 *
 * A cobertura raster não verifica a ordem física de bordado: o Tatami V2
 * percorria scanlines por Y e podia começar no A de "MARIA" em vez do M.
 *
 * NÃO liberar o gerador experimental até substituir o percurso por uma
 * sequência validada de hastes/faixas Satin em ordem de leitura e homologar
 * as cinco fontes originais em Android e máquina de bordar.
 */
internal object ImportedGlyphSequenceSafety {
    // Impede que -PbrotherTatamiQa=true reative o trajeto condenado.
    const val EXPERIMENTAL_ROUTE_BLOCKED: Boolean = true

    data class Diagnosis(
        val acceptableReadingEntry: Boolean,
        val initialXUnits: Float?,
        val leftmostInkXUnits: Float,
        val horizontalDistanceToFirstInkUnits: Float,
        val reason: String
    )

    /**
     * Detecta o defeito de partida: escolher o topo absoluto da palavra
     * em vez da primeira letra em ordem de leitura.
     *
     * Diagnóstico CONSERVADOR. Passar nesta verificação NÃO homologa
     * Satin, segurança física, entradas/saídas ou acabamento de máquina.
     */
    fun diagnoseEntry(
        contours: List<List<ImportedGlyphTatamiPlanner.P>>,
        sequence: List<ImportedGlyphTatamiPlanner.Needle>,
        allowanceUnits: Float = 8f
    ): Diagnosis {
        require(allowanceUnits.isFinite() && allowanceUnits >= 0f)
        val ink = contours.flatten()
        require(ink.isNotEmpty())
        val left = ink.minOf { it.x }
        val first = sequence.firstOrNull()?.point?.x
        val displacement = if (first == null) Float.POSITIVE_INFINITY
            else max(0f, first - left)
        val valid = first != null && displacement <= allowanceUnits
        return Diagnosis(
            acceptableReadingEntry = valid,
            initialXUnits = first,
            leftmostInkXUnits = left,
            horizontalDistanceToFirstInkUnits = displacement,
            reason = if (valid) "Entrada esquerda aproximada; fluxo Satin ainda não homologado"
                else "Ordem de partida inválida: a primeira faixa não está na primeira região à esquerda"
        )
    }
}
