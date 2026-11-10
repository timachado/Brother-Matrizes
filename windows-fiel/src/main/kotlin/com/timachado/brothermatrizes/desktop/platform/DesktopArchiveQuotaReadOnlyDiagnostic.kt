package com.timachado.brothermatrizes.desktop.platform

import kotlinx.coroutines.CancellationException

/**
 * Read-only post-crash quota inspection. QA-only, available only behind explicit opt-in.
 *
 * All fresh reservations stay blocked while a journal marker survives.
 * The diagnostic must not call reserve, finalize, rollback, completed,
 * unlink, delete, or infer Pro entitlement. A status is only evidence for
 * explicit/manual reconciliation after the local output is verified.
 */
internal class DesktopArchiveQuotaReadOnlyDiagnostic(
    private val journal: DesktopArchiveQuotaJournal,
    private val server: DesktopArchiveQuotaStatusReader,
    private val verifiedQaToken: suspend () -> String
) {
    suspend fun inspectPending(
        expectedCategory: DesktopArchiveInventory.Category? = null
    ): DesktopArchiveQuotaRecoveryAssessment.Assessment? {
        // OS-level lock prevents the journal from being modified by a second
        // Windows process while its evidence is being inspected.
        journal.acquireLease().use {
            val records = journal.listPending()
            check(records.size <= 1) {
                "Mais de um recibo pendente. Reconciliação manual obrigatória."
            }
            val pending = records.singleOrNull() ?: return null
            check(expectedCategory == null || pending.category == expectedCategory) {
                "Categoria incompatível no registro. Diagnóstico bloqueado."
            }
            val key = DesktopArchiveQuotaRecoveryAssessment
                .diagnosticRequestKey(pending)
                ?: return DesktopArchiveQuotaRecoveryAssessment.inspect(pending, null)

            // Never open a connection when the local marker cannot prove a
            // request key. Even found=true is not permission to settle.
            val status = try {
                val token = verifiedQaToken()
                check(token.length in 16..8192 &&
                    token.all { it.code in 0x21..0x7e && it != '"' && it != '\\' }) {
                    "Sessão Google de diagnóstico inválida."
                }
                server.lookupKnown(token, pending.category, key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Timeouts, revocation, 503, malformed/mismatched HTTP
                // all leave the marker and any client data untouched.
                null
            }
            return DesktopArchiveQuotaRecoveryAssessment.inspect(pending, status)
        }
    }

    override fun toString(): String = "DesktopArchiveQuotaReadOnlyDiagnostic(redacted)"
}
