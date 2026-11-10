package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Windows counterpart of Android's "Importar matriz do Telegram / Arquivos".
 *
 * The picker never accesses Telegram chats: the user explicitly selects a
 * local DST/PES/JEF file. Uses the SAME account, Free V2 journal and validated
 * FLP sink as the original Android archive extractor bridge. Disabled unless
 * a separate operator-reviewed QA opt-in is active in FielMain.
 *
 * A lost reserve/finalize reply leaves a durable marker for review. No
 * automatic retries, local Pro grant, overwrite or customer data in journal.
 */
internal class DesktopWindowsStandaloneMatrixAuthorizedImport(
    private val usage: DesktopArchiveUsageGateway,
    private val verifiedSession: DesktopVerifiedFontSession,
    private val journal: DesktopArchiveQuotaJournal,
    private val projectFolder: Path,
    private val fontFolder: Path
) {
    suspend fun importOne(selectedFile: Path): DesktopArchiveImportCommit {
        // The SHA identifies the bytes the user actually selected; never
        // record their path or real name in the quota journal.
        val source = selectedFile.toAbsolutePath().normalize()
        val extension = source.fileName.toString().substringAfterLast('.', "")
            .uppercase(java.util.Locale.ROOT)
        require(extension in setOf("PES", "DST", "JEF") &&
            !Files.isSymbolicLink(source) &&
            !Files.isSymbolicLink(source.parent) &&
            Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(source) in 1..(24L * 1024L * 1024L)) {
            "Selecione uma matriz local PES, DST ou JEF válida e regular."
        }
        val bytes = Files.size(source)
        val expectedHash = DesktopArchiveInventory.sha256(source)
        val item = DesktopArchiveInventory.Item(
            index = 0,
            // Pseudonymized entry ID: no customer filename in journal.
            entryName = "local/" + expectedHash.take(16) + "." +
                extension.lowercase(java.util.Locale.ROOT),
            displayName = "Matriz." + extension.lowercase(java.util.Locale.ROOT),
            extension = extension,
            sizeBytes = bytes,
            category = DesktopArchiveInventory.Category.MATRIX
        )

        val sinkFactory = DesktopArchiveValidatedLibrarySinkFactory(
            projectFolder = projectFolder, fontFolder = fontFolder
        )
        val importer = object : DesktopArchiveOwnershipAwareImportFactory {
            override val supportsDurableOwnership: Boolean = true

            override suspend fun begin(
                item: DesktopArchiveInventory.Item
            ): DesktopArchiveItemTransaction =
                error("Importação V2 exige comprovante de propriedade durável.")

            override suspend fun beginWithEvidence(
                item: DesktopArchiveInventory.Item,
                recorder: DesktopArchiveOutputEvidenceRecorder
            ): DesktopArchiveItemTransaction {
                val sink = sinkFactory.beginWithEvidence(item, recorder)
                return object : DesktopArchiveItemTransaction {
                    override suspend fun apply(): DesktopArchiveImportCommit {
                        val temp = Files.createTempDirectory(
                            "brother-matrix-selected-private-"
                        )
                        val staged = Files.createTempFile(
                            temp, "single-matrix-",
                            "." + extension.lowercase(java.util.Locale.ROOT)
                        )
                        try {
                            withContext(Dispatchers.IO) {
                                // Stream into a private regular file using a
                                // bounded copy rather than trusting the picker.
                                Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)
                                    .use { input ->
                                        Files.newOutputStream(
                                            staged, StandardOpenOption.WRITE,
                                            StandardOpenOption.TRUNCATE_EXISTING,
                                            LinkOption.NOFOLLOW_LINKS
                                        ).use { output ->
                                            val buffer = ByteArray(16_384)
                                            var written = 0L
                                            while (true) {
                                                val n = input.read(buffer)
                                                if (n < 0) break
                                                written += n
                                                check(written <= bytes && written <=
                                                    24L * 1024L * 1024L) {
                                                    "Matriz alterada durante a cópia."
                                                }
                                                output.write(buffer, 0, n)
                                            }
                                            check(written == bytes) {
                                                "Matriz incompleta: nenhuma saída autorizada."
                                            }
                                        }
                                    }
                                check(DesktopArchiveInventory.sha256(staged) ==
                                    expectedHash &&
                                    DesktopArchiveInventory.sha256(source) ==
                                        expectedHash) {
                                    "A matriz mudou durante a importação."
                                }
                            }
                            return sink.validateAndStore(staged)
                        } finally {
                            withContext(NonCancellable + Dispatchers.IO) {
                                Files.deleteIfExists(staged)
                                Files.deleteIfExists(temp)
                            }
                        }
                    }

                    override suspend fun rollback() = sink.rollback()
                }
            }
        }
        return DesktopArchiveCommercialImportGate(
            usage = usage,
            verifiedSession = verifiedSession,
            importer = importer,
            journal = journal
        ).importOne(item)
    }
}
