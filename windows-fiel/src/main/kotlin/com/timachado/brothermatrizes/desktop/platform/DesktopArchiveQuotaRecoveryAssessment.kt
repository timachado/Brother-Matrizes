package com.timachado.brothermatrizes.desktop.platform

/**
 * Pure, READ-ONLY interpretation of a previously journaled quota attempt.
 * No state transitions, no remote retries, no deletion and no entitlement
 * inferred from an HTTP response.
 *
 * A remote result is diagnostic evidence, NOT permission to release the
 * journal marker or delete a persisted matrix or font. Server idempotency,
 * local ownership and persisted success intent are still missing.
 */
internal object DesktopArchiveQuotaRecoveryAssessment {
    enum class Finding {
        NO_SERVER_KEY,
        SERVER_STATUS_UNAVAILABLE,
        UNRECOGNIZED_SERVER_RESERVATION,
        SERVER_STILL_RESERVED,
        SERVER_COMPLETED_LOCAL_UNVERIFIED,
        SERVER_FAILED_LOCAL_UNVERIFIED
    }

    data class Assessment(
        val localId: String,
        val finding: Finding,
        val blocksNewImports: Boolean = true,
        val permitsAutomaticFinalize: Boolean = false,
        val permitsAutomaticCleanup: Boolean = false
    )

    fun inspect(
        pending: DesktopArchiveQuotaJournal.Pending,
        server: DesktopArchiveQuotaStatus?
    ): Assessment {
        require(pending.reservationKey == null ||
            pending.reservationKey.startsWith("usage_") ||
            pending.reservationKey.startsWith("pro_")) {
            "Registro local inválido."
        }
        // Lost reserve response: the server minted an UNKNOWN key.
        // No known key can be queried, and probing a different key is unsafe.
        if (pending.reservationKey == null || pending.reservationKey.startsWith("pro_")) {
            require(server == null) {
                "Estado remoto não pode ser associado à tentativa local."
            }
            return Assessment(pending.localId, Finding.NO_SERVER_KEY)
        }

        if (server == null) {
            return Assessment(pending.localId, Finding.SERVER_STATUS_UNAVAILABLE)
        }
        require(server.requestKey == pending.reservationKey &&
            server.category == pending.category) {
            "Resposta remota pertence a outra operação ou outra categoria."
        }
        val finding = when (server.state) {
            DesktopArchiveQuotaStatus.State.NOT_FOUND ->
                Finding.UNRECOGNIZED_SERVER_RESERVATION
            DesktopArchiveQuotaStatus.State.RESERVED ->
                Finding.SERVER_STILL_RESERVED
            DesktopArchiveQuotaStatus.State.COMPLETED ->
                Finding.SERVER_COMPLETED_LOCAL_UNVERIFIED
            DesktopArchiveQuotaStatus.State.FAILED ->
                Finding.SERVER_FAILED_LOCAL_UNVERIFIED
        }
        return Assessment(pending.localId, finding)
    }
}
