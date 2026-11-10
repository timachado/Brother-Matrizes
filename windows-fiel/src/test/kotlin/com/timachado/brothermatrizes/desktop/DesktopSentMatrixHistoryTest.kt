package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopMachineTransferFile
import com.timachado.brothermatrizes.desktop.platform.DesktopSentMatrixHistory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Android Enviados parity: history follows a successful, explicit export
 * from the Windows machine-file path. No actual machine/device/network.
 */
class DesktopSentMatrixHistoryTest {
    private fun cleanup(root: Path) {
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun successfulMachineFileSaveCanBeRecordedAndSurvivesRestart() {
        val root = Files.createTempDirectory("bm-sent-history-")
        try {
            val history = DesktopSentMatrixHistory(root.resolve("meta").resolve("sent.tsv"))
            assertTrue(history.list().isEmpty())
            val payload = ByteArray(542) { (it % 91).toByte() }
            val prepared = DesktopMachineTransferFile.Prepared("DST", "nome.dst", payload)
            val output = root.resolve("nome.dst")
            val saved = DesktopMachineTransferFile.saveNew(prepared, output)
            assertEquals(output, saved)
            assertContentEquals(payload, Files.readAllBytes(saved))
            val records = history.recordExport(saved.fileName.toString(), "DST", "USB / OTG", 12345678L)
            assertEquals(1, records.size)
            assertEquals("nome.dst", records.single().fileName)
            assertEquals("USB / OTG", records.single().method)
            assertEquals(12345678L, records.single().sentAtMillis)
            assertEquals(records, DesktopSentMatrixHistory(root.resolve("meta/sent.tsv")).list())

            assertTrue(history.remove(records.single().id).isEmpty())
            assertTrue(history.list().isEmpty())
            assertContentEquals(payload, Files.readAllBytes(saved))
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun failedOrCanceledExportDoesNotCreateAHistoryRecord() {
        val root = Files.createTempDirectory("bm-sent-no-export-")
        try {
            val file = root.resolve("already.dst")
            val existing = byteArrayOf(1, 2, 3)
            Files.write(file, existing)
            val history = DesktopSentMatrixHistory(root.resolve("meta/sent.tsv"))
            assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                DesktopMachineTransferFile.saveNew(
                    DesktopMachineTransferFile.Prepared("DST", "already.dst", byteArrayOf(4, 5)),
                    file
                )
            }
            assertTrue(history.list().isEmpty())
            assertContentEquals(existing, Files.readAllBytes(file))
            assertTrue(history.clear().isEmpty())
            assertTrue(history.list().isEmpty())
            assertContentEquals(existing, Files.readAllBytes(file))
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun keepsOnlyLastFiftyRecordsAndClearingCannotDeleteMachineFiles() {
        val root = Files.createTempDirectory("bm-sent-maximum-")
        try {
            val matrixFile = root.resolve("matriz.pes")
            val bytes = byteArrayOf(4, 8, 15, 16, 23, 42)
            Files.write(matrixFile, bytes)
            val store = DesktopSentMatrixHistory(root.resolve("meta/sent.tsv"))
            for (i in 1..53) {
                store.recordExport("nome$i.pes", "PES", "Wi-Fi / app", 10_000L+i)
            }
            assertEquals(50, store.list().size)
            assertEquals("nome53.pes", store.list().first().fileName)
            assertEquals("nome4.pes", store.list().last().fileName)
            store.clear()
            assertTrue(store.list().isEmpty())
            assertContentEquals(bytes, Files.readAllBytes(matrixFile))
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun malformedEntriesAndExternalPathsAreRejectedWithoutWriting() {
        val root = Files.createTempDirectory("bm-sent-malformed-")
        try {
            val store = DesktopSentMatrixHistory(root.resolve("sent.tsv"))
            for (name in listOf("", "../cliente.dst", "C:\\Dados\\cliente.dst", "a".repeat(221))) {
                assertFailsWith<IllegalArgumentException> {
                    store.recordExport(name, "DST", "USB / OTG")
                }
            }
            assertFailsWith<IllegalArgumentException> {
                store.recordExport("nome.dst", "DST", "inventar envio Wi-Fi")
            }
            assertFailsWith<IllegalArgumentException> {
                store.remove("outro_identificador")
            }
            assertTrue(store.list().isEmpty())
            assertTrue(!Files.exists(root.resolve("sent.tsv")))
        } finally {
            cleanup(root)
        }
    }
}
