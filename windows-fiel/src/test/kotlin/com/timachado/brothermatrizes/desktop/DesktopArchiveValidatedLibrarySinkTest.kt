package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveCommercialImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveSelectiveImportFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveValidatedLibrarySinkFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
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
 * Windows regression tests with original Android embroidery codecs and a
 * real runner OS font. Mock token and mock quota only: never production.
 */
class DesktopArchiveValidatedLibrarySinkTest {
    private val qaSession = DesktopVerifiedFontSession { "mock-google-only-qa-123456789" }
    private val validKey = "usage_12345678-1234-1234-1234-123456789abc"

    private class Usage(var denyFinalization: Boolean = false) : DesktopArchiveUsageGateway {
        val reserved = mutableListOf<DesktopArchiveInventory.Category>()
        val finished = mutableListOf<Boolean>()
        override suspend fun reserve(
            accessToken: String, category: DesktopArchiveInventory.Category
        ): String {
            check(accessToken.startsWith("mock-google-only-qa"))
            reserved += category
            return "usage_12345678-1234-1234-1234-123456789abc"
        }
        override suspend fun finalize(
            accessToken: String, category: DesktopArchiveInventory.Category,
            requestKey: String, success: Boolean
        ) {
            check(requestKey == "usage_12345678-1234-1234-1234-123456789abc")
            if (denyFinalization) error("quota server unreachable — fake")
            finished += success
        }
    }

    private fun originalName() = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Maria",
                heightMm = 12f,
                style = TextStitchStyle.RUNNING
            )
        )
    ).getOrThrow()

    private fun zipped(path: Path, entryName: String, payload: ByteArray) {
        ZipOutputStream(Files.newOutputStream(path)).use { writer ->
            writer.putNextEntry(ZipEntry(entryName))
            writer.write(payload)
            writer.closeEntry()
        }
    }

    private fun fontFixture(): Path {
        val windowsFonts = Path.of(
            System.getenv("WINDIR") ?: "C:\\Windows", "Fonts"
        )
        return listOf(
            windowsFonts.resolve("arial.ttf"),
            windowsFonts.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Runner has no accessible public TTF font fixture.")
    }

    private fun cleanDir(path: Path) {
        if (Files.exists(path)) {
            Files.list(path).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun realAndroidPesDstJefParsersProduceVerifiedFlpLibraryEntriesWithNoOverwrite() = runBlocking {
        val root = Files.createTempDirectory("bm-validated-matrices-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val source = originalName()
        try {
            for (format in listOf("PES", "DST", "JEF")) {
                val bytes = MatrixConverter.convert(source, format).getOrThrow().bytes
                val archive = root.resolve("$format.zip")
                zipped(archive, "Colecao/matriz.$format", bytes)
                val originalZip = Files.readAllBytes(archive)
                val inventory = DesktopArchiveInventory.inspect(archive)
                val usage = Usage()
                val gate = DesktopArchiveCommercialImportGate(
                    usage, qaSession,
                    DesktopArchiveSelectiveImportFactory(
                        archive, inventory,
                        DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)
                    )
                )
                val item = inventory.entries.single()
                val created = gate.importOne(item)
                assertTrue(created.created, format)
                assertEquals(listOf(true), usage.finished)
                val flp = projects.resolve(checkNotNull(created.itemId))
                assertTrue(Files.isRegularFile(flp))
                assertTrue(flp.fileName.toString().endsWith(".flp"))
                val reopened = DesktopEmbroideryFiles.open(flp)
                assertTrue(
                    reopened.points.count { it.command == StitchCommand.STITCH } > 10,
                    "Motor Android não conseguiu reabrir a matriz $format"
                )
                assertEquals(format, reopened.format)
                val originalFlp = Files.readAllBytes(flp)

                val duplicate = gate.importOne(item)
                assertFalse(duplicate.created, format)
                assertEquals(listOf(true, false), usage.finished)
                assertContentEquals(originalFlp, Files.readAllBytes(flp))
                assertContentEquals(originalZip, Files.readAllBytes(archive))
            }
            val catalog = DesktopProjectCatalog.scan(projects)
            assertEquals(3, catalog.projects.size)
            assertEquals(0, catalog.skippedInvalid)
        } finally {
            cleanDir(projects)
            cleanDir(fonts)
            Files.list(root).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun invalidMatrixDoesNotPersistAndFinalizesFalse() = runBlocking {
        val root = Files.createTempDirectory("bm-invalid-archive-matrix-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val archive = root.resolve("invalid.zip")
        try {
            zipped(archive, "Colecao/arquivo.pes", byteArrayOf(1, 2, 3, 4, 5))
            val inventory = DesktopArchiveInventory.inspect(archive)
            val usage = Usage()
            val gate = DesktopArchiveCommercialImportGate(
                usage, qaSession,
                DesktopArchiveSelectiveImportFactory(
                    archive, inventory,
                    DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)
                )
            )
            assertFailsWith<Exception> { gate.importOne(inventory.entries.single()) }
            assertEquals(listOf(false), usage.finished)
            assertTrue(Files.list(projects).use { it.count() == 0L })
            assertTrue(Files.list(fonts).use { it.count() == 0L })
        } finally {
            Files.deleteIfExists(archive)
            cleanDir(projects)
            cleanDir(fonts)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun quotaFinalizationFailureRollsBackCreatedFlpAndKeepsExistingProject() = runBlocking {
        val root = Files.createTempDirectory("bm-sink-quota-rollback-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val prior = projects.resolve("prior.flp")
        val archive = root.resolve("new.zip")
        try {
            val source = originalName()
            com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
                .saveNew(source, prior)
            val originalPrior = Files.readAllBytes(prior)
            zipped(
                archive, "new.dst",
                MatrixConverter.convert(source, "DST").getOrThrow().bytes
            )
            val originalArchive = Files.readAllBytes(archive)
            val preview = DesktopArchiveInventory.inspect(archive)
            val usage = Usage(denyFinalization = true)
            val gate = DesktopArchiveCommercialImportGate(
                usage, qaSession,
                DesktopArchiveSelectiveImportFactory(
                    archive, preview,
                    DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)
                )
            )
            assertFailsWith<IllegalStateException> {
                gate.importOne(preview.entries.single())
            }
            assertEquals(1, usage.reserved.size)
            assertEquals(listOf(prior), Files.list(projects).use { it.toList() })
            assertContentEquals(originalPrior, Files.readAllBytes(prior))
            assertContentEquals(originalArchive, Files.readAllBytes(archive))
        } finally {
            Files.deleteIfExists(archive)
            cleanDir(projects)
            cleanDir(fonts)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun realSkiaTtfIsValidatedStoredDeduplicatedAndNeverChangesSystemFont() = runBlocking {
        val root = Files.createTempDirectory("bm-validated-font-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val archive = root.resolve("fonts.zip")
        val system = fontFixture()
        val originalFont = Files.readAllBytes(system)
        try {
            zipped(archive, "Fontes/MinhaFonte.ttf", originalFont)
            val preview = DesktopArchiveInventory.inspect(archive)
            val usage = Usage()
            val gate = DesktopArchiveCommercialImportGate(
                usage, qaSession,
                DesktopArchiveSelectiveImportFactory(
                    archive, preview,
                    DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)
                )
            )
            val item = preview.entries.single()
            val first = gate.importOne(item)
            assertTrue(first.created)
            val records = DesktopImportedFontLibrary(fonts).list()
            assertEquals(1, records.size)
            assertEquals(first.itemId, records.single().id)
            assertContentEquals(
                originalFont, Files.readAllBytes(Path.of(records.single().absolutePath))
            )
            val duplicate = gate.importOne(item)
            assertFalse(duplicate.created)
            assertEquals(listOf(true, false), usage.finished)
            assertEquals(1, DesktopImportedFontLibrary(fonts).list().size)
            assertContentEquals(originalFont, Files.readAllBytes(system))
        } finally {
            Files.deleteIfExists(archive)
            cleanDir(projects)
            cleanDir(fonts)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun fakeTtfMagicIsRejectedBySkiaWithoutPublishingToFontLibrary() = runBlocking {
        val root = Files.createTempDirectory("bm-reject-fake-ttf-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val archive = root.resolve("fake.zip")
        try {
            zipped(
                archive, "fake.ttf",
                byteArrayOf(0, 1, 0, 0) + ByteArray(256)
            )
            val preview = DesktopArchiveInventory.inspect(archive)
            val usage = Usage()
            val gate = DesktopArchiveCommercialImportGate(
                usage, qaSession,
                DesktopArchiveSelectiveImportFactory(
                    archive, preview,
                    DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)
                )
            )
            assertFailsWith<Exception> { gate.importOne(preview.entries.single()) }
            assertEquals(listOf(false), usage.finished)
            assertTrue(DesktopImportedFontLibrary(fonts).list().isEmpty())
        } finally {
            Files.deleteIfExists(archive)
            cleanDir(projects)
            cleanDir(fonts)
            Files.deleteIfExists(root)
        }
    }
    @Test
    fun endToEndMatrixPersistsOwnershipEvidenceBeforeFinishingQuota() = runBlocking {
        val root = Files.createTempDirectory("bm-output-evidence-matrix-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val records = Files.createDirectory(root.resolve("journal"))
        val archive = root.resolve("matrix.zip")
        try {
            val source = originalName()
            zipped(archive, "Colecao/matriz.pes",
                MatrixConverter.convert(source, "PES").getOrThrow().bytes)
            val preview = DesktopArchiveInventory.inspect(archive)
            val journal = DesktopArchiveQuotaJournal(records)
            val usage = Usage()
            val gate = DesktopArchiveCommercialImportGate(
                usage, qaSession,
                DesktopArchiveSelectiveImportFactory(archive, preview,
                    DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)),
                journal
            )
            val receipt = gate.importOne(preview.entries.single())
            assertTrue(receipt.created)
            assertEquals(listOf(true), usage.finished)
            assertTrue(journal.listPending().isEmpty())
            val target = projects.resolve(checkNotNull(receipt.itemId))
            assertTrue(Files.isRegularFile(target))
            assertTrue(DesktopEmbroideryFiles.open(target).points.isNotEmpty())
            val duplicate = gate.importOne(preview.entries.single())
            assertFalse(duplicate.created)
            assertEquals(listOf(true, false), usage.finished)
            assertTrue(journal.listPending().isEmpty())
        } finally {
            Files.deleteIfExists(archive)
            cleanDir(projects)
            cleanDir(fonts)
            cleanDir(records)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun lostFinalizationKeepsVerifiedOwnershipMarkerWithoutDeletingExistingContent() = runBlocking {
        val root = Files.createTempDirectory("bm-output-evidence-unknown-")
        val projects = Files.createDirectory(root.resolve("projects"))
        val fonts = Files.createDirectory(root.resolve("fonts"))
        val records = Files.createDirectory(root.resolve("journal"))
        val archive = root.resolve("new.zip")
        val existing = projects.resolve("existing.flp")
        try {
            Files.write(existing, byteArrayOf(1, 2, 3, 4))
            val original = Files.readAllBytes(existing)
            zipped(archive, "matrix.dst",
                MatrixConverter.convert(originalName(), "DST").getOrThrow().bytes)
            val preview = DesktopArchiveInventory.inspect(archive)
            val usage = Usage(denyFinalization = true)
            val journal = DesktopArchiveQuotaJournal(records)
            val gate = DesktopArchiveCommercialImportGate(
                usage, qaSession,
                DesktopArchiveSelectiveImportFactory(archive, preview,
                    DesktopArchiveValidatedLibrarySinkFactory(projects, fonts)),
                journal
            )
            assertFailsWith<IllegalStateException> {
                gate.importOne(preview.entries.single())
            }
            val pending = journal.listPending().single()
            assertEquals(DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN, pending.stage)
            assertEquals(true, pending.output?.verifiedNew)
            assertTrue(pending.output?.fileName?.endsWith(".flp") == true)
            assertTrue(pending.output?.sha256?.length == 64)
            assertFalse(Files.exists(projects.resolve(checkNotNull(pending.output).fileName)))
            assertContentEquals(original, Files.readAllBytes(existing))
            assertFailsWith<IllegalStateException> {
                journal.acquireLease().use {
                    journal.begin(preview.entries.single())
                }
            }
        } finally {
            Files.deleteIfExists(archive)
            cleanDir(projects)
            cleanDir(fonts)
            cleanDir(records)
            Files.deleteIfExists(root)
        }
    }

}
