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
        val speed =
            speedMultiplier
                .coerceAtLeast(
                    0.25f
                )

        val base =
            when (command) {
                StitchCommand.STITCH ->
                    80L

                StitchCommand.JUMP ->
                    24L

                StitchCommand.TRIM ->
                    140L

                StitchCommand.COLOR_CHANGE ->
                    520L

                StitchCommand.STOP ->
                    650L

                StitchCommand.SEQUIN ->
                    100L

                StitchCommand.END ->
                    0L
            }

        return (
            base /
                speed
            ).toLong()
            .coerceAtLeast(8L)
    }
}
