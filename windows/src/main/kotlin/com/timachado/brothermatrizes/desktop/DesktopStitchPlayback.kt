package com.timachado.brothermatrizes.desktop

/**
 * Reprodução VISUAL de comandos de pontada na interface Windows.
 *
 * Não estima tempo de costura, velocidade de máquina, tensão, aceleração,
 * colisões nem comportamento de hardware. Não modifica matrizes ou arquivos.
 */
internal object DesktopStitchPlayback {
    const val MAX_COMMANDS = 2_000_000
    const val TICK_MILLIS = 200L
    val availableRates = listOf(100, 500, 2_000) // comandos visuais/segundo, NÃO RPM da máquina

    fun visibleCount(total: Int, progress: Float): Int {
        require(total in 0..MAX_COMMANDS) { "Quantidade de comandos inválida." }
        require(progress.isFinite() && progress in 0f..1f) {
            "Posição da sequência inválida."
        }
        return (total.toFloat() * progress).toInt().coerceIn(0, total)
    }

    /** Converte índice inteiro para a mesma fração usada pelo desenho Compose. */
    fun seek(total: Int, count: Int): Float {
        require(total in 0..MAX_COMMANDS && count in 0..total) {
            "Posição de reprodução fora dos limites."
        }
        if (total == 0 || count == 0) return 0f
        if (count == total) return 1f
        // nextUp evita erro de arredondamento em (total * progress).toInt()
        // quando a UI exibe exatamente um comando por avanço.
        return Math.nextUp((count.toDouble() / total.toDouble()).toFloat())
            .coerceAtMost(1f)
    }

    fun advance(total: Int, progress: Float, delta: Int): Float {
        val current = visibleCount(total, progress)
        val next = (current.toLong() + delta.toLong()).coerceIn(0L, total.toLong()).toInt()
        return seek(total, next)
    }

    fun commandsPerTick(rate: Int): Int {
        require(rate in availableRates) { "Velocidade de reprodução inválida." }
        return (rate.toLong() * TICK_MILLIS / 1000L).toInt().coerceAtLeast(1)
    }
}
