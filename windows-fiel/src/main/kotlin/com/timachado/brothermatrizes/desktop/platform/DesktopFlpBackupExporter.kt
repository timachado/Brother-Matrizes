package com.timachado.brothermatrizes.desktop.platform

import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Only the local FLP project subset of Android's "Backup completo".
 *
 * This is deliberately named FLP, not "full backup": Android's full backup
 * also includes fonts and app state; those components have not been ported.
 * Safe export only, no remote I/O, no restoration and no overwrites.
 */
internal object DesktopFlpBackupExporter {
    private const val MAX_PROJECTS = 200
    private const val MAX_EACH = 64L * 1024L * 1024L
    private const val MAX_TOTAL = 128L * 1024L * 1024L

    data class ExportResult(
        val destination: Path,
        val exportedProjects: Int,
        val totalSourceBytes: Long
    )

    fun exportLocalFlp(chosenFolder: Path, outputZip: Path): ExportResult {
        val catalog = DesktopProjectCatalog.scan(chosenFolder)
        val sourceFolder = catalog.folder
        val destination = outputZip.toAbsolutePath().normalize()
        require(destination.fileName.toString().lowercase(Locale.ROOT).endsWith(".zip")) {
            "Escolha um arquivo .zip para o backup local de projetos."
        }
        require(!Files.isSymbolicLink(destination)) {
            "O destino do backup não pode ser um link simbólico."
        }
        require(Files.isDirectory(destination.parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(destination.parent)) {
            "Escolha uma pasta real e existente para o backup."
        }
        val ordered = catalog.pathById.values.sortedBy { it.fileName.toString().lowercase(Locale.ROOT) }
        require(ordered.isNotEmpty()) {
            "Nenhum projeto FLP válido foi encontrado na pasta selecionada."
        }
        require(ordered.size <= MAX_PROJECTS) {
            "A pasta excede o limite de projetos para backup."
        }
        require(ordered.map { it.fileName.toString().lowercase(Locale.ROOT) }.toSet().size ==
            ordered.size) {
            "Projetos com nomes iguais variando maiúsculas não podem ser exportados juntos."
        }
        // Keep empty/invalid/missing projects out; fail if the folder has a
        // mix of valid and invalid FLP rather than silently claiming complete.
        require(catalog.skippedInvalid == 0) {
            "Há projetos FLP inválidos. Corrija a pasta antes de criar um backup íntegro."
        }

        var total = 0L
        for (source in ordered) {
            require(source.parent == sourceFolder &&
                !Files.isSymbolicLink(source) &&
                Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                "Projeto inválido ou fora da pasta selecionada."
            }
            val name = source.fileName.toString()
            require(name.length in 5..200 &&
                name.endsWith(".flp", ignoreCase = true) &&
                name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it.isISOControl() }) {
                "Nome de projeto incompatível com ZIP seguro."
            }
            val length = Files.size(source)
            require(length in 1..MAX_EACH && length <= MAX_TOTAL - total) {
                "Backup local excederia o limite de segurança de 128 MB."
            }
            total += length
        }

        var created = false
        try {
            // CREATE_NEW is essential: we never replace or truncate a backup.
            val output = Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            )
            created = true
            ZipOutputStream(output.buffered()).use { archive ->
                for (source in ordered) {
                    val name = source.fileName.toString()
                    archive.putNextEntry(ZipEntry(name).apply { time = 0L })
                    // NOFOLLOW_LINKS protects against a swapped source symlink.
                    BufferedInputStream(Files.newInputStream(
                        source, LinkOption.NOFOLLOW_LINKS
                    )).use { input ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        while (true) {
                            val bytes = input.read(buffer)
                            if (bytes < 0) break
                            copied += bytes
                            check(copied <= MAX_EACH && copied <= MAX_TOTAL) {
                                "Projeto mudou durante o backup."
                            }
                            archive.write(buffer, 0, bytes)
                        }
                        check(copied == Files.size(source)) {
                            "Projeto alterado durante a exportação."
                        }
                    }
                    archive.closeEntry()
                }
            }
            return ExportResult(destination, ordered.size, total)
        } catch (failure: Exception) {
            if (created) {
                try {
                    Files.deleteIfExists(destination)
                } catch (cleanup: Exception) {
                    failure.addSuppressed(cleanup)
                }
            }
            throw failure
        }
    }
}
