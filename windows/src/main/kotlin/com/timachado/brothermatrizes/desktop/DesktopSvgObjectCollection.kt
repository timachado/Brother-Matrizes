package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorPoint

/**
 * Operações estruturais em caminhos vetoriais da sessão Windows.
 *
 * Nunca altera o SVG importado, o FLP no disco ou matrizes importadas
 * DST/PES/JEF; só devolve uma cópia validada para DesktopSvgEditSession.
 * A seleção é devolvida junto à geometria para não apontar a um índice velho.
 */
internal data class DesktopSvgCollectionChange(
    val artwork: VectorArtwork,
    val selectedIndex: Int
)

internal object DesktopSvgObjectCollection {
    const val DUPLICATE_OFFSET_UNITS = 10f

    fun duplicate(artwork: VectorArtwork, index: Int): DesktopSvgCollectionChange {
        DesktopSvgImport.validate(artwork)
        require(index in artwork.paths.indices) {
            "Selecione um caminho SVG válido antes de duplicar."
        }
        require(artwork.paths.size < DesktopSvgImport.MAX_PATHS) {
            "Limite de caminhos SVG atingido; duplicação recusada."
        }
        val source = artwork.paths[index]
        // Deslocamento visível em coordenadas SVG, e NÃO em mm de bordado.
        val copied = source.copy(points = source.points.map {
            VectorPoint(
                it.x + DUPLICATE_OFFSET_UNITS,
                it.y + DUPLICATE_OFFSET_UNITS
            )
        })
        val paths = artwork.paths.toMutableList()
        val addedIndex = index + 1
        paths.add(addedIndex, copied)
        val next = artwork.copy(paths = paths)
        DesktopSvgImport.validate(next) // inclui limites de pontos e coordenadas
        return DesktopSvgCollectionChange(next, addedIndex)
    }

    fun delete(artwork: VectorArtwork, index: Int): DesktopSvgCollectionChange {
        DesktopSvgImport.validate(artwork)
        require(index in artwork.paths.indices) {
            "Selecione um caminho SVG válido antes de excluir."
        }
        require(artwork.paths.size > 1) {
            "A última forma SVG não pode ser excluída."
        }
        val paths = artwork.paths.toMutableList()
        paths.removeAt(index)
        val next = artwork.copy(paths = paths)
        // Impede excluir a geometria necessária à largura/altura do desenho.
        DesktopSvgImport.validate(next)
        return DesktopSvgCollectionChange(
            artwork = next,
            selectedIndex = index.coerceAtMost(paths.lastIndex)
        )
    }
}
