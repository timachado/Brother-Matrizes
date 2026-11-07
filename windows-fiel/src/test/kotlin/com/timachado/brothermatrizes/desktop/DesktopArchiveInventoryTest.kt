package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Read-only ZIP listing; no real client matrix is ever used or extracted. */
class DesktopArchiveInventoryTest {
    private fun makeZip(path: Path, contents: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            for ((name, bytes) in contents) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun listsOnlyCompatibleMatricesAndFontsWithoutAnyExtraction() {
        val folder = Files.createTempDirectory("bm-zip-inventory-")
        val archive = folder.resolve("muitas-matrizes.zip")
        try {
            makeZip(archive, listOf(
                "Colecao/flor.pes" to byteArrayOf(1, 2, 3, 4),
                "casa.DST" to byteArrayOf(5, 6, 7),
                "fontes/letra.ttf" to byteArrayOf(8, 9),
                "manual.pdf" to byteArrayOf(10, 11),
                "Colecao/" to byteArrayOf()
            ))
            val original = Files.readAllBytes(archive)
            val preview = DesktopArchiveInventory.inspect(archive)
            assertEquals(5, preview.scannedCount)
            assertEquals(3, preview.entries.size)
            assertEquals(2, preview.matrixCount)
            assertEquals(1, preview.fontCount)
            assertEquals(1, preview.incompatibleCount)
            assertEquals("flor.pes", preview.entries.first().displayName)
            assertEquals("Colecao/flor.pes", preview.entries.first().entryName)
            assertContentEquals(original, Files.readAllBytes(archive))
            // Never write extracted payloads (not even hidden staging files).
            Files.list(folder).use { children ->
                assertEquals(listOf(archive), children.toList())
            }
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun traversalReservedWindowsNamesAndUnsupportedFilesAreNotListed() {
        val folder = Files.createTempDirectory("bm-zip-safety-")
        val archive = folder.resolve("unsafe.zip")
        try {
            makeZip(archive, listOf(
                "../outside.pes" to byteArrayOf(1),
                "C:/absolute.dst" to byteArrayOf(1),
                "CON.pes" to byteArrayOf(1),
                "deep/../../bad.pes" to byteArrayOf(1),
                "OK/bom.jef" to byteArrayOf(2),
                "OK/bom.jef" to byteArrayOf(3)
            ).distinctBy { it.first })
            val preview = DesktopArchiveInventory.inspect(archive)
            assertEquals(5, preview.scannedCount)
            assertEquals(1, preview.matrixCount)
            assertEquals(4, preview.incompatibleCount)
            assertEquals("OK/bom.jef", preview.entries.single().entryName)
            assertFalse(Files.exists(folder.resolve("outside.pes")))
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun duplicateFileNamesDifferingByCaseAreHiddenAndNotExtracted() {
        val folder = Files.createTempDirectory("bm-zip-case-")
        val archive = folder.resolve("case.zip")
        try {
            makeZip(archive, listOf(
                "Flor.PES" to byteArrayOf(1),
                "flor.pes" to byteArrayOf(2)
            ))
            val preview = DesktopArchiveInventory.inspect(archive)
            assertEquals(2, preview.scannedCount)
            assertEquals(1, preview.matrixCount)
            assertEquals(1, preview.incompatibleCount)
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun requiresGenuineNonemptyZipAndRejectsMoreThan300Entries() {
        val folder = Files.createTempDirectory("bm-zip-limits-")
        val fake = folder.resolve("not-zip.zip")
        val many = folder.resolve("many.zip")
        try {
            Files.write(fake, byteArrayOf(1, 2, 3, 4, 5))
            assertFailsWith<Exception> { DesktopArchiveInventory.inspect(fake) }
            makeZip(many, (1..301).map {
                "matrix-$it.pes" to byteArrayOf(it.toByte())
            })
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveInventory.inspect(many)
            }
        } finally {
            Files.deleteIfExists(fake)
            Files.deleteIfExists(many)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun doesNotOpenSymlinkOrArchiveWithUnexpectedExtension() {
        val folder = Files.createTempDirectory("bm-zip-link-")
        val archive = folder.resolve("regular.zip")
        val invalid = folder.resolve("regular.rar")
        val symlink = folder.resolve("linked.zip")
        try {
            makeZip(archive, listOf("bom.dst" to byteArrayOf(1)))
            Files.copy(archive, invalid)
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveInventory.inspect(invalid)
            }
            if (runCatching { Files.createSymbolicLink(symlink, archive) }.isSuccess) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopArchiveInventory.inspect(symlink)
                }
            }
            assertTrue(Files.size(archive) > 0)
        } finally {
            Files.deleteIfExists(symlink)
            Files.deleteIfExists(invalid)
            Files.deleteIfExists(archive)
            Files.deleteIfExists(folder)
        }
    }
}
