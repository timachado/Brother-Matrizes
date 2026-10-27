package com.timachado.brothermatrizes.core.embroidery

object SimulationTiming {
    const val BASE_STITCHES_PER_MINUTE = 750f

    fun estimatedSeconds(
        stitches: Int,
        speedMultiplier: Float
    ): Int {
        if (
            stitches <= 0 ||
            speedMultiplier <= 0f
        ) {
            return 0
        }

        val stitchesPerSecond =
            BASE_STITCHES_PER_MINUTE /
                60f *
                speedMultiplier

        return kotlin.math.ceil(
            stitches /
                stitchesPerSecond
        ).toInt()
    }

    /**
     * Intervalo teórico por comando para simulação em lote, sem truncamento
     * mínimo de 8 ms. Até 32× o motor agrupa vários comandos por frame,
     * preservando o STOP e o tempo virtual total.
     */
    fun eventIntervalNanos(speedMultiplier: Float): Double =
        80_000_000.0 / speedMultiplier.coerceIn(0.25f, 32f)

    /**
     * Calcula quantos comandos podem ser aplicados após o tempo decorrido
     * no relógio monotônico. Limita avanços por frame para evitar saltos
     * enormes depois de pausar o aplicativo ou perder quadros.
     */
    fun commandsForFrame(
        carryNanos: Double,
        elapsedNanos: Long,
        speedMultiplier: Float,
        maxCommands: Int = 40
    ): Pair<Int, Double> {
        val delta = elapsedNanos.coerceIn(0L, 100_000_000L).toDouble()
        val tick = eventIntervalNanos(speedMultiplier)
        val total = (carryNanos + delta).coerceAtLeast(0.0)
        val count = kotlin.math.floor(total / tick).toInt()
            .coerceIn(0, maxCommands)
        return count to (total - count * tick)
    }

    data class PlaybackAdvance(val nextIndex: Int, val stopped: Boolean)

    /** Paradas reais da matriz jamais são atravessadas pelo avanço em lote. */
    fun advancePlayback(
        points: List<EmbroideryPoint>,
        fromIndex: Int,
        commandBudget: Int
    ): PlaybackAdvance {
        var index = fromIndex.coerceIn(0, points.size)
        val end = (index.toLong() + commandBudget.coerceAtLeast(0))
            .coerceAtMost(points.size.toLong()).toInt()
        while (index < end) {
            val command = points[index++].command
            if (command == StitchCommand.STOP) {
                return PlaybackAdvance(index, true)
            }
        }
        return PlaybackAdvance(index, false)
    }

    fun eventDelayMs(
        command: StitchCommand,
        speedMultiplier: Float
    ): Long {
        if (
            command ==
                StitchCommand.END
        ) {
            return 0L
        }

        val speed =
            speedMultiplier
                .coerceAtLeast(
                    0.25f
                )

        /*
         * MãoDesign avança um comando por tick fixo de 80 ms em 1×.
         * JUMP/TRIM/COLOR_CHANGE não recebem relógios diferentes.
         */
        return (
            80L /
                speed
            ).toLong()
            .coerceAtLeast(
                8L
            )
    }
}
