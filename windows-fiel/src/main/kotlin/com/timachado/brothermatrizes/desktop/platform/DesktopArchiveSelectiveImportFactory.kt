package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Post-reservation bridge: SELECT ONE item -> extract to private temporary
 * file -> call a separate validated sink -> delete temporary file.
 *
 * Not connected to Desktop Windows UI. Caller MUST provide an importer sink
 * that validates the real PES/DST/JEF/TTF/OTF before saving, guarantees that
 * rollback removes only newly created content, and implements crash recovery.
 * There is deliberately NO permissive default sink.
 */
internal interface DesktopArchiveValidatedSink {
    /** Complete validation and persistence; never trust file extension alone. */
    suspend fun validateAndStore(privateExtractedFile: Path): DesktopArchiveImportCommit

    /** Safe even if validateAndStore threw after a partially written record. */
    suspend fun rollback()
}

internal fun interface DesktopArchiveValidatedSinkFactory {
    suspend fun begin(item: DesktopArchiveInventory.Item): DesktopArchiveValidatedSink
}

/**
 * Used only when injected explicitly into DesktopArchiveCommercialImportGate
 * with a verified session, a server quota adapter, and a verified sink.
 * No production importer is wired in the application.
 */
internal class DesktopArchiveSelectiveImportFactory(
    private val originalArchive: Path,
    private val expectedPreview: DesktopArchiveInventory.Preview,
    private val validatedSinkFactory: DesktopArchiveValidatedSinkFactory,
    private val cancelled: () -> Boolean = { false }
) : DesktopArchiveImportFactory {

    override suspend fun begin(
        item: DesktopArchiveInventory.Item
    ): DesktopArchiveItemTransaction {
        require(expectedPreview.archiveSha256 != null &&
            expectedPreview.entries.contains(item)) {
            "Seleção não pertence à análise do pacote."
        }
        // The gate calls begin ONLY after reserve (never from preview UI).
        // Sink construction must not write user files.
        val sink = validatedSinkFactory.begin(item)
        return object : DesktopArchiveItemTransaction {
            override suspend fun apply(): DesktopArchiveImportCommit {
                val callerContext = currentCoroutineContext()
                val stage = withContext(Dispatchers.IO) {
                    DesktopArchiveSelectiveStager.extract(
                        originalArchive,
                        expectedPreview,
                        item,
                        cancelled = { !callerContext.isActive || cancelled() }
                    )
                }
                return try {
                    // Only a real validated sink is allowed to commit data.
                    sink.validateAndStore(stage.file)
                } finally {
                    // Never leave unvalidated extracted bytes in TEMP,
                    // even if the coroutine was cancelled.
                    withContext(NonCancellable) { stage.close() }
                }
            }

            override suspend fun rollback() {
                sink.rollback()
            }
        }
    }
}
