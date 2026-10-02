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

        data class Sample(
            val centerX: Double,
            val centerY: Double,
            val normalX: Double,
            val normalY: Double,
            val turnCross: Double,
            val cornerInfluence: Double
        )

        val frames =
            buildFrames(
                stroke
            )

        val safeStep =
            stepUnits
                .coerceAtLeast(
                    0.5f
                )

        val baseHalfWidth =
            widthUnits /
                2f +
                pullCompensationUnits

        val samples =
            mutableListOf<
                Sample
            >()

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

            if (
                distance <
                    0.001
            ) {
                continue
            }

            val count =
                max(
                    1,
                    ceil(
                        distance /
                            safeStep
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
                    firstPart..count
            ) {
                val ratio =
                    part.toDouble() /
                        count

                val normal =
                    normalize(
                        x =
                            startFrame.normalX *
                                (
                                    1.0 -
                                        ratio
                                    ) +
                                endFrame.normalX *
                                    ratio,
                        y =
                            startFrame.normalY *
                                (
                                    1.0 -
                                        ratio
                                    ) +
                                endFrame.normalY *
                                    ratio
                    )

                val startInfluence =
                    startFrame.sharpness *
                        (
                            1.0 -
                                ratio
                            ) *
                        (
                            1.0 -
                                ratio
                            )

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

                samples +=
                    Sample(
                        centerX =
                            startPoint.first +
                                dx *
                                    ratio,
                        centerY =
                            startPoint.second +
                                dy *
                                    ratio,
                        normalX =
                            normal.first,
                        normalY =
                            normal.second,
                        turnCross =
                            cornerFrame
                                .turnCross,
                        cornerInfluence =
                            max(
                                startInfluence,
                                endInfluence
                            )
                    )
            }
        }

        if (
            samples.isEmpty()
        ) {
            return SatinBuildResult(
                currentX =
                    currentX,
                currentY =
                    currentY,
                stitchCount =
                    0,
                jumpCount =
                    0
            )
        }

        var x =
            currentX

        var y =
            currentY

        var stitches =
            0

        var jumps =
            0

        fun emitStitch(
            targetX: Int,
            targetY: Int
        ) {
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

            if (
                distance <
                    0.001
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
                            70.0
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
                    part.toDouble() /
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

        fun targetFor(
            sample: Sample,
            side: Double,
            scale: Double =
                1.0
        ): Pair<Int, Int> {
            val inner =
                sample.turnCross *
                    side >
                    0.0

            val shortFactor =
                if (
                    shortStitches &&
                    inner
                ) {
                    (
                        1.0 -
                            0.45 *
                                sample.cornerInfluence
                        ).coerceIn(
                        0.55,
                        1.0
                    )
                } else {
                    1.0
                }

            val half =
                baseHalfWidth *
                    shortFactor *
                    scale

            return Pair(
                (
                    sample.centerX +
                        sample.normalX *
                            half *
                            side
                    ).roundToInt(),
                (
                    sample.centerY +
                        sample.normalY *
                            half *
                            side
                    ).roundToInt()
            )
        }

        val first =
            samples.first()

        val firstA =
            targetFor(
                first,
                1.0
            )

        val firstB =
            targetFor(
                first,
                -1.0
            )

        val distanceA =
            hypot(
                (
                    firstA.first -
                        x
                    ).toDouble(),
                (
                    firstA.second -
                        y
                    ).toDouble()
            )

        val distanceB =
            hypot(
                (
                    firstB.first -
                        x
                    ).toDouble(),
                (
                    firstB.second -
                        y
                    ).toDouble()
            )

        var nextSide =
            if (
                distanceA <=
                    distanceB
            ) {
                1.0
            } else {
                -1.0
            }

        val entry =
            if (
                nextSide >
                    0.0
            ) {
                firstA
            } else {
                firstB
            }

        val travel =
            appendReferenceTravel(
                points =
                    points,
                currentX =
                    x,
                currentY =
                    y,
                targetX =
                    entry.first,
                targetY =
                    entry.second
            )

        x =
            travel.x

        y =
            travel.y

        jumps +=
            travel.jumps

        val underlayStep =
            max(
                1,
                (
                    20f /
                        safeStep
                    ).roundToInt()
            )

        var underlaySide =
            1.0

        samples.forEachIndexed {
                index,
                sample ->
            if (
                index %
                    underlayStep ==
                    0 ||
                index ==
                    samples.lastIndex
            ) {
                when (
                    underlayMode
                ) {
                    SatinUnderlayMode.NONE ->
                        Unit

                    SatinUnderlayMode.CENTER ->
                        emitStitch(
                            sample.centerX
                                .roundToInt(),
                            sample.centerY
                                .roundToInt()
                        )

                    SatinUnderlayMode.ZIGZAG -> {
                        val underlay =
                            targetFor(
                                sample,
                                underlaySide,
                                0.60
                            )

                        emitStitch(
                            underlay.first,
                            underlay.second
                        )

                        underlaySide *=
                            -1.0
                    }

                    SatinUnderlayMode.BOTH -> {
                        emitStitch(
                            sample.centerX
                                .roundToInt(),
                            sample.centerY
                                .roundToInt()
                        )

                        val underlay =
                            targetFor(
                                sample,
                                underlaySide,
                                0.60
                            )

                        emitStitch(
                            underlay.first,
                            underlay.second
                        )

                        underlaySide *=
                            -1.0
                    }
                }
            }

            val edge =
                targetFor(
                    sample,
                    nextSide
                )

            emitStitch(
                edge.first,
                edge.second
            )

            nextSide *=
                -1.0
        }

        return SatinBuildResult(
            currentX =
                x,
            currentY =
                y,
            stitchCount =
                stitches,
            jumpCount =
                jumps
        )
    }

    private fun appendReferenceTravel(
        points: MutableList<EmbroideryPoint>,
        currentX: Int,
        currentY: Int,
        targetX: Int,
        targetY: Int
    ): PassResult {
        var x =
            currentX

        var y =
            currentY

        var jumps =
            0

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

        if (
            points.isEmpty()
        ) {
            points +=
                EmbroideryPoint(
                    targetX,
                    targetY,
                    StitchCommand.JUMP,
                    0
                )

            return PassResult(
                x = targetX,
                y = targetY,
                stitches = 0,
                jumps = 1
            )
        }

        if (
            distance <
                0.5
        ) {
            return PassResult(
                x = targetX,
                y = targetY,
                stitches = 0,
                jumps = 0
            )
        }

        if (
            distance >
                50.0
        ) {
            points +=
                EmbroideryPoint(
                    x,
                    y,
                    StitchCommand.TRIM,
                    0
                )
        }

        val segments =
            max(
                1,
                ceil(
                    distance /
                        70.0
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
                part.toDouble() /
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

        return PassResult(
            x = x,
            y = y,
            stitches = 0,
            jumps = jumps
        )
    }

    private fun appendReferenceLock(
        points: MutableList<EmbroideryPoint>,
        currentX: Int,
        currentY: Int,
        a: Pair<Int, Int>,
        b: Pair<Int, Int>
    ): PassResult {
        var x =
            currentX

        var y =
            currentY

        var stitches =
            0

        var jumps =
            0

        fun stitchTo(
            targetX: Int,
            targetY: Int
        ) {
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

            if (
                distance <
                    0.001
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
                            70.0
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
                    part.toDouble() /
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

        stitchTo(
            a.first,
            a.second
        )

        val dx =
            b.first -
                a.first

        val dy =
            b.second -
                a.second

        val length =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )

        val ux =
            if (
                length >
                    0.001
            ) {
                dx /
                    length
            } else {
                1.0
            }

        val uy =
            if (
                length >
                    0.001
            ) {
                dy /
                    length
            } else {
                0.0
            }

        val lockX =
            (
                a.first +
                    ux *
                        6.0
                ).roundToInt()

        val lockY =
            (
                a.second +
                    uy *
                        6.0
                ).roundToInt()

        stitchTo(
            lockX,
            lockY
        )

        stitchTo(
            a.first,
            a.second
        )

        return PassResult(
            x = x,
            y = y,
            stitches = stitches,
            jumps = jumps
        )
    }

    private fun appendCenterUnderlay(
        points: MutableList<EmbroideryPoint>,
        stroke: List<Pair<Int, Int>>,
        currentX: Int,
        currentY: Int,
        stepUnits: Float,
        connectFirstWithStitch: Boolean =
            false
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
            val command =
                if (
                    connectFirstWithStitch &&
                    points.isNotEmpty()
                ) {
                    StitchCommand.STITCH
                } else {
                    StitchCommand.JUMP
                }

            points +=
                EmbroideryPoint(
                    first.first,
                    first.second,
                    command,
                    0
                )

            x =
                first.first

            y =
                first.second

            if (
                command ==
                    StitchCommand.STITCH
            ) {
                stitches++
            } else {
                jumps++
            }
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
        shortStitches: Boolean,
        connectFirstWithStitch: Boolean =
            false
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
                        val command =
                            if (
                                connectFirstWithStitch &&
                                points.isNotEmpty()
                            ) {
                                StitchCommand.STITCH
                            } else {
                                StitchCommand.JUMP
                            }

                        points +=
                            EmbroideryPoint(
                                px,
                                py,
                                command,
                                0
                            )

                        if (
                            command ==
                                StitchCommand.STITCH
                        ) {
                            stitches++
                        } else {
                            jumps++
                        }
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
