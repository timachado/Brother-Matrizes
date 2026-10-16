package com.timachado.brothermatrizes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.timachado.brothermatrizes.core.embroidery.RasterVectorContour
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.ManualSatinSection
import com.timachado.brothermatrizes.core.embroidery.SatinRailPair
import com.timachado.brothermatrizes.core.embroidery.SeparatedColorArtwork
import kotlin.math.hypot
import kotlin.math.min

/**
 * Editor tátil dos DOIS trilhos: dourado A, azul B.
 * Os controles são normalizados ao recorte, não ao tamanho do telefone.
 */
private data class SatinCanvasArea(
    val left: Float, val top: Float, val width: Float, val height: Float
) {
    fun position(u: Float, v: Float) =
        Offset(left + u * width, top + v * height)
    fun normalized(position: Offset) = Offset(
        ((position.x - left) / width).coerceIn(0f, 1f),
        ((position.y - top) / height).coerceIn(0f, 1f)
    )
}

private fun satinCanvasArea(
    canvasWidth: Float,
    canvasHeight: Float,
    art: SeparatedColorArtwork
): SatinCanvasArea {
    val w = (art.maxX - art.minX + 1).coerceAtLeast(1)
    val h = (art.maxY - art.minY + 1).coerceAtLeast(1)
    val ratio = min(
        (canvasWidth * 0.92f) / w,
        (canvasHeight * 0.92f) / h
    )
    val width = w * ratio
    val height = h * ratio
    return SatinCanvasArea(
        left = (canvasWidth - width) / 2f,
        top = (canvasHeight - height) / 2f,
        width = width,
        height = height
    )
}

@Composable
internal fun ManualSatinRailCanvas(
    art: SeparatedColorArtwork,
    imageWidth: Int,
    colorIndex: Int,
    section: ManualSatinSection,
    vectorContours: List<RasterVectorContour> = emptyList(),
    onBeginDrag: () -> Unit,
    onEdit: (ManualSatinSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSection by rememberUpdatedState(section)
    val onEditCurrent by rememberUpdatedState(onEdit)
    val onBeginDragCurrent by rememberUpdatedState(onBeginDrag)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(art, imageWidth, colorIndex) {
                var active = -1
                detectDragGestures(
                    onDragStart = { at ->
                        val region = satinCanvasArea(
                            size.width.toFloat(), size.height.toFloat(), art
                        )
                        var nearest = Float.POSITIVE_INFINITY
                        active = -1
                        for ((index, rail) in currentSection.rails.withIndex()) {
                            for ((side, p) in listOf(
                                rail.ax to rail.ay, rail.bx to rail.by
                            ).withIndex()) {
                                val center = region.position(p.first, p.second)
                                val distance = hypot(
                                    (at.x - center.x).toDouble(),
                                    (at.y - center.y).toDouble()
                                ).toFloat()
                                if (distance < nearest) {
                                    nearest = distance
                                    active = index * 2 + side
                                }
                            }
                        }
                        if (nearest > 42.dp.toPx()) active = -1
                        if (active >= 0) onBeginDragCurrent()
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                    onDrag = { change, _ ->
                        if (active >= 0) {
                            change.consume()
                            val region = satinCanvasArea(
                                size.width.toFloat(), size.height.toFloat(), art
                            )
                            val n = region.normalized(change.position)
                            val index = active / 2
                            val aSide = active % 2 == 0
                            val current = currentSection
                            if (index in current.rails.indices) {
                                val rails = current.rails.toMutableList()
                                val original = rails[index]
                                rails[index] = if (aSide) original.copy(
                                    ax = n.x, ay = n.y
                                ) else original.copy(
                                    bx = n.x, by = n.y
                                )
                                onEditCurrent(current.copy(rails = rails))
                            }
                        }
                    }
                )
            }
    ) {
        val area = satinCanvasArea(size.width, size.height, art)
        val sx = area.width / (art.maxX - art.minX + 1)
        val sy = area.height / (art.maxY - art.minY + 1)
        drawRect(
            color = Color(0xFF101D26),
            topLeft = Offset(area.left, area.top),
            size = Size(area.width, area.height)
        )
        // Visualização de máscara da camada escolhida, sem alterar pontos PES.
        for (y in art.minY..art.maxY) {
            for (x in art.minX..art.maxX) {
                if (art.pixels[y * imageWidth + x] != colorIndex) continue
                drawRect(
                    color = Color(0xFF5B6A75).copy(alpha = .46f),
                    topLeft = Offset(
                        area.left + (x - art.minX) * sx,
                        area.top + (y - art.minY) * sy
                    ),
                    size = Size(sx + .5f, sy + .5f)
                )
            }
        }
        // Exact extracted pixel-edge loops, including counters (holes).
        // These are references for rail placement, NEVER stitch commands.
        for (loop in vectorContours) {
            if (loop.vertices.size < 3) continue
            val path = Path()
            val first = loop.vertices.first()
            path.moveTo(area.left + first.x * sx, area.top + first.y * sy)
            for (i in 1 until loop.vertices.size) {
                val p = loop.vertices[i]
                path.lineTo(area.left + p.x * sx, area.top + p.y * sy)
            }
            path.close()
            drawPath(
                path = path,
                color = if (loop.isHole) Color(0xFFFF9069) else Color(0xFF63D5E3),
                style = Stroke(width = 1.8f)
            )
        }
        val rails = section.rails
        for (i in rails.indices) {
            val rail = rails[i]
            val a = area.position(rail.ax, rail.ay)
            val b = area.position(rail.bx, rail.by)
            if (i > 0) {
                val previous = rails[i - 1]
                drawLine(
                    Color(0xFFF2B75A),
                    area.position(previous.ax, previous.ay), a,
                    strokeWidth = 2.5f, cap = StrokeCap.Round
                )
                drawLine(
                    Color(0xFF62BCEF),
                    area.position(previous.bx, previous.by), b,
                    strokeWidth = 2.5f, cap = StrokeCap.Round
                )
            }
            drawLine(
                color = Color(0xFFB5D0DB).copy(alpha = .8f),
                start = a, end = b,
                strokeWidth = 1.5f
            )
            drawCircle(Color(0xFFF2B75A), radius = 7f, center = a)
            drawCircle(Color(0xFF62BCEF), radius = 7f, center = b)
        }
    }
}
