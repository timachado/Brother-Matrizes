package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveCommercialImportGate
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveImportCommit
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveImportFactory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveItemTransaction
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveQuotaJournal
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopBrotherArchiveUsageGateway
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedFontSession
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Simulated crash / quota responses; never invokes Supabase or Google. */
class DesktopArchiveQuotaJournalTest {
    private val verified = DesktopVerifiedFontSession { "mock-verified-token-qa-only" }
    private val reservation = "usage_12345678-1234-1234-1234-123456789abc"

    private fun matrix() = DesktopArchiveInventory.Item(
        0, "Colecao/minha-matriz.pes", "minha-matriz.pes", "PES", 123,
        DesktopArchiveInventory.Category.MATRIX
    )
    private fun font() = DesktopArchiveInventory.Item(
        1, "Fontes/TTFTeste.ttf", "TTFTeste.ttf", "TTF", 234,
        DesktopArchiveInventory.Category.FONT
    )

    private fun withFolder(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("bm-usage-journal-qa-")
        try {
            block(root)
        } finally {
            Files.list(root).use { stream ->
                stream.forEach(Files::deleteIfExists)
            }
            Files.deleteIfExists(root)
        }
    }

    private class Usage(
        var reserveFails: Boolean = false,
        var finalizeFails: Boolean = false,
        val beforeReserve: (() -> Unit)? = null
    ) : DesktopArchiveUsageGateway {
        var reservations = 0
        val completed = mutableListOf<Boolean>()
        override suspend fun reserve(
            accessToken: String, category: DesktopArchiveInventory.Category
        ): String {
            check(accessToken == "mock-verified-token-qa-only")
            beforeReserve?.invoke()
            reservations++
            if (reserveFails) error("connection lost while reserving")
            return "usage_12345678-1234-1234-1234-123456789abc"
        }

        override suspend fun finalize(
            accessToken: String,
            category: DesktopArchiveInventory.Category,
            requestKey: String,
            success: Boolean
        ) {
            check(requestKey == "usage_12345678-1234-1234-1234-123456789abc")
            if (finalizeFails) error("server response lost AFTER finalize")
            completed += success
        }
    }

    private class Tx(
        val file: Path,
        var rejects: Boolean = false
    ) : DesktopArchiveItemTransaction {
        var rolls = 0
        override suspend fun apply(): DesktopArchiveImportCommit {
            Files.write(file, byteArrayOf(1, 2, 3))
            if (rejects) error("client validator refused item")
            return DesktopArchiveImportCommit(true, file.fileName.toString())
        }
        override suspend fun rollback() {
            rolls++
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun persistsOpaqueRecordBeforeNetworkAndNeverStoresBearerOrOriginalArchiveName() {
        withFolder { folder ->
            val journal = DesktopArchiveQuotaJournal(folder)
            val pending = journal.acquireLease().use {
                journal.begin(matrix())
            }
            assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN, pending.stage)
            assertEquals(listOf(pending), DesktopArchiveQuotaJournal(folder).listPending())
            val onDisk = Files.list(folder).use { paths ->
                paths.filter { it.fileName.toString().endsWith(".json") }
                    .findFirst().orElseThrow()
            }
            val raw = Files.readString(onDisk)
            assertTrue(raw.contains("entrySha256"))
            assertFalse(raw.contains("minha-matriz"))
            assertFalse(raw.contains("Colecao"))
            assertFalse(raw.contains("mock-verified-token"))
            assertFalse(raw.contains("sb_secret"))
            assertEquals(null, pending.reservationKey)
        }
    }

    @Test
    fun survivesNewInstanceAndPreventsSecondReservationUntilManualReconciliation() {
        withFolder { folder ->
            val journal = DesktopArchiveQuotaJournal(folder)
            journal.acquireLease().use {
                val record = journal.begin(font())
                val reserved = journal.reserved(record, reservation)
                assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVED, reserved.stage)
            }
            val restarted = DesktopArchiveQuotaJournal(folder)
            restarted.acquireLease().use {
                val pending = restarted.listPending().single()
                assertEquals(reservation, pending.reservationKey)
                assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVED, pending.stage)
                val error = assertFailsWith<IllegalStateException> {
                    restarted.begin(matrix())
                }
                assertTrue(error.message.orEmpty().contains("reconciliação"))
            }
        }
    }

    @Test
    fun supportsDurableFinalizeStatesButNeverAutoFinalizesOrDeletesOnStartup() {
        withFolder { folder ->
            val journal = DesktopArchiveQuotaJournal(folder)
            val record = journal.acquireLease().use {
                val opened = journal.begin(matrix())
                val reserved = journal.reserved(opened, reservation)
                journal.finalizing(reserved)
            }
            val restarted = DesktopArchiveQuotaJournal(folder)
            val unresolved = restarted.listPending().single()
            assertEquals(DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN, unresolved.stage)
            assertEquals(record, unresolved)
            assertFailsWith<IllegalStateException> {
                restarted.begin(matrix())
            }
            // Local tests explicitly simulate the server ACK and successful
            // rollback; only then may the caller remove this marker.
            restarted.acquireLease().use {
                val cleanup = restarted.needsCleanup(unresolved)
                assertEquals(DesktopArchiveQuotaJournal.Stage.CLEANUP_UNKNOWN, cleanup.stage)
                restarted.completed(cleanup)
            }
            assertTrue(restarted.listPending().isEmpty())
        }
    }

    @Test
    fun corruptMarkerOrAbandonedAtomicTempBlocksEveryNewReservation() {
        withFolder { folder ->
            val journal = DesktopArchiveQuotaJournal(folder)
            val temporary = Files.createFile(folder.resolve("pending-abandoned.tmp"))
            assertFailsWith<IllegalStateException> { journal.listPending() }
            assertFailsWith<IllegalStateException> {
                journal.acquireLease().use { journal.begin(matrix()) }
            }
            Files.delete(temporary)
            val pending = journal.acquireLease().use { journal.begin(matrix()) }
            val file = folder.resolve("pending-${pending.localId}.json")
            Files.writeString(file, "{untrusted")
            assertFailsWith<Exception> { DesktopArchiveQuotaJournal(folder).listPending() }
            assertFailsWith<Exception> {
                journal.acquireLease().use { journal.begin(font()) }
            }
        }
    }

    @Test
    fun simultaneousJvmInstancesCannotAcquireSameCrossProcessLease() {
        withFolder { folder ->
            val first = DesktopArchiveQuotaJournal(folder)
            val second = DesktopArchiveQuotaJournal(folder)
            first.acquireLease().use {
                assertFailsWith<IllegalStateException> {
                    second.acquireLease()
                }
            }
            second.acquireLease().use {
                assertTrue(second.listPending().isEmpty())
            }
        }
    }

    @Test
    fun fakeServerSuccessSettlesRecordAndAllowsNextOperation() = runBlocking {
        withFolder { folder ->
            val journal = DesktopArchiveQuotaJournal(folder)
            val output = folder.resolve("new.pes")
            val tx = Tx(output)
            val usage = Usage(beforeReserve = {
                assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN,
                    journal.listPending().single().stage)
            })
            val gate = DesktopArchiveCommercialImportGate(
                usage, verified, DesktopArchiveImportFactory { tx }, journal
            )
            val result = gate.importOne(matrix())
            assertTrue(result.created)
            assertEquals(listOf(true), usage.completed)
            assertEquals(1, usage.reservations)
            assertTrue(journal.listPending().isEmpty())
            assertTrue(Files.isRegularFile(output))
            Files.delete(output)
        }
    }

    @Test
    fun lostReserveResponseBlocksRetriesEvenThoughLocalKeyIsUnknown() = runBlocking {
        withFolder { folder ->
            val journal = DesktopArchiveQuotaJournal(folder)
            val usage = Usage(reserveFails = true)
            val gate = DesktopArchiveCommercialImportGate(
                usage, verified,
                DesktopArchiveImportFactory {
                    error("No extraction is authorized")
                },
                journal
            )
            assertFailsWith<IllegalStateException> { gate.importOne(matrix()) }
            assertEquals(1, usage.reservations)
            val pending = journal.listPending().single()
            assertEquals(DesktopArchiveQuotaJournal.Stage.RESERVE_UNKNOWN, pending.stage)
            assertEquals(null, pending.reservationKey)
            assertFailsWith<IllegalStateException> { gate.importOne(matrix()) }
            assertEquals(1, usage.reservations)
        }
    }

    @Test
    fun lostFinalizeResponseRollsBackNewFileButLeavesUnresolvedRecord() = runBlocking {
        withFolder { folder ->
            val output = folder.resolve("created.pes")
            val journal = DesktopArchiveQuotaJournal(folder)
            val tx = Tx(output)
            val usage = Usage(finalizeFails = true)
            val gate = DesktopArchiveCommercialImportGate(
                usage, verified, DesktopArchiveImportFactory { tx }, journal
            )
            val error = assertFailsWith<IllegalStateException> {
                gate.importOne(matrix())
            }
            assertTrue(error.message.orEmpty().contains("reconciliação"))
            assertFalse(Files.exists(output))
            assertEquals(1, tx.rolls)
            assertEquals(DesktopArchiveQuotaJournal.Stage.FINALIZE_UNKNOWN,
                journal.listPending().single().stage)
            assertEquals(reservation, journal.listPending().single().reservationKey)
            assertFailsWith<IllegalStateException> { gate.importOne(font()) }
            assertEquals(1, usage.reservations)
        }
    }

    @Test
    fun validationFailureFinalizesFalseRollsBackAndClosesJournal() = runBlocking {
        withFolder { folder ->
            val output = folder.resolve("bad.pes")
            val journal = DesktopArchiveQuotaJournal(folder)
            val tx = Tx(output, rejects = true)
            val usage = Usage()
            val gate = DesktopArchiveCommercialImportGate(
                usage, verified, DesktopArchiveImportFactory { tx }, journal
            )
            assertFailsWith<IllegalStateException> {
                gate.importOne(matrix())
            }
            assertEquals(listOf(false), usage.completed)
            assertEquals(1, tx.rolls)
            assertFalse(Files.exists(output))
            assertTrue(journal.listPending().isEmpty())
        }
    }

    @Test
    fun productionProtocolRequiresCrashJournalEvenWithInjectedVerifiedToken() = runBlocking {
        val gateway = DesktopBrotherArchiveUsageGateway(
            publishableKey = "sb_publishable_local_test_not_production",
            versionCode = 195
        ) { error("No network must be invoked") }
        val gate = DesktopArchiveCommercialImportGate(
            usage = gateway,
            verifiedSession = verified,
            importer = DesktopArchiveImportFactory { error("No importer") }
        )
        assertFailsWith<IllegalStateException> {
            gate.importOne(matrix())
        }
    }
}
