package com.timachado.brothermatrizes.core.archive

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivePackageIntegrationTest {
    @Test fun zipWithDirectoriesAndMixedFormatsListsOnlySupportedEntries() {
        val zip = File.createTempFile("brother-scan-", ".zip")
        try {
            ZipOutputStream(zip.outputStream()).use { archive ->
                for ((name, bytes) in listOf(
                    "flowers/" to byteArrayOf(),
                    "flowers/Rosa.PES" to byteArrayOf(1,2,3),
                    "flowers/outro.dst" to byteArrayOf(1,2),
                    "fonts/Adamiya.otf" to byteArrayOf(1,2,3,4),
                    "executar.exe" to byteArrayOf(5),
                    "../outside.jef" to byteArrayOf(0)
                )) {
                    archive.putNextEntry(ZipEntry(name))
                    archive.write(bytes)
                    archive.closeEntry()
                }
            }
            val result = SafeArchiveExtractor.inspect(zip)
            assertEquals(ArchiveFormat.ZIP, result.format)
            assertEquals(6, result.scannedCount)
            assertEquals(3, result.entries.size)
            assertEquals(1, result.entries.count { it.category == ArchiveCategory.FONT })
            assertEquals(2, result.entries.count { it.category == ArchiveCategory.MATRIX })
            assertEquals(2, result.incompatibleCount)
        } finally { zip.delete() }
    }

    @Test fun sevenZCanBeScannedWithMetadataOnly() {
        val archive = File.createTempFile("brother-scan-", ".7z")
        val payload = File.createTempFile("brother-original-", ".pes")
        try {
            payload.writeBytes(byteArrayOf(1,2,3,4))
            SevenZOutputFile(archive).use { output ->
                val entry = output.createArchiveEntry(payload, "Flor.pes")
                output.putArchiveEntry(entry)
                output.write(payload.readBytes())
                output.closeArchiveEntry()
            }
            val result = SafeArchiveExtractor.inspect(archive)
            assertEquals(ArchiveFormat.SEVEN_Z, result.format)
            assertEquals(1, result.entries.size)
            assertEquals("Flor.pes", result.entries.single().name)
        } finally { archive.delete(); payload.delete() }
    }

    @Test(expected = IOException::class)
    fun corruptedZipCannotBePresentedAsValidArchive() {
        val archive = File.createTempFile("corrupt-", ".zip")
        try {
            archive.writeBytes(byteArrayOf(0x50,0x4b,0x03,0x04,0x00))
            SafeArchiveExtractor.inspect(archive)
        } finally { archive.delete() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun limitsDoNotAllowPackagesOver128MiB() {
        val huge = File.createTempFile("overlimit-", ".zip")
        try {
            java.io.RandomAccessFile(huge, "rw").use {
                it.setLength(SafeArchiveExtractor.MAX_ARCHIVE + 1)
            }
            SafeArchiveExtractor.inspect(huge)
        } finally { huge.delete() }
    }
}
