package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class SpecialStitchMode(
    val displayName: String,
    val description: String
) {
    BEAN(
        "Ponto Feijão",
        "Vai e volta em cada pequeno trecho para criar uma linha mais grossa e marcada."
    ),
    TRIPLE_RUNNING(
        "Ponto Corrido Triplo",
        "Percorre o mesmo caminho três vezes para aumentar durabilidade e visibilidade."
    ),
    PROGRAMMED_MOTIF(
        "Ponto de Motivo",
        "Aplica um padrão decorativo repetido ao longo do traçado."
    )
}

object SpecialStitchProcessor {

    fun apply(
        points: List<EmbroideryPoint>,
        mode: SpecialStitchMode?
    ): MutableList<EmbroideryPoint> {
        if (
            mode ==
                null
        ) {
            return points
                .toMutableList()
        }

        val result =
            mutableListOf<
                EmbroideryPoint
            >()

        val run =
            mutableListOf<
                EmbroideryPoint
            >()

        fun flushRun() {
            if (
                run.isEmpty()
            ) {
                return
            }

            val transformed =
                when (
                    mode
                ) {
                    SpecialStitchMode.BEAN ->
                        bean(
                            run
                        )

                    SpecialStitchMode.TRIPLE_RUNNING ->
                        tripleRunning(
                            run
                        )

                    SpecialStitchMode.PROGRAMMED_MOTIF ->
                        motif(
                            run
                        )
                }

            require(
                result.size +
                    transformed.size <=
                    EmbroideryStressPolicy
                        .MAX_GENERATED_COMMANDS
            ) {
                "O ponto especial gerou comandos demais para processar com segurança no celular."
            }

            result +=
                transformed

            run.clear()
        }

        points.forEach {
                point ->
            when (
                point.command
            ) {
                StitchCommand.JUMP -> {
                    flushRun()

                    run +=
                        point
                }

                StitchCommand.STITCH -> {
                    if (
                        run.isEmpty()
                    ) {
                        run +=
                            point.copy(
                                command =
                                    StitchCommand.JUMP
                            )
                    } else {
                        run +=
                            point
                    }
                }

                StitchCommand.TRIM,
                StitchCommand.STOP,
                StitchCommand.COLOR_CHANGE,
                StitchCommand.SEQUIN,
                StitchCommand.END -> {
                    flushRun()

                    require(
                        result.size <
                            EmbroideryStressPolicy
                                .MAX_GENERATED_COMMANDS
                    ) {
                        "O ponto especial gerou comandos demais para processar com segurança no celular."
                    }

                    result +=
                        point
                }
            }
        }

        flushRun()

        return result
    }

    private fun bean(
        source:
            List<EmbroideryPoint>
    ): List<EmbroideryPoint> {
        if (
            source.size <
                2
        ) {
            return source
        }

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        output +=
            source.first()
                .copy(
                    command =
                        StitchCommand.JUMP
                )

        var previous =
            source.first()

        source.drop(1)
            .forEach {
                    target ->
                output +=
                    target.copy(
                        command =
                            StitchCommand.STITCH
                    )

                output +=
                    previous.copy(
                        command =
                            StitchCommand.STITCH
                    )

                output +=
                    target.copy(
                        command =
                            StitchCommand.STITCH
                    )

                previous =
                    target
            }

        return output
    }

    private fun tripleRunning(
        source:
            List<EmbroideryPoint>
    ): List<EmbroideryPoint> {
        if (
            source.size <
                2
        ) {
            return source
        }

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val first =
            source.first()

        output +=
            first.copy(
                command =
                    StitchCommand.JUMP
            )

        source.drop(1)
            .forEach {
                    point ->
                output +=
                    point.copy(
                        command =
                            StitchCommand.STITCH
                    )
            }

        for (
            index in
                source.lastIndex -
                    1 downTo
                    0
        ) {
            output +=
                source[index]
                    .copy(
                        command =
                            StitchCommand.STITCH
                    )
        }

        source.drop(1)
            .forEach {
                    point ->
                output +=
                    point.copy(
                        command =
                            StitchCommand.STITCH
                    )
            }

        return output
    }

    private fun motif(
        source:
            List<EmbroideryPoint>
    ): List<EmbroideryPoint> {
        if (
            source.size <
                2
        ) {
            return source
        }

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        output +=
            source.first()
                .copy(
                    command =
                        StitchCommand.JUMP
                )

        val motifLengthUnits =
            18f

        val amplitudeUnits =
            4f

        var previous =
            source.first()

        source.drop(1)
            .forEach {
                    target ->
                val dx =
                    (
                        target.xUnits -
                            previous.xUnits
                        ).toFloat()

                val dy =
                    (
                        target.yUnits -
                            previous.yUnits
                        ).toFloat()

                val distance =
                    hypot(
                        dx.toDouble(),
                        dy.toDouble()
                    ).toFloat()

                if (
                    distance <
                        0.5f
                ) {
                    previous =
                        target

                    return@forEach
                }

                val normalX =
                    -dy /
                        distance

                val normalY =
                    dx /
                        distance

                val pieces =
                    maxOf(
                        1,
                        ceil(
                            distance /
                                motifLengthUnits
                        ).toInt()
                    )

                for (
                    part in
                        0 until
                            pieces
                ) {
                    val startRatio =
                        part.toFloat() /
                            pieces

                    val endRatio =
                        (
                            part +
                                1
                            ).toFloat() /
                            pieces

                    val span =
                        endRatio -
                            startRatio

                    val ratios =
                        floatArrayOf(
                            startRatio +
                                span *
                                    .25f,
                            startRatio +
                                span *
                                    .50f,
                            startRatio +
                                span *
                                    .75f,
                            endRatio
                        )

                    val offsets =
                        floatArrayOf(
                            amplitudeUnits,
                            0f,
                            -amplitudeUnits,
                            0f
                        )

                    ratios.indices
                        .forEach {
                                index ->
                            val ratio =
                                ratios[
                                    index
                                ]

                            val offset =
                                offsets[
                                    index
                                ]

                            val x =
                                previous.xUnits +
                                    dx *
                                        ratio +
                                    normalX *
                                        offset

                            val y =
                                previous.yUnits +
                                    dy *
                                        ratio +
                                    normalY *
                                        offset

                            output +=
                                EmbroideryPoint(
                                    xUnits =
                                        x.roundToInt(),
                                    yUnits =
                                        y.roundToInt(),
                                    command =
                                        StitchCommand.STITCH,
                                    colorIndex =
                                        target.colorIndex
                                )
                        }
                }

                previous =
                    target
            }

        return output
    }
}
