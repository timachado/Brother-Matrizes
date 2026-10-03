package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.StitchCommand

private val simulationPalette =
    listOf(
        Color(0xFFE6BE70),
        Color(0xFFE76F51),
        Color(0xFF2A9D8F),
        Color(0xFF457B9D),
        Color(0xFF9B5DE5),
        Color(0xFFF4A261),
        Color(0xFFF4A7B9),
        Color(0xFF6D597A)
    )

@Composable
fun MachineSimulationCanvas(
    design: EmbroideryDesign,
    pointLimit: Int,
    displayMode:
        EmbroideryDisplayMode =
        EmbroideryDisplayMode.REALISTIC,
    hoop:
        HoopProfile? =
        design.hoopProfile,
    showConnections:
        Boolean =
        false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .background(
                    Color(
                        0xFFF4EDDD
                    ),
                    RoundedCornerShape(
                        24.dp
                    )
                )
    ) {
        Canvas(
            Modifier.fillMaxSize()
        ) {
            val transform =
                SimulationTransform(
                    design =
                        design,
                    canvasWidth =
                        size.width,
                    canvasHeight =
                        size.height,
                    padding =
                        22.dp.toPx(),
                    hoop =
                        hoop
                )

            drawFabricGrid(
                transform
            )

            drawHoop(
                transform =
                    transform
            )

            if (
                design.guidePoints
                    .isNotEmpty()
            ) {
                drawReferenceGuide(
                    design =
                        design,
                    transform =
                        transform
                )
            } else {
                drawStitches(
                    design =
                        design,
                    transform =
                        transform,
                    pointLimit =
                        design.points.size,
                    ghost =
                        true,
                    displayMode =
                        displayMode,
                    showConnections =
                        showConnections
                )
            }

            drawStitches(
                design =
                    design,
                transform =
                    transform,
                pointLimit =
                    pointLimit,
                ghost = false,
                displayMode =
                    displayMode,
                showConnections =
                    showConnections
            )

            val visiblePoints =
                design.points
                    .filter {
                        it.command !=
                            StitchCommand.END
                    }

            val current =
                visiblePoints
                    .getOrNull(
                        (
                            pointLimit -
                                1
                            ).coerceAtLeast(
                                0
                            )
                    )

            if (
                pointLimit > 0 &&
                current != null
            ) {
                drawNeedle(
                    point = current,
                    transform =
                        transform,
                    color =
                        threadColor(
                            design,
                            current
                                .colorIndex
                        )
                )
            }
        }
    }
}

private data class SimulationTransform(
    val design: EmbroideryDesign,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val padding: Float,
    val hoop: HoopProfile?
) {
    private val referencePoints =
        (
            design.points +
                design.guidePoints
            )
            .filter {
                it.command !=
                    StitchCommand.END
            }

    private val minXUnits =
        referencePoints
            .minOfOrNull {
                it.xUnits
            }
            ?: design.bounds
                .minXUnits

    private val maxXUnits =
        referencePoints
            .maxOfOrNull {
                it.xUnits
            }
            ?: design.bounds
                .maxXUnits

    private val minYUnits =
        referencePoints
            .minOfOrNull {
                it.yUnits
            }
            ?: design.bounds
                .minYUnits

    private val maxYUnits =
        referencePoints
            .maxOfOrNull {
                it.yUnits
            }
            ?: design.bounds
                .maxYUnits

    private val widthUnits =
        (
            maxXUnits -
                minXUnits
            ).coerceAtLeast(1)

    private val heightUnits =
        (
            maxYUnits -
                minYUnits
            ).coerceAtLeast(1)

    private val availableWidth =
        (
            canvasWidth -
                padding *
                    2f
            ).coerceAtLeast(
                1f
            )

    private val availableHeight =
        (
            canvasHeight -
                padding *
                    2f
            ).coerceAtLeast(
                1f
            )

    private val designLandscape =
        widthUnits >
            heightUnits

    private val hoopLandscape =
        hoop
            ?.let {
                it.widthMm >
                    it.heightMm
            }
            ?: designLandscape

    private val rotateHoop =
        hoop !=
            null &&
        hoop.widthMm !=
            hoop.heightMm &&
        designLandscape !=
            hoopLandscape

    private val hoopWidthUnits =
        hoop
            ?.let {
                (
                    if (
                        rotateHoop
                    ) {
                        it.heightMm
                    } else {
                        it.widthMm
                    }
                    ) *
                    10f
            }

    private val hoopHeightUnits =
        hoop
            ?.let {
                (
                    if (
                        rotateHoop
                    ) {
                        it.widthMm
                    } else {
                        it.heightMm
                    }
                    ) *
                    10f
            }

    val scale: Float =
        simulationScale(
            availableWidth =
                availableWidth,
            availableHeight =
                availableHeight,
            designWidthUnits =
                widthUnits.toFloat(),
            designHeightUnits =
                heightUnits.toFloat(),
            hoopWidthUnits =
                hoopWidthUnits,
            hoopHeightUnits =
                hoopHeightUnits
        )

    val hoopFrameWidthPx: Float =
        (
            hoopWidthUnits
                ?: widthUnits.toFloat()
            ) *
            scale

    val hoopFrameHeightPx: Float =
        (
            hoopHeightUnits
                ?: heightUnits.toFloat()
            ) *
            scale

    val hoopFrameLeftPx: Float =
        (
            canvasWidth -
                hoopFrameWidthPx
            ) /
            2f

    val hoopFrameTopPx: Float =
        (
            canvasHeight -
                hoopFrameHeightPx
            ) /
            2f

    private val centerXUnits =
        if (
            design.isModified
        ) {
            0f
        } else {
            (
                minXUnits +
                    maxXUnits
                ) /
                2f
        }

    private val centerYUnits =
        if (
            design.isModified
        ) {
            0f
        } else {
            (
                minYUnits +
                    maxYUnits
                ) /
                2f
        }

    private val originX =
        canvasWidth /
            2f -
            centerXUnits *
                scale

    fun point(
        embroideryPoint:
            EmbroideryPoint
    ): Offset =
        Offset(
            x =
                originX +
                    embroideryPoint
                        .xUnits *
                    scale,
            y =
                renderScreenY(
                    centerScreenY =
                        canvasHeight /
                            2f,
                    centerYUnits =
                        centerYUnits,
                    pointYUnits =
                        embroideryPoint
                            .yUnits
                            .toFloat(),
                    scale =
                        scale,
                    sourceYAxisDown =
                        design.sourceYAxisDown
                )
        )

    fun unitsToPx(
        units: Float
    ): Float =
        units *
            scale
}

internal fun simulationScale(
    availableWidth: Float,
    availableHeight: Float,
    designWidthUnits: Float,
    designHeightUnits: Float,
    hoopWidthUnits: Float?,
    hoopHeightUnits: Float?
): Float {
    val safeWidth =
        availableWidth
            .coerceAtLeast(
                1f
            )

    val safeHeight =
        availableHeight
            .coerceAtLeast(
                1f
            )

    val designScale =
        minOf(
            safeWidth /
                designWidthUnits
                    .coerceAtLeast(
                        1f
                    ),
            safeHeight /
                designHeightUnits
                    .coerceAtLeast(
                        1f
                    )
        )

    val hoopScale =
        if (
            hoopWidthUnits !=
                null &&
            hoopHeightUnits !=
                null
        ) {
            minOf(
                safeWidth /
                    hoopWidthUnits
                        .coerceAtLeast(
                            1f
                        ),
                safeHeight /
                    hoopHeightUnits
                        .coerceAtLeast(
                            1f
                        )
            )
        } else {
            null
        }

    return (
        hoopScale
            ?: designScale
        ).coerceAtLeast(
        0.01f
    )
}

private fun DrawScope.drawFabricGrid(
    transform:
        SimulationTransform
) {
    val minor =
        transform
            .unitsToPx(
                25f
            )
            .coerceIn(
                10.dp.toPx(),
                28.dp.toPx()
            )

    var x = 0f
    var index = 0

    while (
        x <= size.width
    ) {
        drawLine(
            color =
                if (
                    index %
                        4 ==
                        0
                ) {
                    Color(
                        0x2A8C877C
                    )
                } else {
                    Color(
                        0x168C877C
                    )
                },
            start =
                Offset(
                    x,
                    0f
                ),
            end =
                Offset(
                    x,
                    size.height
                ),
            strokeWidth =
                if (
                    index %
                        4 ==
                        0
                ) {
                    1.2f
                } else {
                    0.8f
                }
        )

        index++
        x += minor
    }

    var y = 0f
    index = 0

    while (
        y <= size.height
    ) {
        drawLine(
            color =
                if (
                    index %
                        4 ==
                        0
                ) {
                    Color(
                        0x2A8C877C
                    )
                } else {
                    Color(
                        0x168C877C
                    )
                },
            start =
                Offset(
                    0f,
                    y
                ),
            end =
                Offset(
                    size.width,
                    y
                ),
            strokeWidth =
                if (
                    index %
                        4 ==
                        0
                ) {
                    1.2f
                } else {
                    0.8f
                }
        )

        index++
        y += minor
    }

    drawLine(
        color =
            Color(
                0x33958E80
            ),
        start =
            Offset(
                0f,
                size.height /
                    2f
            ),
        end =
            Offset(
                size.width,
                size.height /
                    2f
            ),
        strokeWidth =
            1.2f
    )

    drawLine(
        color =
            Color(
                0x33958E80
            ),
        start =
            Offset(
                size.width /
                    2f,
                0f
            ),
        end =
            Offset(
                size.width /
                    2f,
                size.height
            ),
        strokeWidth =
            1.2f
    )
}

private fun DrawScope.drawHoop(
    transform:
        SimulationTransform
) {
    drawRoundRect(
        color =
            Color(
                0xB346433E
            ),
        topLeft =
            Offset(
                transform
                    .hoopFrameLeftPx,
                transform
                    .hoopFrameTopPx
            ),
        size =
            androidx.compose.ui.geometry.Size(
                width =
                    transform
                        .hoopFrameWidthPx,
                height =
                    transform
                        .hoopFrameHeightPx
            ),
        cornerRadius =
            CornerRadius(
                18.dp.toPx(),
                18.dp.toPx()
            ),
        style =
            Stroke(
                width =
                    1.35.dp.toPx(),
                pathEffect =
                    PathEffect
                        .dashPathEffect(
                            floatArrayOf(
                                8.dp.toPx(),
                                6.dp.toPx()
                            )
                        )
            )
    )
}

private fun DrawScope.drawReferenceGuide(
    design: EmbroideryDesign,
    transform:
        SimulationTransform
) {
    val path =
        Path().apply {
            fillType =
                PathFillType.EvenOdd
        }

    var contourOpen =
        false

    design.guidePoints
        .forEach {
                point ->
            val position =
                transform.point(
                    point
                )

            when (
                point.command
            ) {
                StitchCommand.JUMP -> {
                    if (
                        contourOpen
                    ) {
                        path.close()
                    }

                    path.moveTo(
                        position.x,
                        position.y
                    )

                    contourOpen =
                        true
                }

                StitchCommand.STITCH -> {
                    if (
                        !contourOpen
                    ) {
                        path.moveTo(
                            position.x,
                            position.y
                        )

                        contourOpen =
                            true
                    } else {
                        path.lineTo(
                            position.x,
                            position.y
                        )
                    }
                }

                StitchCommand.TRIM,
                StitchCommand.STOP,
                StitchCommand.COLOR_CHANGE,
                StitchCommand.SEQUIN,
                StitchCommand.END -> {
                    if (
                        contourOpen
                    ) {
                        path.close()

                        contourOpen =
                            false
                    }
                }
            }
        }

    if (
        contourOpen
    ) {
        path.close()
    }

    val guideColor =
        threadColor(
            design,
            design.guidePoints
                .firstOrNull()
                ?.colorIndex
                ?: 0
        )

    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.19f
            )
    )

    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.16f
            ),
        style =
            Stroke(
                width =
                    3.4.dp.toPx()
            )
    )

    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.62f
            ),
        style =
            Stroke(
                width =
                    1.45.dp.toPx()
            )
    )
}

private fun DrawScope.drawStitches(
    design: EmbroideryDesign,
    transform:
        SimulationTransform,
    pointLimit: Int,
    ghost: Boolean,
    displayMode:
        EmbroideryDisplayMode,
    showConnections:
        Boolean
) {
    var previous:
        EmbroideryPoint? =
        null

    val limit =
        pointLimit
            .coerceIn(
                0,
                design.points.size
            )

    for (
        index in
            0 until limit
    ) {
        val point =
            design.points[
                index
            ]

        when (
            point.command
        ) {
            StitchCommand.COLOR_CHANGE,
            StitchCommand.TRIM,
            StitchCommand.STOP,
            StitchCommand.END -> {
                previous =
                    null
            }

            StitchCommand.JUMP -> {
                if (
                    showConnections &&
                    previous !=
                        null
                ) {
                    drawLine(
                        color =
                            Color(
                                0x668C8F94
                            ),
                        start =
                            transform.point(
                                previous
                            ),
                        end =
                            transform.point(
                                point
                            ),
                        strokeWidth =
                            0.9.dp
                                .toPx(),
                        pathEffect =
                            PathEffect
                                .dashPathEffect(
                                    floatArrayOf(
                                        5.dp.toPx(),
                                        4.dp.toPx()
                                    )
                                )
                    )
                }

                previous =
                    point
            }

            StitchCommand.SEQUIN -> {
                val color =
                    threadColor(
                        design,
                        point.colorIndex
                    )

                drawCircle(
                    color =
                        if (ghost) {
                            color.copy(
                                alpha =
                                    0.24f
                            )
                        } else {
                            color
                        },
                    radius =
                        if (ghost) {
                            1.2.dp
                                .toPx()
                        } else {
                            2.4.dp
                                .toPx()
                        },
                    center =
                        transform
                            .point(
                                point
                            )
                )

                previous =
                    point
            }

            StitchCommand.STITCH -> {
                val before =
                    previous

                if (
                    before != null
                ) {
                    val baseColor =
                        threadColor(
                            design,
                            point
                                .colorIndex
                        )

                    val color =
                        if (ghost) {
                            baseColor
                                .copy(
                                    alpha =
                                        0.24f
                                )
                        } else {
                            baseColor
                                .copy(
                                    alpha =
                                        0.98f
                                )
                        }

                    val start =
                        transform
                            .point(
                                before
                            )

                    val end =
                        transform
                            .point(
                                point
                            )

                    if (
                        ghost
                    ) {
                        drawLine(
                            color =
                                color,
                            start =
                                start,
                            end =
                                end,
                            strokeWidth =
                                .42.dp
                                    .toPx(),
                            cap =
                                StrokeCap.Round
                        )
                    } else {
                        when (
                            displayMode
                        ) {
                            EmbroideryDisplayMode.SOLID -> {
                                drawLine(
                                    color =
                                        baseColor,
                                    start =
                                        start,
                                    end =
                                        end,
                                    strokeWidth =
                                        1.35.dp
                                            .toPx(),
                                    cap =
                                        StrokeCap.Round
                                )
                            }

                            EmbroideryDisplayMode.POINTS -> {
                                drawLine(
                                    color =
                                        baseColor.copy(
                                            alpha =
                                                0.28f
                                        ),
                                    start =
                                        start,
                                    end =
                                        end,
                                    strokeWidth =
                                        0.75.dp
                                            .toPx(),
                                    cap =
                                        StrokeCap.Round
                                )

                                drawCircle(
                                    color =
                                        baseColor,
                                    radius =
                                        1.8.dp
                                            .toPx(),
                                    center =
                                        end
                                )
                            }

                            EmbroideryDisplayMode.REALISTIC -> {
                                val vector =
                                    end -
                                        start

                                val length =
                                    kotlin.math.sqrt(
                                        vector.x *
                                            vector.x +
                                            vector.y *
                                                vector.y
                                    ).coerceAtLeast(
                                        0.001f
                                    )

                                val normal =
                                    Offset(
                                        x =
                                            -vector.y /
                                                length,
                                        y =
                                            vector.x /
                                                length
                                    )

                                val shadowOffset =
                                    normal *
                                        .32.dp.toPx()

                                val highlightOffset =
                                    normal *
                                        -.18.dp.toPx()

                                val shadow =
                                    Color(
                                        red =
                                            baseColor.red *
                                                .38f,
                                        green =
                                            baseColor.green *
                                                .38f,
                                        blue =
                                            baseColor.blue *
                                                .38f,
                                        alpha =
                                            .36f
                                    )

                                drawLine(
                                    color =
                                        shadow,
                                    start =
                                        start +
                                            shadowOffset,
                                    end =
                                        end +
                                            shadowOffset,
                                    strokeWidth =
                                        1.35.dp
                                            .toPx(),
                                    cap =
                                        StrokeCap.Round
                                )

                                drawLine(
                                    color =
                                        baseColor.copy(
                                            alpha =
                                                .98f
                                        ),
                                    start =
                                        start,
                                    end =
                                        end,
                                    strokeWidth =
                                        1.00.dp
                                            .toPx(),
                                    cap =
                                        StrokeCap.Round
                                )

                                drawLine(
                                    color =
                                        Color.White
                                            .copy(
                                                alpha =
                                                    .18f
                                            ),
                                    start =
                                        start +
                                            highlightOffset,
                                    end =
                                        end +
                                            highlightOffset,
                                    strokeWidth =
                                        .22.dp
                                            .toPx(),
                                    cap =
                                        StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                previous =
                    point
            }
        }
    }
}

private fun DrawScope.drawNeedle(
    point: EmbroideryPoint,
    transform:
        SimulationTransform,
    color: Color
) {
    val center =
        transform.point(
            point
        )

    drawCircle(
        color =
            Color.White,
        radius =
            5.dp.toPx(),
        center =
            center
    )

    drawCircle(
        color =
            color,
        radius =
            3.6.dp.toPx(),
        center =
            center
    )

    drawCircle(
        color =
            Color.White,
        radius =
            1.2.dp.toPx(),
        center =
            center
    )
}

private fun threadColor(
    design: EmbroideryDesign,
    colorIndex: Int
): Color {
    val raw =
        design.threadColors
            .getOrNull(
                colorIndex
            )

    return if (
        raw != null
    ) {
        Color(
            0xFF000000 or
                raw.toLong()
        )
    } else {
        simulationPalette[
            colorIndex %
                simulationPalette.size
        ]
    }
}
