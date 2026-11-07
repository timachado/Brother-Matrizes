package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveCommercialImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveImportCommit
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveImportFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveItemTransaction
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** All quota servers are fake and all records are synthetic. NO network. */
class DesktopArchiveCommercialImportGateTest {
    private val reservation = "usage_12345678-1234-1234-1234-123456789abc"
    private val verifiedSession = DesktopVerifiedFontSession { "mock-verified-google-token" }

    private fun matrix() = DesktopArchiveInventory.Item(
        0, "Colecao/bordado.pes", "bordado.pes", "PES", 100,
        DesktopArchiveInventory.Category.MATRIX
    )

    private fun font() = DesktopArchiveInventory.Item(
        1, "Letras/alfabeto.ttf", "alfabeto.ttf", "TTF", 80,
        DesktopArchiveInventory.Category.FONT
    )

    private class FakeUsage(
        var reject: Boolean = false,
        var malformed: Boolean = false,
        var failFinalize: Boolean = false
    ) : DesktopArchiveUsageGateway {
        val reserved = mutableListOf<DesktopArchiveInventory.Category>()
        val finalized = mutableListOf<Pair<DesktopArchiveInventory.Category, Boolean>>()
        override suspend fun reserve(
            accessToken: String,
            category: DesktopArchiveInventory.Category
        ): String {
            check(accessToken == "mock-verified-google-token")
            reserved += category
            if (reject) error("Cota esgotada no servidor")
            return if (malformed) "local_pro_override" else
                "usage_12345678-1234-1234-1234-123456789abc"
        }

        override suspend fun finalize(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            requestKey: String,
            success: Boolean
        ) {
            check(accessToken == "mock-verified-google-token")
            check(requestKey == "usage_12345678-1234-1234-1234-123456789abc")
            if (failFinalize) error("Servidor de cotas indisponível")
            finalized += category to success
        }
    }

    private class TempTransaction(
        private val output: Path,
        private val duplicate: Boolean = false,
        private val failAfterWrite: Boolean = false,
        private val delayMs: Long = 0
    ) : DesktopArchiveItemTransaction {
        var applied = 0
        var rolledBack = 0
        override suspend fun apply(): DesktopArchiveImportCommit {
            applied++
            if (delayMs > 0) delay(delayMs)
            if (duplicate) return DesktopArchiveImportCommit(false, null)
            Files.write(output, byteArrayOf(1, 2, 3))
            if (failAfterWrite) error("Matriz inválida após validação local")
            return DesktopArchiveImportCommit(true, output.fileName.toString())
        }

        override suspend fun rollback() {
            rolledBack++
            // This test uses only a fresh file owned by this transaction.
            if (!duplicate) Files.deleteIfExists(output)
        }
    }

    @Test
    fun noVerifiedGoogleSessionRejectsBeforeServerOrImport() = runBlocking {
        val usage = FakeUsage()
        var invoked = 0
        val gate = DesktopArchiveCommercialImportGate(
            usage = usage,
            importer = DesktopArchiveImportFactory {
                invoked++
                error("should never reach importer")
            }
        )
        assertFailsWith<IllegalStateException> {
            gate.importOne(matrix())
        }
        assertEquals(0, invoked)
        assertTrue(usage.reserved.isEmpty())
        assertTrue(usage.finalized.isEmpty())
    }

    @Test
    fun verifiedSessionWithUnavailableArchiveImporterDoesNotReserveQuota() = runBlocking {
        val usage = FakeUsage()
        val gate = DesktopArchiveCommercialImportGate(
            usage = usage,
            verifiedSession = verifiedSession
        )
        val error = assertFailsWith<IllegalStateException> {
            gate.importOne(matrix())
        }
        assertTrue(error.message.orEmpty().contains("nenhuma cota foi reservada"))
        assertTrue(usage.reserved.isEmpty())
        assertTrue(usage.finalized.isEmpty())
    }

    @Test
    fun rejectsInvalidMetadataBeforeAnyAuthOrQuotaCall() = runBlocking {
        val usage = FakeUsage()
        var authCalls = 0
        val session = DesktopVerifiedFontSession { authCalls++; "mock-verified-google-token" }
        val gate = DesktopArchiveCommercialImportGate(usage, session)
        assertFailsWith<IllegalArgumentException> {
            gate.importOne(matrix().copy(extension = "EXE"))
        }
        assertFailsWith<IllegalArgumentException> {
            gate.importOne(font().copy(sizeBytes = 25L * 1024L * 1024L))
        }
        assertEquals(0, authCalls)
        assertTrue(usage.reserved.isEmpty())
    }

    @Test
    fun reservesAndFinalizesEachCategoryIndividuallyAfterSuccessfulNewImport() = runBlocking {
        val root = Files.createTempDirectory("bm-archive-quota-success-")
        try {
            val usage = FakeUsage()
            val calls = mutableListOf<DesktopArchiveInventory.Category>()
            val gate = DesktopArchiveCommercialImportGate(
                usage, verifiedSession,
                DesktopArchiveImportFactory { item ->
                    calls += item.category
                    TempTransaction(root.resolve("${item.index}.record"))
                }
            )
            val first = gate.importOne(matrix())
            val second = gate.importOne(font())
            assertTrue(first.created)
            assertTrue(second.created)
            assertEquals(
                listOf(DesktopArchiveInventory.Category.MATRIX, DesktopArchiveInventory.Category.FONT),
                usage.reserved
            )
            assertEquals(
                listOf(
                    DesktopArchiveInventory.Category.MATRIX to true,
                    DesktopArchiveInventory.Category.FONT to true
                ), usage.finalized
            )
            assertEquals(usage.reserved, calls)
            assertTrue(Files.exists(root.resolve("0.record")))
            assertTrue(Files.exists(root.resolve("1.record")))
        } finally {
            Files.list(root).use { it.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun duplicateNeverConsumesQuotaAndNeverDeletesPreviouslyExistingFile() = runBlocking {
        val root = Files.createTempDirectory("bm-archive-quota-duplicate-")
        val existing = root.resolve("ja-existente.pes")
        Files.write(existing, byteArrayOf(9, 8, 7))
        val usage = FakeUsage()
        val tx = TempTransaction(existing, duplicate = true)
        try {
            val gate = DesktopArchiveCommercialImportGate(
                usage, verifiedSession, DesktopArchiveImportFactory { tx }
            )
            val receipt = gate.importOne(matrix())
            assertFalse(receipt.created)
            assertEquals(
                listOf(DesktopArchiveInventory.Category.MATRIX to false),
                usage.finalized
            )
            assertEquals(0, tx.rolledBack)
            assertEquals(listOf<Byte>(9, 8, 7), Files.readAllBytes(existing).toList())
        } finally {
            Files.deleteIfExists(existing)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun rejectsServerQuotaDenialAndFakeReservationWithoutCreatingItem() = runBlocking {
        for (malformed in listOf(false, true)) {
            val usage = FakeUsage(reject = !malformed, malformed = malformed)
            var importerCalls = 0
            val gate = DesktopArchiveCommercialImportGate(
                usage, verifiedSession,
                DesktopArchiveImportFactory {
                    importerCalls++
                    error("importer should never start")
                }
            )
            assertFailsWith<IllegalStateException> { gate.importOne(matrix()) }
            assertEquals(1, usage.reserved.size)
            assertTrue(usage.finalized.isEmpty())
            assertEquals(0, importerCalls)
        }
    }

    @Test
    fun partialImportFailureRollsBackAndFinalizesAsUnsuccessful() = runBlocking {
        val root = Files.createTempDirectory("bm-archive-rollback-")
        val output = root.resolve("partial.pes")
        val tx = TempTransaction(output, failAfterWrite = true)
        val usage = FakeUsage()
        try {
            val gate = DesktopArchiveCommercialImportGate(
                usage, verifiedSession, DesktopArchiveImportFactory { tx }
            )
            assertFailsWith<IllegalStateException> { gate.importOne(matrix()) }
            assertEquals(1, tx.rolledBack)
            assertEquals(
                listOf(DesktopArchiveInventory.Category.MATRIX to false),
                usage.finalized
            )
            assertFalse(Files.exists(output))
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun failedFinalizationRollsBackOnlyNewContent() = runBlocking {
        val root = Files.createTempDirectory("bm-archive-finalize-")
        val output = root.resolve("new.pes")
        val usage = FakeUsage(failFinalize = true)
        val tx = TempTransaction(output)
        try {
            val gate = DesktopArchiveCommercialImportGate(
                usage, verifiedSession, DesktopArchiveImportFactory { tx }
            )
            val error = assertFailsWith<IllegalStateException> {
                gate.importOne(matrix())
            }
            assertTrue(error.message.orEmpty().contains("não foi sincronizada"))
            assertEquals(1, tx.rolledBack)
            assertFalse(Files.exists(output))
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun cancellationAfterReservationFinalizesFalseAndRollsBack() = runBlocking {
        val root = Files.createTempDirectory("bm-archive-cancel-")
        val output = root.resolve("cancelled.pes")
        val usage = FakeUsage()
        val tx = TempTransaction(output, delayMs = 1_000)
        try {
            val gate = DesktopArchiveCommercialImportGate(
                usage, verifiedSession, DesktopArchiveImportFactory { tx }
            )
            assertFailsWith<CancellationException> {
                withTimeout(80) { gate.importOne(matrix()) }
            }
            assertEquals(1, tx.rolledBack)
            assertEquals(
                listOf(DesktopArchiveInventory.Category.MATRIX to false),
                usage.finalized
            )
            assertFalse(Files.exists(output))
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun multipleGateInstancesSerializeReservationsAndPreventOverlappingImports() = runBlocking {
        val root = Files.createTempDirectory("bm-archive-concurrent-")
        val output = root.resolve("record.pes")
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val created = AtomicInteger(0)
        val usage = FakeUsage()
        val factory = DesktopArchiveImportFactory {
            object : DesktopArchiveItemTransaction {
                private var ownsNew = false
                override suspend fun apply(): DesktopArchiveImportCommit {
                    val count = active.incrementAndGet()
                    maximum.accumulateAndGet(count) { prev, next -> maxOf(prev, next) }
                    try {
                        delay(70)
                        if (!Files.exists(output)) {
                            Files.write(output, byteArrayOf(1))
                            ownsNew = true
                            created.incrementAndGet()
                        }
                        return DesktopArchiveImportCommit(ownsNew, if (ownsNew) "new" else null)
                    } finally {
                        active.decrementAndGet()
                    }
                }
                override suspend fun rollback() {
                    if (ownsNew) Files.deleteIfExists(output)
                }
            }
        }
        try {
            val first = DesktopArchiveCommercialImportGate(usage, verifiedSession, factory)
            val second = DesktopArchiveCommercialImportGate(usage, verifiedSession, factory)
            val outcomes = withTimeout(10_000) {
                listOf(
                    async(Dispatchers.Default) { first.importOne(matrix()) },
                    async(Dispatchers.Default) { second.importOne(matrix()) }
                ).awaitAll()
            }
            assertEquals(1, maximum.get())
            assertEquals(1, created.get())
            assertEquals(1, outcomes.count { it.created })
            assertEquals(2, usage.reserved.size)
            assertEquals(1, usage.finalized.count { it.second })
            assertEquals(1, usage.finalized.count { !it.second })
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(root)
        }
    }
}
