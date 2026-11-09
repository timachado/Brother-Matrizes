package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path

/**
 * Single-font bridge from the ORIGINAL Android-compatible ZIP archive
 * inventory into the existing, quota-authorized TTF/OTF import gate.
 *
 * This is NEVER a local quota bypass. The UI must first ask the user to
 * confirm ownership/license and potential server quota consumption.
 * Real credentials, authorization, quota and journal remain exclusively
 * inside DesktopCommercialFontImportGate, which is disabled by default.
 */
internal object DesktopFontZipAuthorizedImporter {
    suspend fun importOne(
        archive: Path,
        reviewed: DesktopFontZipPreview.Result,
        selected: DesktopFontZipPreview.Font,
        gate: DesktopCommercialFontImportGate
    ): DesktopFontRecord {
        val fingerprint = requireNotNull(reviewed.archiveSha256) {
            "Reanalise o ZIP antes de importar."
        }
        require(Regex("[0-9a-f]{64}").matches(fingerprint)) {
            "Prévia ZIP sem integridade comprovada."
        }
        require(reviewed.fonts.count { it.index == selected.index && it == selected } == 1) {
            "Fonte ZIP não foi selecionada da prévia original."
        }
        val current = DesktopArchiveInventory.inspect(archive)
        require(current.format == DesktopArchiveInventory.Format.ZIP &&
            current.archiveSha256 == fingerprint &&
            current.archiveName == reviewed.zipName &&
            current.fontCount == reviewed.fontCount &&
            current.entries.count { it.category == DesktopArchiveInventory.Category.FONT } ==
                reviewed.fontCount) {
            "O pacote foi alterado desde a prévia. Nenhuma cota foi solicitada."
        }
        val entry = current.entries.singleOrNull {
            it.index == selected.index &&
                it.category == DesktopArchiveInventory.Category.FONT &&
                it.displayName == selected.name &&
                it.extension == selected.format &&
                it.sizeBytes == selected.sizeBytes
        } ?: error("Fonte ZIP não corresponde à seleção original.")
        // The import gate accepts only 4..12 MiB, not the extractor's larger
        // limit; reject before any quota reservation or file publication.
        require(entry.sizeBytes in 4..(12L * 1024L * 1024L)) {
            "Fonte ZIP ultrapassa o limite de 12 MB."
        }
        return DesktopArchiveSelectiveStager.extract(archive, current, entry).use { stage ->
            val report = DesktopReadOnlyFontAnalyzer.inspect(stage.file)
            check(report.byteSize == entry.sizeBytes &&
                report.format.equals(entry.extension, ignoreCase = true)) {
                "Fonte extraída não passou na validação de contornos."
            }
            // The gate revalidates source and verifies Google + quota itself.
            // Temporary extraction is always deleted, including on errors.
            gate.importFont(stage.file, selected.name)
        }
    }
}
