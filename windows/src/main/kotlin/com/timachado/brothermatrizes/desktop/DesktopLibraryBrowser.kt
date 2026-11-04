package com.timachado.brothermatrizes.desktop

import java.util.Locale

/**
 * Pesquisa e pagina SOMENTE o resultado da pasta já autorizada e lida
 * por DesktopLocalLibrary. Não percorre o disco, redes ou subdiretórios.
 */
internal data class DesktopLibraryPage(
    val items: List<LocalMatrixEntry>,
    val matching: Int,
    val currentPage: Int,
    val totalPages: Int
)

internal object DesktopLibraryBrowser {
    const val PAGE_SIZE: Int = 40
    val filters: List<String> = listOf("TODOS", "DST", "PES", "JEF", "FLP")

    fun page(
        entries: List<LocalMatrixEntry>,
        query: String,
        format: String,
        page: Int
    ): DesktopLibraryPage {
        require(format in filters) { "Filtro de arquivo não reconhecido." }
        require(page >= 0) { "Página inválida." }
        val search = query.trim().take(80).lowercase(Locale.ROOT)
        val matches = entries.filter { entry ->
            (format == "TODOS" || entry.format == format) &&
                (search.isEmpty() || entry.name.lowercase(Locale.ROOT).contains(search))
        }
        val count = matches.size
        val numberOfPages = if (count == 0) 0 else ((count - 1) / PAGE_SIZE) + 1
        val safePage = if (numberOfPages == 0) 0 else page.coerceAtMost(numberOfPages - 1)
        val offset = safePage * PAGE_SIZE
        return DesktopLibraryPage(
            items = matches.drop(offset).take(PAGE_SIZE),
            matching = count,
            currentPage = safePage,
            totalPages = numberOfPages
        )
    }
}
