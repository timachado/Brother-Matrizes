package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopFontZipPreview
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
import kotlin.test.assertTrue

/** Fully offline synthetic archives; no import, network, Google or quota. */
class DesktopFontZipPreviewTest {
    private fun createZip(
        dir: Path,
        entries: List<Pair<String, ByteArray>>
    ): Path {
        val zip = dir.resolve("fontes-qa.zip")
        ZipOutputStream(Files.newOutputStream(zip)).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return zip
    }

    private fun deleteTree(root: Path) {
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun previewsOnlyTtfOtfAndDoesNotExtractOrChangeArchive() {
        val root = Files.createTempDirectory("bm-font-zip-qa-")
        try {
            val zip = createZip(root, listOf(
                "DaFont/Original.ttf" to byteArrayOf(0, 1, 0, 0, 11, 22),
                "NetFontes/Letra.otf" to byteArrayOf(79, 84, 84, 79, 9, 8),
                "Matrizes/flor.pes" to byteArrayOf(1, 2, 3),
                "licenca.txt" to "Author copyright".toByteArray()
            ))
            val prior = Files.readAllBytes(zip)
            val result = DesktopFontZipPreview.inspect(zip)
            assertEquals("fontes-qa.zip", result.zipName)
            assertEquals(2, result.fontCount)
            assertEquals(listOf("Original.ttf", "Letra.otf"), result.fonts.map { it.name })
            assertEquals(listOf("TTF", "OTF"), result.fonts.map { it.format })
            assertEquals(2, result.skippedItems)
            assertTrue(result.hasSha256)
            assertContentEquals(prior, Files.readAllBytes(zip))
            assertEquals(setOf("fontes-qa.zip"), Files.list(root).use { files ->
                files.map { it.fileName.toString() }.toList().toSet()
            })
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun noFontsInZipDoesNotInventImportableEntriesOrCharge() {
        val root = Files.createTempDirectory("bm-empty-fonts-qa-")
        try {
            val zip = createZip(root, listOf(
                "Matrizes/maria.dst" to byteArrayOf(1, 2, 3),
                "readme.txt" to byteArrayOf(0, 0, 0)
            ))
            val result = DesktopFontZipPreview.inspect(zip)
            assertEquals(0, result.fontCount)
            assertTrue(result.fonts.isEmpty())
            assertEquals(2, result.skippedItems)
            assertTrue(Files.exists(zip))
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun unsafeZipEntriesRemainExcludedBySharedAndroidSafetyRules() {
        val root = Files.createTempDirectory("bm-unsafe-fonts-qa-")
        try {
            val zip = createZip(root, listOf(
                "../fora.ttf" to byteArrayOf(0, 1, 0, 0),
                "ok/font.otf" to byteArrayOf(79, 84, 84, 79)
            ))
            val result = DesktopFontZipPreview.inspect(zip)
            assertEquals(listOf("font.otf"), result.fonts.map { it.name })
            assertEquals(1, result.skippedItems)
            assertFalse(Files.exists(root.resolve("fora.ttf")))
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun renamedFakeZipFailsBeforeListingFiles() {
        val root = Files.createTempDirectory("bm-fake-font-zip-")
        try {
            val fake = root.resolve("falso.zip")
            Files.writeString(fake, "<html>not a font archive</html>")
            assertFailsWith<IllegalStateException> {
                DesktopFontZipPreview.inspect(fake)
            }
            assertEquals(1L, Files.list(root).use { it.count() })
        } finally {
            deleteTree(root)
        }
    }
}
