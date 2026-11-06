package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopEmbroideryFiles
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopEmbroideryFilesTest {
    private fun name() = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Maria",
                heightMm = 12f,
                style = TextStitchStyle.RUNNING
            )
        )
    ).getOrThrow()

    @Test
    fun `importa PES DST JEF reais usando parsers da versao Android`() {
        val root = Files.createTempDirectory("brother-windows-import-")
        try {
            val design = name()
            for (format in listOf("PES", "DST", "JEF")) {
                val converted = MatrixConverter.convert(design, format).getOrThrow()
                val path = root.resolve(converted.fileName)
                Files.write(path, converted.bytes)
                val originalBytes = Files.readAllBytes(path)
                val imported = DesktopEmbroideryFiles.open(path)
                assertTrue(imported.points.count {
                    it.command == StitchCommand.STITCH
                } > 10, "Importação $format perdeu as pontadas reais.")
                assertTrue(imported.bounds.widthMm > 0f)
                assertTrue(imported.bounds.heightMm > 0f)
                assertContentEquals(originalBytes, Files.readAllBytes(path))
            }
        } finally {
            Files.list(root).use { stream ->
                stream.forEach { file -> Files.deleteIfExists(file) }
            }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `importacao FLP usa o mesmo projeto salvo e mantém ordem`() {
        val root = Files.createTempDirectory("brother-windows-load-flp-")
        val path = root.resolve("cliente.flp")
        try {
            val original = name()
            DesktopFlpProjectFiles.saveNew(original, path)
            assertEquals(original.points, DesktopEmbroideryFiles.open(path).points)
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `recusa formatos não habilitados e entradas inválidas`() {
        val root = Files.createTempDirectory("brother-windows-reject-import-")
        val exe = root.resolve("x.exe")
        val fake = root.resolve("falso.pes")
        val empty = root.resolve("vazio.dst")
        try {
            Files.write(exe, byteArrayOf(1, 2, 3))
            Files.write(fake, byteArrayOf(1, 2, 3))
            Files.createFile(empty)
            for (path in listOf(exe, fake, empty)) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopEmbroideryFiles.open(path)
                }
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopEmbroideryFiles.open(root.resolve("nao-existe.flp"))
            }
        } finally {
            Files.deleteIfExists(exe)
            Files.deleteIfExists(fake)
            Files.deleteIfExists(empty)
            Files.deleteIfExists(root)
        }
    }
}
