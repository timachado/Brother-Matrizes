package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopMatrixServiceTest {
    private fun generated() = TextMatrixGenerator.generate(
        TextMatrixOptions(text = "BM", heightMm = 12f, outputFormat = "DST")
    ).getOrThrow()

    private fun fixture(format: String): ByteArray {
        val result = MatrixConverter.convert(generated(), format, "teste_desktop").getOrThrow()
        return result.bytes
    }

    @Test
    fun `abre DST PES e JEF com o parser oficial e preserva bytes originais`() {
        for (format in listOf("DST", "PES", "JEF")) {
            val bytes = fixture(format)
            val unchanged = bytes.copyOf()
            val doc = DesktopMatrixService.open("matriz.${format.lowercase()}", bytes)
            assertEquals(format, doc.current.format)
            assertTrue(doc.current.stitchCount > 0)
            assertTrue(doc.current.endFound)
            assertTrue(unchanged.contentEquals(bytes))
            assertEquals(doc.original, doc.current)
        }
    }

    @Test
    fun `edicao e restauracao preservam matriz carregada`() {
        val doc = DesktopMatrixService.open("matriz.dst", fixture("DST"))
        val before = doc.current.points.map { it.xUnits to it.yUnits }
        val edited = DesktopMatrixService.edit(
            doc, EditTransform(scale = 1.3f, rotationDegrees = 30f, offsetXUnits = 25)
        )
        assertTrue(edited.edited)
        assertEquals(before, doc.current.points.map { it.xUnits to it.yUnits })
        assertEquals(before, edited.original.points.map { it.xUnits to it.yUnits })
        val restored = DesktopMatrixService.reset(edited)
        assertEquals(before, restored.current.points.map { it.xUnits to it.yUnits })
    }

    @Test
    fun `salva copia nova mas recusa sobrescrever arquivo existente`() {
        val original = fixture("DST")
        val doc = DesktopMatrixService.open("matriz.dst", original)
        val folder = Files.createTempDirectory("bm-windows-qa-")
        val output = folder.resolve("copia.pes")
        try {
            DesktopMatrixService.saveNew(doc, "PES", output)
            assertTrue(Files.size(output) > 12)
            assertTrue(DesktopMatrixService.open("copia.pes", Files.readAllBytes(output)).current.stitchCount > 0)
            val saved = Files.readAllBytes(output)
            assertFailsWith<FileAlreadyExistsException> {
                DesktopMatrixService.saveNew(doc, "PES", output)
            }
            assertTrue(saved.contentEquals(Files.readAllBytes(output)))
            assertTrue(original.contentEquals(doc.original.sourceBytes))
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `recusa dados gigantes e extensao incompatível`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.open("invalido.png", byteArrayOf(1, 2, 3))
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.open("grande.dst", ByteArray(8 * 1024 * 1024 + 1))
        }
    }
}
