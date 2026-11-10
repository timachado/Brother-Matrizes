package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.project.CompleteLibraryBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectBackupCodec
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Comparator
import java.util.UUID

/**
 * Restores BOTH parts of the original Android full backup into a NEW Windows
 * folder. Original Android codecs stay unchanged. Font files are recovered as
 * files, never silently enrolled in a quota-controlled commercial font catalog.
 * Never overwrites an existing file or touches Supabase/account/quotas.
 */
internal object DesktopAndroidCompleteBackupRecovery {
    private const val MAX_PROJECTS = 200
    private const val MAX_PROJECT_SIZE = 64L * 1024L * 1024L

    data class Result(
        val folder: Path,
        val recoveredProjects: Int,
        val recoveredFonts: Int
    )

    fun restore(backupFile: Path, chosenParent: Path): Result {
        val parent = chosenParent.toAbsolutePath().normalize()
        require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(parent)) {
            "A recuperação exige uma pasta de destino local real."
        }
        val preview = DesktopAndroidBackupInspector.inspect(backupFile)
        require(preview.kind == DesktopAndroidBackupInspector.Kind.ANDROID_COMPLETE) {
            "Selecione o backup completo original do Brother Matrizes."
        }
        require(preview.projectCount in 0..MAX_PROJECTS) {
            "O backup contém mais de 200 matrizes; nenhuma foi restaurada."
        }
        val complete = CompleteLibraryBackupCodec.decode(
            Files.readAllBytes(backupFile.toAbsolutePath().normalize())
        )
        val projects = ProjectBackupCodec.decode(complete.projectsBackup)
        require(projects.size == preview.projectCount &&
            complete.fonts.size == preview.fontCount &&
            (projects.isNotEmpty() || complete.fonts.isNotEmpty())) {
            "O backup está vazio ou foi alterado durante a recuperação."
        }
        projects.forEach { bytes ->
            require(bytes.size in 1..MAX_PROJECT_SIZE.toInt()) {
                "Matriz FLP maior que o limite permitido."
            }
            ProjectCodec.decode(bytes)
        }
        complete.fonts.forEach(CompleteLibraryBackupCodec::validateFont)

        val staging = Files.createTempDirectory(parent, ".bm-complete-android-")
        var committed = false
        try {
            projects.forEachIndexed { index, bytes ->
                val project = staging.resolve(
                    "Android-" + (index + 1).toString().padStart(5, '0') + ".flp"
                )
                Files.newOutputStream(project,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                    it.write(bytes)
                }
                DesktopFlpProjectFiles.open(project)
            }
            if (complete.fonts.isNotEmpty()) {
                val fontsFolder = Files.createDirectory(staging.resolve("fonts"))
                complete.fonts.forEach { font ->
                    val file = fontsFolder.resolve(font.fileName).normalize()
                    require(file.parent == fontsFolder &&
                        !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                        "Fonte duplicada ou fora da pasta de recuperação."
                    }
                    Files.newOutputStream(file,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use {
                        it.write(font.bytes)
                    }
                }
            }
            val checked = DesktopProjectCatalog.scan(staging)
            check(checked.skippedInvalid == 0 &&
                checked.projects.size == projects.size) {
                "Nem todas as matrizes originais foram validadas."
            }
            val final = parent.resolve("Brother-Matrizes-Backup-Android-" + UUID.randomUUID())
            Files.move(staging, final)
            committed = true
            return Result(final, projects.size, complete.fonts.size)
        } finally {
            if (!committed) {
                Files.walk(staging).use { entries ->
                    entries.sorted(Comparator.reverseOrder()).forEach {
                        Files.deleteIfExists(it)
                    }
                }
            }
        }
    }
}
