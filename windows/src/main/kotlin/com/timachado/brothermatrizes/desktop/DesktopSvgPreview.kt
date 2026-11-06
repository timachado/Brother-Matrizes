package com.timachado.brothermatrizes.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork

/**
 * Prévia vetorial SEM execução ou renderização de XML/SVG no navegador.
 * Apenas desenha pontos já aceitos pelo parser Android e guarda Desktop.
 */
@Composable
internal fun DesktopSvgPreview(artwork: VectorArtwork) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .background(DesktopBrandColors.surfaceAlt, RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val points = artwork.paths.flatMap { it.points }
            if (points.isEmpty()) return@Canvas
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val geometryWidth = (maxX - minX).coerceAtLeast(0.001f)
            val geometryHeight = (maxY - minY).coerceAtLeast(0.001f)
            val scale = minOf(
                (size.width - 48f).coerceAtLeast(1f) / geometryWidth,
                (size.height - 48f).coerceAtLeast(1f) / geometryHeight
            )
            val dx = (size.width - geometryWidth * scale) / 2f
            val dy = (size.height - geometryHeight * scale) / 2f
            fun screen(x: Float, y: Float) = Offset(
                dx + (x - minX) * scale, dy + (y - minY) * scale
            )
            val color = DesktopBrandColors.gold
            artwork.paths.forEach { path ->
                path.points.zipWithNext().forEach { (a, b) ->
                    drawLine(color, screen(a.x, a.y), screen(b.x, b.y),
                        strokeWidth = 2.2f)
                }
                if (path.closed && path.points.size >= 3) {
                    val first = path.points.first()
                    val last = path.points.last()
                    drawLine(color, screen(last.x, last.y), screen(first.x, first.y),
                        strokeWidth = 2.2f)
                }
            }
        }
    }
}
