package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpBackupExporter
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpBackupRestorer
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopProjectCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** No user files, network, Google or commercial quotas. */
class DesktopFlpBackupRestorerTest {
    private fun design() = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Ana", heightMm = 12f, style = TextStitchStyle.RUNNING
            )
        )
    ).getOrThrow()

    private fun withRoot(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("bm-flp-restore-safety-")
        try {
            block(root)
        } finally {
            Files.walk(root).use { items ->
                items.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun zip(path: Path, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(path)).use { archive ->
            for ((name, bytes) in entries) {
                archive.putNextEntry(ZipEntry(name))
                archive.write(bytes)
                archive.closeEntry()
            }
        }
    }

    @Test
    fun roundTripsRealAndroidFlpZipToUniqueFolderWithoutReplacingOriginals() = withRoot { root ->
        val original = Files.createDirectory(root.resolve("originais"))
        val restoredParent = Files.createDirectory(root.resolve("destino"))
        val samples = listOf("Ana.flp", "Maria.flp")
        val before = samples.associateWith { name ->
            val file = original.resolve(name)
            DesktopFlpProjectFiles.saveNew(design(), file)
            Files.readAllBytes(file)
        }
        // Even a same-named file in the parent must survive unchanged.
        val existing = restoredParent.resolve("Ana.flp")
        val unrelatedBytes = byteArrayOf(100, 42, 7, 1)
        Files.write(existing, unrelatedBytes)
        val archive = root.resolve("backup-local.zip")
        val exported = DesktopFlpBackupExporter.exportLocalFlp(original, archive)
        assertEquals(2, exported.exportedProjects)
        val originalZipBytes = Files.readAllBytes(archive)

        val first = DesktopFlpBackupRestorer.restoreLocalFlpZip(archive, restoredParent)
        assertEquals(2, first.importedProjects)
        assertEquals(restoredParent, first.folder.parent)
        val catalog = DesktopProjectCatalog.scan(first.folder)
        assertEquals(2, catalog.projects.size)
        for ((name, bytes) in before) {
            assertContentEquals(bytes, Files.readAllBytes(original.resolve(name)))
            assertContentEquals(bytes, Files.readAllBytes(first.folder.resolve(name)))
        }
        assertContentEquals(unrelatedBytes, Files.readAllBytes(existing))
        assertContentEquals(originalZipBytes, Files.readAllBytes(archive))

        val second = DesktopFlpBackupRestorer.restoreLocalFlpZip(archive, restoredParent)
        assertNotEquals(first.folder, second.folder)
        assertEquals(2, second.importedProjects)
        assertContentEquals(
            before.getValue("Ana.flp"), Files.readAllBytes(second.folder.resolve("Ana.flp"))
        )
    }

    @Test
    fun rejectsTraversalReservedNamesNestedPathsAndForeignFormatsWithoutPublishing() =
        withRoot { root ->
            val parent = Files.createDirectory(root.resolve("saida"))
            val original = root.resolve("good.flp")
            DesktopFlpProjectFiles.saveNew(design(), original)
            val validBytes = Files.readAllBytes(original)
            val hostileEntries = listOf(
                "../escaped.flp", "/absolute.flp", "folder/nested.flp",
                "folder\\nested.flp", "C:\\Windows\\file.flp",
                "CON.flp", "COM1.flp", "NUL.flp",
                "bad.exe", ".hidden.flp", "trailing..flp"
            )
            for ((i, name) in hostileEntries.withIndex()) {
                val archive = root.resolve("hostile-$i.zip")
                zip(archive, listOf(name to validBytes))
                assertFailsWith<Exception>(name) {
                    DesktopFlpBackupRestorer.restoreLocalFlpZip(archive, parent)
                }
                Files.list(parent).use { assertEquals(0L, it.count()) }
                assertFalse(Files.exists(root.resolve("escaped.flp")))
            }
        }

    @Test
    fun rejectsCaseDuplicateMalformedAndEmptyArchivesAndRollsBackStage() =
        withRoot { root ->
            val parent = Files.createDirectory(root.resolve("destino"))
            val project = root.resolve("source.flp")
            DesktopFlpProjectFiles.saveNew(design(), project)
            val good = Files.readAllBytes(project)
            val attempts = listOf(
                listOf("Ana.flp" to good, "ana.FLP" to good),
                listOf("ok.flp" to good, "broken.flp" to byteArrayOf(1, 2, 3)),
                emptyList()
            )
            for ((i, entries) in attempts.withIndex()) {
                val archive = root.resolve("invalid-$i.zip")
                zip(archive, entries)
                assertFailsWith<Exception> {
                    DesktopFlpBackupRestorer.restoreLocalFlpZip(archive, parent)
                }
                Files.list(parent).use { assertEquals(0L, it.count()) }
            }
            val notAFlpZip = root.resolve("backup.brother_matrizes-fullbackup")
            Files.write(notAFlpZip, byteArrayOf(1, 2, 3))
            assertFailsWith<IllegalArgumentException> {
                DesktopFlpBackupRestorer.restoreLocalFlpZip(notAFlpZip, parent)
            }
            Files.list(parent).use { assertEquals(0L, it.count()) }
        }
}
