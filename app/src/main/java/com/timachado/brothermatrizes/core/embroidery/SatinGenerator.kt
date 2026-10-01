package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SatinUnderlayMode(
    val displayName: String
) {
    NONE("Nenhum"),
    CENTER("Central"),
    ZIGZAG("Zigue-zague"),
    BOTH("Central + zigue-zague")
}

data class SatinBuildResult(
    val currentX: Int,
    val currentY: Int,
    val stitchCount: Int,
    val jumpCount: Int
)

object SatinGenerator {

    private data class Frame(
        val normalX: Double,
        val normalY: Double,
        val turnCross: Double,
        val sharpness: Double
    )

    private data class PassResult(
        val x: Int,
        val y: Int,
        val stitches: Int,
        val jumps: Int
    )

    fun append(
        points: MutableList<EmbroideryPoint>,
        stroke: List<Pair<Int, Int>>,
        currentX: Int,
        currentY: Int,
        widthUnits: Float,
        stepUnits: Float,
        pullCompensationUnits: Float,
        shortStitches: Boolean,
        underlayMode: SatinUnderlayMode
    ): SatinBuildResult {
        require(
            stroke.size >=
                2
        ) {
            "Satin precisa de pelo menos dois pontos."
        }

        /*
         * O motor anterior fazia passes globais no traço inteiro:
         * underlay completo -> zigue-zague completo -> cobertura completa.
         * Na simulação isso parece que a máquina percorre a mesma letra
         * duas ou três vezes.
         *
         * O motor de referência trabalha por colunas locais. Para as fontes
         * internas (que são descritas por linhas centrais), cada segmento
         * geométrico vira uma coluna Satin local e é concluído antes de
         * avançar para o próximo:
         * travel -> underlay -> trava -> Satin -> trava.
         */
        data class LocalRow(
            val ax: Float,
            val ay: Float,
            val bx: Float,
            val by: Float
        )

        val frames =
            buildFrames(
                stroke
            )

        var x =
            currentX
                .toFloat()

        var y =
            currentY
                .toFloat()

        var stitches =
            0

        var jumps =
            0

        var started =
            points.isNotEmpty()

        fun emitJumpTo(
            targetX: Float,
            targetY: Float
        ) {
            if (
                !started
            ) {
                points +=
                    EmbroideryPoint(
                        targetX
                            .roundToInt(),
                        targetY
                            .roundToInt(),
                        StitchCommand.JUMP,
                        0
                    )

                x =
                    targetX

                y =
                    targetY

                jumps++

                started =
                    true

                return
            }

            val dx =
                targetX -
                    x

            val dy =
                targetY -
                    y

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
                    .toFloat()

            if (
                distance <
                    0.5f
            ) {
                x =
                    targetX

                y =
                    targetY

                return
            }

            if (
                distance >
                    50f
            ) {
                points +=
                    EmbroideryPoint(
                        x.roundToInt(),
                        y.roundToInt(),
                        StitchCommand.TRIM,
                        0
                    )
            }

            val segments =
                max(
                    1,
                    ceil(
                        distance /
                            70f
                    ).toInt()
                )

            val startX =
                x

            val startY =
                y

            for (
                part in
                    1..segments
            ) {
                val ratio =
                    part.toFloat() /
                        segments

                points +=
                    EmbroideryPoint(
                        (
                            startX +
                                dx *
                                    ratio
                            ).roundToInt(),
                        (
                            startY +
                                dy *
                                    ratio
                            ).roundToInt(),
                        StitchCommand.JUMP,
                        0
                    )

                jumps++
            }

            x =
                targetX

            y =
                targetY
        }

        fun emitStitchTo(
            targetX: Float,
            targetY: Float
        ) {
            if (
                !started
            ) {
                emitJumpTo(
                    targetX,
                    targetY
                )

                return
            }

            val dx =
                targetX -
                    x

            val dy =
                targetY -
                    y

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
                    .toFloat()

            if (
                distance <
                    0.001f
            ) {
                x =
                    targetX

                y =
                    targetY

                return
            }

            val segments =
                max(
                    1,
                    ceil(
                        distance /
                            70f
                    ).toInt()
                )

            val startX =
                x

            val startY =
                y

            for (
                part in
                    1..segments
            ) {
                val ratio =
                    part.toFloat() /
                        segments

                points +=
                    EmbroideryPoint(
                        (
                            startX +
                                dx *
                                    ratio
                            ).roundToInt(),
                        (
                            startY +
                                dy *
                                    ratio
                            ).roundToInt(),
                        StitchCommand.STITCH,
                        0
                    )

                stitches++
            }

            x =
                targetX

            y =
                targetY
        }

        fun emitLock(
            row: LocalRow
        ) {
            val dx =
                row.bx -
                    row.ax

            val dy =
                row.by -
                    row.ay

            val length =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
                    .toFloat()

            val ux =
                if (
                    length >
                        0.001f
                ) {
                    dx /
                        length
                } else {
                    1f
                }

            val uy =
                if (
                    length >
                        0.001f
                ) {
                    dy /
                        length
                } else {
                    0f
                }

            emitStitchTo(
                row.ax,
                row.ay
            )

            emitStitchTo(
                row.ax +
                    ux *
                        6f,
                row.ay +
                    uy *
                        6f
            )

            emitStitchTo(
                row.ax,
                row.ay
            )
        }

        val safeStep =
            stepUnits
                .coerceAtLeast(
                    0.5f
                )

        val halfWidth =
            widthUnits /
                2f +
                pullCompensationUnits

        for (
            segmentIndex in
                1 until
                    stroke.size
        ) {
            val startPoint =
                stroke[
                    segmentIndex -
                        1
                ]

            val endPoint =
                stroke[
                    segmentIndex
                ]

            val dx =
                endPoint.first -
                    startPoint.first

            val dy =
                endPoint.second -
                    startPoint.second

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
                    .toFloat()

            if (
                distance <
                    0.001f
            ) {
                continue
            }

            val samples =
                max(
                    1,
                    ceil(
                        distance /
                            safeStep
                    ).toInt()
                )

            val startFrame =
                frames[
                    segmentIndex -
                        1
                ]

            val endFrame =
                frames[
                    segmentIndex
                ]

            val rows =
                mutableListOf<
                    LocalRow
                >()

            for (
                part in
                    0..samples
            ) {
                val ratio =
                    part.toFloat() /
                        samples

                val centerX =
                    startPoint.first +
                        dx *
                            ratio

                val centerY =
                    startPoint.second +
                        dy *
                            ratio

                val normal =
                    normalize(
                        x =
                            startFrame.normalX *
                                (1.0 -
                                    ratio) +
                                endFrame.normalX *
                                    ratio,
                        y =
                            startFrame.normalY *
                                (1.0 -
                                    ratio) +
                                endFrame.normalY *
                                    ratio
                    )

                var localHalfWidth =
                    halfWidth

                if (
                    shortStitches
                ) {
                    val startInfluence =
                        startFrame.sharpness *
                            (1.0 -
                                ratio) *
                            (1.0 -
                                ratio)

                    val endInfluence =
                        endFrame.sharpness *
                            ratio *
                            ratio

                    val cornerInfluence =
                        max(
                            startInfluence,
                            endInfluence
                        )

                    localHalfWidth *=
                        (
                            1.0 -
                                0.18 *
                                    cornerInfluence
                            )
                            .coerceIn(
                                0.82,
                                1.0
                            )
                            .toFloat()
                }

                rows +=
                    LocalRow(
                        ax =
                            (
                                centerX +
                                    normal.first *
                                        localHalfWidth
                                ).toFloat(),
                        ay =
                            (
                                centerY +
                                    normal.second *
                                        localHalfWidth
                                ).toFloat(),
                        bx =
                            (
                                centerX -
                                    normal.first *
                                        localHalfWidth
                                ).toFloat(),
                        by =
                            (
                                centerY -
                                    normal.second *
                                        localHalfWidth
                                ).toFloat()
                    )
            }

            if (
                rows.isEmpty()
            ) {
                continue
            }

            val first =
                rows.first()

            emitJumpTo(
                first.ax,
                first.ay
            )

            if (
                rows.size >=
                    4 &&
                (
                    underlayMode ==
                        SatinUnderlayMode.CENTER ||
                    underlayMode ==
                        SatinUnderlayMode.BOTH
                    )
            ) {
                val rowStep =
                    max(
                        1,
                        (
                            20f /
                                safeStep
                            ).roundToInt()
                    )

                val centers =
                    mutableListOf<
                        Pair<Float, Float>
                    >()

                var rowIndex =
                    0

                while (
                    rowIndex <
                        rows.size
                ) {
                    val row =
                        rows[
                            rowIndex
                        ]

                    centers +=
                        Pair(
                            (
                                row.ax +
                                    row.bx
                                ) /
                                2f,
                            (
                                row.ay +
                                    row.by
                                ) /
                                2f
                        )

                    rowIndex +=
                        rowStep
                }

                val last =
                    rows.last()

                val lastCenter =
                    Pair(
                        (
                            last.ax +
                                last.bx
                            ) /
                            2f,
                        (
                            last.ay +
                                last.by
                            ) /
                            2f
                    )

                if (
                    centers.lastOrNull() !=
                        lastCenter
                ) {
                    centers +=
                        lastCenter
                }

                centers.forEach {
                        center ->
                    emitStitchTo(
                        center.first,
                        center.second
                    )
                }

                for (
                    reverseIndex in
                        centers.size -
                            2 downTo
                            0
                ) {
                    val center =
                        centers[
                            reverseIndex
                        ]

                    emitStitchTo(
                        center.first,
                        center.second
                    )
                }
            }

            if (
                rows.size >=
                    4 &&
                (
                    underlayMode ==
                        SatinUnderlayMode.ZIGZAG ||
                    underlayMode ==
                        SatinUnderlayMode.BOTH
                    )
            ) {
                val rowStep =
                    max(
                        1,
                        (
                            20f /
                                safeStep
                            ).roundToInt()
                    )

                var rowIndex =
                    0

                var sideA =
                    true

                while (
                    rowIndex <
                        rows.size
                ) {
                    val row =
                        rows[
                            rowIndex
                        ]

                    val centerX =
                        (
                            row.ax +
                                row.bx
                            ) /
                            2f

                    val centerY =
                        (
                            row.ay +
                                row.by
                            ) /
                            2f

                    val edgeX =
                        if (
                            sideA
                        ) {
                            row.ax
                        } else {
                            row.bx
                        }

                    val edgeY =
                        if (
                            sideA
                        ) {
                            row.ay
                        } else {
                            row.by
                        }

                    emitStitchTo(
                        centerX +
                            (
                                edgeX -
                                    centerX
                                ) *
                                0.60f,
                        centerY +
                            (
                                edgeY -
                                    centerY
                                ) *
                                0.60f
                    )

                    sideA =
                        !sideA

                    rowIndex +=
                        rowStep
                }
            }

            emitLock(
                first
            )

            rows.forEach {
                    row ->
                emitStitchTo(
                    row.ax,
                    row.ay
                )

                emitStitchTo(
                    row.bx,
                    row.by
                )
            }

            emitLock(
                rows.last()
            )
        }

        return SatinBuildResult(
            currentX =
                x.roundToInt(),
            currentY =
                y.roundToInt(),
            stitchCount =
                stitches,
            jumpCount =
                jumps
        )
    }

    private fun appendCenterUnderlay(
        points: MutableList<EmbroideryPoint>,
        stroke: List<Pair<Int, Int>>,
        currentX: Int,
        currentY: Int,
        stepUnits: Float
    ): PassResult {
        var x = currentX
        var y = currentY
        var stitches = 0
        var jumps = 0

        val first =
            stroke.first()

        if (
            points.isEmpty() ||
            x != first.first ||
            y != first.second
        ) {
            points +=
                EmbroideryPoint(
                    first.first,
                    first.second,
                    StitchCommand.JUMP,
                    0
                )

            x = first.first
            y = first.second
            jumps++
        }

        for (
            index in
                1 until stroke.size
        ) {
            val target =
                stroke[index]

            val dx =
                target.first -
                    x

            val dy =
                target.second -
                    y

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )

            val segments =
                max(
                    1,
                    ceil(
                        distance /
                            stepUnits
                    ).toInt()
                )

            val startX = x
            val startY = y

            for (
                part in
                    1..segments
            ) {
                val ratio =
                    part.toDouble() /
                        segments

                val px =
                    (
                        startX +
                            dx * ratio
                        ).roundToInt()

                val py =
                    (
                        startY +
                            dy * ratio
                        ).roundToInt()

                points +=
                    EmbroideryPoint(
                        px,
                        py,
                        StitchCommand.STITCH,
                        0
                    )

                stitches++
            }

            x = target.first
            y = target.second
        }

        return PassResult(
            x = x,
            y = y,
            stitches = stitches,
            jumps = jumps
        )
    }

    private fun appendZigzagPass(
        points: MutableList<EmbroideryPoint>,
        stroke: List<Pair<Int, Int>>,
        frames: List<Frame>,
        currentX: Int,
        currentY: Int,
        halfWidth: Float,
        stepUnits: Float,
        shortStitches: Boolean
    ): PassResult {
        var x = currentX
        var y = currentY
        var stitches = 0
        var jumps = 0
        var parity = 0
        var started = false

        for (
            segmentIndex in
                1 until stroke.size
        ) {
            val start =
                stroke[
                    segmentIndex -
                        1
                ]

            val target =
                stroke[
                    segmentIndex
                ]

            val dx =
                target.first -
                    start.first

            val dy =
                target.second -
                    start.second

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )

            if (
                distance <
                    0.001
            ) {
                continue
            }

            val samples =
                max(
                    1,
                    ceil(
                        distance /
                            stepUnits
                    ).toInt()
                )

            val firstPart =
                if (
                    segmentIndex ==
                        1
                ) {
                    0
                } else {
                    1
                }

            val startFrame =
                frames[
                    segmentIndex -
                        1
                ]

            val endFrame =
                frames[
                    segmentIndex
                ]

            for (
                part in
                    firstPart..samples
            ) {
                val ratio =
                    part.toDouble() /
                        samples

                val centerX =
                    start.first +
                        dx * ratio

                val centerY =
                    start.second +
                        dy * ratio

                val normal =
                    normalize(
                        x =
                            startFrame.normalX *
                                (1.0 - ratio) +
                                endFrame.normalX *
                                ratio,
                        y =
                            startFrame.normalY *
                                (1.0 - ratio) +
                                endFrame.normalY *
                                ratio
                    )

                val side =
                    if (
                        parity %
                            2 ==
                            0
                    ) {
                        1.0
                    } else {
                        -1.0
                    }

                val startInfluence =
                    startFrame.sharpness *
                        (1.0 - ratio) *
                        (1.0 - ratio)

                val endInfluence =
                    endFrame.sharpness *
                        ratio *
                        ratio

                val cornerFrame =
                    if (
                        startInfluence >=
                            endInfluence
                    ) {
                        startFrame
                    } else {
                        endFrame
                    }

                val cornerInfluence =
                    max(
                        startInfluence,
                        endInfluence
                    )

                val innerSide =
                    cornerFrame.turnCross *
                        side >
                        0.0

                val shortFactor =
                    if (
                        shortStitches &&
                        innerSide
                    ) {
                        (
                            1.0 -
                                0.45 *
                                cornerInfluence
                            ).coerceIn(
                                0.55,
                                1.0
                            )
                    } else {
                        1.0
                    }

                val localHalfWidth =
                    halfWidth *
                        shortFactor

                val px =
                    (
                        centerX +
                            normal.first *
                            localHalfWidth *
                            side
                        ).roundToInt()

                val py =
                    (
                        centerY +
                            normal.second *
                            localHalfWidth *
                            side
                        ).roundToInt()

                if (!started) {
                    if (
                        points.isEmpty() ||
                        x != px ||
                        y != py
                    ) {
                        points +=
                            EmbroideryPoint(
                                px,
                                py,
                                StitchCommand.JUMP,
                                0
                            )

                        jumps++
                    }

                    x = px
                    y = py
                    started = true
                } else {
                    points +=
                        EmbroideryPoint(
                            px,
                            py,
                            StitchCommand.STITCH,
                            0
                        )

                    x = px
                    y = py
                    stitches++
                }

                parity++
            }
        }

        return PassResult(
            x = x,
            y = y,
            stitches = stitches,
            jumps = jumps
        )
    }

    private fun buildFrames(
        stroke: List<Pair<Int, Int>>
    ): List<Frame> {
        val frames =
            ArrayList<Frame>(
                stroke.size
            )

        for (
            index in
                stroke.indices
        ) {
            val incoming =
                if (
                    index >
                        0
                ) {
                    unitDirection(
                        stroke[
                            index -
                                1
                        ],
                        stroke[index]
                    )
                } else {
                    unitDirection(
                        stroke[index],
                        stroke[
                            index +
                                1
                        ]
                    )
                }

            val outgoing =
                if (
                    index <
                        stroke.lastIndex
                ) {
                    unitDirection(
                        stroke[index],
                        stroke[
                            index +
                                1
                        ]
                    )
                } else {
                    incoming
                }

            val tangent =
                normalize(
                    incoming.first +
                        outgoing.first,
                    incoming.second +
                        outgoing.second
                ).let {
                    if (
                        it.first ==
                            0.0 &&
                        it.second ==
                            0.0
                    ) {
                        outgoing
                    } else {
                        it
                    }
                }

            val cross =
                if (
                    index >
                        0 &&
                    index <
                        stroke.lastIndex
                ) {
                    incoming.first *
                        outgoing.second -
                        incoming.second *
                        outgoing.first
                } else {
                    0.0
                }

            val dot =
                (
                    incoming.first *
                        outgoing.first +
                        incoming.second *
                        outgoing.second
                    ).coerceIn(
                        -1.0,
                        1.0
                    )

            val sharpness =
                if (
                    index >
                        0 &&
                    index <
                        stroke.lastIndex
                ) {
                    (
                        (1.0 -
                            dot) /
                            2.0
                        ).coerceIn(
                            0.0,
                            1.0
                        )
                } else {
                    0.0
                }

            frames +=
                Frame(
                    normalX =
                        -tangent.second,
                    normalY =
                        tangent.first,
                    turnCross =
                        cross,
                    sharpness =
                        sharpness
                )
        }

        return frames
    }

    private fun unitDirection(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>
    ): Pair<Double, Double> =
        normalize(
            x =
                (
                    to.first -
                        from.first
                    ).toDouble(),
            y =
                (
                    to.second -
                        from.second
                    ).toDouble()
        )

    private fun normalize(
        x: Double,
        y: Double
    ): Pair<Double, Double> {
        val length =
            sqrt(
                x * x +
                    y * y
            )

        return if (
            length <
                0.000001
        ) {
            Pair(
                0.0,
                0.0
            )
        } else {
            Pair(
                x /
                    length,
                y /
                    length
            )
        }
    }
}
