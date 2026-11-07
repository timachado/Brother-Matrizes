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

    /**
     * A READ-ONLY diagnostic request candidate, not an authorization token.
     * Only a V2 protocol persisted BEFORE the initial HTTP request may
     * reconstruct a request key when the first server ACK was lost.
     * Version-1/legacy Pro UUIDs are never assumed to be V2.
     */
    fun diagnosticRequestKey(pending: DesktopArchiveQuotaJournal.Pending): String? {
        val given = pending.reservationKey
        val id = pending.localId
        return when (pending.protocol) {
            DesktopArchiveQuotaJournal.ReservationProtocol.LEGACY_UNKNOWN ->
                given?.takeIf { it.matches(Regex(
                    "^usage_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
                )) }
            DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2,
            DesktopArchiveQuotaJournal.ReservationProtocol.PRO_V2 -> {
                if (!id.matches(Regex(
                    "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
                ))) return null
                val prefix = if (pending.protocol ==
                    DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2) "usage_" else "pro_"
                val derived = prefix + id
                if (given == null || given == derived) derived else null
            }
        }
    }

    fun inspect(
        pending: DesktopArchiveQuotaJournal.Pending,
        server: DesktopArchiveQuotaStatus?
    ): Assessment {
        val diagnosticKey = diagnosticRequestKey(pending)
        if (diagnosticKey == null) {
            require(server == null) {
                "Estado remoto não pode ser associado à tentativa local."
            }
            return Assessment(pending.localId, Finding.NO_SERVER_KEY)
        }

        // A lost ACK is queryable only when the protocol was durably
        // recorded, but even a FOUND response NEVER settles the journal.
        if (server == null) {
            return Assessment(pending.localId, Finding.SERVER_STATUS_UNAVAILABLE)
        }
        require(server.requestKey == diagnosticKey &&
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
