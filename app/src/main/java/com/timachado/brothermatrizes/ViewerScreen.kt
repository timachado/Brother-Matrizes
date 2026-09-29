package com.timachado.brothermatrizes

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.timachado.brothermatrizes.ui.theme.FioBackground
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioSurface
import com.timachado.brothermatrizes.ui.theme.FioSurfaceAlt
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import java.util.Locale

@Composable
fun ViewerScreen(
    design: EmbroideryDesign,
    displayMode: EmbroideryDisplayMode,
    onDisplayModeChange: (EmbroideryDisplayMode) -> Unit,
    referenceHoop: HoopProfile,
    onReferenceHoopChange: (HoopProfile) -> Unit,
    showConnections: Boolean,
    onShowConnectionsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onSimulate: () -> Unit,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onTransfer: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit
) {
    var showDisplaySettings by remember {
        mutableStateOf(
            false
        )
    }

    if (
        showDisplaySettings
    ) {
        DisplaySettingsSheet(
            displayMode =
                displayMode,
            onDisplayModeChange =
                onDisplayModeChange,
            hoop =
                referenceHoop,
            onHoopChange =
                onReferenceHoopChange,
            showConnections =
                showConnections,
            onShowConnectionsChange =
                onShowConnectionsChange,
            onDismiss = {
                showDisplaySettings =
                    false
            }
        )
    }

    Column(
        Modifier.fillMaxSize()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        12.dp,
                    vertical =
                        6.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack
            ) {
                Text(
                    "‹ Voltar",
                    color = FioGold
                )
            }

            Column(
                Modifier.weight(1f),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Text(
                    design.fileName,
                    color = FioText,
                    fontWeight =
                        FontWeight
                            .SemiBold,
                    maxLines = 1
                )

                Text(
                    design.format +
                        when {
                            design.sourceBytes
                                .isEmpty() ->
                                " • criada no Brother Matrizes"

                            design.isModified ->
                                " • edição aplicada"

                            else ->
                                " • original protegido"
                        },
                    color =
                        FioTextMuted,
                    fontSize = 11.sp
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
                onClick = onOpen
            ) {
                Text(
                    "Abrir",
                    color = FioGold
                )
            }
        }

        EmbroideryCanvas(
            design = design,
            interactive = true,
            hoop =
                referenceHoop,
            displayMode =
                displayMode,
            showConnections =
                showConnections,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1.18f)
                    .padding(
                        horizontal =
                            16.dp
                    )
                    .background(
                        Color(
                            0xFF071017
                        ),
                        RoundedCornerShape(
                            24.dp
                        )
                    )
        )

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(.92f)
                    .padding(
                        horizontal =
                            16.dp,
                        vertical =
                            10.dp
                    ),
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            FioSurface
                    ),
            shape =
                RoundedCornerShape(
                    24.dp
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        18.dp
                    )
            ) {
                Text(
                    "Informações da matriz",
                    color = FioText,
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 18.sp
                )

                design.label?.let {
                    Text(
                        "Identificação: " +
                            it,
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )
                }

                design.hoopProfile?.let {
                    hoop ->
                    val designLandscape =
                        design.bounds.widthMm >
                            design.bounds.heightMm

                    val hoopLandscape =
                        hoop.widthMm >
                            hoop.heightMm

                    val rotateForDisplay =
                        hoop.widthMm !=
                            hoop.heightMm &&
                        designLandscape !=
                            hoopLandscape

                    val effectiveWidthMm =
                        if (
                            rotateForDisplay
                        ) {
                            hoop.heightMm
                        } else {
                            hoop.widthMm
                        }

                    val effectiveHeightMm =
                        if (
                            rotateForDisplay
                        ) {
                            hoop.widthMm
                        } else {
                            hoop.heightMm
                        }

                    val effectiveUsableWidthMm =
                        if (
                            rotateForDisplay
                        ) {
                            hoop.usableHeightMm
                        } else {
                            hoop.usableWidthMm
                        }

                    val effectiveUsableHeightMm =
                        if (
                            rotateForDisplay
                        ) {
                            hoop.usableWidthMm
                        } else {
                            hoop.usableHeightMm
                        }

                    Text(
                        "Bastidor: " +
                            hoop.displayName +
                            " • orientação " +
                            mm(
                                effectiveWidthMm
                            ) +
                            " × " +
                            mm(
                                effectiveHeightMm
                            ) +
                            " mm • área segura " +
                            mm(
                                effectiveUsableWidthMm
                            ) +
                            " × " +
                            mm(
                                effectiveUsableHeightMm
                            ) +
                            " mm",
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )
                }

                design.fabricProfile?.let {
                    fabric ->
                    Text(
                        "Tecido: " +
                            fabric.displayName,
                        color =
                            FioTextMuted,
                        fontSize =
                            11.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Row(
                    horizontalArrangement =
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    InfoChip(
                        design
                            .stitchCount
                            .toString(),
                        "pontos",
                        Modifier.weight(
                            1f
                        )
                    )

                    InfoChip(
                        mm(
                            design
                                .bounds
                                .widthMm
                        ) +
                            " × " +
                            mm(
                                design
                                    .bounds
                                    .heightMm
                            ),
                        "mm",
                        Modifier.weight(
                            1f
                        )
                    )

                    InfoChip(
                        design
                            .colorCount
                            .toString(),
                        "blocos",
                        Modifier.weight(
                            1f
                        )
                    )
                }

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Button(
                    onClick =
                        onTransfer,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
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
                        "⇧ Enviar para máquina",
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                OutlinedButton(
                    onClick =
                        onSaveProject,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                ) {
                    Text(
                        "★ Salvar em Minhas Matrizes"
                    )
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                OutlinedButton(
                    onClick =
                        onSimulate,
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    Text(
                        "▶ Simular bordado"
                    )
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    horizontalArrangement =
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    OutlinedButton(
                        onClick =
                            onEdit,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    ) {
                        Text(
                            "✎ Editar"
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onConvert,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    ) {
                        Text(
                            "⇄ Converter"
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    horizontalArrangement =
                        Arrangement
                            .spacedBy(
                                8.dp
                            )
                ) {
                    OutlinedButton(
                        onClick =
                            onSaveCopy,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    ) {
                        Text(
                            when {
                                design.sourceBytes
                                    .isEmpty() ->
                                    "Salvar matriz"

                                design.isModified ->
                                    "Salvar edição"

                                else ->
                                    "Salvar cópia"
                            }
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onShare,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                    ) {
                        Text(
                            "Compartilhar"
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Text(
                    "O original nunca é sobrescrito. Edições e conversões geram um novo arquivo.",
                    color =
                        FioTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun InfoChip(
    value: String,
    label: String,
    modifier: Modifier =
        Modifier
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        FioSurfaceAlt
                ),
        shape =
            RoundedCornerShape(
                16.dp
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical =
                        10.dp,
                    horizontal =
                        6.dp
                ),
            horizontalAlignment =
                Alignment
                    .CenterHorizontally
        ) {
            Text(
                value,
                color = FioText,
                fontWeight =
                    FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                label,
                color =
                    FioTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

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
