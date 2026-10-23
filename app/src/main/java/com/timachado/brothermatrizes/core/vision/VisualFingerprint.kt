package com.timachado.brothermatrizes.core.vision

/**
 * dHash horizontal (64 bits) calculado sobre uma miniatura em tons de cinza 9x8.
 * Não usa ML nem conteúdo do Telegram: compara exclusivamente imagens locais
 * escolhidas pelo usuário. Um score alto é similaridade, não prova de igualdade.
 */
object VisualFingerprint {
    fun fromGray9x8(gray: IntArray): Long {
        require(gray.size == 72) { "A miniatura deve ter 9x8 pixels." }
        require(gray.all { it in 0..255 }) { "Pixel inválido." }
        var bits = 0L
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (gray[row * 9 + col] > gray[row * 9 + col + 1]) {
                    bits = bits or (1L shl (row * 8 + col))
                }
            }
        }
        return bits
    }

    fun hasEnoughDetail(gray: IntArray): Boolean {
        if (gray.size != 72) return false
        val range = (gray.maxOrNull() ?: 0) - (gray.minOrNull() ?: 0)
        val adjacentChanges = (0 until 8).sumOf { row ->
            (0 until 8).count { col ->
                kotlin.math.abs(gray[row * 9 + col] - gray[row * 9 + col + 1]) >= 12
            }
        }
        return range >= 25 && adjacentChanges >= 5
    }

    fun similarity(first: Long, second: Long): Float =
        1f - java.lang.Long.bitCount(first xor second) / 64f
}
