package com.timachado.brothermatrizes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MatrixEditor
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale

private val editorPalette =
    listOf(
        0xE6BE70,
        0xFFFFFF,
        0x111111,
        0xE63946,
        0xF4A261,
        0x2A9D8F,
        0x457B9D,
        0x9B5DE5,
        0xF4A7B9,
        0x6D597A
    )

@Composable
fun EditorScreen(
    design: EmbroideryDesign,
    onBack: () -> Unit,
    onApply: (
        EmbroideryDesign
    ) -> Unit
) {
    var scale by remember {
        mutableFloatStateOf(1f)
    }

    var rotation by remember {
        mutableFloatStateOf(0f)
    }

    var offsetXmm by remember {
        mutableFloatStateOf(0f)
    }

    var offsetYmm by remember {
        mutableFloatStateOf(0f)
    }

    var mirrorH by remember {
        mutableStateOf(false)
    }

    var mirrorV by remember {
        mutableStateOf(false)
    }

    var centerAtOrigin by remember {
        mutableStateOf(true)
    }

    var displayMode by remember(
        design.fileName
    ) {
        mutableStateOf(
            EmbroideryDisplayMode
                .REALISTIC
        )
    }

    var referenceHoop by remember(
        design.fileName
    ) {
        mutableStateOf(
            design.hoopProfile
                ?: recommendedHoopFor(
                    design
                )
        )
    }

    var showConnections by remember(
        design.fileName
    ) {
        mutableStateOf(false)
    }

    var showDisplaySettings by remember {
        mutableStateOf(false)
    }

    if (
        showDisplaySettings
    ) {
        DisplaySettingsSheet(
            displayMode =
                displayMode,
            onDisplayModeChange = {
                displayMode =
                    it
            },
            hoop =
                referenceHoop,
            onHoopChange = {
                referenceHoop =
                    it
            },
            showConnections =
                showConnections,
            onShowConnectionsChange = {
                showConnections =
                    it
            },
            onDismiss = {
                showDisplaySettings =
                    false
            }
        )
    }

    val initialColors =
        remember(design.fileName) {
            val count =
                maxOf(
                    design.colorCount,
                    1
                )

            List(count) { index ->
                design.threadColors
                    .getOrNull(index)
                    ?: editorPalette[
                        index %
                            editorPalette.size
                    ]
            }
        }

    var colors by remember {
        mutableStateOf(
            initialColors
        )
    }

    var selectedColorBlock by remember {
        mutableIntStateOf(0)
    }

    val transform =
        EditTransform(
            scale = scale,
            rotationDegrees =
                rotation,
            offsetXUnits =
                (offsetXmm * 10f)
                    .toInt(),
            offsetYUnits =
                (offsetYmm * 10f)
                    .toInt(),
            mirrorHorizontal =
                mirrorH,
            mirrorVertical =
                mirrorV,
            centerAtOrigin =
                true,
            threadColors =
                colors
        )

    val preview =
        MatrixEditor.apply(
            design,
            transform
        )

    val hasPendingChanges =
        scale !=
            1f ||
        rotation !=
            0f ||
        offsetXmm !=
            0f ||
        offsetYmm !=
            0f ||
        mirrorH ||
        mirrorV ||
        colors !=
            initialColors

    var showExitDialog by remember {
        mutableStateOf(false)
    }

    fun requestBack() {
        if (
            hasPendingChanges
        ) {
            showExitDialog =
                true
        } else {
            onBack()
        }
    }

    BackHandler {
        requestBack()
    }

    if (
        showExitDialog
    ) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog =
                    false
            },
            title = {
                Text(
                    "Alterações não aplicadas"
                )
            },
            text = {
                Text(
                    "Você alterou a matriz. Deseja aplicar as mudanças antes de voltar?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog =
                            false
                        onApply(
                            preview
                        )
                    }
                ) {
                    Text(
                        "Aplicar e voltar"
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitDialog =
                                false
                        }
                    ) {
                        Text(
                            "Cancelar"
                        )
                    }

                    TextButton(
                        onClick = {
                            showExitDialog =
                                false
                            onBack()
                        }
                    ) {
                        Text(
                            "Descartar"
                        )
                    }
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                horizontal = 14.dp
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 4.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    requestBack()
                }
            ) {
                Text(
                    "‹ Voltar",
                    color = FioGold
                )
            }

            Column(
                Modifier.weight(1f)
            ) {
                Text(
                    "Editor",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 19.sp
                )

                Text(
                    "Edição não destrutiva",
                    color = FioTextMuted,
                    fontSize = 10.sp
                )
            }

            TextButton(
                onClick = {
                    showDisplaySettings =
                        true
                }
            ) {
                Text(
                    "Exibição",
                    color = FioGold
                )
            }

            TextButton(
                onClick = {
                    scale = 1f
                    rotation = 0f
                    offsetXmm = 0f
                    offsetYmm = 0f
                    mirrorH = false
                    mirrorV = false
                    centerAtOrigin =
                        true
                    colors =
                        initialColors
                    selectedColorBlock =
                        0
                }
            ) {
                Text(
                    "Resetar",
                    color = FioGold
                )
            }
        }

        EmbroideryCanvas(
            design = preview,
            hoop =
                referenceHoop,
            displayMode =
                displayMode,
            showConnections =
                showConnections,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .background(
                        Color(0xFF071017),
                        RoundedCornerShape(
                            24.dp
                        )
                    )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.25f)
                    .padding(
                        vertical = 12.dp
                    ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        FioSurface
                ),
            shape =
                RoundedCornerShape(
                    22.dp
                )
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {
                Text(
                    "Tamanho " +
                        percent(scale),
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Slider(
                    value = scale,
                    onValueChange = {
                        scale = it
                    },
                    valueRange =
                        0.5f..2f
                )

                Text(
                    "Rotação " +
                        rotation
                            .toInt() +
                        "°",
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Slider(
                    value = rotation,
                    onValueChange = {
                        rotation = it
                    },
                    valueRange =
                        -180f..180f
                )

                Text(
                    "Mover X " +
                        mm(offsetXmm) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value = offsetXmm,
                    onValueChange = {
                        offsetXmm = it
                    },
                    valueRange =
                        -50f..50f
                )

                Text(
                    "Mover Y " +
                        mm(offsetYmm) +
                        " mm",
                    color = FioText,
                    fontSize = 12.sp
                )

                Slider(
                    value = offsetYmm,
                    onValueChange = {
                        offsetYmm = it
                    },
                    valueRange =
                        -50f..50f
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    OutlinedButton(
                        onClick = {
                            mirrorH =
                                !mirrorH
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            if (mirrorH) {
                                "↔ Espelhado"
                            } else {
                                "↔ Horizontal"
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            mirrorV =
                                !mirrorV
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            if (mirrorV) {
                                "↕ Espelhado"
                            } else {
                                "↕ Vertical"
                            }
                        )
                    }
                }

                Spacer(
                    Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {
                        offsetXmm =
                            0f
                        offsetYmm =
                            0f
                        centerAtOrigin =
                            true
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            offsetXmm ==
                                0f &&
                            offsetYmm ==
                                0f
                        ) {
                            "◎ Matriz centralizada"
                        } else {
                            "◎ Centralizar matriz"
                        }
                    )
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                Text(
                    "Cor do bloco " +
                        (selectedColorBlock + 1) +
                        " de " +
                        colors.size,
                    color = FioText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            vertical = 8.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            9.dp
                        )
                ) {
                    editorPalette.forEach {
                            rawColor ->
                        val selected =
                            colors[
                                selectedColorBlock
                            ] ==
                                rawColor

                        Box(
                            Modifier
                                .size(38.dp)
                                .background(
                                    Color(
                                        0xFF000000 or
                                            rawColor
                                                .toLong()
                                    ),
                                    CircleShape
                                )
                                .border(
                                    if (selected) {
                                        3.dp
                                    } else {
                                        1.dp
                                    },
                                    if (selected) {
                                        FioGold
                                    } else {
                                        Color(
                                            0xFF52616B
                                        )
                                    },
                                    CircleShape
                                )
                                .clickable {
                                    colors =
                                        colors
                                            .toMutableList()
                                            .also {
                                                list ->
                                                list[
                                                    selectedColorBlock
                                                ] =
                                                    rawColor
                                            }
                                }
                        )
                    }
                }

                if (colors.size > 1) {
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedColorBlock =
                                    (
                                        selectedColorBlock -
                                            1 +
                                            colors.size
                                        ) %
                                        colors.size
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "‹ Cor anterior"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                selectedColorBlock =
                                    (
                                        selectedColorBlock +
                                            1
                                        ) %
                                        colors.size
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text(
                                "Próxima cor ›"
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                Text(
                    "Prévia: " +
                        mm(
                            preview.bounds
                                .widthMm
                        ) +
                        " × " +
                        mm(
                            preview.bounds
                                .heightMm
                        ) +
                        " mm",
                    color = FioTextMuted,
                    fontSize = 11.sp
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Button(
                    onClick = {
                        onApply(preview)
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    FioGold,
                                contentColor =
                                    FioBackground
                            )
                ) {
                    Text(
                        "Aplicar edição",
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun percent(
    value: Float
): String =
    (
        value *
            100f
        ).toInt()
        .toString() +
        "%"

private fun mm(
    value: Float
): String =
    String.format(
        Locale.forLanguageTag(
            "pt-BR"
        ),
        "%.1f",
        value
    )
