package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
    val referenceTextSimulation =
        design.isModified &&
        design.fileName
            .startsWith(
                "nome-"
            )

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
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val cachedTransform =
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
                                hoop,
                            referenceTextMode =
                                referenceTextSimulation,
                            uiScale =
                                1.dp.toPx()
                        )

                    val ghostPaths =
                        buildGhostStitchPaths(
                            design =
                                design,
                            transform =
                                cachedTransform
                        )

                    onDrawBehind {
                        drawFabricGrid(
                            cachedTransform
                        )

                        drawHoop(
                            transform =
                                cachedTransform
                        )

                        /*
                         * Referência do vídeo: toda a matriz permanece visível
                         * em rosa claro e a parte executada substitui esse
                         * fantasma por vermelho forte.
                         *
                         * Os caminhos são montados no cache e só são
                         * recalculados quando design/tamanho mudam. O avanço
                         * da agulha não percorre novamente todos os pontos.
                         */
                        ghostPaths.forEach {
                                item ->
                            drawPath(
                                path =
                                    item.path,
                                color =
                                    threadColor(
                                        design,
                                        item.colorIndex
                                    ).copy(
                                        alpha =
                                            if (
                                                referenceTextSimulation
                                            ) {
                                                38f /
                                                    255f
                                            } else {
                                                0.18f
                                            }
                                    ),
                                style =
                                    Stroke(
                                        width =
                                            if (
                                                referenceTextSimulation
                                            ) {
                                                cachedTransform
                                                    .referenceGhostStrokeWidthPx
                                            } else {
                                                1.05.dp
                                                    .toPx()
                                            },
                                        cap =
                                            if (
                                                referenceTextSimulation
                                            ) {
                                                StrokeCap.Butt
                                            } else {
                                                StrokeCap.Round
                                            }
                                    )
                            )
                        }
                    }
                }
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
                        hoop,
                    referenceTextMode =
                        referenceTextSimulation,
                    uiScale =
                        1.dp.toPx()
                )

            if (
                referenceTextSimulation
            ) {
                drawReferenceCompletedStitches(
                    design =
                        design,
                    transform =
                        transform,
                    pointLimit =
                        pointLimit,
                    showConnections =
                        showConnections
                )
            } else {
                drawStitches(
                    design =
                        design,
                    transform =
                        transform,
                    pointLimit =
                        pointLimit,
                    ghost =
                        false,
                    displayMode =
                        displayMode,
                    showConnections =
                        showConnections
                )
            }

            /*
             * MãoDesign usa CurrentStitchIndex como posição da agulha,
             * enquanto as linhas já executadas usam os comandos anteriores.
             * Assim o marcador aponta para o próximo comando a executar,
             * não fica um ponto atrasado.
             */
            val current =
                if (
                    pointLimit >
                        0 &&
                    design.points
                        .isNotEmpty()
                ) {
                    design.points
                        .getOrNull(
                            minOf(
                                pointLimit,
                                design.points
                                    .lastIndex
                            )
                        )
                        ?.takeIf {
                            it.command !=
                                StitchCommand.END
                        }
                } else {
                    null
                }

            if (
                current !=
                    null
            ) {
                if (
                    referenceTextSimulation
                ) {
                    drawReferenceNeedle(
                        point =
                            current,
                        transform =
                            transform,
                        color =
                            threadColor(
                                design,
                                current
                                    .colorIndex
                            )
                    )
                } else {
                    drawNeedle(
                        point =
                            current,
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
}

private data class GhostStitchPath(
    val colorIndex: Int,
    val path: Path
)

private fun buildGhostStitchPaths(
    design: EmbroideryDesign,
    transform: SimulationTransform
): List<GhostStitchPath> {
    val paths =
        linkedMapOf<
            Int,
            Path
        >()

    var previous:
        EmbroideryPoint? =
        null

    design.points
        .forEach {
                point ->
            when (
                point.command
            ) {
                StitchCommand.COLOR_CHANGE,
                StitchCommand.JUMP,
                StitchCommand.TRIM,
                StitchCommand.END -> {
                    previous =
                        null
                }

                StitchCommand.STOP,
                StitchCommand.SEQUIN -> {
                    previous =
                        point
                }

                StitchCommand.STITCH -> {
                    val before =
                        previous

                    if (
                        before !=
                            null
                    ) {
                        val start =
                            transform.point(
                                before
                            )

                        val end =
                            transform.point(
                                point
                            )

                        paths
                            .getOrPut(
                                point.colorIndex
                            ) {
                                Path()
                            }
                            .apply {
                                moveTo(
                                    start.x,
                                    start.y
                                )

                                lineTo(
                                    end.x,
                                    end.y
                                )
                            }
                    }

                    previous =
                        point
                }
            }
        }

    return paths.map {
            entry ->
        GhostStitchPath(
            colorIndex =
                entry.key,
            path =
                entry.value
        )
    }
}

private data class SimulationTransform(
    val design: EmbroideryDesign,
    val canvasWidth: Float,
    val canvasHeight: Float,
    val padding: Float,
    val hoop: HoopProfile?,
    val referenceTextMode: Boolean =
        false,
    val uiScale: Float =
        1f
) {
    private val referencePoints =
        (
            if (
                referenceTextMode
            ) {
                /*
                 * O simulador do MãoDesign enquadra o bloco de pontos gerado.
                 * guidePoints é apenas geometria auxiliar e não participa do
                 * bounds visual da simulação.
                 */
                design.points
            } else {
                design.points +
                    design.guidePoints
            }
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

    /*
     * No modo de referência a linha tracejada representa a zona segura real,
     * não o tamanho visual arbitrário do canvas. A orientação acompanha a
     * mesma rotação física aplicada ao bastidor.
     */
    private val hoopSafeWidthUnits =
        hoop
            ?.let {
                (
                    if (
                        rotateHoop
                    ) {
                        it.usableHeightMm
                    } else {
                        it.usableWidthMm
                    }
                    ) *
                    10f
            }

    private val hoopSafeHeightUnits =
        hoop
            ?.let {
                (
                    if (
                        rotateHoop
                    ) {
                        it.usableWidthMm
                    } else {
                        it.usableHeightMm
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
                if (
                    referenceTextMode
                ) {
                    hoopSafeWidthUnits
                } else {
                    hoopWidthUnits
                },
            hoopHeightUnits =
                if (
                    referenceTextMode
                ) {
                    hoopSafeHeightUnits
                } else {
                    hoopHeightUnits
                }
        )

    val referenceGhostStrokeWidthPx: Float
        get() {
            if (
                !referenceTextMode
            ) {
                return 1.05f *
                    uiScale
            }

            /*
             * O MãoDesign pré-renderiza o ghost em 1080×1080 com stroke 1.5
             * e depois recorta/escala o bitmap para o designRect.
             */
            val ghostScale =
                minOf(
                    1080f /
                        widthUnits
                            .toFloat()
                            .coerceAtLeast(
                                1f
                            ),
                    1080f /
                        heightUnits
                            .toFloat()
                            .coerceAtLeast(
                                1f
                            )
                )

            return (
                1.5f *
                    scale /
                    ghostScale
                        .coerceAtLeast(
                            0.0001f
                        )
                ).coerceAtLeast(
                    0.35f *
                        uiScale
                )
        }

    val hoopFrameWidthPx: Float =
        (
            if (
                referenceTextMode
            ) {
                hoopSafeWidthUnits
            } else {
                hoopWidthUnits
            }
                ?: widthUnits.toFloat()
            ) *
            scale

    val hoopFrameHeightPx: Float =
        (
            if (
                referenceTextMode
            ) {
                hoopSafeHeightUnits
            } else {
                hoopHeightUnits
            }
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
            referenceTextMode
        ) {
            (
                minXUnits +
                    maxXUnits
                ) /
                2f
        } else if (
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
            referenceTextMode
        ) {
            (
                minYUnits +
                    maxYUnits
                ) /
                2f
        } else if (
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

    private val centerScreenX =
        canvasWidth /
            2f

    private val centerScreenY =
        canvasHeight /
            2f

    private val originX =
        centerScreenX -
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
                        centerScreenY,
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
            if (
                transform.referenceTextMode
            ) {
                Color(
                    red =
                        58f /
                            255f,
                    green =
                        60f /
                            255f,
                    blue =
                        82f /
                            255f,
                    alpha =
                        1f
                )
            } else {
                Color(
                    0xB346433E
                )
            },
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
                if (
                    transform.referenceTextMode
                ) {
                    24f *
                        transform.uiScale
                } else {
                    18.dp.toPx()
                },
                if (
                    transform.referenceTextMode
                ) {
                    24f *
                        transform.uiScale
                } else {
                    18.dp.toPx()
                }
            ),
        style =
            Stroke(
                width =
                    if (
                        transform.referenceTextMode
                    ) {
                        1.5f *
                            transform.uiScale
                    } else {
                        1.35.dp.toPx()
                    },
                pathEffect =
                    PathEffect
                        .dashPathEffect(
                            if (
                                transform.referenceTextMode
                            ) {
                                floatArrayOf(
                                    5f *
                                        transform.uiScale,
                                    4f *
                                        transform.uiScale
                                )
                            } else {
                                floatArrayOf(
                                    8.dp.toPx(),
                                    6.dp.toPx()
                                )
                            }
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

    /*
     * O guia é apenas referência de forma. Nunca deve ser preenchido:
     * contornos cursivos podem se cruzar e um fill EvenOdd cria manchas que
     * parecem pontos Satin quebrados antes mesmo da simulação começar.
     */
    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.18f
            ),
        style =
            Stroke(
                width =
                    1.8.dp.toPx()
            )
    )

    drawPath(
        path =
            path,
        color =
            guideColor.copy(
                alpha =
                    0.42f
            ),
        style =
            Stroke(
                width =
                    0.75.dp.toPx()
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
    if (
        !ghost &&
        displayMode ==
            EmbroideryDisplayMode.SOLID &&
        !showConnections
    ) {
        drawSolidStitchPaths(
            design =
                design,
            transform =
                transform,
            pointLimit =
                pointLimit
        )

        return
    }

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

private fun DrawScope.drawReferenceCompletedStitches(
    design: EmbroideryDesign,
    transform: SimulationTransform,
    pointLimit: Int,
    showConnections: Boolean
) {
    val paths =
        linkedMapOf<
            Int,
            Path
        >()

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
            0 until
                limit
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
                                red =
                                    180f /
                                        255f,
                                green =
                                    180f /
                                        255f,
                                blue =
                                    180f /
                                        255f,
                                alpha =
                                    110f /
                                        255f
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
                            1f,
                        pathEffect =
                            PathEffect
                                .dashPathEffect(
                                    floatArrayOf(
                                        4f,
                                        4f
                                    )
                                )
                    )
                }

                /*
                 * No MãoDesign o JUMP interrompe o traço Satin visível.
                 * O primeiro STITCH após o salto inicia um novo segmento.
                 */
                previous =
                    null
            }

            StitchCommand.STOP,
            StitchCommand.SEQUIN -> {
                previous =
                    point
            }

            StitchCommand.STITCH -> {
                val before =
                    previous

                if (
                    before !=
                        null
                ) {
                    val start =
                        transform.point(
                            before
                        )

                    val end =
                        transform.point(
                            point
                        )

                    paths
                        .getOrPut(
                            point.colorIndex
                        ) {
                            Path()
                        }
                        .apply {
                            moveTo(
                                start.x,
                                start.y
                            )

                            lineTo(
                                end.x,
                                end.y
                            )
                        }
                }

                previous =
                    point
            }
        }
    }

    paths.forEach {
            entry ->
        drawPath(
            path =
                entry.value,
            color =
                threadColor(
                    design,
                    entry.key
                ),
            style =
                Stroke(
                    width =
                        2f,
                    cap =
                        StrokeCap.Butt
                )
        )
    }
}

private fun DrawScope.drawReferenceNeedle(
    point: EmbroideryPoint,
    transform: SimulationTransform,
    color: Color
) {
    val center =
        transform.point(
            point
        )

    val crosshair =
        Color(
            red =
                58f /
                    255f,
            green =
                60f /
                    255f,
            blue =
                82f /
                    255f,
            alpha =
                145f /
                    255f
        )

    drawLine(
        color =
            crosshair,
        start =
            Offset(
                transform
                    .referenceFrameMargin,
                center.y
            ),
        end =
            Offset(
                size.width -
                    transform
                        .referenceFrameMargin,
                center.y
            ),
        strokeWidth =
            transform.uiScale
    )

    drawLine(
        color =
            crosshair,
        start =
            Offset(
                center.x,
                center.y -
                    16f *
                        transform.uiScale
            ),
        end =
            Offset(
                center.x,
                center.y +
                    5f *
                        transform.uiScale
            ),
        strokeWidth =
            transform.uiScale
    )

    drawCircle(
        color =
            Color(
                red =
                    233f /
                        255f,
                green =
                    225f /
                        255f,
                blue =
                    208f /
                        255f,
                alpha =
                    235f /
                        255f
            ),
        radius =
            5.5f *
                transform.uiScale,
        center =
            center
    )

    drawCircle(
        color =
            color.copy(
                alpha =
                    245f /
                        255f
            ),
        radius =
            3.5f *
                transform.uiScale,
        center =
            center
    )
}

private fun DrawScope.drawSolidStitchPaths(
    design: EmbroideryDesign,
    transform: SimulationTransform,
    pointLimit: Int
) {
    val paths =
        linkedMapOf<
            Int,
            Path
        >()

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
            0 until
                limit
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

            StitchCommand.JUMP,
            StitchCommand.SEQUIN -> {
                previous =
                    point
            }

            StitchCommand.STITCH -> {
                val before =
                    previous

                if (
                    before !=
                        null
                ) {
                    val start =
                        transform.point(
                            before
                        )

                    val end =
                        transform.point(
                            point
                        )

                    paths
                        .getOrPut(
                            point.colorIndex
                        ) {
                            Path()
                        }
                        .apply {
                            moveTo(
                                start.x,
                                start.y
                            )

                            lineTo(
                                end.x,
                                end.y
                            )
                        }
                }

                previous =
                    point
            }
        }
    }

    paths.forEach {
            entry ->
        drawPath(
            path =
                entry.value,
            color =
                threadColor(
                    design,
                    entry.key
                ).copy(
                    alpha =
                        0.98f
                ),
            style =
                Stroke(
                    width =
                        1.35.dp.toPx(),
                    cap =
                        StrokeCap.Round
                )
        )
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
