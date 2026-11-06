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

    /**
     * Altera em uma posição a ordem dos caminhos SVG. A ordem é significativa:
     * quando salvar como FLP novo, afeta a sequência gerada de pontos.
     * Não é uma operação de sobreposição gráfica ("z-index").
     */
    fun reorder(
        artwork: VectorArtwork,
        index: Int,
        direction: Int
    ): DesktopSvgCollectionChange {
        DesktopSvgImport.validate(artwork)
        require(direction == -1 || direction == 1) {
            "Reordenação SVG permite apenas subir ou descer uma posição."
        }
        require(index in artwork.paths.indices &&
            index.toLong() + direction.toLong() in artwork.paths.indices.first.toLong()..
                artwork.paths.indices.last.toLong()) {
            "Caminho não pode ultrapassar o limite da sequência."
        }
        val nextIndex = index + direction
        val paths = artwork.paths.toMutableList()
        val current = paths.removeAt(index)
        paths.add(nextIndex, current)
        val next = artwork.copy(paths = paths)
        DesktopSvgImport.validate(next)
        return DesktopSvgCollectionChange(next, nextIndex)
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
