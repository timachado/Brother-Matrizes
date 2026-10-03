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
        require(stroke.size >= 2) {
            "Satin precisa de pelo menos dois pontos."
        }

        val frames =
            buildFrames(stroke)

        var x = currentX
        var y = currentY
        var stitches = 0
        var jumps = 0

        if (
            underlayMode ==
                SatinUnderlayMode.CENTER ||
            underlayMode ==
                SatinUnderlayMode.BOTH
        ) {
            val center =
                appendCenterUnderlay(
                    points = points,
                    stroke = stroke,
                    currentX = x,
                    currentY = y,
                    stepUnits =
                        max(
                            20f,
                            stepUnits *
                                4f
                        )
                )

            x = center.x
            y = center.y
            stitches +=
                center.stitches
            jumps +=
                center.jumps
        }

        if (
            underlayMode ==
                SatinUnderlayMode.ZIGZAG ||
            underlayMode ==
                SatinUnderlayMode.BOTH
        ) {
            val edge =
                appendZigzagPass(
                    points = points,
                    stroke = stroke,
                    frames = frames,
                    currentX = x,
                    currentY = y,
                    halfWidth =
                        widthUnits *
                            0.30f,
                    stepUnits =
                        max(
                            18f,
                            stepUnits *
                                3f
                        ),
                    shortStitches =
                        false
                )

            x = edge.x
            y = edge.y
            stitches +=
                edge.stitches
            jumps +=
                edge.jumps
        }

        val top =
            appendZigzagPass(
                points = points,
                stroke = stroke,
                frames = frames,
                currentX = x,
                currentY = y,
                halfWidth =
                    widthUnits /
                        2f +
                        pullCompensationUnits,
                stepUnits =
                    stepUnits,
                shortStitches =
                    shortStitches
            )

        return SatinBuildResult(
            currentX =
                top.x,
            currentY =
                top.y,
            stitchCount =
                stitches +
                    top.stitches,
            jumpCount =
                jumps +
                    top.jumps
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
