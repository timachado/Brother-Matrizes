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
    private val importer: DesktopArchiveImportFactory = WindowsArchiveImporterUnavailable,
    // Optional for isolated legacy QA tests. MANDATORY with real HTTP gateway.
    // Production UI still does not instantiate the import gate.
    private val journal: DesktopArchiveQuotaJournal? = null
) {
    private companion object {
        val transactionLock = Mutex()
        val serverReservation = Regex(
            "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
    }

    suspend fun importOne(item: DesktopArchiveInventory.Item): DesktopArchiveImportCommit =
        transactionLock.withLock {
            val lease = journal?.acquireLease()
            try {
                importLocked(item)
            } finally {
                lease?.close()
            }
        }

    private suspend fun importLocked(
        item: DesktopArchiveInventory.Item
    ): DesktopArchiveImportCommit {
        require(item.entryName.isNotBlank() && item.displayName.isNotBlank() &&
            item.sizeBytes in 0..(24L * 1024L * 1024L) &&
            item.extension in when (item.category) {
                DesktopArchiveInventory.Category.MATRIX -> setOf("PES", "DST", "JEF")
                DesktopArchiveInventory.Category.FONT -> setOf("TTF", "OTF")
            }
        ) { "Item do pacote inválido para importação." }

        val token = verifiedSession.accessToken()
        check(token.isNotBlank()) { "Sessão Google verificada necessária." }
        check(importer !== WindowsArchiveImporterUnavailable) {
            "Importação Windows ainda não homologada; nenhuma cota foi reservada."
        }
        // It must be IMPOSSIBLE to attach a live quota adapter without an
        // explicit crash journal. The normal Windows UI has neither.
        // Every V2 HTTP adapter, including a future isolated Pro V2 one,
        // must prove journal persistence and durable output ownership.
        // Do not restrict this check to the original Free V2 class.
        check(usage !is DesktopArchiveIdempotentUsageGateway || journal != null) {
            "O serviço V2 exige registro durável de reservas."
        }
        check(usage !is DesktopArchiveIdempotentUsageGateway ||
            (importer is DesktopArchiveOwnershipAwareImportFactory &&
                importer.supportsDurableOwnership)) {
            "O serviço V2 exige rastreamento durável da saída local."
        }

        // Persist RESERVE_UNKNOWN BEFORE making the network call. Even a
        // timeout may mean the server processed the request; never blindly
        // repeat reserve after a crash.
        val protocol = if (usage is DesktopArchiveIdempotentUsageGateway)
            usage.reservationProtocol
        else DesktopArchiveQuotaJournal.ReservationProtocol.LEGACY_UNKNOWN
        // Persist the protocol before reserveOnce; never infer Pro V2 from
        // a returned pro_ key after an ambiguous server response.
        var pending = journal?.begin(item, protocol)
        val reservation = if (usage is DesktopArchiveIdempotentUsageGateway) {
            // The same UUIDv4 was persisted by journal.begin BEFORE network.
            // A lost first HTTP response can be diagnosed without minting
            // another quota request ID. No automatic retries after crashes.
            val persisted = checkNotNull(pending) {
                "Reserva V2 exige journal durável antes de conectar."
            }
            usage.reserveOnce(token, item.category, persisted.localId)
        } else {
            // Existing fake gateways and the Android V1 protocol keep
            // their behavior. The concrete Windows HTTP gateway implements
            // reserveOnce and can no longer use legacy reserve here.
            usage.reserve(token, item.category)
        }
        check(serverReservation.matches(reservation)) {
            "Reserva de importação inválida retornada pelo servidor."
        }
        if (pending != null) {
            pending = checkNotNull(journal).reserved(pending, reservation)
        }

        var transaction: DesktopArchiveItemTransaction? = null
        var committed: DesktopArchiveImportCommit? = null
        var importError: Throwable? = null
        val recordsOwnership = pending != null &&
            importer is DesktopArchiveOwnershipAwareImportFactory &&
            importer.supportsDurableOwnership
        val recorder = object : DesktopArchiveOutputEvidenceRecorder {
            override fun beforePublish(fileName: String, sha256: String) {
                pending = checkNotNull(journal).declareOutput(
                    checkNotNull(pending), fileName, sha256
                )
            }
            override fun afterVerified(fileName: String, sha256: String) {
                pending = checkNotNull(journal).verifyOutput(
                    checkNotNull(pending), fileName, sha256
                )
            }
        }
        try {
            transaction = if (recordsOwnership) {
                (importer as DesktopArchiveOwnershipAwareImportFactory)
                    .beginWithEvidence(item, recorder)
            } else importer.begin(item)
            committed = transaction.apply()
            require(!committed.created || !committed.itemId.isNullOrBlank()) {
                "A importação não retornou o identificador do novo item."
            }
            if (recordsOwnership && committed.created) {
                check(checkNotNull(journal).verifiedOutputMatches(
                    checkNotNull(pending), checkNotNull(committed.itemId)
                )) {
                    "Saída criada sem confirmação persistida no journal."
                }
            }
        } catch (error: Throwable) {
            importError = error
        }

        val serverSuccess = importError == null && committed?.created == true
        var syncError: Throwable? = null

        // Persist FINALIZE_UNKNOWN BEFORE sending finalize. A response
        // lost AFTER the server ACK must not trigger an unverified retry.
        try {
            if (pending != null) {
                pending = checkNotNull(journal).finalizing(pending)
            }
        } catch (error: Throwable) {
            syncError = error
        }

        if (syncError == null) {
            withContext(NonCancellable) {
                try {
                    usage.finalize(token, item.category, reservation, serverSuccess)
                } catch (error: Throwable) {
                    syncError = error
                }
            }
        }

        if (syncError == null && importError == null) {
            // Server answered successfully. If deleting the local journal
            // fails, leave the marker pending for MANUAL reconciliation.
            if (pending != null) checkNotNull(journal).completed(pending)
            return checkNotNull(committed) {
                "A importação não produziu resultado válido."
            }
        }

        // If server acknowledged 'false', mark cleanup needed. If the
        // server response is unknown, keep FINALIZE_UNKNOWN unchanged.
        var cleanupRecordError: Throwable? = null
        if (syncError == null && pending != null) {
            try {
                pending = checkNotNull(journal).needsCleanup(pending)
            } catch (error: Throwable) {
                cleanupRecordError = error
            }
        }

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
        if (syncError == null &&
            cleanupRecordError == null &&
            cleanupError == null &&
            pending != null
        ) {
            checkNotNull(journal).completed(pending)
        }

        if (syncError != null || cleanupRecordError != null || cleanupError != null) {
            val error = IllegalStateException(
                "A cota não foi sincronizada. Reversão ou quota sem confirmação; " +
                    "operação bloqueada para reconciliação manual com o servidor. " +
                    "Não tente importar novamente.",
                syncError ?: cleanupRecordError ?: cleanupError
            )
            importError?.let(error::addSuppressed)
            cleanupError?.takeIf { it !== error.cause }?.let(error::addSuppressed)
            throw error
        }
        throw checkNotNull(importError)
    }
}
