package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopCommercialFontImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopFontIdempotentUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Synthetic local-only test of a lost server reservation response.
 * No actual network, Supabase user, font publication or quota consumption.
 */
class DesktopCommercialFontFreeV2JournalTest {
    @Test
    fun freeV2PersistsSameClientUuidBeforeReserveAndNeverFallsBackToLegacy() =
        runBlocking {
            val root = Files.createTempDirectory("bm-font-free-v2-journal-")
            try {
                val source = root.resolve("font.ttf")
                Files.write(source, byteArrayOf(0, 1, 2, 3))
                val journalDir = Files.createDirectory(root.resolve("journal"))
                val journal = DesktopArchiveQuotaJournal(journalDir)
                var reserveV1Count = 0
                var reserveV2Count = 0
                var sentUuid: String? = null
                val server = object : DesktopFontIdempotentUsageGateway {
                    override suspend fun reserveFont(accessToken: String): String {
                        reserveV1Count++
                        error("Legacy V1 must NEVER be called by Windows gate")
                    }
                    override suspend fun reserveFontOnce(
                        accessToken: String, clientRequestId: String
                    ): String {
                        assertEquals("synthetic-verified-qa-token", accessToken)
                        reserveV2Count++
                        sentUuid = clientRequestId
                        // A lost response may follow a successful remote
                        // reserve: the only safe action is to retain marker.
                        error("Simulated lost reserve response")
                    }
                    override suspend fun finalizeFont(
                        accessToken: String, requestKey: String, success: Boolean
                    ) {
                        error("Never finalize a reservation without receipt")
                    }
                }
                val gate = DesktopCommercialFontImportGate(
                    library = DesktopImportedFontLibrary(root.resolve("fonts")),
                    verifiedSession = DesktopVerifiedFontSession {
                        "synthetic-verified-qa-token"
                    },
                    usage = server,
                    journal = journal
                )
                assertFailsWith<IllegalStateException> { gate.importFont(source) }
                assertEquals(1, reserveV2Count)
                assertEquals(0, reserveV1Count)
                val saved = journal.listPending().single()
                assertEquals(DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2,
                    saved.protocol)
                assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN,
                    saved.stage)
                assertEquals(saved.localId, sentUuid)
                assertEquals(null, saved.reservationKey)
                assertTrue(Files.readString(
                    journalDir.resolve("pending-${saved.localId}.json")
                ).contains("FREE_V2"))

                // Restart/retry must NEVER mint a second reservation when
                // the previous attempt has an ambiguous remote outcome.
                assertFailsWith<IllegalStateException> { gate.importFont(source) }
                assertEquals(1, reserveV2Count)
                assertEquals(0, reserveV1Count)
                assertEquals(saved, journal.listPending().single())
                assertTrue(!Files.exists(root.resolve("fonts")) ||
                    Files.list(root.resolve("fonts")).use { !it.findAny().isPresent })
            } finally {
                Files.walk(root).use { paths ->
                    paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }
}
