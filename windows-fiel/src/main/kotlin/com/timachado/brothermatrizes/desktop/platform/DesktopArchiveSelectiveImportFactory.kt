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

/** Evidential callback: persist intent BEFORE publication, verify only AFTER checking bytes. */
internal interface DesktopArchiveOutputEvidenceRecorder {
    fun beforePublish(fileName: String, sha256: String)
    fun afterVerified(fileName: String, sha256: String)
}

internal interface DesktopArchiveOwnershipAwareSinkFactory :
    DesktopArchiveValidatedSinkFactory {
    suspend fun beginWithEvidence(
        item: DesktopArchiveInventory.Item,
        recorder: DesktopArchiveOutputEvidenceRecorder
    ): DesktopArchiveValidatedSink
}

internal interface DesktopArchiveOwnershipAwareImportFactory :
    DesktopArchiveImportFactory {
    val supportsDurableOwnership: Boolean
    suspend fun beginWithEvidence(
        item: DesktopArchiveInventory.Item,
        recorder: DesktopArchiveOutputEvidenceRecorder
    ): DesktopArchiveItemTransaction
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
    private val cancelled: () -> Boolean = { false },
    private val archivePassword: CharArray? = null
) : DesktopArchiveOwnershipAwareImportFactory {
    override val supportsDurableOwnership: Boolean
        get() = validatedSinkFactory is DesktopArchiveOwnershipAwareSinkFactory

    override suspend fun beginWithEvidence(
        item: DesktopArchiveInventory.Item,
        recorder: DesktopArchiveOutputEvidenceRecorder
    ): DesktopArchiveItemTransaction {
        check(supportsDurableOwnership) {
            "O destino não oferece comprovante de gravação durável."
        }
        return beginInternal(item, recorder)
    }

    override suspend fun begin(
        item: DesktopArchiveInventory.Item
    ): DesktopArchiveItemTransaction = beginInternal(item, null)

    private suspend fun beginInternal(
        item: DesktopArchiveInventory.Item,
        recorder: DesktopArchiveOutputEvidenceRecorder?
    ): DesktopArchiveItemTransaction {
        require(expectedPreview.archiveSha256 != null &&
            expectedPreview.entries.contains(item)) {
            "Seleção não pertence à análise do pacote."
        }
        // The gate calls begin ONLY after reserve (never from preview UI).
        // Sink construction must not write user files.
        val sink = if (recorder != null) {
            (validatedSinkFactory as DesktopArchiveOwnershipAwareSinkFactory)
                .beginWithEvidence(item, recorder)
        } else validatedSinkFactory.begin(item)
        return object : DesktopArchiveItemTransaction {
            override suspend fun apply(): DesktopArchiveImportCommit {
                val callerContext = currentCoroutineContext()
                val stage = withContext(Dispatchers.IO) {
                    DesktopArchiveSelectiveStager.extract(
                        originalArchive,
                        expectedPreview,
                        item,
                        cancelled = { !callerContext.isActive || cancelled() },
                        password = archivePassword
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
