package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherFontUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontImportSafety
import com.timachado.brothermatrizes.desktop.platform.DesktopFontRecord
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** No network or real account: a lost finalize must hide an unconfirmed font. */
class DesktopFontImportJournalSafetyTest {
    private fun withFolder(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("bm-font-journal-")
        try { block(root) }
        finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun pendingFontCannotAppearInCreatorUntilServerConfirmation() =
        withFolder { root ->
            val fonts = root.resolve("fonts")
            val id = "Maria-0123456789.ttf"
            val sha = "a".repeat(64)
            val source = DesktopFontRecord(id, "Maria", id, "ttf", fonts.resolve(id).toString())
            val journal = DesktopFontImportSafety.journalFor(fonts)
            val item = DesktopArchiveInventory.Item(
                0, "Maria.ttf", "Maria.ttf", "TTF", 128,
                DesktopArchiveInventory.Category.FONT
            )
            var pending = journal.begin(item)
            assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN, pending.stage)
            pending = journal.reserved(
                pending, "usage_12345678-1234-1234-1234-123456789abc"
            )
            pending = journal.declareOutput(pending, id, sha)
            pending = journal.verifyOutput(pending, id, sha)
            pending = journal.finalizing(pending)
            assertTrue(DesktopFontImportSafety.published(fonts, listOf(source)).isEmpty())
            assertFailsWith<IllegalStateException> { journal.begin(item) }
            // A true server ACK is the only allowed resolution here.
            journal.completed(pending)
            assertEquals(listOf(source), DesktopFontImportSafety.published(fonts, listOf(source)))
        }

    @Test
    fun liveUsageGatewayWithoutDurableJournalFailsBeforeAuthOrNetwork() =
        withFolder { root ->
            val key = "sb_publishable_offline_font_journal_fixture_123456"
            val gateway = DesktopBrotherFontUsageGateway(key, 196) {
                error("No real connection is permitted in this test.")
            }
            val gate = DesktopCommercialFontImportGate(
                DesktopImportedFontLibrary(root.resolve("fonts")),
                DesktopVerifiedFontSession {
                    error("Auth must not run without the journal.")
                },
                gateway
            )
            runBlocking {
                val e = assertFailsWith<IllegalStateException> {
                    gate.importFont(root.resolve("not-used.ttf"))
                }
                assertTrue(e.message.orEmpty().contains("registro durável"))
            }
            assertTrue(Files.notExists(root.resolve("fonts")))
        }
}
