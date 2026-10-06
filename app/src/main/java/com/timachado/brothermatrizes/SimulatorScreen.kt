package com.timachado.brothermatrizes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.SimulationTiming
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SimulatorScreen(
    design: EmbroideryDesign,
    displayMode:
        EmbroideryDisplayMode =
        EmbroideryDisplayMode.REALISTIC,
    referenceHoop:
        HoopProfile =
        design.hoopProfile
            ?: HoopProfile.H100X100,
    showConnections:
        Boolean =
        false,
    onBack: () -> Unit
) {
    val points =
        remember(design) {
            design.points
                .filter {
                    it.command !=
                        StitchCommand.END
                }
        }

    val stitchPrefix =
        remember(points) {
            IntArray(
                points.size +
                    1
            ).also {
                    prefix ->
                points.forEachIndexed {
                        pointIndex,
                        point ->
                    prefix[
                        pointIndex +
                            1
                    ] =
                        prefix[
                            pointIndex
                        ] +
                            if (
                                point.command ==
                                    StitchCommand.STITCH
                            ) {
                                1
                            } else {
                                0
                            }
                }
            }
        }

    var index by remember(
        design.fileName
    ) {
        mutableIntStateOf(0)
    }

    var playing by remember(
        design.fileName
    ) {
        mutableStateOf(false)
    }

    var speed by remember(
        design.fileName
    ) {
        mutableFloatStateOf(1f)
    }

    var stoppedForStop by remember(
        design.fileName
    ) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        playing,
        speed,
        design.fileName
    ) {
        while (
            playing &&
            index <
                points.size
        ) {
            val command =
                points[index]
                    .command

            delay(
                SimulationTiming
                    .eventDelayMs(
                        command,
                        speed
                    )
            )

            index =
                (
                    index +
                        1
                    ).coerceAtMost(
                        points.size
                    )

            if (
                command ==
                    StitchCommand.STOP
            ) {
                /*
                 * A referência pausa apenas em STOP.
                 * COLOR_CHANGE troca o fio e continua a simulação.
                 */
                stoppedForStop =
                    true
                playing =
                    false
                break
            }
        }

        if (
            index >=
                points.size
        ) {
            playing =
                false
        }
    }

    val completedStitches =
        stitchPrefix[
            index.coerceIn(
                0,
                points.size
            )
        ]

    val progress =
        if (
            design.stitchCount <=
                0
        ) {
            0f
        } else {
            (
                completedStitches.toFloat() /
                    design.stitchCount
                ).coerceIn(
                0f,
                1f
            )
        }

    val currentPoint =
        points.getOrNull(
            (
                index -
                    1
                ).coerceAtLeast(
                    0
                )
        )

    val block =
        (
            currentPoint
                ?.colorIndex
                ?: 0
            ) +
            1

    val remainingStitches =
        (
            design.stitchCount -
                completedStitches
            ).coerceAtLeast(
                0
            )

    val remainingSeconds =
        SimulationTiming
            .estimatedSeconds(
                stitches =
                    remainingStitches,
                speedMultiplier =
                    speed
            )

    val currentThreadColor =
        remember(
            design.threadColors,
            block
        ) {
            val raw =
                design.threadColors
                    .getOrNull(
                        (
                            block -
                                1
                            ).coerceAtLeast(
                                0
                            )
                    )
                    ?: 0xE6BE70

            Color(
                0xFF000000 or
                    raw.toLong()
            )
        }

    Column(
        Modifier.fillMaxSize()
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            8.dp,
                        vertical =
                            5.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick =
                    onBack
            ) {
                Text(
                    "‹",
                    color =
                        FioText,
                    fontSize =
                        24.sp
                )
            }

            Text(
                "Simulação",
                modifier =
                    Modifier.weight(
                        1f
                    ),
                color =
                    FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    17.sp
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    )
            ) {
                listOf(
                    1f,
                    2f,
                    4f
                ).forEach {
                        option ->
                    SpeedChip(
                        speed =
                            option,
                        selected =
                            speed ==
                                option,
                        onClick = {
                            speed =
                                option
                        }
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal =
                            6.dp
                    )
        ) {
            MachineSimulationCanvas(
                design =
                    design,
                pointLimit =
                    index,
                displayMode =
                    displayMode,
                hoop =
                    referenceHoop,
                showConnections =
                    showConnections,
                modifier =
                    Modifier.fillMaxSize()
            )

            OverlayChip(
                text =
                    (
                        progress *
                            100f
                        ).toInt()
                        .toString() +
                        "%",
                modifier =
                    Modifier
                        .align(
                            Alignment.TopStart
                        )
                        .padding(
                            8.dp
                        )
            )

            OverlayChip(
                text =
                    mm(
                        design.bounds
                            .widthMm
                    ) +
                        " × " +
                        mm(
                            design.bounds
                                .heightMm
                        ) +
                        " mm",
                modifier =
                    Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(
                            8.dp
                        )
            )
        }

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            8.dp,
                        vertical =
                            7.dp
                    ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        FioSurface
                ),
            shape =
                RoundedCornerShape(
                    18.dp
                )
        ) {
            Column(
                Modifier.padding(
                    horizontal =
                        12.dp,
                    vertical =
                        10.dp
                )
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(
                                22.dp
                            )
                            .background(
                                currentThreadColor,
                                CircleShape
                            )
                    )

                    Column(
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .padding(
                                    start =
                                        8.dp
                                )
                    ) {
                        Text(
                            "Linha " +
                                block,
                            color =
                                FioText,
                            fontWeight =
                                FontWeight.SemiBold,
                            fontSize =
                                12.sp
                        )

                        Text(
                            "Fio " +
                                block +
                                "/" +
                                design.colorCount,
                            color =
                                FioTextMuted,
                            fontSize =
                                9.sp
                        )
                    }

                    Text(
                        completedStitches
                            .toString() +
                            "/" +
                            design.stitchCount +
                            " pts",
                        color =
                            FioTextMuted,
                        fontSize =
                            10.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Text(
                        (
                            SimulationTiming
                                .BASE_STITCHES_PER_MINUTE *
                                speed
                            ).toInt()
                            .toString() +
                            " pts/min",
                        color =
                            FioTextMuted,
                        fontSize =
                            9.sp
                    )

                    Text(
                        if (
                            remainingStitches >
                                0
                        ) {
                            "Restante: " +
                                formatTime(
                                    remainingSeconds
                                )
                        } else {
                            "Concluído"
                        },
                        color =
                            if (
                                remainingStitches >
                                    0
                            ) {
                                FioTextMuted
                            } else {
                                FioGold
                            },
                        fontSize =
                            9.sp
                    )
                }

                Slider(
                    value =
                        progress,
                    onValueChange = {
                        playing =
                            false
                        stoppedForStop =
                            false
                        val targetStitches =
                            (
                                it *
                                    design.stitchCount
                                ).toInt()
                                .coerceIn(
                                    0,
                                    design.stitchCount
                                )

                        index =
                            pointIndexForStitchCount(
                                prefix =
                                    stitchPrefix,
                                targetStitches =
                                    targetStitches
                            )
                    },
                    colors =
                        SliderDefaults.colors(
                            thumbColor =
                                FioGold,
                            activeTrackColor =
                                FioGold,
                            inactiveTrackColor =
                                FioSurfaceAlt
                        )
                )

                if (
                    stoppedForStop
                ) {
                    Text(
                        "Parada da máquina: toque em Continuar para prosseguir.",
                        color =
                            FioGold,
                        fontSize =
                            10.sp,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Spacer(
                        Modifier.height(
                            5.dp
                        )
                    )
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            7.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            playing =
                                false
                            stoppedForStop =
                                false
                            index =
                                (
                                    index -
                                        (
                                            points.size *
                                                .10f
                                            ).toInt()
                                    ).coerceAtLeast(
                                        0
                                    )
                        },
                        modifier =
                            Modifier.weight(
                                .65f
                            )
                    ) {
                        Text(
                            "↶ 10%",
                            color =
                                FioTextMuted,
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                10.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (
                                index >=
                                    points.size
                            ) {
                                index =
                                    0
                            }

                            if (
                                stoppedForStop
                            ) {
                                stoppedForStop =
                                    false
                                playing =
                                    true
                            } else {
                                playing =
                                    !playing
                            }
                        },
                        modifier =
                            Modifier
                                .weight(
                                    1.8f
                                )
                                .height(
                                    44.dp
                                ),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    FioGold,
                                contentColor =
                                    FioBackground
                            )
                    ) {
                        Text(
                            when {
                                stoppedForStop ->
                                    "▶ Continuar"

                                playing ->
                                    "Ⅱ Pausar"

                                else ->
                                    "▶ Iniciar simulação"
                            },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                11.sp
                        )
                    }

                    TextButton(
                        onClick = {
                            playing =
                                false
                            stoppedForStop =
                                false
                            index =
                                0
                        },
                        modifier =
                            Modifier.weight(
                                .65f
                            )
                    ) {
                        Text(
                            "■ Parar",
                            color =
                                Color(
                                    0xFFFF8E8A
                                ),
                            fontWeight =
                                FontWeight.Bold,
                            fontSize =
                                10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(
    speed: Float,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier =
            Modifier.clickable(
                onClick =
                    onClick
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (
                        selected
                    ) {
                        FioGold
                    } else {
                        FioSurfaceAlt
                    }
            ),
        shape =
            RoundedCornerShape(
                9.dp
            )
    ) {
        Text(
            speed
                .toInt()
                .toString() +
                "×",
            modifier =
                Modifier.padding(
                    horizontal =
                        10.dp,
                    vertical =
                        7.dp
                ),
            color =
                if (
                    selected
                ) {
                    FioBackground
                } else {
                    FioTextMuted
                },
            fontWeight =
                FontWeight.Bold,
            fontSize =
                10.sp
        )
    }
}

@Composable
private fun OverlayChip(
    text: String,
    modifier: Modifier =
        Modifier
) {
    Card(
        modifier =
            modifier,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(
                        0xD9313336
                    )
            ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {
        Text(
            text,
            modifier =
                Modifier.padding(
                    horizontal =
                        9.dp,
                    vertical =
                        5.dp
                ),
            color =
                Color.White,
            fontWeight =
                FontWeight.SemiBold,
            fontSize =
                9.sp
        )
    }
}

private fun mm(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.0f",
        value
    )

private fun pointIndexForStitchCount(
    prefix: IntArray,
    targetStitches: Int
): Int {
    if (
        prefix.isEmpty() ||
        targetStitches <=
            0
    ) {
        return 0
    }

    var low =
        0

    var high =
        prefix.lastIndex

    while (
        low <
            high
    ) {
        val middle =
            (
                low +
                    high
                ) /
                2

        if (
            prefix[middle] <
                targetStitches
        ) {
            low =
                middle +
                    1
        } else {
            high =
                middle
        }
    }

    return low.coerceIn(
        0,
        prefix.lastIndex
    )
}

private fun formatTime(
    seconds: Int
): String {
    val safe =
        seconds.coerceAtLeast(
            0
        )

    val minutes =
        safe /
            60

    val rest =
        safe %
            60

    return if (
        minutes >
            0
    ) {
        minutes
            .toString() +
            "m " +
            rest
                .toString()
                .padStart(
                    2,
                    '0'
                ) +
            "s"
    } else {
        rest
            .toString() +
            "s"
    }
}
