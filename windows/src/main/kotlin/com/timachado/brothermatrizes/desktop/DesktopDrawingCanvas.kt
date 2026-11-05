package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.math.hypot

/** Tela de traços original para Windows; não é a prévia de pontadas. */
@Composable
internal fun DesktopDrawingCanvas(
    strokes: List<List<VectorPoint>>,
    enabled: Boolean,
    onStrokeCommitted: (List<VectorPoint>) -> Unit
) {
    var active by remember { mutableStateOf<List<VectorPoint>>(emptyList()) }
    val latestCommit by rememberUpdatedState(onStrokeCommitted)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestStrokes by rememberUpdatedState(strokes)

    val pointer = Modifier.pointerInput(Unit) {
        fun point(at: Offset, canvas: IntSize): VectorPoint = VectorPoint(
            x = (at.x / canvas.width.coerceAtLeast(1)).coerceIn(0f, 1f),
            y = (at.y / canvas.height.coerceAtLeast(1)).coerceIn(0f, 1f)
        )
        detectDragGestures(
            onDragStart = { start ->
                active = if (latestEnabled &&
                    latestStrokes.size < DesktopDrawingService.MAX_STROKES)
                    listOf(point(start, size)) else emptyList()
            },
            onDragEnd = {
                val path = active
                active = emptyList()
                if (path.size >= 2 && latestEnabled &&
                    latestStrokes.size < DesktopDrawingService.MAX_STROKES) {
                    latestCommit(path)
                }
            },
            onDragCancel = { active = emptyList() }
        ) { change, _ ->
            change.consume()
            if (active.isNotEmpty() &&
                active.size < DesktopDrawingService.MAX_POINTS_PER_STROKE) {
                val p = point(change.position, size)
                val old = active.last()
                if (hypot((p.x - old.x).toDouble(),
                        (p.y - old.y).toDouble()) > 0.002) {
                    active = active + p
                }
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .background(DesktopBrandColors.surfaceAlt, RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().then(pointer)) {
            val color = Color(0xFFE6BE70)
            val w = size.width
            val h = size.height
            // Grade apenas visual; não é transformada em comandos de bordado.
            for (i in 1..9) {
                val x = w * i / 10f
                val y = h * i / 10f
                drawLine(color = Color(0x33798899), start = Offset(x, 0f),
                    end = Offset(x, h), strokeWidth = 0.8f)
                drawLine(color = Color(0x33798899), start = Offset(0f, y),
                    end = Offset(w, y), strokeWidth = 0.8f)
            }
            for (path in strokes + listOf(active)) {
                path.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        color = color,
                        start = Offset(a.x * w, a.y * h),
                        end = Offset(b.x * w, b.y * h),
                        strokeWidth = 2.8f
                    )
                }
            }
        }
    }
}
