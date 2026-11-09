package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

/**
 * Contrato de autorização comercial, sem credenciais persistidas.
 *
 * Paridade com BrotherMatrizesAccountService.authorizeImport/finalizeImport
 * do Android: conta verificada -> reserva "import_font" no servidor ->
 * importação local -> finalize(success = nova importação).
 *
 * Nada nesta classe ativa a UI ou autoriza acesso por estado local.
 * O port Windows ainda não tem OAuth/PKCE homologado; até lá,
 * WindowsFontSessionUnavailable impede a emissão de qualquer autorização.
 */
internal fun interface DesktopVerifiedFontSession {
    suspend fun accessToken(): String
}

internal interface DesktopFontUsageGateway {
    suspend fun reserveFont(accessToken: String): String
    suspend fun finalizeFont(accessToken: String, requestKey: String, success: Boolean)
}

/**
 * The live Windows HTTP adapter MUST reserve using the UUID written to the
 * crash journal before network I/O. Legacy V1 reserve() stays available only
 * for existing offline test doubles and the original Android contract.
 */
internal interface DesktopFontIdempotentUsageGateway : DesktopFontUsageGateway {
    suspend fun reserveFontOnce(accessToken: String, clientRequestId: String): String
}

/** Falha fechada para toda a aplicação Windows enquanto não houver OAuth homologado. */
internal object WindowsFontSessionUnavailable : DesktopVerifiedFontSession {
    override suspend fun accessToken(): String =
        error("Conta Google Windows ainda não homologada. Importação comercial indisponível.")
}

/**
 * Testado com adaptadores em memória e fontes de referência.
 * NUNCA interpretar token local, perfil, 'pro=true' ou arquivo existente
 * como prova de entitlement. A validação pertence ao servidor.
 */
/**
 * New font imports now require crash-safe quota evidence when a real HTTP
 * gateway is attached. Existing fake tests can remain fully offline.
 *
 * A marker is persisted BEFORE reserve, the target SHA is recorded BEFORE
 * publishing the file, and FINALIZE_UNKNOWN is written BEFORE HTTP finalize.
 * If the process crashes, DesktopFontImportSafety hides unconfirmed fonts.
 * Neither this class nor startup code automatically retries unknown quota
 * requests (V1 reserve has no guaranteed request idempotency).
 */
internal class DesktopCommercialFontImportGate(
    private val library: DesktopImportedFontLibrary,
    private val verifiedSession: DesktopVerifiedFontSession = WindowsFontSessionUnavailable,
    private val usage: DesktopFontUsageGateway,
    private val journal: DesktopArchiveQuotaJournal? = null
) {
    private companion object {
        val transactionLock = Mutex()
    }
    private val serverReservation = Regex(
        "^(usage|pro)_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    )

    suspend fun importFont(source: Path, displayName: String? = null): DesktopFontRecord {
        transactionLock.lock()
        var lease: DesktopArchiveQuotaJournal.Lease? = null
        try {
            check(usage !is DesktopBrotherFontUsageGateway || journal != null) {
                "Importação com cotas reais exige registro durável de transações."
            }
            lease = journal?.acquireLease()
            return importFontLocked(source, displayName)
        } finally {
            lease?.close()
            transactionLock.unlock()
        }
    }

    private suspend fun importFontLocked(source: Path, displayName: String?): DesktopFontRecord {
        val token = verifiedSession.accessToken()
        check(token.isNotBlank()) { "É necessário entrar na conta Google verificada." }

        // Validate the selected local file before reserving. No symlink,
        // arbitrary extension, oversized file or missing file is permitted.
        val file = source.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(file) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file.parent)) {
            "Selecione um arquivo TTF/OTF local regular."
        }
        val extension = file.fileName.toString().substringAfterLast('.', "")
            .uppercase(java.util.Locale.ROOT)
        require(extension in setOf("TTF", "OTF") &&
            Files.size(file) in 4..(12L * 1024L * 1024L)) {
            "Fonte TTF/OTF inválida ou maior que 12 MB."
        }

        // Persist a nonidentifying fingerprint and RESERVE_UNKNOWN first.
        val selected = DesktopArchiveInventory.Item(
            index = 0, entryName = file.fileName.toString(),
            displayName = file.fileName.toString(),
            extension = extension, sizeBytes = Files.size(file),
            category = DesktopArchiveInventory.Category.FONT
        )
        val protocol = if (usage is DesktopFontIdempotentUsageGateway)
            DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2
        else DesktopArchiveQuotaJournal.ReservationProtocol.LEGACY_UNKNOWN
        // A free V2 request is journalled BEFORE the first HTTP call.
        // Pro V2 requires a separate persisted receipt: never silently
        // fall back to Pro V1 or mint another UUID after any uncertainty.
        var pending = journal?.begin(selected, protocol)
        val reservation = if (usage is DesktopFontIdempotentUsageGateway) {
            val persisted = checkNotNull(pending) {
                "Reserva idempotente de fonte exige journal durável."
            }
            usage.reserveFontOnce(token, persisted.localId)
        } else usage.reserveFont(token)
        check(serverReservation.matches(reservation)) {
            "Reserva de importação inválida recebida do servidor."
        }
        if (pending != null) {
            pending = checkNotNull(journal).reserved(checkNotNull(pending), reservation)
        }

        var created: DesktopFontRecord? = null
        var result: DesktopFontRecord? = null
        var importFailure: Throwable? = null
        try {
            val stored = library.importFileWithResult(file, displayName,
                beforePublish = if (journal == null) null else { fileName, sha256 ->
                    pending = checkNotNull(journal).declareOutput(
                        checkNotNull(pending), fileName, sha256
                    )
                }
            )
            result = stored.record
            if (stored.created) {
                created = stored.record
                if (journal != null) {
                    val sha = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(Path.of(stored.record.absolutePath)))
                        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    pending = journal.verifyOutput(
                        checkNotNull(pending), stored.record.id, sha
                    )
                }
            }
        } catch (failure: Throwable) {
            importFailure = failure
        }

        // If publication was declared but the library threw before returning
        // an ownership receipt, the new file MAY already exist on disk.
        // Finalizing(false) and clearing this marker would expose it in
        // DesktopSavedFontBridge without a confirmed import_font quota.
        // Preserve RESERVED + output evidence for manual reconciliation.
        // Never guess that an unverified file is safe to delete or finalize.
        if (importFailure != null && created == null &&
            pending?.output != null) {
            throw IllegalStateException(
                "Publicação da fonte incerta. O registro durável bloqueia a " +
                    "fonte e novas importações até reconciliação.",
                importFailure
            )
        }

        // A finalize response may be lost after the server has committed.
        // Never clear this marker or perform another reserve on uncertainty.
        if (pending != null) {
            pending = checkNotNull(journal).finalizing(checkNotNull(pending))
        }
        val confirmed = created != null && importFailure == null
        try {
            withContext(NonCancellable) {
                usage.finalizeFont(token, reservation, confirmed)
            }
        } catch (failure: Throwable) {
            if (created != null) {
                try {
                    library.delete(created.id)
                } catch (cleanup: Throwable) {
                    failure.addSuppressed(cleanup)
                }
            }
            // FINALIZE_UNKNOWN deliberately stays on disk. Any remaining
            // font is filtered from the creator/library by SHA-256 identity.
            throw IllegalStateException(
                "Cota da fonte não confirmada; importação bloqueada até reconciliação.",
                failure
            )
        }

        // The server acknowledged failure, so no new file may survive.
        if (importFailure != null && created != null) {
            library.delete(created.id)
        }
        // Only after server ACK and local cleanup does the marker disappear.
        if (pending != null) checkNotNull(journal).completed(checkNotNull(pending))
        if (importFailure != null) throw importFailure
        return checkNotNull(result) { "Importação não produziu registro de fonte." }
    }
}
