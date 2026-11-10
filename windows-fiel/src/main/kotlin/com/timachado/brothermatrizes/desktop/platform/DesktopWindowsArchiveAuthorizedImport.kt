package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.ArchiveFormat
import com.timachado.brothermatrizes.core.archive.ArchiveInventory
import com.timachado.brothermatrizes.core.archive.ArchiveItem
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Maps the SELECTED item from the original Android ArchiveExtractorScreen
 * to the crash-safe, server-authorized Windows importer.
 *
 * ZIP, RAR/RAR5 and 7Z use the Android library's selected-item semantics.
 * RAR/7Z passwords are passed only for the active operation and are never stored.
 * No file or quota can be published by this class without the injected
 * server gateway, verified token, persistent journal and durable sink.
 *
 * This bridge does NOT open a real session or grant any plan. It is wired
 * only through a separate, explicitly reviewed developer QA configuration.
 */
internal class DesktopWindowsArchiveAuthorizedImport(
    private val usage: DesktopArchiveUsageGateway,
    private val session: DesktopVerifiedFontSession,
    private val journal: DesktopArchiveQuotaJournal,
    private val projectFolder: Path,
    private val fontFolder: Path
) {
    /**
     * Telegram and local file picker share this exact archive quota journal.
     * Never start a parallel ledger or bypass per-item server authorization.
     */
    suspend fun importStandaloneMatrix(
        source: Path
    ): DesktopArchiveImportCommit = DesktopWindowsStandaloneMatrixAuthorizedImport(
        usage = usage,
        verifiedSession = session,
        journal = journal,
        projectFolder = projectFolder,
        fontFolder = fontFolder
    ).importOne(source)

    suspend fun importOne(
        originalArchive: Path,
        originalAndroidPreview: ArchiveInventory,
        selected: ArchiveItem,
        password: CharArray? = null
    ): DesktopArchiveImportCommit {
        val source = originalArchive.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(source) &&
            Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) &&
            listOf(".zip", ".rar", ".7z").any {
                source.fileName.toString().endsWith(it, ignoreCase = true)
            }) {
            "Selecione um pacote ZIP, RAR/RAR5 ou 7Z regular."
        }
        require(
            originalAndroidPreview.entries.count { it == selected } == 1) {
            "Seleção não corresponde ao pacote mostrado pelo Extrator Android."
        }
        val stagedOriginal = originalAndroidPreview.sourceFile.toPath()
            .toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(stagedOriginal) &&
            Files.isRegularFile(stagedOriginal, LinkOption.NOFOLLOW_LINKS)) {
            "A cópia temporária do pacote não está mais disponível."
        }
        val inspected = DesktopArchiveInventory.inspect(source, password)
        val expectedFormat = when (originalAndroidPreview.format) {
            ArchiveFormat.ZIP -> DesktopArchiveInventory.Format.ZIP
            ArchiveFormat.RAR -> DesktopArchiveInventory.Format.RAR
            ArchiveFormat.SEVEN_Z -> DesktopArchiveInventory.Format.SEVEN_Z
        }
        require(inspected.format == expectedFormat &&
            inspected.archiveSha256 != null &&
            inspected.archiveSha256 == DesktopArchiveInventory.sha256(stagedOriginal)) {
            "O pacote mudou após a prévia original: nenhuma cota foi solicitada."
        }

        val match = inspected.entries.singleOrNull { candidate ->
            candidate.index == selected.index &&
                candidate.entryName == selected.path &&
                candidate.displayName == selected.name &&
                candidate.extension == selected.extension &&
                candidate.sizeBytes == selected.sizeBytes &&
                ((candidate.category == DesktopArchiveInventory.Category.MATRIX &&
                    selected.category == ArchiveCategory.MATRIX) ||
                    (candidate.category == DesktopArchiveInventory.Category.FONT &&
                        selected.category == ArchiveCategory.FONT))
        } ?: error("Item não corresponde ao que foi selecionado no Android.")

        // This layer never creates or overwrites the selected library folder.
        // The validated sink enforces local destination safety and CREATE_NEW.
        val factory = DesktopArchiveSelectiveImportFactory(
            originalArchive = source,
            expectedPreview = inspected,
            validatedSinkFactory = DesktopArchiveValidatedLibrarySinkFactory(
                projectFolder = projectFolder, fontFolder = fontFolder
            ),
            archivePassword = password
        )
        return DesktopArchiveCommercialImportGate(
            usage = usage,
            verifiedSession = session,
            importer = factory,
            journal = journal
        ).importOne(match)
    }
}
