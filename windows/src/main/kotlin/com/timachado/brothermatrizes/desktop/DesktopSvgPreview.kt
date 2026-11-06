package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork

/**
 * Preview Compose: renderiza somente vetores interpretados pelo parser
 * Android. Clique seleciona caminho; arraste edita uma cópia em memória.
 */
@Composable
internal fun DesktopSvgPreview(
    artwork: VectorArtwork,
    selectedPathIndex: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onMove: (Int, Offset, IntSize) -> Unit
) {
    var dragIndex by remember(artwork) { mutableStateOf<Int?>(null) }
    var dragDelta by remember(artwork) { mutableStateOf(Offset.Zero) }
    val latestSelect by rememberUpdatedState(onSelect)
    val latestMove by rememberUpdatedState(onMove)
    val latestEnabled by rememberUpdatedState(enabled)

    val dragModifier = Modifier.pointerInput(artwork, enabled) {
        var current: Int? = null
        var accumulated = Offset.Zero
        detectDragGestures(
            onDragStart = { start ->
                current = if (latestEnabled) {
                    runCatching {
                        DesktopSvgPathEditor.select(
                            artwork, size.width.toFloat(),
                            size.height.toFloat(), start
                        )
                    }.getOrNull()
                } else null
                accumulated = Offset.Zero
                dragIndex = current
                dragDelta = Offset.Zero
                current?.let(latestSelect)
            },
            onDragEnd = {
                val selected = current
                val finalDelta = accumulated
                current = null
                dragIndex = null
                dragDelta = Offset.Zero
                accumulated = Offset.Zero
                if (selected != null && finalDelta != Offset.Zero &&
                    latestEnabled) {
                    latestMove(selected, finalDelta, size)
                }
            },
            onDragCancel = {
                current = null
                accumulated = Offset.Zero
                dragIndex = null
                dragDelta = Offset.Zero
            }
        ) { change, step ->
            change.consume()
            if (current != null) {
                accumulated += step
                dragDelta = accumulated
            }
        }
    }
    val tapModifier = Modifier.pointerInput(artwork, enabled) {
        detectTapGestures(onTap = { cursor ->
            if (latestEnabled) {
                val index = runCatching {
                    DesktopSvgPathEditor.select(
                        artwork, size.width.toFloat(),
                        size.height.toFloat(), cursor
                    )
                }.getOrNull()
                if (index != null) latestSelect(index)
            }
        })
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .background(DesktopBrandColors.surfaceAlt, RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .then(dragModifier).then(tapModifier)
        ) {
            val transform = runCatching {
                DesktopSvgPathEditor.viewport(artwork, size.width, size.height)
            }.getOrNull() ?: return@Canvas
            artwork.paths.forEachIndexed { index, path ->
                val delta = if (dragIndex == index) dragDelta else Offset.Zero
                fun point(x: Float, y: Float) = transform.point(
                    com.timachado.brothermatrizes.core.embroidery.VectorPoint(x, y)
                ) + delta
                val color = if (index == selectedPathIndex || index == dragIndex)
                    DesktopBrandColors.goldSoft else DesktopBrandColors.gold
                path.points.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        color, point(a.x, a.y), point(b.x, b.y),
                        strokeWidth = if (index == selectedPathIndex) 3.8f else 2.2f
                    )
                }
                if (path.closed && path.points.size >= 3) {
                    val first = path.points.first()
                    val last = path.points.last()
                    drawLine(
                        color, point(last.x, last.y), point(first.x, first.y),
                        strokeWidth = 2.2f
                    )
                }
                if (index == selectedPathIndex || index == dragIndex) {
                    val minX = path.points.minOf { it.x }
                    val minY = path.points.minOf { it.y }
                    val maxX = path.points.maxOf { it.x }
                    val maxY = path.points.maxOf { it.y }
                    val upperLeft = point(minX, minY)
                    val bottomRight = point(maxX, maxY)
                    drawRect(
                        color = DesktopBrandColors.goldSoft,
                        topLeft = upperLeft,
                        size = androidx.compose.ui.geometry.Size(
                            (bottomRight.x - upperLeft.x).coerceAtLeast(1f),
                            (bottomRight.y - upperLeft.y).coerceAtLeast(1f)
                        ),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }
    }
}
