package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaRecoveryAssessment
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Completely offline diagnosis, including post-crash marker preservation.
 * No production account, OAuth session, storage, or Supabase requests.
 */
class DesktopArchiveQuotaRecoveryAssessmentTest {
    private val key = "usage_12345678-1234-1234-1234-123456789abc"
    private val item = DesktopArchiveInventory.Item(
        0, "QA/matriz.pes", "matriz.pes", "PES", 123,
        DesktopArchiveInventory.Category.MATRIX
    )

    private fun prepareJournal(
        stage: DesktopArchiveQuotaJournal.Stage,
        action: (DesktopArchiveQuotaJournal, DesktopArchiveQuotaJournal.Pending) -> Unit
    ) {
        val folder = Files.createTempDirectory("bm-reconciliation-readonly-")
        try {
            val journal = DesktopArchiveQuotaJournal(folder)
            val record = journal.acquireLease().use {
                var p = journal.begin(item)
                if (stage != DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN) {
                    p = journal.reserved(p, key)
                }
                if (stage == DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN ||
                    stage == DesktopArchiveQuotaJournal.Stage.CLEANUP_UNKNOWN) {
                    p = journal.finalizing(p)
                }
                if (stage == DesktopArchiveQuotaJournal.Stage.CLEANUP_UNKNOWN) {
                    p = journal.needsCleanup(p)
                }
                p
            }
            action(journal, record)
            assertEquals(listOf(record), journal.listPending())
            assertFailsWith<IllegalStateException> {
                journal.acquireLease().use { journal.begin(item) }
            }
        } finally {
            Files.list(folder).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun unknownReserveWithoutServerKeyNeverQueriesOrAutomaticallyReplays() {
        prepareJournal(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN) { _, record ->
            val result = DesktopArchiveQuotaRecoveryAssessment.inspect(record, null)
            assertEquals(
                DesktopArchiveQuotaRecoveryAssessment.Finding.NO_SERVER_KEY,
                result.finding
            )
            assertTrue(result.blocksNewImports)
            assertFalse(result.permitsAutomaticFinalize)
            assertFalse(result.permitsAutomaticCleanup)
        }
    }

    @Test
    fun knownReserveStatesRemainBlockedAcrossProcessRestart() {
        for (stage in listOf(
            DesktopArchiveQuotaJournal.Stage.RESERVED,
            DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN,
            DesktopArchiveQuotaJournal.Stage.CLEANUP_UNKNOWN
        )) {
            prepareJournal(stage) { journal, record ->
                val before = journal.listPending()
                val cases = listOf(
                    DesktopArchiveQuotaStatus.State.RESERVED to
                        DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STILL_RESERVED,
                    DesktopArchiveQuotaStatus.State.COMPLETED to
                        DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_COMPLETED_LOCAL_UNVERIFIED,
                    DesktopArchiveQuotaStatus.State.FAILED to
                        DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_FAILED_LOCAL_UNVERIFIED,
                    DesktopArchiveQuotaStatus.State.NOT_FOUND to
                        DesktopArchiveQuotaRecoveryAssessment.Finding.UNRECOGNIZED_SERVER_RESERVATION
                )
                for ((serverState, finding) in cases) {
                    val result = DesktopArchiveQuotaRecoveryAssessment.inspect(
                        record,
                        DesktopArchiveQuotaStatus(
                            key, DesktopArchiveInventory.Category.MATRIX, serverState
                        )
                    )
                    assertEquals(finding, result.finding)
                    assertTrue(result.blocksNewImports)
                    assertFalse(result.permitsAutomaticFinalize)
                    assertFalse(result.permitsAutomaticCleanup)
                }
                assertEquals(before, journal.listPending())
            }
        }
    }

    @Test
    fun timeoutAndWrongRemoteKeyNeverAuthorizeJournalDeletion() {
        prepareJournal(DesktopArchiveQuotaJournal.Stage.RESERVED) { _, record ->
            val timeout = DesktopArchiveQuotaRecoveryAssessment.inspect(record, null)
            assertEquals(
                DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STATUS_UNAVAILABLE,
                timeout.finding
            )
            for (bad in listOf(
                DesktopArchiveQuotaStatus(
                    "usage_00000000-0000-0000-0000-000000000000",
                    DesktopArchiveInventory.Category.MATRIX,
                    DesktopArchiveQuotaStatus.State.COMPLETED
                ),
                DesktopArchiveQuotaStatus(
                    key, DesktopArchiveInventory.Category.FONT,
                    DesktopArchiveQuotaStatus.State.COMPLETED
                )
            )) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopArchiveQuotaRecoveryAssessment.inspect(record, bad)
                }
            }
        }
    }

    @Test
    fun ephemeralProReservationCannotBeReconciledFromUsageTable() {
        val record = DesktopArchiveQuotaJournal.Pending(
            localId = "12345678-1234-1234-1234-123456789abc",
            category = DesktopArchiveInventory.Category.FONT,
            entrySha256 = "1".repeat(64),
            reservationKey = "pro_12345678-1234-1234-1234-123456789abc",
            stage = DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN,
            createdAtMillis = 1L
        )
        val status = DesktopArchiveQuotaRecoveryAssessment.inspect(record, null)
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding.NO_SERVER_KEY,
            status.finding)
        assertFalse(status.permitsAutomaticCleanup)
    }

    @Test
    fun durableProV2StatusIsDiagnosticOnlyAndNeverDeletesJournal() {
        val folder = Files.createTempDirectory("bm-pro-v2-status-")
        try {
            val journal = DesktopArchiveQuotaJournal(folder)
            val stage = journal.acquireLease().use {
                val begun = journal.begin(item)
                journal.reserved(begun, "pro_${begun.localId}")
            }
            for ((state, finding) in listOf(
                DesktopArchiveQuotaStatus.State.RESERVED to
                    DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STILL_RESERVED,
                DesktopArchiveQuotaStatus.State.COMPLETED to
                    DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_COMPLETED_LOCAL_UNVERIFIED,
                DesktopArchiveQuotaStatus.State.FAILED to
                    DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_FAILED_LOCAL_UNVERIFIED,
                DesktopArchiveQuotaStatus.State.NOT_FOUND to
                    DesktopArchiveQuotaRecoveryAssessment.Finding.UNRECOGNIZED_SERVER_RESERVATION
            )) {
                val assessment = DesktopArchiveQuotaRecoveryAssessment.inspect(
                    stage, DesktopArchiveQuotaStatus(
                        "pro_${stage.localId}",
                        DesktopArchiveInventory.Category.MATRIX, state
                    )
                )
                assertEquals(finding, assessment.finding)
                assertTrue(assessment.blocksNewImports)
                assertFalse(assessment.permitsAutomaticFinalize)
                assertFalse(assessment.permitsAutomaticCleanup)
                assertEquals(listOf(stage), journal.listPending())
                assertFailsWith<IllegalStateException> {
                    journal.acquireLease().use { journal.begin(item) }
                }
            }
            // Legacy Pro V1 also minted UUIDv4, but it was independent of
            // the local journal ID. A syntactically valid key is NOT proof.
            val legacy = stage.copy(
                reservationKey = "pro_00000000-0000-4000-8000-000000000000"
            )
            assertEquals(
                DesktopArchiveQuotaRecoveryAssessment.Finding.NO_SERVER_KEY,
                DesktopArchiveQuotaRecoveryAssessment.inspect(legacy, null).finding
            )
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveQuotaRecoveryAssessment.inspect(
                    legacy, DesktopArchiveQuotaStatus(
                        "pro_00000000-0000-4000-8000-000000000000",
                        DesktopArchiveInventory.Category.MATRIX,
                        DesktopArchiveQuotaStatus.State.COMPLETED
                    )
                )
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveQuotaRecoveryAssessment.inspect(
                    stage, DesktopArchiveQuotaStatus(
                        "pro_00000000-0000-4000-8000-000000000000",
                        DesktopArchiveInventory.Category.MATRIX,
                        DesktopArchiveQuotaStatus.State.COMPLETED
                    )
                )
            }
        } finally {
            Files.list(folder).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
        }
    }
}
