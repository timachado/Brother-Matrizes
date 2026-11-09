package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsPendingFontQuotaReview
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Local synthetic fixtures only; no Google account, backend or paid quota. */
class DesktopWindowsPendingFontQuotaReviewTest {
    private fun inFolder(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("bm-font-quota-review-")
        try {
            block(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder()).forEach { file ->
                    Files.deleteIfExists(file)
                }
            }
        }
    }

    @Test
    fun absentJournalDoesNotCreateAnyFilesOrClaimRemoteQuota() = inFolder { root ->
        val before = Files.list(root).use { it.count() }
        val report = DesktopWindowsPendingFontQuotaReview.inspect(root)
        assertFalse(report.journalFound)
        assertEquals(0, report.pendingCount)
        assertTrue(report.userMessage().contains("NÃO confirma"))
        assertEquals(before, Files.list(root).use { it.count() })
    }

    @Test
    fun pendingFontReservationRemainsUnchangedAfterReview() = inFolder { root ->
        val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
        val journal = DesktopArchiveQuotaJournal(folder)
        val item = DesktopArchiveInventory.Item(
            0, "fonts/Teste.ttf", "Teste.ttf", "TTF", 1024L,
            DesktopArchiveInventory.Category.FONT
        )
        val pending = journal.acquireLease().use {
            journal.begin(item, DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2)
        }
        val marker = folder.resolve("pending-${pending.localId}.json")
        val before = Files.readAllBytes(marker)
        val result = DesktopWindowsPendingFontQuotaReview.inspect(root)
        assertTrue(result.journalFound)
        assertEquals(1, result.pendingCount)
        assertEquals(1, result.stages[DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN])
        assertTrue(result.userMessage().contains("Novas importações ficam bloqueadas"))
        assertTrue(before.contentEquals(Files.readAllBytes(marker)))
        assertEquals(listOf(pending), journal.listPending())
        assertEquals(2L, Files.list(folder).use { it.count() }) // marker + original lock
    }

    @Test
    fun corruptJournalIsRejectedRatherThanMisreportedAsClear() = inFolder { root ->
        val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
        Files.writeString(folder.resolve("pending-broken.json"), "invalid")
        assertFailsWith<Exception> {
            DesktopWindowsPendingFontQuotaReview.inspect(root)
        }
        assertTrue(Files.exists(folder.resolve("pending-broken.json")))
    }

    @Test
    fun directoryWithUnexpectedCategoryFailsClosed() = inFolder { root ->
        val folder = Files.createDirectory(root.resolve("font-import-usage-journal"))
        val journal = DesktopArchiveQuotaJournal(folder)
        journal.acquireLease().use {
            journal.begin(DesktopArchiveInventory.Item(
                0, "matrizes/teste.dst", "teste.dst", "DST", 2048L,
                DesktopArchiveInventory.Category.MATRIX
            ))
        }
        assertFailsWith<IllegalStateException> {
            DesktopWindowsPendingFontQuotaReview.inspect(root)
        }
        assertEquals(1, journal.listPending().size)
    }
}
