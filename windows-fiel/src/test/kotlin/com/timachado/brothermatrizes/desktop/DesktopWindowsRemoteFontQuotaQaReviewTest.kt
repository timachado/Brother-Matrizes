package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatus
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaStatusReader
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaRecoveryAssessment
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsRemoteFontQuotaQaReview
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic private journal and server responses; NO production network. */
class DesktopWindowsRemoteFontQuotaQaReviewTest {
    private val fakeBearer = "fake_verified_account_qa_only_token_12345"
    private val fontItem = DesktopArchiveInventory.Item(
        0, "Fontes/QA.ttf", "QA.ttf", "TTF", 2048L,
        DesktopArchiveInventory.Category.FONT
    )
    private val matrixItem = DesktopArchiveInventory.Item(
        0, "Matrizes/QA.dst", "QA.dst", "DST", 2048L,
        DesktopArchiveInventory.Category.MATRIX
    )

    private fun withFolder(block: suspend (Path) -> Unit) = runBlocking {
        val folder = Files.createTempDirectory("bm-font-remote-status-qa-")
        try {
            block(folder)
        } finally {
            Files.walk(folder).use { entries ->
                entries.sorted(java.util.Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }

    private fun fakeReader(
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
    fun missingAndEmptyJournalNeverAskForGoogleTokenOrNetwork() = withFolder { root ->
        var tokens = 0
        var calls = 0
        val reader = fakeReader { _, _, _ ->
            calls++
            error("No remote request expected")
        }
        val review = DesktopWindowsRemoteFontQuotaQaReview(reader) {
            tokens++
            fakeBearer
        }
        val missing = review.inspect(root)
        assertFalse(missing.journalPresent)
        assertEquals(null, missing.finding)
        assertTrue(missing.userMessage().contains("Nenhuma cota foi alterada"))
        assertEquals(0L, Files.list(root).use { it.count() })

        Files.createDirectory(root.resolve("font-import-usage-journal"))
        val empty = review.inspect(root)
        assertTrue(empty.journalPresent)
        assertEquals(null, empty.finding)
        assertEquals(0, tokens)
        assertEquals(0, calls)
    }

    @Test
    fun v2KnownReservationIsQueriedOnceAndNeverCleared() = withFolder { root ->
        val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
        val journal = DesktopArchiveQuotaJournal(folder)
        val pending = journal.acquireLease().use {
            journal.begin(fontItem, DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2)
        }
        val marker = folder.resolve("pending-${pending.localId}.json")
        val original = Files.readAllBytes(marker)
        var tokens = 0
        var calls = 0
        val review = DesktopWindowsRemoteFontQuotaQaReview(
            fakeReader { access, category, key ->
                calls++
                assertEquals(fakeBearer, access)
                assertEquals(DesktopArchiveInventory.Category.FONT, category)
                assertEquals("usage_${pending.localId}", key)
                DesktopArchiveQuotaStatus(
                    key, category, DesktopArchiveQuotaStatus.State.COMPLETED
                )
            }
        ) { tokens++; fakeBearer }
        val result = review.inspect(root)
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding
            .SERVER_COMPLETED_LOCAL_UNVERIFIED, result.finding)
        assertTrue(result.userMessage().contains("arquivo local ainda exige conferência"))
        assertTrue(result.userMessage().contains("Nenhum registro foi removido"))
        assertEquals(1, calls)
        assertEquals(1, tokens)
        assertTrue(original.contentEquals(Files.readAllBytes(marker)))
        assertEquals(listOf(pending), journal.listPending())
        assertFailsWith<IllegalStateException> {
            journal.acquireLease().use { journal.begin(fontItem) }
        }
    }

    @Test
    fun unknownLegacyProAndFailureMustNotRetryOrUnlock() = withFolder { root ->
        val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
        val journal = DesktopArchiveQuotaJournal(folder)
        val legacy = journal.acquireLease().use {
            val started = journal.begin(fontItem)
            journal.reserved(started, "pro_${started.localId}")
        }
        var tokens = 0
        var calls = 0
        val diag = DesktopWindowsRemoteFontQuotaQaReview(fakeReader { _, _, _ ->
            calls++
            error("Legacy Pro must not be queried")
        }) { tokens++; fakeBearer }
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding.NO_SERVER_KEY,
            diag.inspect(root).finding)
        assertEquals(0, tokens)
        assertEquals(0, calls)
        assertEquals(listOf(legacy), journal.listPending())
    }

    @Test
    fun unavailableServerLeavesJournalPendingAndDoesNotExposeBearer() = withFolder { root ->
        val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
        val journal = DesktopArchiveQuotaJournal(folder)
        val pending = journal.acquireLease().use {
            journal.begin(fontItem, DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2)
        }
        val diag = DesktopWindowsRemoteFontQuotaQaReview(fakeReader { _, _, _ ->
            error("synthetic connection lost")
        }) { fakeBearer }
        val result = diag.inspect(root)
        assertEquals(DesktopArchiveQuotaRecoveryAssessment.Finding
            .SERVER_STATUS_UNAVAILABLE, result.finding)
        assertTrue(result.userMessage().contains("A pendência permanece"))
        assertFalse(result.userMessage().contains(fakeBearer))
        assertEquals(listOf(pending), journal.listPending())
    }

    @Test
    fun corruptMarkerAndForeignMatrixAreRejectedBeforeAnyAuthOrNetwork() =
        withFolder { root ->
            val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
            var calls = 0
            var tokens = 0
            val review = DesktopWindowsRemoteFontQuotaQaReview(fakeReader { _, _, _ ->
                calls++
                error("Forbidden status request")
            }) { tokens++; fakeBearer }
            val corrupt = folder.resolve("pending-broken.json")
            Files.writeString(corrupt, "invalid")
            assertFailsWith<Exception> { review.inspect(root) }
            assertTrue(Files.exists(corrupt))
            Files.delete(corrupt)

            val journal = DesktopArchiveQuotaJournal(folder)
            val pending = journal.acquireLease().use { journal.begin(matrixItem) }
            assertFailsWith<IllegalStateException> { review.inspect(root) }
            assertEquals(0, calls)
            assertEquals(0, tokens)
            assertEquals(listOf(pending), journal.listPending())
        }
}
