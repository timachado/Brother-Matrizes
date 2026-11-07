package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile

/**
 * Restores ONLY flat, local FLP ZIP archives (DesktopFlpBackupExporter).
 *
 * Not the Android .brother_matrizes-fullbackup format. The chosen parent
 * folder is never modified except for adding one NEW, validated child folder.
 * Failures discard only files created inside this operation's private staging
 * directory. No overwrite, symlink traversal, remote access or account state.
 */
internal object DesktopFlpBackupRestorer {
    private const val MAX_PROJECTS = 200
    private const val MAX_EACH = 64L * 1024L * 1024L
    private const val MAX_TOTAL = 128L * 1024L * 1024L

    data class RestoreResult(
        val folder: Path,
        val importedProjects: Int,
        val totalUncompressedBytes: Long
    )

    fun restoreLocalFlpZip(zipFile: Path, chosenParent: Path): RestoreResult {
        val archive = zipFile.toAbsolutePath().normalize()
        val parent = chosenParent.toAbsolutePath().normalize()
        require(archive.fileName.toString().endsWith(".zip", ignoreCase = true) &&
            !Files.isSymbolicLink(archive) &&
            Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(archive) in 1..MAX_TOTAL) {
            "Selecione um ZIP local de projetos FLP válido, com até 128 MB."
        }
        require(!Files.isSymbolicLink(parent) &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            "Selecione uma pasta de destino real, sem link simbólico."
        }

        // ZipInputStream validates each entry's CRC, but does NOT require a
        // complete central directory. Reject truncated and malformed ZIPs
        // before creating even the temporary staging folder.
        ZipFile(archive.toFile()).use { index ->
            require(index.size() in 1..MAX_PROJECTS) {
                "Backup FLP vazio, incompleto ou com excesso de projetos."
            }
        }

        // Never unzip directly in the user's original project folder.
        val staging = Files.createTempDirectory(parent, ".brother-flp-restore-")
        val stagedFiles = ArrayList<Path>()
        var published = false
        try {
            val knownNames = HashSet<String>()
            var total = 0L
            var count = 0

            ZipInputStream(Files.newInputStream(
                archive, LinkOption.NOFOLLOW_LINKS
            ).buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) {
                        "Pastas ou diretórios não são permitidos neste backup FLP."
                    }
                    val name = entry.name
                    validateFlatWindowsFlpName(name)
                    val canonical = Normalizer.normalize(name, Normalizer.Form.NFC)
                        .lowercase(Locale.ROOT)
                    require(knownNames.add(canonical)) {
                        "O backup contém projetos com nomes duplicados."
                    }
                    count++
                    require(count <= MAX_PROJECTS) {
                        "O backup excede o limite de 200 projetos FLP."
                    }
                    require(entry.size < 0 || entry.size in 1..MAX_EACH) {
                        "O backup contém um projeto maior que 64 MB ou vazio."
                    }

                    val target = staging.resolve(name).normalize()
                    require(target.parent == staging && !Files.exists(
                        target, LinkOption.NOFOLLOW_LINKS
                    )) {
                        "Nome de projeto inválido ou repetido no backup."
                    }
                    var copied = 0L
                    Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                    ).use { out ->
                        stagedFiles.add(target)
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val received = zip.read(buffer)
                            if (received < 0) break
                            if (received == 0) continue
                            copied += received
                            require(copied <= MAX_EACH && copied <= MAX_TOTAL - total) {
                                "Backup excede o limite seguro de 128 MB."
                            }
                            out.write(buffer, 0, received)
                        }
                    }
                    require(copied > 0L) { "Projeto FLP vazio no backup." }
                    require(entry.size < 0 || entry.size == copied) {
                        "Tamanho do projeto FLP inconsistente no backup."
                    }
                    total += copied
                    zip.closeEntry() // CRC failure also aborts the operation.

                    // Validate every project with the same FLP v11 decoder
                    // used by Android, BEFORE making the new folder visible.
                    DesktopFlpProjectFiles.open(target)
                }
            }
            require(count > 0) { "O ZIP não contém projetos FLP válidos." }
            val verifiedCatalog = DesktopProjectCatalog.scan(staging)
            check(verifiedCatalog.projects.size == count &&
                verifiedCatalog.skippedInvalid == 0) {
                "Não foi possível verificar integralmente o backup FLP."
            }

            // An unpredictable, new child directory prevents clobbering any
            // existing FLP or user directory. No REPLACE_EXISTING is used.
            val destination = parent.resolve(
                "Brother-Matrizes-FLP-Restaurados-" + UUID.randomUUID()
            )
            require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                "Pasta de restauração já existe."
            }
            Files.move(staging, destination)
            published = true
            return RestoreResult(destination, count, total)
        } finally {
            if (!published) {
                for (path in stagedFiles.asReversed()) {
                    Files.deleteIfExists(path)
                }
                Files.deleteIfExists(staging)
            }
        }
    }

    private fun validateFlatWindowsFlpName(name: String) {
        require(name.length in 5..200 &&
            name.endsWith(".flp", ignoreCase = true) &&
            name.none { it.isISOControl() || it in "<>:\"/\\|?*" ||
                it in "\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069"
            }) {
            "Entrada insegura ou diferente de FLP na restauração."
        }
        val stem = name.substringBeforeLast('.')
        require(stem.isNotBlank() && !stem.endsWith('.') &&
            !stem.endsWith(' ') && !name.startsWith('.') &&
            !name.startsWith(' ')) {
            "Nome de projeto incompatível com o Windows."
        }
        val windowsDevice = stem.substringBefore('.').uppercase(Locale.ROOT)
        require(windowsDevice !in setOf("CON", "PRN", "AUX", "NUL") &&
            !windowsDevice.matches(Regex("COM[1-9]|LPT[1-9]"))) {
            "Nome reservado do Windows não pode ser restaurado."
        }
    }
}
