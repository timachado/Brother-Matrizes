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
