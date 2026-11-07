package com.timachado.brothermatrizes.desktop.platform

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Commercial quota boundary for one archive item, matching the ORIGINAL
 * Android BrotherMatrizesAccountService.authorizeImport/finalizeImport.
 *
 * This is a transaction CONTRACT, not an enabled Windows import feature.
 * No production gateway, extractor or OAuth session is wired into the
 * Windows UI. Do not infer entitlement from profile, local files or Pro text.
 *
 * Workflow:
 * 1. Verify Google session from the authenticated provider.
 * 2. Reserve import_matrix or import_font on the server for EACH item.
 * 3. Allow a provided, separately validated importer to apply that item.
 * 4. Finalize with true only for a newly created, committed item.
 * 5. On any import error or server-finalization failure, invoke rollback.
 *
 * The concrete transaction implementation MUST support rollback even when
 * apply throws halfway through. Server idempotency and durable crash
 * recovery remain necessary before enabling commercial imports.
 */
internal interface DesktopArchiveUsageGateway {
    suspend fun reserve(
        accessToken: String,
        category: DesktopArchiveInventory.Category
    ): String

    suspend fun finalize(
        accessToken: String,
        category: DesktopArchiveInventory.Category,
        requestKey: String,
        success: Boolean
    )
}

internal data class DesktopArchiveImportCommit(
    val created: Boolean,
    val itemId: String?
)

internal interface DesktopArchiveItemTransaction {
    /** May create local data only AFTER a verified remote reservation. */
    suspend fun apply(): DesktopArchiveImportCommit

    /**
     * Idempotent: remove ONLY data created by this transaction.
     * Must be safe after partial failure and may not remove preexisting data.
     */
    suspend fun rollback()
}

internal fun interface DesktopArchiveImportFactory {
    suspend fun begin(
        item: DesktopArchiveInventory.Item
    ): DesktopArchiveItemTransaction
}

/** Explicitly unavailable until authenticated network and importer QA. */
internal object WindowsArchiveImporterUnavailable : DesktopArchiveImportFactory {
    override suspend fun begin(item: DesktopArchiveInventory.Item): DesktopArchiveItemTransaction =
        error("Importação Windows ainda não homologada. Somente análise do pacote está disponível.")
}

internal class DesktopArchiveCommercialImportGate(
    private val usage: DesktopArchiveUsageGateway,
    private val verifiedSession: DesktopVerifiedFontSession = WindowsFontSessionUnavailable,
    private val importer: DesktopArchiveImportFactory = WindowsArchiveImporterUnavailable
) {
    private companion object {
        // Serializes all instances within the same Windows process, avoiding
        // simultaneous duplicate reservations and conflicting rollbacks.
        val transactionLock = Mutex()
        val serverReservation = Regex(
            "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
    }

    suspend fun importOne(item: DesktopArchiveInventory.Item): DesktopArchiveImportCommit =
        transactionLock.withLock {
            // Validate metadata before contacting the server, but do NOT
            // read or extract the archived item before authorization.
            require(item.entryName.isNotBlank() && item.displayName.isNotBlank() &&
                item.sizeBytes in 0..(24L * 1024L * 1024L) &&
                item.extension in when (item.category) {
                    DesktopArchiveInventory.Category.MATRIX -> setOf("PES", "DST", "JEF")
                    DesktopArchiveInventory.Category.FONT -> setOf("TTF", "OTF")
                }
            ) { "Item do pacote inválido para importação." }

            // Offline Windows and QA accounts intentionally fail here.
            val token = verifiedSession.accessToken()
            check(token.isNotBlank()) { "Sessão Google verificada necessária." }
            // A verified account is insufficient if the real archive importer
            // is not yet installed: do not create a server reservation just
            // to reject an unavailable Windows feature afterwards.
            check(importer !== WindowsArchiveImporterUnavailable) {
                "Importação Windows ainda não homologada; nenhuma cota foi reservada."
            }

            val reservation = usage.reserve(token, item.category)
            check(serverReservation.matches(reservation)) {
                "Reserva de importação inválida retornada pelo servidor."
            }

            var transaction: DesktopArchiveItemTransaction? = null
            var committed: DesktopArchiveImportCommit? = null
            var importError: Throwable? = null
            try {
                transaction = importer.begin(item)
                committed = transaction.apply()
                require(!committed.created ||
                    !committed.itemId.isNullOrBlank()) {
                    "A importação não retornou o identificador do novo item."
                }
            } catch (error: Throwable) {
                importError = error
            }

            // Even cancellation must attempt to settle a reserved quota.
            // No retry here: duplicate finalize calls require server-side
            // idempotency and reconciliation, not a speculative local debit.
            val serverSuccess = importError == null && committed?.created == true
            var syncError: Throwable? = null
            withContext(NonCancellable) {
                try {
                    usage.finalize(token, item.category, reservation, serverSuccess)
                } catch (error: Throwable) {
                    syncError = error
                }
            }

            if (importError != null || syncError != null) {
                var cleanupError: Throwable? = null
                withContext(NonCancellable) {
                    if (transaction != null) {
                        try {
                            transaction.rollback()
                        } catch (error: Throwable) {
                            cleanupError = error
                        }
                    }
                }

                if (syncError != null) {
                    val error = IllegalStateException(
                        "A cota não foi sincronizada. Importação bloqueada; " +
                            "verifique o estado no servidor antes de repetir.",
                        syncError
                    )
                    importError?.let(error::addSuppressed)
                    cleanupError?.let(error::addSuppressed)
                    throw error
                }
                val error = checkNotNull(importError)
                cleanupError?.let(error::addSuppressed)
                throw error
            }

            checkNotNull(committed) { "A importação não produziu resultado válido." }
        }
}
