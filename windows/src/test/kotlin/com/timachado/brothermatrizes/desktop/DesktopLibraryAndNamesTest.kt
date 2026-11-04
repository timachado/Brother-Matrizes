package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopLibraryAndNamesTest {
    @Test
    fun `biblioteca local lista somente matrizes do diretorio selecionado`() {
        val folder = Files.createTempDirectory("bm-library-")
        val nested = Files.createDirectory(folder.resolve("pasta-interna"))
        try {
            Files.write(folder.resolve("Zeta.PES"), byteArrayOf(1, 2))
            Files.write(folder.resolve("Alfa.dst"), byteArrayOf(3, 4))
            Files.write(folder.resolve("descartar.txt"), byteArrayOf(5))
            Files.write(folder.resolve("vazio.jef"), byteArrayOf())
            Files.write(nested.resolve("nao-listar.dst"), byteArrayOf(1))
            val entries = DesktopLocalLibrary.list(folder)
            assertEquals(listOf("Alfa.dst", "Zeta.PES"), entries.map { it.name })
            assertEquals(listOf("DST", "PES"), entries.map { it.format })
            assertTrue(entries.all { it.path.parent == folder })
        } finally {
            Files.deleteIfExists(nested.resolve("nao-listar.dst"))
            Files.deleteIfExists(nested)
            for (name in listOf("Zeta.PES", "Alfa.dst", "descartar.txt", "vazio.jef")) {
                Files.deleteIfExists(folder.resolve(name))
            }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `nome criado pelo gerador Android vira matriz exportavel sem arquivo original`() {
        for (style in listOf(TextStitchStyle.RUNNING, TextStitchStyle.SATIN)) {
            val doc = DesktopMatrixService.createName("ANA", 12f, style)
            assertEquals("ANA", doc.current.label)
            assertTrue(doc.current.stitchCount > 0)
            assertTrue(doc.current.endFound)
            assertTrue(doc.original.sourceBytes.isEmpty())
            val result = DesktopMatrixService.convert(doc, "DST")
            assertTrue(result.bytes.size > 512)
            val reopened = DesktopMatrixService.open(result.fileName, result.bytes)
            assertTrue(reopened.current.stitchCount > 0)
        }
    }

    @Test
    fun `nome recusa valores invalidos antes de gerar`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createName(" ", 12f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createName("A".repeat(25), 12f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createName("ANA", Float.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createName("ANA", 70f)
        }
    }
}
