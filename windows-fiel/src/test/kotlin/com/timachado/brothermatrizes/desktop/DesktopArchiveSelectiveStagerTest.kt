package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveSelectiveStager
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic local archive fixtures; no authenticated import or user file. */
class DesktopArchiveSelectiveStagerTest {
    private fun zip(path: Path, names: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(path)).use { writer ->
            for ((name, content) in names) {
                writer.putNextEntry(ZipEntry(name))
                writer.write(content)
                writer.closeEntry()
            }
        }
    }

    private fun sevenZ(path: Path, names: List<Pair<String, ByteArray>>) {
        SevenZOutputFile(path.toFile()).use { writer ->
            writer.setContentCompression(SevenZMethod.COPY)
            for ((name, content) in names) {
                writer.putArchiveEntry(SevenZArchiveEntry().apply {
                    this.name = name
                })
                writer.write(content)
                writer.closeArchiveEntry()
            }
        }
    }

    private fun children(path: Path): List<Path> =
        Files.list(path).use { files -> files.toList() }

    @Test
    fun zipExtractsOnlyChosenMatrixToPrivateRandomFileAndDeletesIt() {
        val root = Files.createTempDirectory("bm-selective-zip-")
        val zip = root.resolve("catalogo.zip")
        val stageRoot = root.resolve("stage")
        Files.createDirectory(stageRoot)
        val matrix = "#PES0040 SYNTHETIC QA MATRIX".toByteArray()
        val font = "OTTO SYNTHETIC QA FONT".toByteArray()
        try {
            zip(zip, listOf(
                "Colecao/" to byteArrayOf(),
                "Colecao/flor.pes" to matrix,
                "Fontes/letra.otf" to font
            ))
            val untouched = Files.readAllBytes(zip)
            val preview = DesktopArchiveInventory.inspect(zip)
            assertEquals(DesktopArchiveInventory.Format.ZIP, preview.format)
            assertTrue(preview.archiveSha256?.length == 64)
            val item = preview.entries.single { it.extension == "PES" }
            val stage = DesktopArchiveSelectiveStager.extract(
                zip, preview, item, stagingParent = stageRoot
            )
            val file = stage.file
            val privateFolder = file.parent
            try {
                assertEquals(matrix.size.toLong(), stage.bytes)
                assertContentEquals(matrix, Files.readAllBytes(file))
                assertFalse(file.fileName.toString().contains("flor"))
                assertEquals(1, children(privateFolder).size)
                assertEquals(1, children(stageRoot).size)
                assertContentEquals(untouched, Files.readAllBytes(zip))
                assertFalse(Files.exists(root.resolve("Colecao")))
            } finally {
                stage.close()
            }
            assertFalse(Files.exists(file))
            assertFalse(Files.exists(privateFolder))
            assertTrue(children(stageRoot).isEmpty())
            assertContentEquals(untouched, Files.readAllBytes(zip))
        } finally {
            Files.deleteIfExists(zip)
            Files.deleteIfExists(stageRoot)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun sevenZExtractsSelectedFontWithoutCreatingAnyArchiveDirectories() {
        val root = Files.createTempDirectory("bm-selective-seven-")
        val archive = root.resolve("fonts.7z")
        val stageRoot = root.resolve("stage")
        Files.createDirectory(stageRoot)
        val font = byteArrayOf(0x4f, 0x54, 0x54, 0x4f, 4, 3, 2, 1)
        try {
            sevenZ(archive, listOf(
                "Colecao/ignore.txt" to byteArrayOf(9),
                "Fontes/real.otf" to font
            ))
            val original = Files.readAllBytes(archive)
            val preview = DesktopArchiveInventory.inspect(archive)
            val item = preview.entries.single()
            assertEquals(DesktopArchiveInventory.Format.SEVEN_Z, preview.format)
            DesktopArchiveSelectiveStager.extract(
                archive, preview, item, stagingParent = stageRoot
            ).use { staged ->
                assertContentEquals(font, Files.readAllBytes(staged.file))
                assertFalse(Files.exists(root.resolve("Fontes")))
            }
            assertTrue(children(stageRoot).isEmpty())
            assertContentEquals(original, Files.readAllBytes(archive))
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(stageRoot)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun archiveReplacementWithSameNameAndSizeRejectsBeforeStaging() {
        val root = Files.createTempDirectory("bm-selective-tamper-")
        val archive = root.resolve("igual.zip")
        val stageRoot = root.resolve("stage")
        Files.createDirectory(stageRoot)
        try {
            zip(archive, listOf("bordado.dst" to byteArrayOf(1, 2, 3, 4, 5)))
            val expected = DesktopArchiveInventory.inspect(archive)
            val item = expected.entries.single()
            val before = Files.readAllBytes(archive)
            val offset = before.indexOfFirst { it == 3.toByte() }
            assertTrue(offset >= 0)
            before[offset] = 8
            Files.write(archive, before)
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveSelectiveStager.extract(
                    archive, expected, item, stagingParent = stageRoot
                )
            }
            assertTrue(children(stageRoot).isEmpty())
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(stageRoot)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun zipCrcMismatchIsRejectedEvenWithFreshMatchingArchiveFingerprint() {
        val root = Files.createTempDirectory("bm-selective-crc-")
        val archive = root.resolve("crc.zip")
        val stageRoot = root.resolve("stage")
        Files.createDirectory(stageRoot)
        val payload = "CRC-STORED-MATRIX-TEST".toByteArray()
        try {
            ZipOutputStream(Files.newOutputStream(archive)).use { writer ->
                val crc = CRC32().apply { update(payload) }
                val entry = ZipEntry("test.pes").apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    compressedSize = payload.size.toLong()
                    this.crc = crc.value
                }
                writer.putNextEntry(entry)
                writer.write(payload)
                writer.closeEntry()
            }
            val bytes = Files.readAllBytes(archive)
            val match = bytes.indices.firstOrNull { i ->
                i + payload.size <= bytes.size &&
                    bytes.copyOfRange(i, i + payload.size).contentEquals(payload)
            } ?: error("The QA payload was not found.")
            bytes[match + 4] = (bytes[match + 4].toInt() xor 1).toByte()
            Files.write(archive, bytes)
            val preview = DesktopArchiveInventory.inspect(archive)
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveSelectiveStager.extract(
                    archive, preview, preview.entries.single(), stagingParent = stageRoot
                )
            }
            assertTrue(children(stageRoot).isEmpty())
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(stageRoot)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun refusesUncheckedSyntheticPreviewAndAnotherItemBeforeDiskWrites() {
        val root = Files.createTempDirectory("bm-selective-spoof-")
        val archive = root.resolve("real.zip")
        val stageRoot = root.resolve("stage")
        Files.createDirectory(stageRoot)
        try {
            zip(archive, listOf("valid.jef" to byteArrayOf(1, 2, 3)))
            val current = DesktopArchiveInventory.inspect(archive)
            val fabricated = current.copy(archiveSha256 = null)
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveSelectiveStager.extract(
                    archive, fabricated, current.entries.single(), stagingParent = stageRoot
                )
            }
            val malicious = current.entries.single().copy(
                index = 10,
                entryName = "../unsafe.jef",
                displayName = "unsafe.jef"
            )
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveSelectiveStager.extract(
                    archive, current, malicious, stagingParent = stageRoot
                )
            }
            assertTrue(children(stageRoot).isEmpty())
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(stageRoot)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun interruptionDuringStreamingRemovesPartiallyExtractedFile() {
        val root = Files.createTempDirectory("bm-selective-cancel-")
        val archive = root.resolve("large.zip")
        val stageRoot = root.resolve("stage")
        Files.createDirectory(stageRoot)
        try {
            // Reproducible random-like source avoids excessive deflate ratio.
            val source = ByteArray(256 * 1024) { (it * 29 + it / 251).toByte() }
            zip(archive, listOf("large.dst" to source))
            val preview = DesktopArchiveInventory.inspect(archive)
            var checkCount = 0
            assertFailsWith<IllegalStateException> {
                DesktopArchiveSelectiveStager.extract(
                    archive, preview, preview.entries.single(),
                    cancelled = { ++checkCount > 4 },
                    stagingParent = stageRoot
                )
            }
            assertTrue(checkCount > 4)
            assertTrue(children(stageRoot).isEmpty())
            assertFalse(Files.exists(root.resolve("large.dst")))
        } finally {
            Files.deleteIfExists(archive)
            Files.deleteIfExists(stageRoot)
            Files.deleteIfExists(root)
        }
    }
}
