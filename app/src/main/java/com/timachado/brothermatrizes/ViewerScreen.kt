package com.timachado.brothermatrizes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning
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
    onEditName: () -> Unit,
    onConvert: () -> Unit,
    onTransfer: () -> Unit,
    onSaveProject: () -> Unit,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit,
    onPositionChange: (EmbroideryDesign) -> Unit = {}
) {
    var placementTarget by remember { mutableStateOf<HoopLayerPositioning.Target?>(null) }
    var placementError by remember { mutableStateOf<String?>(null) }
    var gestureDelta by remember { mutableStateOf(Offset.Zero) }
    var positioningCanvas by remember { mutableStateOf(IntSize.Zero) }

    fun selectPlacement(target: HoopLayerPositioning.Target) {
        placementError = null
        gestureDelta = Offset.Zero
        if (placementTarget == target) {
            placementTarget = null
        } else {
            HoopLayerPositioning.prepare(design, referenceHoop).fold(
                onSuccess = {
                    if (it !== design) onPositionChange(it)
                    placementTarget = target
                },
                onFailure = { placementError =
                    it.message ?: "Não foi possível iniciar o posicionamento."
                }
            )
        }
    }

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

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { selectPlacement(HoopLayerPositioning.Target.MATRIX) },
                modifier = Modifier.weight(1f).testTag("brother_position_matrix")
            ) {
                Text(if (placementTarget == HoopLayerPositioning.Target.MATRIX)
                    "✓ Matriz" else "Mover matriz", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { selectPlacement(HoopLayerPositioning.Target.NAME) },
                enabled = design.editableName != null,
                modifier = Modifier.weight(1f).testTag("brother_position_name")
            ) {
                Text(if (placementTarget == HoopLayerPositioning.Target.NAME)
                    "✓ Nome" else "Mover nome", fontSize = 12.sp)
            }
        }
        if (placementError != null) {
            Text(
                requireNotNull(placementError),
                color = Color(0xFFFF9696),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
                    .testTag("brother_position_error")
            )
        }
        if (placementTarget != null) {
            Text(
                "Arraste com um dedo para posicionar. Toque no modo novamente " +
                    "para concluir; salve em Minhas Matrizes para manter a posição.",
                color = FioTextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.18f)
                .padding(horizontal = 16.dp)
                .onSizeChanged { positioningCanvas = it }
                .testTag("brother_position_canvas")
                .then(if (placementTarget != null) {
                    Modifier.pointerInput(placementTarget, design, referenceHoop) {
                        detectDragGestures(
                            onDragStart = { gestureDelta = Offset.Zero },
                            onDragCancel = { gestureDelta = Offset.Zero },
                            onDragEnd = {
                                val activeTarget = placementTarget
                                if (activeTarget != null &&
                                    positioningCanvas.width > 0 &&
                                    positioningCanvas.height > 0 &&
                                    gestureDelta != Offset.Zero
                                ) {
                                    val placement = NameEditorPlacement.screenOffset(
                                        design, referenceHoop, 0, 0,
                                        positioningCanvas.width,
                                        positioningCanvas.height,
                                        36.dp.toPx()
                                    )
                                    val direction =
                                        if (design.sourceYAxisDown) 1f else -1f
                                    val (dx, dy) = NameEditorPlacement.dragUnits(
                                        gestureDelta.x, gestureDelta.y,
                                        placement.unitsPerPixel, direction
                                    )
                                    HoopLayerPositioning.move(
                                        design, dx, dy, activeTarget,
                                        referenceHoop
                                    ).fold(
                                        onSuccess = {
                                            placementError = null
                                            onPositionChange(it)
                                        },
                                        onFailure = {
                                            placementError = it.message ?:
                                                "A nova posição é inválida."
                                        }
                                    )
                                }
                                gestureDelta = Offset.Zero
                            }
                        ) { change, drag ->
                            gestureDelta += drag
                            change.consume()
                        }
                    }
                } else Modifier)
        ) {
            EmbroideryCanvas(
                design = design,
                interactive = placementTarget == null,
                hoop = referenceHoop,
                displayMode = displayMode,
                showConnections = showConnections,
                designDragOffset = gestureDelta,
                positionMode = placementTarget != null,
                modifier = Modifier.fillMaxSize().background(
                    Color(0xFF071017), RoundedCornerShape(24.dp))
            )
        }

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

                // Files saved by the previous glyph engine contain the
                // original stitch sequence. Updating the APK never changes
                // these coordinates automatically.
                if (design.editableName?.options?.importedFontId != null) {
                    Text(
                        "Esta matriz contém pontadas de nome já salvas. " +
                            "Para aplicar correções da fonte, reprocessar o nome.",
                        color = FioTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onEditName,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("↻ Atualizar glifos do nome salvo")
                    }
                    Spacer(Modifier.height(8.dp))
                }

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

                OutlinedButton(
                    onClick = onEditName,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✎ Adicionar / Editar Nome")
                }

                Spacer(Modifier.height(8.dp))

                // Atalho da tela de matriz: abre o editor que faz a rotação
                // REAL de pontos e do nome, com verificação de bastidor.
                OutlinedButton(
                    onClick = onEditName,
                    modifier = Modifier.fillMaxWidth()
                        .testTag("brother_viewer_open_matrix_rotation")
                ) {
                    Text("↻ Girar matriz • escolher ângulo")
                }

                Spacer(Modifier.height(8.dp))

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
