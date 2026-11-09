package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopVerifiedMatrixExport
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Uses synthetic "Edvaldo"; never handles a customer's Defender-blocked file. */
class DesktopVerifiedMatrixExportTest {
    private fun design() = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Edvaldo", heightMm = 12f,
                style = TextStitchStyle.RUNNING
            )
        )
    ).getOrThrow()

    private fun inCleanFolder(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("bm-export-verified-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun generatedPESForBrotherAndPeDesignRoundTripsWithIdenticalBytes() =
        inCleanFolder { dir ->
            val source = design()
            val profiles = listOf(
                MatrixConverter.convert(source, "PES").getOrThrow(),
                MatrixConverter.convertForPeDesign(source).getOrThrow()
            )
            assertTrue(!profiles[0].bytes.contentEquals(profiles[1].bytes))
            for ((i, generated) in profiles.withIndex()) {
                val selected = dir.resolve("nome-Edvaldo-${i + 1}.pes")
                assertEquals(
                    selected,
                    DesktopVerifiedMatrixExport.saveNew(
                        generated.format, generated.bytes, selected
                    )
                )
                assertContentEquals(generated.bytes, Files.readAllBytes(selected))
                val reopened = DesktopEmbroideryFiles.open(selected)
                assertEquals("PES", reopened.format)
                assertTrue(reopened.stitchCount > 10)
                assertTrue(reopened.endFound)
                assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                    DesktopVerifiedMatrixExport.saveNew(
                        generated.format, generated.bytes, selected
                    )
                }
                assertContentEquals(generated.bytes, Files.readAllBytes(selected))
            }
        }

    @Test
    fun rejectsExecutableHeaderAndWrongFileExtensionWithoutWriting() =
        inCleanFolder { dir ->
            val invalid = dir.resolve("nome-Edvaldo.pes")
            assertFailsWith<IllegalArgumentException> {
                DesktopVerifiedMatrixExport.saveNew(
                    "PES", byteArrayOf(0x4d, 0x5a, 0x00, 0x00), invalid
                )
            }
            assertTrue(!Files.exists(invalid))
            val valid = MatrixConverter.convert(design(), "PES").getOrThrow()
            assertFailsWith<IllegalArgumentException> {
                DesktopVerifiedMatrixExport.saveNew(
                    "PES", valid.bytes, dir.resolve("nome-Edvaldo.exe")
                )
            }
            assertTrue(!Files.exists(dir.resolve("nome-Edvaldo.exe")))
        }

    @Test
    fun exportedDstAndJefStillUseOriginalAndroidFormats() = inCleanFolder { dir ->
        val source = design()
        for (format in listOf("DST", "JEF")) {
            val converted = MatrixConverter.convert(source, format).getOrThrow()
            val target = dir.resolve("Edvaldo.${format.lowercase()}")
            DesktopVerifiedMatrixExport.saveNew(converted.format, converted.bytes, target)
            assertContentEquals(converted.bytes, Files.readAllBytes(target))
            assertEquals(format, DesktopEmbroideryFiles.open(target).format)
        }
    }
}
