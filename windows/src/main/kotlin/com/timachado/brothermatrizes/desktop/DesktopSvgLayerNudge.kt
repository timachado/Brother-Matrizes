package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPoint

/**
 * Movimenta um caminho SVG por UMA unidade vetorial no eixo escolhido.
 *
 * Unidade SVG é uma coordenada do desenho de origem, não milímetro de bordado.
 * O arquivo SVG importado permanece intacto. O resultado alimenta somente
 * a sessão reversível DesktopSvgEditSession existente.
 */
internal object DesktopSvgLayerNudge {
    fun move(
        artwork: VectorArtwork,
        index: Int,
        deltaX: Int,
        deltaY: Int
    ): VectorArtwork {
        require(index in artwork.paths.indices) {
            "Selecione um caminho SVG válido."
        }
        require((deltaX == 0 && (deltaY == 1 || deltaY == -1)) ||
                (deltaY == 0 && (deltaX == 1 || deltaX == -1))) {
            "O ajuste fino permite apenas uma unidade vetorial em um eixo."
        }
        // Nunca calcular os valores a partir do canvas/zoom: usa as
        // coordenadas locais que o parser Android reconheceu.
        val updatedPaths = artwork.paths.toMutableList()
        val source = updatedPaths[index]
        updatedPaths[index] = source.copy(
            points = source.points.map { point ->
                VectorPoint(point.x + deltaX, point.y + deltaY)
            }
        )
        val updated = artwork.copy(paths = updatedPaths)
        DesktopSvgImport.validate(updated) // rejeita extremos ou contornos inválidos
        return updated
    }
}
