package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPreviewOptimizer
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.ui.theme.FioGold

private val fallbackPalette = listOf(
    Color(0xFFE6BE70),
    Color(0xFFFF9F9A),
    Color(0xFF7ED6DF),
    Color(0xFFB8E994),
    Color(0xFFD6A2E8),
    Color(0xFFFFD56B),
    Color(0xFF82CCDD),
    Color(0xFFF8C291)
)

@Composable
fun EmbroideryCanvas(
    design: EmbroideryDesign,
    modifier: Modifier = Modifier,
    pointLimit: Int = design.points.size,
    interactive: Boolean = false,
    hoop: HoopProfile? = null,
    displayMode: EmbroideryDisplayMode =
        EmbroideryDisplayMode.REALISTIC,
    showConnections: Boolean = false
) {
    var zoom by remember(
        design.fileName,
        design.isModified
    ) {
        mutableFloatStateOf(1f)
    }

    var offset by remember(
        design.fileName,
        design.isModified
    ) {
        mutableStateOf(Offset.Zero)
    }

    val fullPreview =
        pointLimit >=
            design.points.size

    val optimizePreview =
        interactive &&
            fullPreview &&
            design.points.size >
                EmbroideryPreviewOptimizer
                    .MAX_PREVIEW_COMMANDS

    val renderPoints =
        remember(
            design.points,
            pointLimit,
            optimizePreview
        ) {
            when {
                optimizePreview ->
                    EmbroideryPreviewOptimizer
                        .optimize(
                            design.points
                        )

                fullPreview ->
                    design.points

                else ->
                    design.points
                        .subList(
                            0,
                            pointLimit.coerceIn(
                                0,
                                design.points.size
                            )
                        )
            }
        }

    val gestures = if (interactive) {
        Modifier.pointerInput(
            design.fileName,
            design.isModified
        ) {
            detectTransformGestures {
                    _,
                    pan,
                    change,
                    _ ->
                zoom =
                    (
                        zoom *
                            change
                        ).coerceIn(
                            0.5f,
                            8f
                        )
                offset += pan
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .border(
                1.dp,
                Color(0xFF243340),
                RoundedCornerShape(24.dp)
            )
            .then(gestures)
    ) {
        Canvas(
            Modifier.fillMaxSize()
        ) {
            drawRect(
                color =
                    Color(
                        0xFFF4EDDD
                    )
            )

            drawGrid(
                realistic =
                    true
            )

            if (hoop != null) {
                drawHoopPreview(
                    design = design,
                    hoop = hoop,
                    realistic =
                        true
                )
            }

            drawDesign(
                design = design,
                points = renderPoints,
                lightweight =
                    optimizePreview,
                userScale = zoom,
                userOffset = offset,
                hoop = hoop,
                displayMode =
                    displayMode,
                showConnections =
                    showConnections
            )
        }
    }
}


private fun DrawScope.drawHoopPreview(
    design: EmbroideryDesign,
    hoop: HoopProfile,
    realistic: Boolean
) {
    val padding =
        36.dp.toPx()

    val designLandscape =
        design.bounds.widthMm >
            design.bounds.heightMm

    val hoopLandscape =
        hoop.widthMm >
            hoop.heightMm

    val rotateHoop =
        hoop.widthMm !=
            hoop.heightMm &&
        designLandscape !=
            hoopLandscape

    val hoopWidthUnits =
        (
            if (
                rotateHoop
            ) {
                hoop.heightMm
            } else {
                hoop.widthMm
            }
            ) *
            10f

    val hoopHeightUnits =
        (
            if (
                rotateHoop
            ) {
                hoop.widthMm
            } else {
                hoop.heightMm
            }
            ) *
            10f

    val scale =
        minOf(
            (
                size.width -
                    padding *
                        2f
                ) /
                hoopWidthUnits,
            (
                size.height -
                    padding *
                        2f
                ) /
                hoopHeightUnits
        )

    val width =
        hoopWidthUnits *
            scale

    val height =
        hoopHeightUnits *
            scale

    val left =
        (
            size.width -
                width
            ) /
            2f

    val top =
        (
            size.height -
                height
            ) /
            2f

    drawRoundRect(
        color =
            if (
                realistic
            ) {
                Color(
                    0xD9F0A62B
                )
            } else {
                Color(
                    0xFF6B7780
                )
            },
        topLeft =
            Offset(
                left,
                top
            ),
        size =
            androidx.compose.ui.geometry.Size(
                width,
                height
            ),
        cornerRadius =
            androidx.compose.ui.geometry.CornerRadius(
                20.dp.toPx(),
                20.dp.toPx()
            ),
        style =
            Stroke(
                width =
                    if (
                        realistic
                    ) {
                        1.45.dp.toPx()
                    } else {
                        2.dp.toPx()
                    },
                pathEffect =
                    if (
                        realistic
                    ) {
                        PathEffect
                            .dashPathEffect(
                                floatArrayOf(
                                    9.dp.toPx(),
                                    7.dp.toPx()
                                )
                            )
                    } else {
                        null
                    }
            )
    )

    val safeInset =
        hoop.safeMarginMm *
            10f *
            scale

    drawRoundRect(
        color =
            Color(
                0x886D7C87
            ),
        topLeft =
            Offset(
                left +
                    safeInset,
                top +
                    safeInset
            ),
        size =
            androidx.compose.ui.geometry.Size(
                (
                    width -
                        safeInset *
                            2f
                    ).coerceAtLeast(
                        1f
                    ),
                (
                    height -
                        safeInset *
                            2f
                    ).coerceAtLeast(
                        1f
                    )
            ),
        cornerRadius =
            androidx.compose.ui.geometry.CornerRadius(
                14.dp.toPx(),
                14.dp.toPx()
            ),
        style =
            Stroke(
                width =
                    1.dp.toPx(),
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

private fun DrawScope.drawGrid(
    realistic: Boolean
) {
    val step =
        if (
            realistic
        ) {
            18.dp.toPx()
        } else {
            32.dp.toPx()
        }

    var x = 0f
    var index = 0

    while (x < size.width) {
        drawLine(
            color =
                if (
                    realistic
                ) {
                    if (
                        index % 4 ==
                            0
                    ) {
                        Color(
                            0x288C877C
                        )
                    } else {
                        Color(
                            0x148C877C
                        )
                    }
                } else {
                    Color(
                        0x152F4858
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
                    realistic &&
                    index % 4 ==
                        0
                ) {
                    1.15f
                } else {
                    .75f
                }
        )

        index++
        x += step
    }

    var y = 0f
    index = 0

    while (y < size.height) {
        drawLine(
            color =
                if (
                    realistic
                ) {
                    if (
                        index % 4 ==
                            0
                    ) {
                        Color(
                            0x288C877C
                        )
                    } else {
                        Color(
                            0x148C877C
                        )
                    }
                } else {
                    Color(
                        0x152F4858
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
                    realistic &&
                    index % 4 ==
                        0
                ) {
                    1.15f
                } else {
                    .75f
                }
        )

        index++
        y += step
    }
}

private fun DrawScope.drawDesign(
    design: EmbroideryDesign,
    points: List<EmbroideryPoint>,
    lightweight: Boolean,
    userScale: Float,
    userOffset: Offset,
    hoop: HoopProfile?,
    displayMode:
        EmbroideryDisplayMode,
    showConnections:
        Boolean
) {
    val bounds =
        design.bounds

    val widthUnits =
        (
            bounds.maxXUnits -
                bounds.minXUnits
            ).coerceAtLeast(1)

    val heightUnits =
        (
            bounds.maxYUnits -
                bounds.minYUnits
            ).coerceAtLeast(1)

    val padding =
        36.dp.toPx()

    val designLandscape =
        widthUnits >
            heightUnits

    val hoopLandscape =
        hoop
            ?.let {
                it.widthMm >
                    it.heightMm
            }
            ?: designLandscape

    val rotateHoop =
        hoop !=
            null &&
        hoop.widthMm !=
            hoop.heightMm &&
        designLandscape !=
            hoopLandscape

    val frameWidthUnits =
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
            ?: widthUnits.toFloat()

    val frameHeightUnits =
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
            ?: heightUnits.toFloat()

    val baseScale =
        minOf(
            (
                size.width -
                    padding * 2
                ) /
                frameWidthUnits,
            (
                size.height -
                    padding * 2
                ) /
                frameHeightUnits
        ).coerceAtLeast(
            0.01f
        )

    val scale =
        baseScale *
            userScale

    val preserveHoopPosition =
        hoop !=
            null &&
        design.isModified

    val centerXUnits =
        if (
            preserveHoopPosition
        ) {
            0f
        } else {
            (
                bounds.minXUnits +
                    bounds.maxXUnits
                ) /
                2f
        }

    val centerYUnits =
        if (
            preserveHoopPosition
        ) {
            0f
        } else {
            (
                bounds.minYUnits +
                    bounds.maxYUnits
                ) /
                2f
        }

    val originX =
        size.width /
            2f -
            centerXUnits *
                scale +
            userOffset.x

    var previous: Offset? =
        null

    var colorIndex = 0

    points
        .forEach { point ->
            val current =
                Offset(
                    originX +
                        point.xUnits *
                            scale,
                    renderScreenY(
                        centerScreenY =
                            size.height /
                                2f,
                        centerYUnits =
                            centerYUnits,
                        pointYUnits =
                            point.yUnits
                                .toFloat(),
                        scale =
                            scale,
                        sourceYAxisDown =
                            design.sourceYAxisDown,
                        userOffsetY =
                            userOffset.y
                    )
                )

            when (point.command) {
                StitchCommand.COLOR_CHANGE -> {
                    colorIndex =
                        point.colorIndex
                    previous = current
                }

                StitchCommand.END,
                StitchCommand.TRIM,
                StitchCommand.STOP -> {
                    previous = current
                }

                StitchCommand.STITCH -> {
                    previous?.let {
                            before ->
                        drawStitchByDisplay(
                            start =
                                before,
                            end =
                                current,
                            color =
                                threadColor(
                                    design,
                                    colorIndex
                                ),
                            mode =
                                displayMode,
                            lightweight =
                                lightweight
                        )
                    }

                    if (
                        displayMode ==
                            EmbroideryDisplayMode
                                .POINTS
                    ) {
                        drawCircle(
                            color =
                                threadColor(
                                    design,
                                    colorIndex
                                ),
                            radius =
                                1.8.dp
                                    .toPx(),
                            center =
                                current
                        )
                    }

                    previous =
                        current
                }

                StitchCommand.JUMP -> {
                    if (
                        showConnections
                    ) {
                        previous?.let {
                            drawLine(
                                color =
                                    Color(
                                        0x668C8F94
                                    ),
                                start =
                                    it,
                                end =
                                    current,
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
                    }

                    previous =
                        current
                }

                StitchCommand.SEQUIN -> {
                    drawCircle(
                        color = FioGold,
                        radius =
                            2.2.dp.toPx(),
                        center = current,
                        style =
                            Stroke(
                                1.dp.toPx()
                            )
                    )

                    previous = current
                }
            }
        }
}

private fun DrawScope.drawStitchByDisplay(
    start: Offset,
    end: Offset,
    color: Color,
    mode: EmbroideryDisplayMode,
    lightweight: Boolean
) {
    when (
        mode
    ) {
        EmbroideryDisplayMode.SOLID -> {
            drawLine(
                color =
                    color,
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
                    color.copy(
                        alpha =
                            .28f
                    ),
                start =
                    start,
                end =
                    end,
                strokeWidth =
                    .75.dp
                        .toPx(),
                cap =
                    StrokeCap.Round
            )

            drawCircle(
                color =
                    color,
                radius =
                    1.8.dp
                        .toPx(),
                center =
                    start
            )
        }

        EmbroideryDisplayMode.REALISTIC -> {
            if (
                lightweight
            ) {
                drawLine(
                    color =
                        color,
                    start =
                        start,
                    end =
                        end,
                    strokeWidth =
                        1.25.dp
                            .toPx(),
                    cap =
                        StrokeCap.Round
                )

                return
            }

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
                        color.red *
                            .38f,
                    green =
                        color.green *
                            .38f,
                    blue =
                        color.blue *
                            .38f,
                    alpha =
                        .58f
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
                    1.75.dp
                        .toPx(),
                cap =
                    StrokeCap.Round
            )

            drawLine(
                color =
                    color.copy(
                        alpha =
                            .98f
                    ),
                start =
                    start,
                end =
                    end,
                strokeWidth =
                    1.18.dp
                        .toPx(),
                cap =
                    StrokeCap.Round
            )

            drawLine(
                color =
                    Color.White
                        .copy(
                            alpha =
                                .30f
                        ),
                start =
                    start +
                        highlightOffset,
                end =
                    end +
                        highlightOffset,
                strokeWidth =
                    .28.dp
                        .toPx(),
                cap =
                    StrokeCap.Round
            )
        }
    }
}

private fun threadColor(
    design: EmbroideryDesign,
    index: Int
): Color {
    val raw =
        design.threadColors
            .getOrNull(index)

    return if (raw != null) {
        Color(
            0xFF000000 or
                raw.toLong()
        )
    } else {
        fallbackPalette[
            index %
                fallbackPalette.size
        ]
    }
}
