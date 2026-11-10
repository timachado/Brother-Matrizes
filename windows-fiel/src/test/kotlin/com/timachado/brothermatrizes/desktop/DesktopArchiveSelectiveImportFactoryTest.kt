package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveCommercialImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveImportCommit
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveSelectiveImportFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveValidatedSink
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveValidatedSinkFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
 * Authenticated path is simulated entirely in memory and a private temp
 * directory. No user data, network, live quotas or production project store.
 */
class DesktopArchiveSelectiveImportFactoryTest {
    private val verified = DesktopVerifiedFontSession { "verified-mock-google-session" }
    private val reservation = "usage_12345678-1234-1234-1234-123456789abc"

    private class FakeUsage(var failFinalize: Boolean = false) : DesktopArchiveUsageGateway {
        var reserveCount = 0
        val finalized = mutableListOf<Boolean>()
        override suspend fun reserve(
            accessToken: String, category: DesktopArchiveInventory.Category
        ): String {
            check(accessToken == "verified-mock-google-session")
            reserveCount++
            return "usage_12345678-1234-1234-1234-123456789abc"
        }
        override suspend fun finalize(
            accessToken: String, category: DesktopArchiveInventory.Category,
            requestKey: String, success: Boolean
        ) {
            check(requestKey == "usage_12345678-1234-1234-1234-123456789abc")
            if (failFinalize) error("quota response unavailable")
            finalized += success
        }
    }

    private fun archive(path: Path, payload: ByteArray) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("Colecao/minha-matriz.dst"))
            zip.write(payload)
            zip.closeEntry()
        }
    }

    private fun sink(
        target: Path,
        expectReservation: () -> Unit,
        failAfterWrite: Boolean = false,
        duplicate: Boolean = false
    ): DesktopArchiveValidatedSinkFactory = DesktopArchiveValidatedSinkFactory {
        var ownsCreatedFile = false
        object : DesktopArchiveValidatedSink {
            override suspend fun validateAndStore(privateExtractedFile: Path): DesktopArchiveImportCommit {
                expectReservation()
                assertEquals("dst", privateExtractedFile.fileName.toString().substringAfterLast('.'))
                // This is ONLY a synthetic fixture check. Production will
                // require Android's PES/DST/JEF validator before persistence.
                assertEquals("SYNTHETIC-DST-QA", Files.readString(privateExtractedFile))
                if (duplicate) return DesktopArchiveImportCommit(false, null)
                Files.write(
                    target, Files.readAllBytes(privateExtractedFile),
                    StandardOpenOption.CREATE_NEW
                )
                ownsCreatedFile = true
                if (failAfterWrite) error("validation failed after partial store")
                return DesktopArchiveImportCommit(true, target.fileName.toString())
            }
            override suspend fun rollback() {
                if (ownsCreatedFile) Files.deleteIfExists(target)
            }
        }
    }

    @Test
    fun verifiedSessionAndServerReservationRequiredBeforeAnyExtractionOrCommit() = runBlocking {
        val root = Files.createTempDirectory("bm-selective-gate-")
        val zip = root.resolve("source.zip")
        val target = root.resolve("committed.dst")
        try {
            archive(zip, "SYNTHETIC-DST-QA".toByteArray())
            val preview = DesktopArchiveInventory.inspect(zip)
            val usage = FakeUsage()
            val factory = DesktopArchiveSelectiveImportFactory(
                zip, preview, sink(target, { assertEquals(1, usage.reserveCount) })
            )
            val denied = DesktopArchiveCommercialImportGate(
                usage = usage, importer = factory
            )
            assertFailsWith<IllegalStateException> {
                denied.importOne(preview.entries.single())
            }
            assertEquals(0, usage.reserveCount)
            assertFalse(Files.exists(target))

            val approved = DesktopArchiveCommercialImportGate(
                usage = usage, verifiedSession = verified, importer = factory
            )
            val result = approved.importOne(preview.entries.single())
            assertTrue(result.created)
            assertEquals("committed.dst", result.itemId)
            assertEquals(1, usage.reserveCount)
            assertEquals(listOf(true), usage.finalized)
            assertContentEquals(
                "SYNTHETIC-DST-QA".toByteArray(), Files.readAllBytes(target)
            )
            assertTrue(Files.isRegularFile(zip))
        } finally {
            Files.deleteIfExists(target)
            Files.deleteIfExists(zip)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun serverFinalizeErrorRemovesOnlyOwnNewOutputAndNeverTouchesOriginalArchive() = runBlocking {
        val root = Files.createTempDirectory("bm-selective-sync-error-")
        val zip = root.resolve("source.zip")
        val target = root.resolve("new.dst")
        val older = root.resolve("old.dst")
        val originalOld = byteArrayOf(4, 5, 6)
        val payload = "SYNTHETIC-DST-QA".toByteArray()
        try {
            archive(zip, payload)
            Files.write(older, originalOld)
            val originalZip = Files.readAllBytes(zip)
            val preview = DesktopArchiveInventory.inspect(zip)
            val usage = FakeUsage(failFinalize = true)
            val gate = DesktopArchiveCommercialImportGate(
                usage, verified,
                DesktopArchiveSelectiveImportFactory(zip, preview,
                    sink(target, { assertEquals(1, usage.reserveCount) })
                )
            )
            assertFailsWith<IllegalStateException> {
                gate.importOne(preview.entries.single())
            }
            assertEquals(1, usage.reserveCount)
            assertFalse(Files.exists(target))
            assertContentEquals(originalOld, Files.readAllBytes(older))
            assertContentEquals(originalZip, Files.readAllBytes(zip))
        } finally {
            Files.deleteIfExists(target)
            Files.deleteIfExists(older)
            Files.deleteIfExists(zip)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun invalidItemOrChangedArchiveAfterInventoryRefundsReservationAndNeverCommits() = runBlocking {
        val root = Files.createTempDirectory("bm-selective-changed-")
        val zip = root.resolve("source.zip")
        val target = root.resolve("committed.dst")
        try {
            archive(zip, "SYNTHETIC-DST-QA".toByteArray())
            val preview = DesktopArchiveInventory.inspect(zip)
            val usage = FakeUsage()
            val factory = DesktopArchiveSelectiveImportFactory(zip, preview, sink(target, { }))
            // Same legitimate entry name and size; new bytes invalidate SHA.
            archive(zip, "SYNTHETIC-DST-QB".toByteArray())
            val gate = DesktopArchiveCommercialImportGate(usage, verified, factory)
            assertFailsWith<IllegalArgumentException> {
                gate.importOne(preview.entries.single())
            }
            assertEquals(1, usage.reserveCount)
            assertEquals(listOf(false), usage.finalized)
            assertFalse(Files.exists(target))
        } finally {
            Files.deleteIfExists(target)
            Files.deleteIfExists(zip)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun partialSinkErrorTriggersRefundAndOnlyOwnedOutputRollback() = runBlocking {
        val root = Files.createTempDirectory("bm-selective-rollback-")
        val zip = root.resolve("source.zip")
        val target = root.resolve("partially-created.dst")
        try {
            archive(zip, "SYNTHETIC-DST-QA".toByteArray())
            val preview = DesktopArchiveInventory.inspect(zip)
            val usage = FakeUsage()
            val factory = DesktopArchiveSelectiveImportFactory(
                zip, preview, sink(target, { }, failAfterWrite = true)
            )
            val gate = DesktopArchiveCommercialImportGate(usage, verified, factory)
            assertFailsWith<IllegalStateException> {
                gate.importOne(preview.entries.single())
            }
            assertEquals(listOf(false), usage.finalized)
            assertFalse(Files.exists(target))
        } finally {
            Files.deleteIfExists(target)
            Files.deleteIfExists(zip)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun duplicateEntryDoesNotClaimSuccessOrDeletePreviousProject() = runBlocking {
        val root = Files.createTempDirectory("bm-selective-duplicate-")
        val zip = root.resolve("source.zip")
        val existing = root.resolve("previous.dst")
        try {
            archive(zip, "SYNTHETIC-DST-QA".toByteArray())
            Files.write(existing, byteArrayOf(4, 3, 2))
            val preview = DesktopArchiveInventory.inspect(zip)
            val usage = FakeUsage()
            val factory = DesktopArchiveSelectiveImportFactory(
                zip, preview, sink(existing, { }, duplicate = true)
            )
            val gate = DesktopArchiveCommercialImportGate(usage, verified, factory)
            val result = gate.importOne(preview.entries.single())
            assertFalse(result.created)
            assertEquals(listOf(false), usage.finalized)
            assertContentEquals(byteArrayOf(4, 3, 2), Files.readAllBytes(existing))
        } finally {
            Files.deleteIfExists(existing)
            Files.deleteIfExists(zip)
            Files.deleteIfExists(root)
        }
    }
}
