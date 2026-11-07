package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile

/**
 * READ-ONLY compatibility preflight for original Android full and historical
 * projects-only backups. Uses unmodified Android Kotlin codecs extracted at
 * compile time; never creates, imports, deletes or overwrites projects/fonts.
 *
 * No Android Account, BuildConfig, license, quota or filesystem write code
 * is carried into the Windows build.
 */
internal object DesktopAndroidBackupInspector {
    private const val MAX_COMPRESSED_BYTES = 192L * 1024L * 1024L
    private const val MAX_UNCOMPRESSED_BYTES = 192L * 1024L * 1024L
    private const val MAX_ENTRIES = 204

    enum class Kind { ANDROID_COMPLETE, ANDROID_PROJECTS_ONLY }

    data class Preview(
        val kind: Kind,
        val projectCount: Int,
        val fontCount: Int,
        val compressedBytes: Long
    )

    fun inspect(input: Path): Preview {
        val file = input.toAbsolutePath().normalize()
        val name = file.fileName.toString().lowercase(Locale.ROOT)
        require(name.endsWith(".brother_matrizes-fullbackup") ||
            name.endsWith(".brother_matrizes-backup")) {
            "Use um backup original do Android Brother Matrizes."
        }
        require(!Files.isSymbolicLink(file) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            "Backup Android precisa ser um arquivo local regular."
        }
        val compressedSize = Files.size(file)
        require(compressedSize in 1..MAX_COMPRESSED_BYTES) {
            "Backup Android vazio ou acima do limite de 192 MB."
        }

        // Validate actual ZIP central directory and inventory before loading
        // into memory. ZipInputStream alone may accept a truncated archive
        // whose central directory is missing entirely.
        ZipFile(file.toFile()).use { archive ->
            val entries = archive.entries().toList()
            require(entries.size in 1..MAX_ENTRIES) {
                "Backup contém itens demais ou está vazio."
            }
            val seen = HashSet<String>()
            var unpacked = 0L
            for (entry in entries) {
                require(!entry.isDirectory && entry.name.isNotEmpty() &&
                    seen.add(entry.name.lowercase(Locale.ROOT)) &&
                    entry.name.none { it.isISOControl() || it == '\\' } &&
                    !entry.name.startsWith('/') && !entry.name.contains("..")) {
                    "Backup contém nomes duplicados, caminhos inseguros ou pastas."
                }
                val allowed = entry.name == "brother_matrizes-complete-backup.txt" ||
                    entry.name == "projects.brother_matrizes-backup" ||
                    entry.name == "brother_matrizes-backup.txt" ||
                    entry.name.matches(Regex("projects/[0-9]{5}\\.flp")) ||
                    entry.name.matches(Regex("fonts/[A-Za-z0-9._-]{1,100}\\.(ttf|otf)", RegexOption.IGNORE_CASE))
                require(allowed) { "Backup contém entrada desconhecida." }
                require(entry.size in 0..MAX_UNCOMPRESSED_BYTES &&
                    entry.size <= MAX_UNCOMPRESSED_BYTES - unpacked) {
                    "Backup ultrapassa o limite de descompressão."
                }
                unpacked += entry.size
            }
        }

        val bytes = Files.readAllBytes(file)
        val complete = name.endsWith(".brother_matrizes-fullbackup")
        if (complete) {
            require(CompleteLibraryBackupCodec.isCompleteArchive(bytes)) {
                "O arquivo não é um backup completo do Android."
            }
            val archive = CompleteLibraryBackupCodec.decode(bytes)
            val projects = ProjectBackupCodec.decode(archive.projectsBackup)
            // Android's codec already validates FLP; keep a separate check
            // for future changes that could unintentionally bypass it.
            projects.forEach { ProjectCodec.decode(it) }
            return Preview(
                kind = Kind.ANDROID_COMPLETE,
                projectCount = projects.size,
                fontCount = archive.fonts.size,
                compressedBytes = compressedSize
            )
        }
        val projects = ProjectBackupCodec.decode(bytes)
        projects.forEach { ProjectCodec.decode(it) }
        return Preview(
            kind = Kind.ANDROID_PROJECTS_ONLY,
            projectCount = projects.size,
            fontCount = 0,
            compressedBytes = compressedSize
        )
    }
}
