package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Path

/**
 * Explicit Windows QA-only, read-only status of an EXISTING font reservation.
 *
 * A local marker is required before any token lookup or server request.
 * No reserve/finalize/rollback, local mutation, Pro grant, quota deduction,
 * or release of a pending font occurs here. The server is the same official
 * Brother Matrizes backend used by Android; no new service is created.
 */
internal class DesktopWindowsRemoteFontQuotaQaReview(
    private val statusReader: DesktopArchiveQuotaStatusReader,
    private val verifiedQaToken: suspend () -> String
) {
    data class Result(
        val journalPresent: Boolean,
        val finding: DesktopArchiveQuotaRecoveryAssessment.Finding?
    ) {
        fun userMessage(): String {
            val detail = when {
                !journalPresent ->
                    "Não há registro local de importação de fontes para consultar."
                finding == null ->
                    "Nenhuma importação pendente foi registrada neste PC."
                finding == DesktopArchiveQuotaRecoveryAssessment.Finding.NO_SERVER_KEY ->
                    "A importação não tem identificador verificável. Revisão manual obrigatória."
                finding == DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STATUS_UNAVAILABLE ->
                    "Não foi possível confirmar a reserva no servidor. A pendência permanece."
                finding == DesktopArchiveQuotaRecoveryAssessment.Finding.UNRECOGNIZED_SERVER_RESERVATION ->
                    "O servidor não reconhece a reserva. Revisão manual obrigatória."
                finding == DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STILL_RESERVED ->
                    "O servidor informa reserva ativa. A importação segue bloqueada."
                finding == DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_COMPLETED_LOCAL_UNVERIFIED ->
                    "O servidor informa conclusão, mas o arquivo local ainda exige conferência."
                else ->
                    "O servidor informa falha, mas a reconciliação local não foi autorizada."
            }
            return detail + "\nNenhuma cota foi alterada. " +
                "Nenhum registro foi removido e nenhuma fonte foi liberada."
        }
    }

    suspend fun inspect(userDataFolder: Path): Result {
        // This check also rejects broken/malicious markers and foreign
        // categories before the token provider or HTTP client can be called.
        val local = DesktopWindowsPendingFontQuotaReview.inspect(userDataFolder)
        if (!local.journalFound || local.pendingCount == 0) {
            return Result(local.journalFound, null)
        }
        val journal = DesktopArchiveQuotaJournal(
            userDataFolder.toAbsolutePath().normalize().resolve("font-import-usage-journal")
        )
        val assessment = DesktopArchiveQuotaReadOnlyDiagnostic(
            journal, statusReader, verifiedQaToken
        ).inspectPending(DesktopArchiveInventory.Category.FONT)
        check(assessment == null || (
            assessment.blocksNewImports &&
                !assessment.permitsAutomaticFinalize &&
                !assessment.permitsAutomaticCleanup
        )) { "Diagnóstico não pode autorizar reconciliação automática." }
        return Result(true, assessment?.finding)
    }

    suspend fun inspectWindowsDefault(): Result =
        inspect(DesktopWindowsDpapiSessionStore.defaultUserDataFolder())

    override fun toString(): String = "DesktopWindowsRemoteFontQuotaQaReview(redacted)"
}
