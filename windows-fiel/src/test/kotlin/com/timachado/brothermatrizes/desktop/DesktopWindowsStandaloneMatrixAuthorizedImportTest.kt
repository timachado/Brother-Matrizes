package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveIdempotentUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsStandaloneMatrixAuthorizedImport
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic standalone Telegram/Files import with no Telegram session,
 * customer files, Supabase account, real reserve, billing or network.
 */
class DesktopWindowsStandaloneMatrixAuthorizedImportTest {
    private class SyntheticFreeV2 : DesktopArchiveUsageGateway,
        DesktopArchiveIdempotentUsageGateway {
        var reserves = 0
        val completed = mutableListOf<Boolean>()
        var failBeforeReply = false
        var lastId = ""
        override suspend fun reserve(
            accessToken: String,
            category: DesktopArchiveInventory.Category
        ): String = error("Legacy reserve is NEVER allowed for local Windows import")

        override suspend fun reserveOnce(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            clientRequestId: String
        ): String {
            assertEquals("synthetic-verified-token-no-network", accessToken)
            assertEquals(DesktopArchiveInventory.Category.MATRIX, category)
            assertTrue(Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
            ).matches(clientRequestId))
            lastId = clientRequestId
            reserves++
            if (failBeforeReply) error("Synthetic lost reservation reply")
            return "usage_$clientRequestId"
        }

        override suspend fun finalize(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            requestKey: String,
            success: Boolean
        ) {
            assertEquals("usage_$lastId", requestKey)
            completed += success
        }
    }

    private fun dst(): ByteArray = MatrixConverter.convert(
        TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = "Lia", heightMm = 12f,
                    style = TextStitchStyle.RUNNING
                )
            )
        ).getOrThrow(), "DST"
    ).getOrThrow().bytes

    private fun withLibrary(block: suspend (
        Path, Path, Path, DesktopArchiveQuotaJournal
    ) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("bm-telegram-quota-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val journal = DesktopArchiveQuotaJournal(
            Files.createDirectory(root.resolve("journal"))
        )
        try {
            block(root, projects, fonts, journal)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun gate(
        gateway: DesktopArchiveUsageGateway,
        projects: Path,
        fonts: Path,
        journal: DesktopArchiveQuotaJournal
    ) = DesktopWindowsStandaloneMatrixAuthorizedImport(
        usage = gateway,
        verifiedSession = DesktopVerifiedFontSession {
            "synthetic-verified-token-no-network"
        },
        journal = journal,
        projectFolder = projects,
        fontFolder = fonts
    )

    @Test
    fun importedSelectedDstBecomesVerifiedFlpWithoutChangingOriginal() =
        withLibrary { root, projects, fonts, journal ->
            val selected = root.resolve("Lia.dst")
            val content = dst()
            Files.write(selected, content)
            val qaServer = SyntheticFreeV2()
            val importer = gate(qaServer, projects, fonts, journal)
            val first = importer.importOne(selected)
            assertTrue(first.created)
            assertTrue(first.itemId!!.endsWith(".flp"))
            assertEquals(1, qaServer.reserves)
            assertEquals(listOf(true), qaServer.completed)
            assertEquals(1, DesktopProjectCatalog.scan(projects).projects.size)
            assertTrue(journal.listPending().isEmpty())
            assertContentEquals(content, Files.readAllBytes(selected))
            assertFalse(Files.exists(root.resolve("Lia.flp")))

            // Duplicate stays untouched: never overwrite an existing FLP.
            val repeated = importer.importOne(selected)
            assertFalse(repeated.created)
            assertEquals(2, qaServer.reserves)
            assertEquals(listOf(true, false), qaServer.completed)
            assertEquals(1, DesktopProjectCatalog.scan(projects).projects.size)
            assertTrue(journal.listPending().isEmpty())
        }

    @Test
    fun pesDstJefFilesImportIntoLibraryWithSingleFreeV2ReservationEach() =
        withLibrary { root, projects, fonts, journal ->
            // Full standalone import path; never uses a real account or quota.
            val design = TextLayoutGenerator.generate(
                TextLayoutOptions(
                    textOptions = TextMatrixOptions(
                        text = "Lia", heightMm = 12f,
                        style = TextStitchStyle.RUNNING
                    )
                )
            ).getOrThrow()
            val qaServer = SyntheticFreeV2()
            val importer = gate(qaServer, projects, fonts, journal)

            for (format in listOf("PES", "DST", "JEF")) {
                val selected = root.resolve("Lia.$format")
                val bytes = MatrixConverter.convert(design, format).getOrThrow().bytes
                Files.write(selected, bytes)
                val imported = importer.importOne(selected)

                assertTrue(imported.created, "Importação autorizada falhou: $format")
                val flp = projects.resolve(checkNotNull(imported.itemId))
                val reopened = DesktopEmbroideryFiles.open(flp)
                assertEquals(format, reopened.format)
                assertTrue(
                    reopened.points.count { it.command == StitchCommand.STITCH } > 10,
                    "As pontadas originais não foram preservadas: $format"
                )
                assertContentEquals(bytes, Files.readAllBytes(selected))
            }
            assertEquals(3, qaServer.reserves)
            assertEquals(listOf(true, true, true), qaServer.completed)
            assertEquals(3, DesktopProjectCatalog.scan(projects).projects.size)
            assertTrue(journal.listPending().isEmpty())
        }

    @Test
    fun invalidLocalFileNeverCreatesReservationOrProject() =
        withLibrary { root, projects, fonts, journal ->
            val qaServer = SyntheticFreeV2()
            val importer = gate(qaServer, projects, fonts, journal)
            val invalid = root.resolve("not-a-matrix.txt")
            Files.write(invalid, byteArrayOf(1, 2, 3))
            assertFailsWith<IllegalArgumentException> {
                importer.importOne(invalid)
            }
            assertFailsWith<IllegalArgumentException> {
                importer.importOne(root.resolve("missing.dst"))
            }
            assertEquals(0, qaServer.reserves)
            assertTrue(journal.listPending().isEmpty())
            assertTrue(DesktopProjectCatalog.scan(projects).projects.isEmpty())
        }

    @Test
    fun lostReservationReplyLeavesSharedMarkerAndBlocksRetry() =
        withLibrary { root, projects, fonts, journal ->
            val selected = root.resolve("example.dst")
            Files.write(selected, dst())
            val qaServer = SyntheticFreeV2().also { it.failBeforeReply = true }
            val importer = gate(qaServer, projects, fonts, journal)
            assertFailsWith<IllegalStateException> {
                importer.importOne(selected)
            }
            assertEquals(1, qaServer.reserves)
            val pending = journal.listPending().single()
            assertEquals(DesktopArchiveQuotaJournal.ReservationProtocol.FREE_V2,
                pending.protocol)
            assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN,
                pending.stage)
            assertEquals(pending.localId, qaServer.lastId)
            assertTrue(pending.reservationKey == null)

            // A new UI click or application restart never mints another
            // reservation while the previous remote result is unknown.
            assertFailsWith<IllegalStateException> {
                gate(qaServer, projects, fonts, journal).importOne(selected)
            }
            assertEquals(1, qaServer.reserves)
            assertEquals(0, qaServer.completed.size)
            assertTrue(DesktopProjectCatalog.scan(projects).projects.isEmpty())
        }
}
