package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path

/**
 * Read-only first stage of Android's FontZipImportScreen on Windows.
 *
 * The archive is inspected using the existing Android-compatible safety
 * checks. Entries are NEVER extracted, copied to the font library, uploaded,
 * charged against quota, registered as commercial fonts or stitch-generated.
 * Deliberately accepts only ZIP and only exposes TTF/OTF metadata.
 */
internal object DesktopFontZipPreview {
    data class Font(
        val index: Int,
        val name: String,
        val format: String,
        val sizeBytes: Long
    )
    data class Result(
        val zipName: String,
        val fonts: List<Font>,
        val skippedItems: Int,
        val hasSha256: Boolean
    ) {
        val fontCount get() = fonts.size
    }

    fun inspect(sourceZip: Path): Result {
        val inventory = DesktopArchiveInventory.inspect(sourceZip)
        require(inventory.format == DesktopArchiveInventory.Format.ZIP) {
            "Selecione um ZIP com fontes TTF/OTF, como no Android."
        }
        val fonts = inventory.entries
            .asSequence()
            .filter { it.category == DesktopArchiveInventory.Category.FONT }
            .map { entry ->
                Font(
                    index = entry.index,
                    name = entry.displayName,
                    format = entry.extension,
                    sizeBytes = entry.sizeBytes
                )
            }
            .toList()
        return Result(
            zipName = inventory.archiveName,
            fonts = fonts,
            skippedItems = inventory.scannedCount - fonts.size,
            hasSha256 = inventory.archiveSha256?.matches(Regex("[0-9a-f]{64}")) == true
        )
    }
}
