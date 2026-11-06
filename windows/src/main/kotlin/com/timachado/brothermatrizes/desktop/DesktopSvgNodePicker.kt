package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import kotlin.math.abs

/** Um nó encontrado na geometria de uma arte SVG, sem alterar o desenho. */
internal data class DesktopSvgNodeHit(
    val pathIndex: Int,
    val nodeIndex: Int
)

/**
 * Seleciona o nó mais próximo do cursor em coordenadas da PRÉVIA.
 *
 * A mesma viewport que desenha o SVG converte cada nó para pixels.
 * Empates favorecem o caminho já selecionado, sem trocar de objeto
 * inadvertidamente quando dois caminhos possuem nós coincidentes.
 * A tolerância é em pixels, não em unidades SVG nem em milímetros.
 */
internal object DesktopSvgNodePicker {
    const val RADIUS_PX = 11f
    private const val TIE_EPSILON = 0.0001

    fun pick(
        artwork: VectorArtwork,
        width: Float,
        height: Float,
        cursor: Offset,
        preferredPathIndex: Int? = null
    ): DesktopSvgNodeHit? {
        require(cursor.x.isFinite() && cursor.y.isFinite()) {
            "Posição do cursor SVG inválida."
        }
        val viewport = DesktopSvgPathEditor.viewport(artwork, width, height)
        val preferred = preferredPathIndex?.takeIf { it in artwork.paths.indices }
        val limitSquared = RADIUS_PX.toDouble() * RADIUS_PX.toDouble()
        var bestDistance = limitSquared
        var best: DesktopSvgNodeHit? = null

        artwork.paths.forEachIndexed { pathIndex, path ->
            path.points.forEachIndexed { nodeIndex, node ->
                val target = viewport.point(node)
                val dx = cursor.x.toDouble() - target.x.toDouble()
                val dy = cursor.y.toDouble() - target.y.toDouble()
                val distance = dx * dx + dy * dy
                if (distance <= limitSquared) {
                    val better = best == null ||
                        distance < bestDistance - TIE_EPSILON ||
                        (abs(distance - bestDistance) <= TIE_EPSILON &&
                            pathIndex == preferred && best?.pathIndex != preferred)
                    if (better) {
                        bestDistance = distance
                        best = DesktopSvgNodeHit(pathIndex, nodeIndex)
                    }
                }
            }
        }
        return best
    }
}
