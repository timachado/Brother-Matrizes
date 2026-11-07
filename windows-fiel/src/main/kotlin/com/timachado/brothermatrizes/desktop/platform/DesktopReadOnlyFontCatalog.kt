package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Windows UI catalog from actual Android embroidery font presets.
 *
 * This reader never imports fonts, reserves quotas, opens the browser,
 * invokes Google or obtains a commercial token. An existing TTF/OTF cache
 * may be inspected only for QA. Imported outlines are NOT yet exposed to
 * the Android stitch generator in this desktop port.
 */
internal class DesktopReadOnlyFontCatalog(private val importedFolder: Path) {
    data class BuiltIn(
        val key: String,
        val displayName: String,
        val category: String,
        val description: String
    )

    data class Snapshot(
        val builtIn: List<BuiltIn>,
        val localFontMetadata: List<DesktopFontRecord>
    )

    fun snapshot(): Snapshot {
        val presets = EmbroideryFontPreset.entries.map { preset ->
            BuiltIn(
                key = preset.name,
                displayName = preset.displayName,
                category = preset.category.displayName,
                description = preset.description
            )
        }
        // No directory is created merely by visiting the Fonts tab.
        val folder = importedFolder.toAbsolutePath().normalize()
        val local = when {
            !Files.exists(folder, LinkOption.NOFOLLOW_LINKS) -> emptyList()
            Files.isSymbolicLink(folder) -> error("Biblioteca de fontes insegura.")
            !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) ->
                error("Biblioteca de fontes inválida.")
            else -> DesktopImportedFontLibrary(folder).list()
        }
        return Snapshot(presets, local)
    }

    companion object {
        fun windowsDefault(): DesktopReadOnlyFontCatalog =
            DesktopReadOnlyFontCatalog(
                DesktopWindowsDpapiSessionStore.defaultUserDataFolder()
                    .resolve("fonts")
            )
    }
}
