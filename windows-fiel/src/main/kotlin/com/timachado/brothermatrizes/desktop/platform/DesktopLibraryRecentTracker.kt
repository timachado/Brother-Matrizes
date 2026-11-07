package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Links a successfully opened/saved FLP to the already selected Windows
 * library. This is read-only: it never creates, replaces, moves or rewrites
 * a project. Call from Dispatchers.IO after file I/O completed successfully.
 *
 * No implicit folder selection: opening an FLP from Downloads does not
 * silently reconfigure the user's library or expose other local files.
 */
internal object DesktopLibraryRecentTracker {
    data class TrackedProject(
        val catalog: DesktopProjectCatalog.ScanResult,
        val id: String
    )

    fun refreshIfInLibrary(
        selectedFolder: String?,
        successfulFile: Path
    ): TrackedProject? {
        if (selectedFolder.isNullOrBlank()) return null
        val folder = Path.of(selectedFolder).toAbsolutePath().normalize()
        val file = successfulFile.toAbsolutePath().normalize()
        if (file.parent != folder ||
            !file.fileName.toString().lowercase(Locale.ROOT).endsWith(".flp") ||
            Files.isSymbolicLink(file) ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
        ) return null

        // Revalidate with exactly the same Android FLP codec and existing
        // catalog safety limits; never trust an ID by its path alone.
        val scanned = DesktopProjectCatalog.scan(folder)
        val id = file.toString()
        return if (id in scanned.pathById) TrackedProject(scanned, id) else null
    }

    fun putFirst(projectId: String, priorRecents: List<String>): List<String> =
        (listOf(projectId) + priorRecents.filter { it != projectId })
            .take(30)
}
