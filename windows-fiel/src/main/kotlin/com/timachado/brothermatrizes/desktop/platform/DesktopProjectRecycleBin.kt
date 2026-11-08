package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.project.SavedProjectSummary
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Windows equivalent of removing a local FLP project from its library:
 * move to the operating system Recycle Bin; never permanently delete.
 *
 * Called ONLY after a human-confirmed UI action. Does not affect the
 * Android app, Supabase, server quotas, account files or backups.
 *
 * The library is rescanned immediately before the operation; stale or
 * changed files fail closed instead of removing a different customer file.
 */
internal object DesktopProjectRecycleBin {
    fun moveConfirmed(
        libraryFolder: Path,
        project: SavedProjectSummary,
        osName: String = System.getProperty("os.name"),
        recycle: (Path) -> Boolean = { file ->
            check(Desktop.isDesktopSupported()) {
                "Lixeira do sistema indisponível."
            }
            val desktop = Desktop.getDesktop()
            check(desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                "Lixeira do Windows não suportada neste computador."
            }
            desktop.moveToTrash(file.toFile())
        }
    ): Path {
        check(osName.startsWith("Windows", ignoreCase = true)) {
            "Exclusão recuperável disponível somente no Windows."
        }
        val catalog = DesktopProjectCatalog.scan(libraryFolder)
        val file = catalog.pathById[project.id]
            ?: error("Projeto não encontrado na pasta selecionada.")
        val normalized = file.toAbsolutePath().normalize()
        require(normalized.parent == catalog.folder &&
            !Files.isSymbolicLink(normalized) &&
            Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) &&
            normalized.fileName.toString().lowercase(Locale.ROOT).endsWith(".flp")) {
            "Projeto inválido para envio à Lixeira."
        }
        val latest = catalog.projects.singleOrNull { it.id == project.id }
            ?: error("Projeto alterado na Biblioteca.")
        check(latest.fileName == project.fileName &&
            latest.updatedAtMillis == project.updatedAtMillis &&
            latest.stitchCount == project.stitchCount &&
            latest.format == project.format) {
            "Projeto mudou desde a última atualização. Reabra a Biblioteca."
        }
        check(recycle(normalized)) {
            "O Windows não confirmou o envio à Lixeira."
        }
        return normalized
    }
}
