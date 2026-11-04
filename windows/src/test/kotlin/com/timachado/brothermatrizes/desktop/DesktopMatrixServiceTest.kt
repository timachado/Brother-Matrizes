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
    fun `exportacao em disco DST PES JEF e PES PC preserva origem e bloqueia sobrescrita`() {
        val folder = Files.createTempDirectory("bm-windows-formats-qa-")
        val source = folder.resolve("origem.dst")
        val originalBytes = fixture("DST")
        try {
            Files.write(source, originalBytes)
            val document = DesktopMatrixService.openFromFile(source)
            val originalPoints = document.current.points.toList()
            val scenarios = listOf(
                Triple("DST", false, "saida_dst.dst"),
                Triple("PES", false, "saida_pes.pes"),
                Triple("JEF", false, "saida_jef.jef"),
                Triple("PES", true, "saida_pc_experimental.pes")
            )
            for ((format, pcProfile, name) in scenarios) {
                val destination = folder.resolve(name)
                val saved = DesktopMatrixService.saveNew(
                    document, format, destination, pesPcProfile = pcProfile
                )
                assertEquals(destination, saved)
                val exported = Files.readAllBytes(destination)
                assertTrue(exported.isNotEmpty(), "Exportação vazia: $name")
                val reopened = DesktopMatrixService.openFromFile(destination)
                assertTrue(reopened.current.endFound, "END ausente: $name")
                assertTrue(reopened.current.stitchCount > 0, "Sem costura: $name")
                assertTrue(reopened.current.bounds.widthMm > 0f, "Largura inválida: $name")
                assertTrue(reopened.current.bounds.heightMm > 0f, "Altura inválida: $name")
                assertFailsWith<FileAlreadyExistsException> {
                    DesktopMatrixService.saveNew(
                        document, format, destination, pesPcProfile = pcProfile
                    )
                }
                assertTrue(exported.contentEquals(Files.readAllBytes(destination)),
                    "Arquivo de saída sobrescrito: $name")
                assertTrue(originalBytes.contentEquals(Files.readAllBytes(source)),
                    "Arquivo de origem modificado: $name")
                assertEquals(originalPoints, document.current.points,
                    "Matriz em memória modificada: $name")
            }
        } finally {
            for (name in listOf(
                "saida_dst.dst", "saida_pes.pes", "saida_jef.jef",
                "saida_pc_experimental.pes", "origem.dst"
            )) {
                Files.deleteIfExists(folder.resolve(name))
            }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `importacao em disco bloqueia arquivos acima de 8 MiB`() {
        val folder = Files.createTempDirectory("bm-windows-input-limit-")
        val large = folder.resolve("grande.dst")
        try {
            java.io.RandomAccessFile(large.toFile(), "rw").use {
                it.setLength(DesktopMatrixService.MAX_INPUT_BYTES + 1)
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopMatrixService.openFromFile(large)
            }
            assertEquals(DesktopMatrixService.MAX_INPUT_BYTES + 1, Files.size(large))
        } finally {
            Files.deleteIfExists(large)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `importacao nao segue atalho simbolico para matriz externa`() {
        val folder = Files.createTempDirectory("bm-windows-input-link-")
        val source = folder.resolve("source.dst")
        val alias = folder.resolve("alias.dst")
        try {
            Files.write(source, fixture("DST"))
            val creation = runCatching { Files.createSymbolicLink(alias, source) }
            org.junit.jupiter.api.Assumptions.assumeTrue(
                creation.isSuccess, "SO não autoriza criar link simbólico no teste"
            )
            assertFailsWith<IllegalArgumentException> {
                DesktopMatrixService.openFromFile(alias)
            }
            assertTrue(Files.size(source) > 512L)
        } finally {
            Files.deleteIfExists(alias)
            Files.deleteIfExists(source)
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
