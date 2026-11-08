package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * A pending import_font reservation must never make a new local TTF/OTF
 * usable while its server confirmation is unknown.
 *
 * The existing archive quota journal is reused; nothing is saved to
 * Supabase by this local-only reader. The journal lives beside /fonts,
 * not inside /fonts (which contains only actual font files).
 */
internal object DesktopFontImportSafety {
    fun journalFor(fontFolder: Path): DesktopArchiveQuotaJournal {
        val fontRoot = fontFolder.toAbsolutePath().normalize()
        val parent = checkNotNull(fontRoot.parent)
        return DesktopArchiveQuotaJournal(parent.resolve("font-import-usage-journal"))
    }

    fun published(
        fontFolder: Path,
        records: List<DesktopFontRecord>
    ): List<DesktopFontRecord> {
        val fontRoot = fontFolder.toAbsolutePath().normalize()
        val markerFolder = checkNotNull(fontRoot.parent)
            .resolve("font-import-usage-journal")
        if (!Files.exists(markerFolder, LinkOption.NOFOLLOW_LINKS)) return records
        check(!Files.isSymbolicLink(markerFolder) &&
            Files.isDirectory(markerFolder, LinkOption.NOFOLLOW_LINKS)) {
            "Registro de cotas de fontes inválido; nenhuma fonte externa será liberada."
        }
        val pending = DesktopArchiveQuotaJournal(markerFolder).listPending()
        if (pending.isEmpty()) return records
        // A source with no output evidence did not publish a file. An
        // unknown output fails closed for ALL files, not just a guessed one.
        check(pending.all {
            it.output != null ||
                it.stage == DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN ||
                it.stage == DesktopArchiveQuotaJournal.Stage.RESERVED
        }) { "Importação com confirmação desconhecida. Fontes bloqueadas até reconciliar." }
        val blocked = pending.mapNotNull { it.output?.fileNameSha256 }.toSet()
        return records.filter { record ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(record.id.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            digest !in blocked
        }
    }

    fun ensureNewImportAllowed(journal: DesktopArchiveQuotaJournal) {
        check(journal.listPending().isEmpty()) {
            "Importação anterior sem confirmação de cota. Resolva a pendência antes de importar."
        }
    }
}
