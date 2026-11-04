package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Biblioteca LOCAL de arquivos existentes, selecionada explicitamente pela pessoa.
 * Não copia, move, altera, indexa outras pastas nem acessa a rede.
 */
internal data class LocalMatrixEntry(
    val path: Path,
    val name: String,
    val format: String,
    val bytes: Long
)

internal object DesktopLocalLibrary {
    private const val MAX_ENTRIES = 500
    private const val MAX_VISITED = 10_000
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    fun list(folder: Path): List<LocalMatrixEntry> {
        require(Files.isDirectory(folder, *noFollow)) {
            "Selecione uma pasta local válida."
        }
        require(!Files.isSymbolicLink(folder)) {
            "Atalhos para outras pastas não são aceitos nesta QA."
        }

        val entries = ArrayList<LocalMatrixEntry>()
        var visited = 0
        Files.newDirectoryStream(folder).use { stream ->
            for (path in stream) {
                visited++
                require(visited <= MAX_VISITED) {
                    "Pasta com arquivos demais para esta versão de homologação."
                }
                if (!Files.isRegularFile(path, *noFollow) || Files.isSymbolicLink(path)) {
                    continue
                }
                val fileName = path.fileName.toString()
                val extension = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
                if (extension !in DesktopMatrixService.inputFormats) continue
                val length = Files.size(path)
                if (length <= 0L || length > DesktopMatrixService.MAX_INPUT_BYTES) continue
                entries += LocalMatrixEntry(
                    path = path.toAbsolutePath().normalize(),
                    name = fileName,
                    format = extension,
                    bytes = length
                )
                require(entries.size <= MAX_ENTRIES) {
                    "A pasta possui mais de 500 matrizes; escolha uma subpasta."
                }
            }
        }
        return entries.sortedWith(
            compareBy<LocalMatrixEntry> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.path.toString() }
        )
    }
}
