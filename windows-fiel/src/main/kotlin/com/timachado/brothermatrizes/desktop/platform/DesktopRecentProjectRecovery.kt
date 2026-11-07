package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Restores Home's last-opened FLP preview from an already approved library.
 *
 * Only IDs present in the current folder's validated catalog are considered.
 * The preference store never grants permission to open an arbitrary path,
 * and this operation does not write to a user's matrix or start a network
 * session. Call from an IO dispatcher, not the Compose UI thread.
 */
internal object DesktopRecentProjectRecovery {
    private const val MAX_RECENT_IDS = 30

    data class Recovered(
        val projectId: String,
        val design: EmbroideryDesign
    )

    fun mostRecentValid(
        catalog: DesktopProjectCatalog.ScanResult,
        recentIds: List<String>
    ): Recovered? {
        val checked = HashSet<String>()
        for (id in recentIds.take(MAX_RECENT_IDS)) {
            if (!checked.add(id)) continue
            val file = catalog.pathById[id] ?: continue
            // Catalog scan is non-recursive; recheck after scan to refuse a
            // replaced symlink, missing file, or entry outside the folder.
            if (file.parent != catalog.folder ||
                Files.isSymbolicLink(file) ||
                !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue
            val design = runCatching { DesktopEmbroideryFiles.open(file) }
                .getOrNull() ?: continue
            return Recovered(projectId = id, design = design)
        }
        return null
    }
}
