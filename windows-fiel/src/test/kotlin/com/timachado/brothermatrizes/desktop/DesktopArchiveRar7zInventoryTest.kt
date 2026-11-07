package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * All archives are generated in a temporary QA folder. No extraction,
 * account, real client matrices, passwords or commercial entitlements.
 */
class DesktopArchiveRar7zInventoryTest {
    @Test
    fun originalAndroidSevenZLibraryListsMatricesAndFontsWithoutExtraction() {
        val dir = Files.createTempDirectory("bm-7z-preview-")
        val archive = dir.resolve("testes.7z")
        try {
            SevenZOutputFile(archive.toFile()).use { seven ->
                seven.setContentCompression(SevenZMethod.COPY)
                for ((name, bytes) in listOf(
                    "Colecao/flor.pes" to byteArrayOf(1, 2, 3),
                    "letras/arial.otf" to byteArrayOf(5, 6),
                    "manual.txt" to byteArrayOf(7)
                )) {
                    seven.putArchiveEntry(SevenZArchiveEntry().apply {
                        this.name = name
                    })
                    seven.write(bytes)
                    seven.closeArchiveEntry()
                }
            }
            val before = Files.readAllBytes(archive)
            val preview = DesktopArchiveInventory.inspect(archive)
            assertEquals(DesktopArchiveInventory.Format.SEVEN_Z, preview.format)
            assertEquals(3, preview.scannedCount)
            assertEquals(1, preview.matrixCount)
            assertEquals(1, preview.fontCount)
            assertEquals(1, preview.incompatibleCount)
            assertEquals(6L, preview.uncompressedBytes)
            assertContentEquals(before, Files.readAllBytes(archive))
            Files.list(dir).use { files ->
                assertEquals(listOf(archive), files.toList())
            }
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun sevenZRejectsTraversalMetadataWithoutWritingAnyFiles() {
        val dir = Files.createTempDirectory("bm-7z-traversal-")
        val archive = dir.resolve("unsafe.7z")
        try {
            SevenZOutputFile(archive.toFile()).use { seven ->
                seven.setContentCompression(SevenZMethod.COPY)
                for (name in listOf("../outside.pes", "CON.pes", "good.dst")) {
                    seven.putArchiveEntry(SevenZArchiveEntry().apply {
                        this.name = name
                    })
                    seven.write(byteArrayOf(1, 2, 3, 4))
                    seven.closeArchiveEntry()
                }
            }
            val preview = DesktopArchiveInventory.inspect(archive)
            assertEquals(3, preview.scannedCount)
            assertEquals(1, preview.matrixCount)
            assertEquals(2, preview.incompatibleCount)
            assertFalse(Files.exists(dir.resolve("outside.pes")))
            Files.list(dir).use { files ->
                assertEquals(listOf(archive), files.toList())
            }
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun refusesArchiveWithMismatchedExtensionOrCorruptedRar7zHeader() {
        val dir = Files.createTempDirectory("bm-rar-7z-signature-")
        val zipDisguisedAsRar = dir.resolve("falso.rar")
        val zipDisguisedAsSevenZ = dir.resolve("falso.7z")
        val bogusRar = dir.resolve("corrupto.rar")
        val bogusSevenZ = dir.resolve("corrupto.7z")
        try {
            ZipOutputStream(Files.newOutputStream(zipDisguisedAsRar)).use { zip ->
                zip.putNextEntry(ZipEntry("bom.pes"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
            Files.copy(zipDisguisedAsRar, zipDisguisedAsSevenZ)
            Files.write(bogusRar, byteArrayOf(
                0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00, 0x00, 0x00
            ))
            Files.write(bogusSevenZ, byteArrayOf(
                0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C,
                0x00, 0x00, 0x00, 0x00
            ))
            for (file in listOf(
                zipDisguisedAsRar, zipDisguisedAsSevenZ,
                bogusRar, bogusSevenZ
            )) {
                assertFailsWith<Exception> {
                    DesktopArchiveInventory.inspect(file)
                }
            }
            assertFalse(Files.exists(dir.resolve("bom.pes")))
        } finally {
            for (file in listOf(
                zipDisguisedAsRar, zipDisguisedAsSevenZ,
                bogusRar, bogusSevenZ
            )) Files.deleteIfExists(file)
            Files.deleteIfExists(dir)
        }
    }
}
