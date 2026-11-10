package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveIdempotentUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsArchiveAuthorizedImport
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Original Android archive and stitch generator. Fake server V2 only.
 * No production Google credentials, Supabase, billing or customer files.
 */
class DesktopWindowsArchiveAuthorizedImportTest {
    private class FakeV2 : DesktopArchiveUsageGateway,
        DesktopArchiveIdempotentUsageGateway {
        var reserveCount = 0
        var finalCount = 0
        var lastId = ""
        override suspend fun reserve(
            accessToken: String, category: DesktopArchiveInventory.Category
        ): String = error("Free V1 must not be used")
        override suspend fun reserveOnce(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            clientRequestId: String
        ): String {
            assertEquals("offline-verified-google-token", accessToken)
            assertEquals(DesktopArchiveInventory.Category.MATRIX, category)
            assertTrue(Regex(
                "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
            ).matches(clientRequestId))
            reserveCount++
            lastId = clientRequestId
            return "usage_$clientRequestId"
        }
        override suspend fun finalize(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            requestKey: String,
            success: Boolean
        ) {
            assertEquals("usage_$lastId", requestKey)
            assertTrue(success)
            finalCount++
        }
    }

    private fun sampleDst(): ByteArray = MatrixConverter.convert(
        TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = TextMatrixOptions(
                    text = "Maria",
                    heightMm = 12f,
                    style = TextStitchStyle.RUNNING
                )
            )
        ).getOrThrow(), "DST"
    ).getOrThrow().bytes

    private fun zipped(path: Path, bytes: ByteArray) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("Matrizes/Maria.dst"))
            zip.write(bytes)
            zip.closeEntry()
        }
    }

    @Test
    fun originalAndroidZipSelectionImportsThroughPersistedFreeV2Only() = runBlocking {
        val root = Files.createTempDirectory("bm-android-archive-bridge-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val journalDir = Files.createDirectory(root.resolve("journal"))
        val archive = root.resolve("fonts-and-matrices.zip")
        zipped(archive, sampleDst())
        val originalBytes = Files.readAllBytes(archive)
        val staged = SafeArchiveExtractor.stage(archive)
        try {
            val usage = FakeV2()
            val journal = DesktopArchiveQuotaJournal(journalDir)
            val client = DesktopWindowsArchiveAuthorizedImport(
                usage, DesktopVerifiedFontSession { "offline-verified-google-token" },
                journal, projects, fonts
            )
            val commit = client.importOne(archive, staged, staged.entries.single())
            assertTrue(commit.created)
            assertTrue(commit.itemId!!.endsWith(".flp"))
            assertEquals(1, usage.reserveCount)
            assertEquals(1, usage.finalCount)
            assertTrue(journal.listPending().isEmpty())
            assertEquals(1, DesktopProjectCatalog.scan(projects).projects.size)
            assertContentEquals(originalBytes, Files.readAllBytes(archive))
            assertFalse(Files.exists(fonts.resolve("Maria.dst")))
        } finally {
            staged.sourceFile.delete()
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun originalAndroid7zSelectionUsesSameDurableFreeV2Importer() = runBlocking {
        val root = Files.createTempDirectory("bm-android-7z-authorized-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val journal = DesktopArchiveQuotaJournal(
            Files.createDirectory(root.resolve("journal"))
        )
        val archive = root.resolve("catalogo.7z")
        SevenZOutputFile(archive.toFile()).use { seven ->
            seven.setContentCompression(SevenZMethod.COPY)
            seven.putArchiveEntry(SevenZArchiveEntry().apply {
                name = "Colecao/Maria.dst"
            })
            seven.write(sampleDst())
            seven.closeArchiveEntry()
        }
        val originalBytes = Files.readAllBytes(archive)
        val androidPreview = SafeArchiveExtractor.stage(archive)
        try {
            val usage = FakeV2()
            val gate = DesktopWindowsArchiveAuthorizedImport(
                usage, DesktopVerifiedFontSession { "offline-verified-google-token" },
                journal, projects, fonts
            )
            val imported = gate.importOne(
                archive, androidPreview, androidPreview.entries.single(),
                charArrayOf()
            )
            assertTrue(imported.created)
            assertTrue(imported.itemId!!.endsWith(".flp"))
            assertEquals(1, usage.reserveCount)
            assertEquals(1, usage.finalCount)
            assertTrue(journal.listPending().isEmpty())
            assertEquals(1, DesktopProjectCatalog.scan(projects).projects.size)
            assertContentEquals(originalBytes, Files.readAllBytes(archive))
            assertFalse(Files.exists(root.resolve("Colecao")))
        } finally {
            androidPreview.sourceFile.delete()
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun alteredOrForgedAndroidSelectionBlocksBeforeNetworkOrQuota() = runBlocking {
        val root = Files.createTempDirectory("bm-android-archive-forgery-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val journal = DesktopArchiveQuotaJournal(Files.createDirectory(root.resolve("journal")))
        val archive = root.resolve("matrices.zip")
        val bytes = sampleDst()
        zipped(archive, bytes)
        val staged = SafeArchiveExtractor.stage(archive)
        try {
            val usage = FakeV2()
            val client = DesktopWindowsArchiveAuthorizedImport(
                usage, DesktopVerifiedFontSession { "offline-verified-google-token" },
                journal, projects, fonts
            )
            val item = staged.entries.single()
            assertFailsWith<IllegalArgumentException> {
                client.importOne(archive, staged, item.copy(index = 111))
            }
            // Preserve the same ZIP path but change the exact bytes reviewed.
            zipped(archive, bytes + byteArrayOf(1))
            assertFailsWith<IllegalArgumentException> {
                client.importOne(archive, staged, item)
            }
            assertEquals(0, usage.reserveCount)
            assertEquals(0, usage.finalCount)
            assertTrue(journal.listPending().isEmpty())
            assertTrue(DesktopProjectCatalog.scan(projects).projects.isEmpty())
        } finally {
            staged.sourceFile.delete()
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
