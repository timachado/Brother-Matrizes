package com.timachado.brothermatrizes

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.ui.theme.FioGold
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlin.math.roundToInt

private data class NameDraft(
    val name: String = "Maria",
    val font: EmbroideryFontPreset = EmbroideryFontPreset.ELEGANT,
    val height: Float = 12f,
    val spacing: Float = 1.2f,
    val density: Float = .45f,
    val color: Int = 0xB22222,
    val x: Int,
    val y: Int
)

@Composable
fun NameEditorScreen(
    original: EmbroideryDesign,
    referenceHoop: HoopProfile,
    onBack: () -> Unit,
    onApply: (EmbroideryDesign) -> Unit
) {
    var draft by remember(original) {
        val existing = original.editableName
        mutableStateOf(
            NameDraft(
                name = existing?.options?.text ?: "Maria",
                font = existing?.options?.font ?: EmbroideryFontPreset.ELEGANT,
                height = existing?.options?.heightMm ?: 12f,
                spacing = existing?.options?.spacingMm ?: 1.2f,
                density = existing?.options?.satinDensityMm ?: .45f,
                color = existing?.options?.color ?: 0xB22222,
                x = existing?.centerXUnits ?: (original.bounds.minXUnits.toLong() +
                    original.bounds.maxXUnits.toLong()).div(2).toInt(),
                y = existing?.centerYUnits ?: (original.bounds.minYUnits.toLong() +
                    original.bounds.maxYUnits.toLong()).div(2).toInt()
            )
        )
    }
    var undo by remember(original) { mutableStateOf(emptyList<NameDraft>()) }
    var redo by remember(original) { mutableStateOf(emptyList<NameDraft>()) }
    var fontsExpanded by remember { mutableStateOf(false) }
    var zoomMode by remember(original) { mutableStateOf(false) }
    var selectingOldName by remember(original) { mutableStateOf(false) }
    var baseDesign by remember(original) { mutableStateOf(original) }

    fun setDraft(next: NameDraft) {
        if (next == draft) return
        undo = (undo + draft).takeLast(40)
        redo = emptyList()
        draft = next
    }

    val result = remember(baseDesign, draft, referenceHoop) {
        NamePersonalization.add(
            original = baseDesign,
            options = TextMatrixOptions(
                text = draft.name,
                font = draft.font,
                heightMm = draft.height,
                spacingMm = draft.spacing,
                satinDensityMm = draft.density,
                style = TextStitchStyle.SATIN,
                outputFormat = original.format,
                color = draft.color,
                hoopProfile = referenceHoop,
                enforceHoop = true
            ),
            centerXUnits = draft.x,
            centerYUnits = draft.y
        )
    }
    val preview = result.getOrNull()

    // O gesto é aplicado em unidades de matriz (0,1 mm), não em pixels
    // gravados no arquivo. A escala usa o mesmo bastidor do Canvas nativo.
    val paddingPx = with(LocalDensity.current) { 36.dp.toPx() }
    val move by rememberUpdatedState<(Float, Float) -> Unit> { dx, dy ->
        setDraft(
            draft.copy(
                x = draft.x + dx.roundToInt(),
                y = draft.y + dy.roundToInt()
            )
        )
    }

    if (selectingOldName) {
        ImportedNameSelectionPanel(
            original = original,
            hoop = referenceHoop,
            onCancel = { selectingOldName = false },
            onConfirm = { selected ->
                baseDesign = selected.originalWithoutBlock
                setDraft(draft.copy(
                    x = selected.centerXUnits,
                    y = selected.centerYUnits
                ))
                selectingOldName = false
                zoomMode = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("‹ Voltar", color = FioGold) }
            Text(
                "Adicionar / Editar Nome",
                color = FioText,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val previous = undo.lastOrNull()
                    if (previous != null) {
                        redo = redo + draft
                        undo = undo.dropLast(1)
                        draft = previous
                    }
                },
                enabled = undo.isNotEmpty()
            ) { Text("Desfazer") }
            TextButton(
                onClick = {
                    val next = redo.lastOrNull()
                    if (next != null) {
                        undo = undo + draft
                        redo = redo.dropLast(1)
                        draft = next
                    }
                },
                enabled = redo.isNotEmpty()
            ) { Text("Refazer") }
        }

        if (original.editableName == null &&
            original.points.any { it.command ==
                com.timachado.brothermatrizes.core.embroidery.StitchCommand.COLOR_CHANGE } &&
            baseDesign === original
        ) {
            OutlinedButton(
                onClick = { selectingOldName = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Substituir nome existente (selecionar bloco)")
            }
        }

        if (baseDesign !== original) {
            OutlinedButton(
                onClick = {
                    baseDesign = original
                    zoomMode = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desfazer seleção do nome antigo")
            }
        }

        Text(
            "Use Mover para arrastar o nome; use Zoom e pan para ampliar. A moldura original " +
                "permanece intacta. O desenho abaixo contém pontadas reais.",
            color = FioTextMuted,
            fontSize = 12.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zoomMode = false }) {
                Text(if (!zoomMode) "✓ Mover nome" else "Mover nome")
            }
            OutlinedButton(onClick = { zoomMode = true }) {
                Text(if (zoomMode) "✓ Zoom e pan" else "Zoom e pan")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(285.dp)
                .then(if (zoomMode) Modifier else Modifier.pointerInput(original, referenceHoop, paddingPx) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        val width = referenceHoop.widthMm * 10f
                        val height = referenceHoop.heightMm * 10f
                        val rotated = (original.bounds.widthMm > original.bounds.heightMm) !=
                            (referenceHoop.widthMm > referenceHoop.heightMm) &&
                            referenceHoop.widthMm != referenceHoop.heightMm
                        val frameWidth = if (rotated) height else width
                        val frameHeight = if (rotated) width else height
                        val scale = minOf(
                            ((size.width - 2f * paddingPx) / frameWidth),
                            ((size.height - 2f * paddingPx) / frameHeight)
                        ).coerceAtLeast(.01f)
                        val direction = if (original.sourceYAxisDown) 1f else -1f
                        move(amount.x / scale, amount.y / (scale * direction))
                    }
                })
        ) {
            EmbroideryCanvas(
                design = preview ?: baseDesign,
                hoop = referenceHoop,
                interactive = zoomMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (preview == null) {
            Text(
                result.exceptionOrNull()?.message ?: "Prévia indisponível.",
                color = Color(0xFFFF9393),
                fontSize = 12.sp
            )
        } else {
            Text(
                "Prévia válida: ${preview.stitchCount} pontos • " +
                    "${preview.bounds.widthMm} × ${preview.bounds.heightMm} mm",
                color = FioTextMuted,
                fontSize = 12.sp
            )
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { setDraft(draft.copy(name = it.take(24))) },
            label = { Text("Nome para bordar") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Box {
            OutlinedButton(onClick = { fontsExpanded = true }) {
                Text("Fonte: " + draft.font.displayName)
            }
            DropdownMenu(
                expanded = fontsExpanded,
                onDismissRequest = { fontsExpanded = false }
            ) {
                EmbroideryFontPreset.entries.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(font.displayName) },
                        onClick = {
                            fontsExpanded = false
                            setDraft(draft.copy(font = font))
                        }
                    )
                }
            }
        }

        Text("Altura: " + "%.1f".format(draft.height) + " mm", color = FioText)
        Slider(
            value = draft.height,
            onValueChange = { setDraft(draft.copy(height = it)) },
            valueRange = 4f..30f
        )
        Text("Espaçamento: " + "%.1f".format(draft.spacing) + " mm", color = FioText)
        Slider(
            value = draft.spacing,
            onValueChange = { setDraft(draft.copy(spacing = it)) },
            valueRange = 0f..5f
        )
        Text("Densidade Satin: " + "%.2f".format(draft.density) + " mm", color = FioText)
        Slider(
            value = draft.density,
            onValueChange = { setDraft(draft.copy(density = it)) },
            valueRange = .3f..1.0f
        )

        Text("Cor da linha", color = FioText)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0xB22222, 0xE6BE70, 0x173E6E, 0xB83A83, 0x17846B, 0x202020)
                .forEachIndexed { index, color ->
                    OutlinedButton(
                        onClick = { setDraft(draft.copy(color = color)) }
                    ) {
                        Text(
                            if (color == draft.color) "✓ ${index + 1}" else "● ${index + 1}",
                            color = Color(color or 0xFF000000.toInt())
                        )
                    }
                }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                setDraft(draft.copy(
                    x = (baseDesign.bounds.minXUnits.toLong() +
                        baseDesign.bounds.maxXUnits.toLong()).div(2).toInt(),
                    y = (baseDesign.bounds.minYUnits.toLong() +
                        baseDesign.bounds.maxYUnits.toLong()).div(2).toInt()
                ))
            }) { Text("Centralizar nome") }
            OutlinedButton(onClick = { setDraft(draft.copy(x = draft.x - 10)) }) {
                Text("←")
            }
            OutlinedButton(onClick = { setDraft(draft.copy(x = draft.x + 10)) }) {
                Text("→")
            }
        }
        Text(
            "A troca de nome recompõe a camada editável sem duplicar pontadas nesta sessão. " +
                "Depois de exportar e reabrir PES/DST, a camada volta a ser somente pontadas. Apagar um nome de PES/DST importado requer seleção " +
                "segura de bloco e não está habilitado nesta etapa.",
            color = FioTextMuted,
            fontSize = 11.sp
        )
        Button(
            onClick = { preview?.let(onApply) },
            enabled = preview != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aplicar nome e abrir matriz personalizada")
        }
        Spacer(Modifier.height(8.dp))
    }
}
