package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timachado.brothermatrizes.core.embroidery.*
import com.timachado.brothermatrizes.ui.theme.FioText
import com.timachado.brothermatrizes.ui.theme.FioTextMuted
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Seleção manual conservadora: só o último bloco de cor completamente isolado.
 * Ao selecionar, o preview mostra como a matriz ficaria após a remoção.
 */
@Composable
fun ImportedNameSelectionPanel(
    original: EmbroideryDesign,
    hoop: HoopProfile,
    onCancel: () -> Unit,
    onConfirm: (IsolatedNameBlock) -> Unit
) {
    var start by remember(original) { mutableStateOf<Offset?>(null) }
    var end by remember(original) { mutableStateOf<Offset?>(null) }
    var verdict by remember(original) { mutableStateOf<Result<IsolatedNameBlock>?>(null) }
    val pad = with(LocalDensity.current) { 36.dp.toPx() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancelar") }
            Text("Selecionar nome antigo", color = FioText)
        }
        Text(
            "Arraste um retângulo envolvendo somente o nome. A troca é permitida " +
                "apenas se ele estiver no último bloco de cor isolado. " +
                "Pontadas da moldura na seleção bloqueiam a remoção.",
            color = FioTextMuted, fontSize = 12.sp
        )
        Box(Modifier.fillMaxWidth().height(335.dp)) {
            EmbroideryCanvas(
                design = verdict?.getOrNull()?.originalWithoutBlock ?: original,
                hoop = hoop,
                interactive = false,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(
                Modifier.fillMaxSize().pointerInput(original, hoop, pad) {
                    detectDragGestures(
                        onDragStart = {
                            start = it
                            end = it
                            verdict = null
                        },
                        onDragEnd = {
                            val a = start
                            val b = end
                            if (a != null && b != null) {
                                val rotate = hoop.widthMm != hoop.heightMm &&
                                    (original.bounds.widthMm > original.bounds.heightMm) !=
                                    (hoop.widthMm > hoop.heightMm)
                                val fw = if (rotate) hoop.heightMm else hoop.widthMm
                                val fh = if (rotate) hoop.widthMm else hoop.heightMm
                                val scale = minOf(
                                    (size.width - 2f * pad) / (fw * 10f),
                                    (size.height - 2f * pad) / (fh * 10f)
                                ).coerceAtLeast(.01f)
                                val preserve = shouldPreserveHoopPosition(original, hoop, rotate)
                                val cx = if (preserve) 0f else
                                    (original.bounds.minXUnits.toLong() +
                                        original.bounds.maxXUnits.toLong()) / 2f
                                val cy = if (preserve) 0f else
                                    (original.bounds.minYUnits.toLong() +
                                        original.bounds.maxYUnits.toLong()) / 2f
                                val direction = if (original.sourceYAxisDown) 1f else -1f
                                fun coordX(px: Float): Int =
                                    ((px - size.width / 2f) / scale + cx).roundToInt()
                                fun coordY(py: Float): Int =
                                    ((py - size.height / 2f) / (scale * direction) + cy).roundToInt()
                                verdict = ImportedNameBlockSelection.isolateFinalColorBlock(
                                    original, StitchSelectionRect(
                                        min(coordX(a.x), coordX(b.x)),
                                        max(coordX(a.x), coordX(b.x)),
                                        min(coordY(a.y), coordY(b.y)),
                                        max(coordY(a.y), coordY(b.y))
                                    )
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            end = change.position
                        }
                    )
                }
            ) {
                val a = start
                val b = end
                if (a != null && b != null) {
                    val origin = Offset(min(a.x,b.x), min(a.y,b.y))
                    val rectSize = Size(max(1f,abs(a.x-b.x)), max(1f,abs(a.y-b.y)))
                    drawRect(Color(0x5533BCD4), topLeft = origin, size = rectSize)
                    drawRect(Color(0xFF00BCD4), topLeft = origin,
                        size = rectSize, style = Stroke(width = 3f))
                }
            }
        }
        val selected = verdict?.getOrNull()
        if (selected != null) {
            Text(
                "${selected.removedStitchCount} pontadas do último bloco isoladas. " +
                    "Prévia agora mostra a matriz sem esse bloco.",
                color = FioText, fontSize = 12.sp
            )
            Button(onClick = { onConfirm(selected) }, modifier = Modifier.fillMaxWidth()) {
                Text("Confirmar remoção e editar novo nome")
            }
        } else {
            Text(
                verdict?.exceptionOrNull()?.message ?: "Selecione a área do nome no desenho.",
                color = if (verdict == null) FioTextMuted else Color(0xFFFF9999),
                fontSize = 12.sp
            )
        }
        Text(
            "Se a matriz não tiver um bloco de nome isolado, nenhuma pontada será apagada. " +
                "A matriz original é preservada.",
            color = FioTextMuted, fontSize = 11.sp
        )
    }
}
