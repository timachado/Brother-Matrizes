package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPoint

/**
 * Edição controlada de um nó de um caminho SVG no Windows (QA).
 *
 * Opera sobre cópias da arte vetorial, nunca sobre o SVG importado ou FLP.
 * Unidades SVG não equivalem necessariamente a milímetros de bordado.
 * Inserir depois do último nó é permitido apenas em contornos fechados.
 */
internal enum class DesktopSvgNodeAction {
    LEFT, RIGHT, UP, DOWN, INSERT_AFTER, DELETE
}

internal data class DesktopSvgNodeChange(
    val artwork: VectorArtwork,
    val selectedNodeIndex: Int
)

internal object DesktopSvgNodeEditor {
    fun apply(
        artwork: VectorArtwork,
        pathIndex: Int,
        nodeIndex: Int,
        action: DesktopSvgNodeAction
    ): DesktopSvgNodeChange {
        DesktopSvgImport.validate(artwork)
        require(pathIndex in artwork.paths.indices) {
            "Caminho SVG selecionado não existe."
        }
        val path = artwork.paths[pathIndex]
        require(nodeIndex in path.points.indices) {
            "Nó SVG selecionado não existe."
        }
        val nodes = path.points.toMutableList()
        val selected: Int
        when (action) {
            DesktopSvgNodeAction.LEFT,
            DesktopSvgNodeAction.RIGHT,
            DesktopSvgNodeAction.UP,
            DesktopSvgNodeAction.DOWN -> {
                val original = nodes[nodeIndex]
                val dx = when (action) {
                    DesktopSvgNodeAction.LEFT -> -1.0
                    DesktopSvgNodeAction.RIGHT -> 1.0
                    else -> 0.0
                }
                val dy = when (action) {
                    DesktopSvgNodeAction.UP -> -1.0
                    DesktopSvgNodeAction.DOWN -> 1.0
                    else -> 0.0
                }
                val x = original.x.toDouble() + dx
                val y = original.y.toDouble() + dy
                require(x in -1_000_000.0..1_000_000.0 &&
                    y in -1_000_000.0..1_000_000.0) {
                    "Movimento de nó ultrapassa limite das coordenadas SVG."
                }
                nodes[nodeIndex] = VectorPoint(x.toFloat(), y.toFloat())
                selected = nodeIndex
            }

            DesktopSvgNodeAction.INSERT_AFTER -> {
                require(nodes.size < DesktopSvgImport.MAX_POINTS_PER_PATH &&
                    artwork.paths.sumOf { it.points.size } < DesktopSvgImport.MAX_POINTS) {
                    "Limite de nós SVG atingido."
                }
                val nextIndex = when {
                    nodeIndex < nodes.lastIndex -> nodeIndex + 1
                    path.closed -> 0
                    else -> throw IllegalArgumentException(
                        "Em caminho aberto, selecione um nó que tenha um próximo segmento."
                    )
                }
                val a = nodes[nodeIndex]
                val b = nodes[nextIndex]
                val center = VectorPoint(
                    ((a.x.toDouble() + b.x.toDouble()) / 2.0).toFloat(),
                    ((a.y.toDouble() + b.y.toDouble()) / 2.0).toFloat()
                )
                nodes.add(nodeIndex + 1, center)
                selected = nodeIndex + 1
            }

            DesktopSvgNodeAction.DELETE -> {
                val minNodes = if (path.closed) 3 else 2
                require(nodes.size > minNodes) {
                    "Este caminho precisa manter ao menos $minNodes nós."
                }
                nodes.removeAt(nodeIndex)
                selected = nodeIndex.coerceAtMost(nodes.lastIndex)
            }
        }

        val paths = artwork.paths.toMutableList()
        paths[pathIndex] = path.copy(points = nodes)
        val updated = artwork.copy(paths = paths)
        // Pode recusar uma edição que colapse a largura/altura do desenho.
        DesktopSvgImport.validate(updated)
        return DesktopSvgNodeChange(updated, selected)
    }
}
