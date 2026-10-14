package com.timachado.brothermatrizes.font

import kotlin.math.ceil
import kotlin.math.abs

/**
 * Preparação vetorial dos glifos TTF/OTF. Não gera pontadas e não participa
 * da simulação: o motor Satin oficial continua recebendo os mesmos tipos
 * de contornos fechados.
 */
internal object ImportedGlyphVectorGeometry {
    private const val TARGET_SEGMENT_UNITS = 0.65f
    private const val MIN_CONTOUR_POINTS = 12
    private const val MAX_CONTOUR_POINTS = 12_000

    /**
     * Refinar curvas Bézier estreitas sem transformar o contorno em reta
     * e impedir alocações excessivas com fontes problemáticas.
     */
    fun sampleCount(length: Float): Int {
        if (!length.isFinite() || length <= 0f) return 0
        if (length >= TARGET_SEGMENT_UNITS * MAX_CONTOUR_POINTS) {
            return MAX_CONTOUR_POINTS
        }
        return ceil(length / TARGET_SEGMENT_UNITS)
            .toInt()
            .coerceIn(MIN_CONTOUR_POINTS, MAX_CONTOUR_POINTS)
    }

    /**
     * Agrupar os contornos já desenhados pela renderização do texto completo
     * junto ao avanço visual mais próximo, sem recortar, mover, esticar ou
     * remodelar nenhum contorno. O objetivo é manter ordem de costura por
     * caractere enquanto usa o desenho REAL com kerning/ligaturas.
     */
    fun nearestGlyphIndex(contourCenterX: Float, advanceCentersX: List<Float>): Int {
        require(contourCenterX.isFinite())
        require(advanceCentersX.isNotEmpty() && advanceCentersX.all { it.isFinite() })
        var best = 0
        var distance = Float.POSITIVE_INFINITY
        for (index in advanceCentersX.indices) {
            val candidate = abs(contourCenterX - advanceCentersX[index])
            if (candidate < distance) {
                distance = candidate
                best = index
            }
        }
        return best
    }
}
