package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.archive.ArchiveCategory
import com.timachado.brothermatrizes.core.archive.ArchiveFormat
import com.timachado.brothermatrizes.core.archive.SafeArchiveExtractor
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopArchiveInventory
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

/**
 * Source parity, not a new archive engine: the original Android inspector
 * and selective extractor run on Windows with ONLY a filesystem adapter.
 * Synthetic files never import matrices, modify accounts, or consume quotas.
 */
class DesktopOriginalAndroidArchiveEngineParityTest {
    private fun withWorkspace(action: (Path) -> Unit) {
        val folder = Files.createTempDirectory("brother-original-archive-qa-")
        try { action(folder) }
        finally {
            Files.walk(folder).use { entries ->
                entries.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun zip(source: Path, items: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(source)).use { out ->
            items.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    @Test
    fun androidEngineInspectsAndExtractsSelectedFilesWithoutTouchingOriginal() =
        withWorkspace { folder ->
            val archive = folder.resolve("matrizes.zip")
            val matrix = byteArrayOf(1, 2, 3, 4, 5)
            val font = byteArrayOf(0, 1, 0, 0, 8, 7, 6)
            zip(archive, listOf(
                "pastas/Flor.dst" to matrix,
                "fontes/Letra.ttf" to font,
                "../fora.pes" to byteArrayOf(9, 9),
                "notas.txt" to byteArrayOf(10)
            ))
            val originalBytes = Files.readAllBytes(archive)
            assertTrue(SafeArchiveExtractor.isArchive(archive))
            val stage = SafeArchiveExtractor.stage(archive)
            val cacheDir = Files.createDirectory(folder.resolve("private"))
            try {
                assertEquals(ArchiveFormat.ZIP, stage.format)
                assertEquals(4, stage.scannedCount)
                assertEquals(2, stage.entries.size)
                assertEquals(2, stage.incompatibleCount)
                val desktop = DesktopArchiveInventory.inspect(archive)
                assertEquals(stage.entries.size, desktop.entries.size)
                assertEquals(
                    stage.entries.map { it.path }.toSet(),
                    desktop.entries.map { it.entryName }.toSet()
                )
                val matrixEntry = stage.entries.single {
                    it.category == ArchiveCategory.MATRIX
                }
                val fontEntry = stage.entries.single {
                    it.category == ArchiveCategory.FONT
                }
                val extractedMatrix = SafeArchiveExtractor.extract(
                    cacheDir.toFile(), stage, matrixEntry
                )
                val extractedFont = SafeArchiveExtractor.extract(
                    cacheDir.toFile(), stage, fontEntry
                )
                try {
                    assertEquals(cacheDir.toFile(), extractedMatrix.parentFile)
                    assertContentEquals(matrix, extractedMatrix.readBytes())
                    assertContentEquals(font, extractedFont.readBytes())
                    assertFalse(Files.exists(folder.resolve("fora.pes")))
                    assertContentEquals(originalBytes, Files.readAllBytes(archive))
                } finally {
                    extractedMatrix.delete()
                    extractedFont.delete()
                }
            } finally {
                Files.deleteIfExists(stage.sourceFile.toPath())
            }
        }

    @Test
    fun originalAndroidEngineRejectsForeignEntryAndCancellation() =
        withWorkspace { folder ->
            val archive = folder.resolve("origem.zip")
            zip(archive, listOf("Ana.dst" to byteArrayOf(1, 2, 3)))
            val stage = SafeArchiveExtractor.stage(archive)
            val cache = Files.createDirectory(folder.resolve("private"))
            try {
                val item = stage.entries.single()
                assertFailsWith<IllegalArgumentException> {
                    SafeArchiveExtractor.extract(
                        cache.toFile(), stage, item.copy(index = 10)
                    )
                }
                assertFailsWith<IllegalStateException> {
                    SafeArchiveExtractor.extract(
                        cache.toFile(), stage, item, cancelled = { true }
                    )
                }
                Files.list(cache).use { assertEquals(0L, it.count()) }
            } finally {
                Files.deleteIfExists(stage.sourceFile.toPath())
            }
        }

    @Test
    fun androidArchivePreviewOpensRealSyntheticDstInWindowsViewerParser() =
        withWorkspace { root ->
            val originalDesign = TextLayoutGenerator.generate(
                TextLayoutOptions(
                    textOptions = TextMatrixOptions(
                        text = "Maria", heightMm = 12f, style = TextStitchStyle.RUNNING
                    )
                )
            ).getOrThrow()
            val dst = MatrixConverter.convert(originalDesign, "DST").getOrThrow().bytes
            val archive = root.resolve("Matrizes.zip")
            zip(archive, listOf("Folder/Maria.dst" to dst))
            val originalZip = Files.readAllBytes(archive)
            val stagedArchive = SafeArchiveExtractor.stage(archive)
            val temporary = Files.createDirectory(root.resolve("preview"))
            try {
                val selected = stagedArchive.entries.single {
                    it.category == ArchiveCategory.MATRIX &&
                        it.extension == "DST"
                }
                val temporaryDst = SafeArchiveExtractor.extract(
                    temporary.toFile(), stagedArchive, selected
                )
                try {
                    val parsed = DesktopEmbroideryFiles.open(temporaryDst.toPath())
                    assertTrue(parsed.points.isNotEmpty())
                    assertTrue(parsed.stitchCount > 0)
                    assertContentEquals(dst, temporaryDst.readBytes())
                    assertContentEquals(originalZip, Files.readAllBytes(archive))
                } finally {
                    temporaryDst.delete()
                }
            } finally {
                Files.deleteIfExists(stagedArchive.sourceFile.toPath())
            }
        }
}
