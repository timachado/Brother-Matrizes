package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.math.abs

/**
 * Transformações vetoriais não destrutivas para caminhos SVG já analisados
 * pelo parser oficial do Android. Não altera ou regrava o arquivo fonte.
 */
internal data class DesktopSvgViewport(
    val minX: Float,
    val minY: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun point(p: VectorPoint) = Offset(
        offsetX + (p.x - minX) * scale,
        offsetY + (p.y - minY) * scale
    )
}

internal object DesktopSvgPathEditor {
    private const val MARGIN_PX = 24f
    private const val HIT_RADIUS_PX = 12f
    private const val MAX_DRAG_PX = 20_000f

    fun viewport(artwork: VectorArtwork, width: Float, height: Float): DesktopSvgViewport {
        require(width.isFinite() && height.isFinite() &&
            width > 2f * MARGIN_PX && height > 2f * MARGIN_PX) {
            "Área de edição SVG inválida."
        }
        DesktopSvgImport.validate(artwork)
        val points = artwork.paths.flatMap { it.points }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val contentWidth = maxX - minX
        val contentHeight = maxY - minY
        val scale = minOf(
            (width - MARGIN_PX * 2f) / contentWidth,
            (height - MARGIN_PX * 2f) / contentHeight
        )
        require(scale.isFinite() && scale > 0f) { "Escala SVG inválida." }
        return DesktopSvgViewport(
            minX, minY, scale,
            (width - contentWidth * scale) / 2f,
            (height - contentHeight * scale) / 2f
        )
    }

    fun select(
        artwork: VectorArtwork,
        width: Float, height: Float, cursor: Offset
    ): Int? {
        require(cursor.x.isFinite() && cursor.y.isFinite()) {
            "Posição inválida do ponteiro."
        }
        val viewport = viewport(artwork, width, height)
        var closest: Int? = null
        var best = HIT_RADIUS_PX * HIT_RADIUS_PX
        artwork.paths.forEachIndexed { index, path ->
            val points = path.points
            val segments = points.zipWithNext().toMutableList()
            if (path.closed && points.size >= 3) {
                segments += points.last() to points.first()
            }
            for ((a, b) in segments) {
                val first = viewport.point(a)
                val last = viewport.point(b)
                val dx = last.x - first.x
                val dy = last.y - first.y
                val squared = dx * dx + dy * dy
                val projection = if (squared > 0f) {
                    ((cursor.x - first.x) * dx +
                        (cursor.y - first.y) * dy) / squared
                } else 0f
                val t = projection.coerceIn(0f, 1f)
                val distX = cursor.x - (first.x + t * dx)
                val distY = cursor.y - (first.y + t * dy)
                val distance = distX * distX + distY * distY
                if (distance <= best) {
                    best = distance
                    closest = index
                }
            }
        }
        return closest
    }

    fun move(
        artwork: VectorArtwork,
        index: Int,
        width: Float, height: Float, delta: Offset
    ): VectorArtwork {
        require(index in artwork.paths.indices) {
            "Selecione um caminho SVG válido."
        }
        require(delta.x.isFinite() && delta.y.isFinite() &&
            abs(delta.x) <= MAX_DRAG_PX && abs(delta.y) <= MAX_DRAG_PX) {
            "Arrasto SVG inválido ou excessivo."
        }
        if (delta == Offset.Zero) return artwork
        val scale = viewport(artwork, width, height).scale
        val dx = delta.x / scale
        val dy = delta.y / scale
        require(dx.isFinite() && dy.isFinite()) {
            "Arrasto SVG não finito."
        }
        val paths = artwork.paths.toMutableList()
        val original = paths[index]
        paths[index] = original.copy(
            points = original.points.map {
                VectorPoint(it.x + dx, it.y + dy)
            }
        )
        val updated = artwork.copy(paths = paths)
        DesktopSvgImport.validate(updated)
        return updated
    }
}

/** Histórico leve: até oito revisões vetoriais por sessão, sem disco. */
internal data class DesktopSvgEditSession private constructor(
    private val revisions: List<VectorArtwork>,
    val position: Int
) {
    companion object {
        const val MAX_UNDO = 8
        fun start(artwork: VectorArtwork): DesktopSvgEditSession {
            DesktopSvgImport.validate(artwork)
            return DesktopSvgEditSession(listOf(artwork), 0)
        }
    }

    val current: VectorArtwork get() = revisions[position]
    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < revisions.lastIndex

    fun record(artwork: VectorArtwork): DesktopSvgEditSession {
        DesktopSvgImport.validate(artwork)
        if (artwork == current) return this
        val revisionsNext = (revisions.take(position + 1) + artwork)
            .takeLast(MAX_UNDO + 1)
        return DesktopSvgEditSession(revisionsNext, revisionsNext.lastIndex)
    }

    fun undo(): DesktopSvgEditSession {
        check(canUndo) { "Não há edição SVG para desfazer." }
        return copy(position = position - 1)
    }

    fun redo(): DesktopSvgEditSession {
        check(canRedo) { "Não há edição SVG para refazer." }
        return copy(position = position + 1)
    }
}
