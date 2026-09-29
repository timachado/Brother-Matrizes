package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil

object EmbroideryPreviewOptimizer {
    const val MAX_PREVIEW_COMMANDS =
        8_000

    fun optimize(
        points: List<EmbroideryPoint>,
        maxCommands: Int =
            MAX_PREVIEW_COMMANDS
    ): List<EmbroideryPoint> {
        require(
            maxCommands >=
                1_000
        ) {
            "Limite de pré-visualização inválido."
        }

        if (
            points.size <=
                maxCommands
        ) {
            return points
        }

        val stitchCount =
            points.count {
                it.command ==
                    StitchCommand.STITCH
            }

        val structuralCount =
            points.size -
                stitchCount

        val stitchBudget =
            (
                maxCommands -
                    structuralCount
                )
                .coerceAtLeast(
                    2_000
                )

        val stride =
            ceil(
                stitchCount.toDouble() /
                    stitchBudget.toDouble()
            )
                .toInt()
                .coerceAtLeast(
                    1
                )

        if (
            stride <=
                1
        ) {
            return points
        }

        val result =
            ArrayList<EmbroideryPoint>(
                minOf(
                    points.size,
                    maxCommands +
                        structuralCount +
                        512
                )
            )

        var stitchIndexInRun =
            0

        var pendingLastStitch:
            EmbroideryPoint? =
            null

        fun flushLastStitch() {
            val pending =
                pendingLastStitch
                    ?: return

            if (
                result.lastOrNull() !=
                    pending
            ) {
                result +=
                    pending
            }

            pendingLastStitch =
                null
        }

        points.forEach {
                point ->
            if (
                point.command ==
                    StitchCommand.STITCH
            ) {
                val keep =
                    stitchIndexInRun ==
                        0 ||
                        stitchIndexInRun %
                            stride ==
                            0

                if (
                    keep
                ) {
                    result +=
                        point

                    pendingLastStitch =
                        null
                } else {
                    pendingLastStitch =
                        point
                }

                stitchIndexInRun++
            } else {
                flushLastStitch()

                result +=
                    point

                stitchIndexInRun =
                    0
            }
        }

        flushLastStitch()

        return result
    }
}
