package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaReadOnlyDiagnostic
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaRecoveryAssessment
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatus
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatusReader
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic quota and tokens only. No Google, Supabase or customer data. */
class DesktopArchiveQuotaReadOnlyDiagnosticTest {
    private val item = DesktopArchiveInventory.Item(
        0, "QA/test.pes", "test.pes", "PES", 200L,
        DesktopArchiveInventory.Category.MATRIX
    )
    private val token = "fake_verified_google_qa_token_123456"

    private fun withJournal(block: suspend (DesktopArchiveQuotaJournal, Path) -> Unit) =
        runBlocking {
            val folder = Files.createTempDirectory("bm-diagnostic-readonly-")
            try {
                block(DesktopArchiveQuotaJournal(folder), folder)
            } finally {
                Files.list(folder).use { files -> files.forEach(Files::deleteIfExists) }
                Files.deleteIfExists(folder)
            }
        }

    private fun statusReader(
        answer: (String, DesktopArchiveInventory.Category, String) ->
            DesktopArchiveQuotaStatus
    ): DesktopArchiveQuotaStatusReader = object : DesktopArchiveQuotaStatusReader {
        override suspend fun lookupKnown(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            requestKey: String
        ): DesktopArchiveQuotaStatus = answer(accessToken, category, requestKey)
    }

    @Test
    fun noPendingAndLegacyProNeverRequestTokenOrQueryServer() = withJournal {
        journal, _ ->
        var tokens = 0
        var calls = 0
        val reader = statusReader { _, _, _ ->
            calls++
            error("Legacy Pro receipt must not trigger a status request")
        }
        val service = DesktopArchiveQuotaReadOnlyDiagnostic(journal, reader) {
            tokens++
            token
        }
        assertEquals(null, service.inspectPending())
        val legacy = journal.acquireLease().use {
            val started = journal.begin(item)
            journal.reserved(started, "pro_${started.localId}")
        }
        val found = service.inspectPending()
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding.NO_SERVER_KEY,
            found?.finding)
        assertEquals(0, calls)
        assertEquals(0, tokens)
        assertEquals(listOf(legacy), journal.listPending())
        assertTrue(found?.blocksNewImports == true)
        assertFalse(found?.permitsAutomaticCleanup ?: true)
    }

    @Test
    fun freeV2LostAckAndProV2KnownKeyAreOnlyInspectedNotSettled() = withJournal {
        journal, folder ->
        for (protocol in listOf(
            DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2,
            DesktopArchiveQuotaJournal.ReservationProtocol.PRO_V2
        )) {
            val pending = journal.acquireLease().use {
                journal.begin(item, protocol)
            }
            val expected = (if (protocol ==
                DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2) "usage_" else "pro_") +
                    pending.localId
            var tokens = 0
            var reads = 0
            val reader = statusReader { auth, category, requestKey ->
                reads++
                assertEquals(token, auth)
                assertEquals(item.category, category)
                assertEquals(expected, requestKey)
                DesktopArchiveQuotaStatus(
                    requestKey, category, DesktopArchiveQuotaStatus.State.COMPLETED
                )
            }
            val diag = DesktopArchiveQuotaReadOnlyDiagnostic(journal, reader) {
                tokens++
                token
            }
            val report = diag.inspectPending()
            assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding
                .SERVER_COMPLETED_LOCAL_UNVERIFIED, report?.finding)
            assertEquals(1, tokens)
            assertEquals(1, reads)
            assertTrue(report?.blocksNewImports == true)
            assertFalse(report?.permitsAutomaticCleanup ?: true)
            assertFalse(report?.permitsAutomaticFinalize ?: true)
            assertEquals(listOf(pending), journal.listPending())
            assertFailsWith<IllegalStateException> {
                journal.acquireLease().use { journal.begin(item) }
            }
            // Delete only our synthetic QA marker to simulate the next
            // independent protocol fixture. The production class never does.
            Files.delete(folder.resolve("pending-${pending.localId}.json"))
        }
    }

    @Test
    fun serverFailureAndInvalidTokenFailClosedWhileRetainingMarker() = withJournal {
        journal, _ ->
        val pending = journal.acquireLease().use {
            journal.begin(item, DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2)
        }
        var reads = 0
        val unavailable = statusReader { _, _, _ ->
            reads++
            error("timeout - synthetic")
        }
        val diag = DesktopArchiveQuotaReadOnlyDiagnostic(journal, unavailable) { token }
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STATUS_UNAVAILABLE,
            diag.inspectPending()?.finding)
        assertEquals(1, reads)
        assertEquals(listOf(pending), journal.listPending())

        val denied = DesktopArchiveQuotaReadOnlyDiagnostic(journal, unavailable) { "bad" }
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding.SERVER_STATUS_UNAVAILABLE,
            denied.inspectPending()?.finding)
        assertEquals(1, reads)
        assertEquals(listOf(pending), journal.listPending())

        val corrupted = DesktopArchiveQuotaReadOnlyDiagnostic(journal,
            statusReader { _, category, _ ->
                DesktopArchiveQuotaStatus("usage_00000000-0000-4000-8000-000000000000",
                    category, DesktopArchiveQuotaStatus.State.COMPLETED)
            }) { token }
        assertFailsWith<IllegalArgumentException> {
            corrupted.inspectPending()
        }
        assertEquals(listOf(pending), journal.listPending())
    }

    @Test
    fun cancellationPropagatesAndDoesNotTouchJournal() = withJournal { journal, _ ->
        val pending = journal.acquireLease().use {
            journal.begin(item, DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2)
        }
        val diag = DesktopArchiveQuotaReadOnlyDiagnostic(journal, statusReader { _, _, _ ->
            throw CancellationException("QA simulated cancel")
        }) { token }
        assertFailsWith<CancellationException> { diag.inspectPending() }
        assertEquals(listOf(pending), journal.listPending())
    }
}
