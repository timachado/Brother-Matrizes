package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Backup local, opt-in, somente de projeto FLP. Nunca altera fontes nem
 * recupera dados da conta Android. O sidecar é opcional para leitura comum,
 * mas, se existir, sua integridade é obrigatória para restaurar as camadas.
 */
internal data class DesktopRestoredBackup(
    val document: DesktopMatrixDocument,
    val letterSession: DesktopLetterSession?
)

internal object DesktopBackupService {
    private const val MAX_VISITED = 10_000
    private const val MAX_LISTED = 200
    private val backupPattern =
        Regex("""Brother-Matrizes-Backup-\d{8}T\d{6}Z-[0-9a-f]{12}\.flp""")
    private val timestamp = DateTimeFormatter
        .ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT)
        .withZone(ZoneOffset.UTC)

    private fun requireFolder(folder: Path) {
        require(folder.isAbsolute &&
            Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(folder)) {
            "Escolha uma pasta existente, sem atalhos ou links simbólicos."
        }
    }

    fun create(
        document: DesktopMatrixDocument,
        folder: Path,
        editableLayers: DesktopLetterLayout? = null
    ): Path {
        requireFolder(folder)
        val name = "Brother-Matrizes-Backup-" + timestamp.format(Instant.now()) +
            "-" + UUID.randomUUID().toString().replace("-", "").take(12) + ".flp"
        val destination = folder.resolve(name)
        if (editableLayers == null) {
            DesktopProjectService.saveNew(document, destination)
        } else {
            // Exige correspondência do estado atual; falha fechada se camadas
            // estiverem desatualizadas após edições globais.
            DesktopLetterProjectBundle.saveNew(editableLayers, document, destination)
        }
        return destination
    }

    fun list(folder: Path): List<Path> {
        requireFolder(folder)
        val found = ArrayList<Path>()
        var visited = 0
        Files.newDirectoryStream(folder).use { stream ->
            for (entry in stream) {
                visited++
                require(visited <= MAX_VISITED) {
                    "Pasta muito grande para listar backups nesta QA."
                }
                if (!backupPattern.matches(entry.fileName.toString()) ||
                    !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) ||
                    Files.isSymbolicLink(entry)) continue
                val size = Files.size(entry)
                if (size !in 1L..DesktopProjectService.MAX_PROJECT_BYTES) continue
                found.add(entry.toAbsolutePath().normalize())
                require(found.size <= MAX_LISTED) {
                    "Mais de 200 backups na pasta; organize cópias antigas em subpastas."
                }
            }
        }
        return found.sortedByDescending { it.fileName.toString() }
    }

    fun restore(file: Path): DesktopRestoredBackup {
        require(file.isAbsolute && backupPattern.matches(file.fileName.toString())) {
            "Escolha um backup FLP criado pelo Brother Matrizes Windows."
        }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file)) {
            "Backup não é um arquivo regular local."
        }
        val sidecar = DesktopLetterProjectBundle.companion(file)
        if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
            val recovered = DesktopLetterProjectBundle.open(file)
            return DesktopRestoredBackup(recovered.document, recovered.session)
        }
        return DesktopRestoredBackup(DesktopProjectService.open(file), null)
    }
}
