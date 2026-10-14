package com.timachado.brothermatrizes.font

import kotlin.math.ceil

/**
 * Mais pontos ao amostrar os contornos vetoriais importados TTF/OTF
 * antes da digitalização. Não modifica o motor Satin, o caminho da
 * agulha, a sequência STITCH/JUMP ou a apresentação da simulação.
 */
internal object ImportedGlyphContourSampling {
    private const val TARGET_SEGMENT_PX = 0.85f
    private const val MIN_SAMPLES = 12
    private const val MAX_SAMPLES = 12_000

    fun sampleCount(contourLengthPx: Float): Int {
        if (!contourLengthPx.isFinite() || contourLengthPx <= 0f) return 0
        // Dividir antes da conversão evita overflow no caso de fontes
        // ornamentais malformadas ou contornos descomunais.
        if (contourLengthPx >= MAX_SAMPLES * TARGET_SEGMENT_PX) return MAX_SAMPLES
        return ceil(contourLengthPx / TARGET_SEGMENT_PX)
            .toInt()
            .coerceIn(MIN_SAMPLES, MAX_SAMPLES)
    }
}
