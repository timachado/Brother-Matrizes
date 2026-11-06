package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Transformações geométricas de UM caminho SVG em sua própria origem.
 *
 * A transformação é apenas do vetor, não das pontadas FLP nem do SVG no disco.
 * O ponto de referência é o centro da caixa delimitadora do caminho; todos
 * os pontos passam pelo validador já utilizado pelo parser oficial Android.
 */
internal enum class DesktopSvgObjectAction {
    SCALE_DOWN, SCALE_UP, ROTATE_LEFT, ROTATE_RIGHT
}

internal object DesktopSvgObjectTransform {
    fun apply(
        artwork: VectorArtwork,
        selectedIndex: Int,
        action: DesktopSvgObjectAction
    ): VectorArtwork {
        DesktopSvgImport.validate(artwork)
        require(selectedIndex in artwork.paths.indices) {
            "Selecione um caminho vetorial válido."
        }
        val path = artwork.paths[selectedIndex]
        // Usar Double antes de calcular o centro evita perdas em extremos de Float.
        val minX = path.points.minOf { it.x }.toDouble()
        val maxX = path.points.maxOf { it.x }.toDouble()
        val minY = path.points.minOf { it.y }.toDouble()
        val maxY = path.points.maxOf { it.y }.toDouble()
        val centerX = minX + (maxX - minX) / 2.0
        val centerY = minY + (maxY - minY) / 2.0
        val factor = when (action) {
            DesktopSvgObjectAction.SCALE_DOWN -> 0.90
            DesktopSvgObjectAction.SCALE_UP -> 1.10
            else -> 1.0
        }
        val degrees = when (action) {
            DesktopSvgObjectAction.ROTATE_LEFT -> -15.0
            DesktopSvgObjectAction.ROTATE_RIGHT -> 15.0
            else -> 0.0
        }
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians)
        val sine = sin(radians)
        val changedPoints = path.points.map { point ->
            val dx = (point.x.toDouble() - centerX) * factor
            val dy = (point.y.toDouble() - centerY) * factor
            val x = centerX + dx * cosine - dy * sine
            val y = centerY + dx * sine + dy * cosine
            require(x.isFinite() && y.isFinite() &&
                x in -1_000_000.0..1_000_000.0 &&
                y in -1_000_000.0..1_000_000.0) {
                "Transformação ultrapassa os limites seguros do desenho."
            }
            VectorPoint(x.toFloat(), y.toFloat())
        }
        val updatedPaths = artwork.paths.toMutableList()
        updatedPaths[selectedIndex] = path.copy(points = changedPoints)
        val updated = artwork.copy(paths = updatedPaths)
        DesktopSvgImport.validate(updated)
        return updated
    }
}
