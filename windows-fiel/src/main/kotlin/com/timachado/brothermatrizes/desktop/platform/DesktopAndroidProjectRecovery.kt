package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectBackupCodec
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Opt-in recovery of FLP documents inside an ORIGINAL Android backup.
 *
 * Supports .brother_matrizes-fullbackup and the historical
 * .brother_matrizes-backup. Fonts are verified by the original Android codec
 * but NEVER written to disk, registered, or used to grant commercial rights.
 * Original archives and any existing Windows files are left untouched.
 */
internal object DesktopAndroidProjectRecovery {
    // The Windows local Project Catalog currently supports at most 200 FLPs.
    private const val MAX_RECOVERED_PROJECTS = 200
    private const val MAX_PROJECT_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 128L * 1024L * 1024L

    data class RecoveryResult(
        val folder: Path,
        val restoredProjects: Int,
        val ignoredFonts: Int,
        val sourceKind: DesktopAndroidBackupInspector.Kind
    )

    /**
     * Checks the complete backup with the existing read-only inspector FIRST.
     * Publishes exactly one newly created directory only after ALL FLPs pass
     * the unmodified Android ProjectCodec validation. Never merge/replace.
     */
    fun restoreProjectsOnly(sourceBackup: Path, chosenParent: Path): RecoveryResult {
        val source = sourceBackup.toAbsolutePath().normalize()
        val parent = chosenParent.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(parent) &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            "Escolha uma pasta real para receber as matrizes Android."
        }

        val preview = DesktopAndroidBackupInspector.inspect(source)
        require(preview.projectCount in 1..MAX_RECOVERED_PROJECTS) {
            "Recuperação Windows limitada a 200 matrizes FLP por backup. " +
                "Nenhuma alteração foi feita."
        }

        // Exact Android source codecs, with no Context, ProjectStore or
        // ImportedFontStore. Fonts stay in memory only for validation.
        val originalBytes = Files.readAllBytes(source)
        val projectBytes = if (preview.kind ==
            DesktopAndroidBackupInspector.Kind.ANDROID_COMPLETE
        ) {
            val data = CompleteLibraryBackupCodec.decode(originalBytes)
            require(data.fonts.size == preview.fontCount) {
                "O backup Android foi modificado durante a leitura."
            }
            ProjectBackupCodec.decode(data.projectsBackup)
        } else {
            ProjectBackupCodec.decode(originalBytes)
        }
        require(projectBytes.size == preview.projectCount) {
            "A quantidade de matrizes mudou durante a leitura do backup."
        }

        var total = 0L
        projectBytes.forEach { bytes ->
            val size = bytes.size.toLong()
            require(size in 1..MAX_PROJECT_BYTES && size <= MAX_TOTAL_BYTES - total) {
                "As matrizes excedem o limite seguro de 128 MB por restauração Windows."
            }
            total += size
        }

        // Neither the original archive nor existing projects are used as a
        // destination. A private staging dir is created only after validation.
        val staging = Files.createTempDirectory(parent, ".bm-android-flp-")
        val stagedFiles = ArrayList<Path>()
        var committed = false
        try {
            projectBytes.forEachIndexed { index, bytes ->
                val filename = "Android-" +
                    (index + 1).toString().padStart(5, '0') + ".flp"
                val destination = staging.resolve(filename)
                Files.newOutputStream(
                    destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
                ).use { output ->
                    stagedFiles.add(destination)
                    output.write(bytes)
                }
                // Read back through EXACTLY the same FLP v11 codec as Android.
                DesktopFlpProjectFiles.open(destination)
            }

            val verified = DesktopProjectCatalog.scan(staging)
            check(verified.projects.size == projectBytes.size &&
                verified.skippedInvalid == 0) {
                "Nem todas as matrizes FLP foram verificadas. Nada foi publicado."
            }

            val publishedFolder = parent.resolve(
                "Brother-Matrizes-Android-FLP-" + UUID.randomUUID()
            )
            require(!Files.exists(publishedFolder, LinkOption.NOFOLLOW_LINKS)) {
                "Pasta de recuperação já existente."
            }
            Files.move(staging, publishedFolder)
            committed = true
            return RecoveryResult(
                folder = publishedFolder,
                restoredProjects = verified.projects.size,
                ignoredFonts = preview.fontCount,
                sourceKind = preview.kind
            )
        } finally {
            if (!committed) {
                // Only paths created in OUR new staging directory are removed.
                stagedFiles.asReversed().forEach { Files.deleteIfExists(it) }
                Files.deleteIfExists(staging)
            }
        }
    }
}
