package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/** Leitura somente local de FLP v11, sem login, rede ou exclusão de arquivos. */
internal object DesktopProjectCatalog {
    private const val MAX_FILES = 200
    private const val MAX_BYTES = 64L * 1024L * 1024L

    data class ScanResult(
        val folder: Path,
        val projects: List<SavedProjectSummary>,
        val pathById: Map<String, Path>,
        val skippedInvalid: Int
    )

    fun scan(chosenFolder: Path): ScanResult {
        val folder = chosenFolder.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(folder) &&
            Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)
        ) { "Selecione uma pasta real de projetos, não um link simbólico." }

        // Listagem não recursiva: nunca percorrer milhares de arquivos,
        // diretórios de backup ou links que saem da pasta escolhida.
        val candidates = Files.list(folder).use { files ->
            files.filter { path ->
                !Files.isSymbolicLink(path) &&
                    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    path.fileName.toString().lowercase(Locale.ROOT).endsWith(".flp")
            }.limit(MAX_FILES.toLong() + 1L).toList()
        }
        require(candidates.size <= MAX_FILES) {
            "Pasta com mais de $MAX_FILES projetos FLP; selecione uma pasta menor."
        }

        var invalid = 0
        val entries = candidates.mapNotNull { file ->
            runCatching {
                val length = Files.size(file)
                require(length in 1..MAX_BYTES) {
                    "Projeto inválido ou maior que o limite de 64 MB."
                }
                val design = DesktopFlpProjectFiles.open(file)
                val summary = SavedProjectSummary(
                    id = file.toAbsolutePath().normalize().toString(),
                    title = design.label?.takeIf { it.isNotBlank() }
                        ?: file.fileName.toString().substringBeforeLast('.'),
                    fileName = file.fileName.toString(),
                    format = design.format,
                    stitchCount = design.stitchCount,
                    colorCount = design.colorCount,
                    widthMm = design.bounds.widthMm,
                    heightMm = design.bounds.heightMm,
                    updatedAtMillis = Files.getLastModifiedTime(file).toMillis()
                )
                summary to file
            }.getOrElse {
                invalid++
                null
            }
        }.sortedWith(compareByDescending<Pair<SavedProjectSummary, Path>> {
            it.first.updatedAtMillis
        }.thenBy { it.first.fileName })

        return ScanResult(
            folder = folder,
            projects = entries.map { it.first },
            pathById = entries.associate { it.first.id to it.second },
            skippedInvalid = invalid
        )
    }
}
