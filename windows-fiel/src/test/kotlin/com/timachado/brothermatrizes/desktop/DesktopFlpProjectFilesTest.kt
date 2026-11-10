package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.desktop.platform.DesktopFlpProjectFiles
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopFlpProjectFilesTest {
    private fun sample() = TextLayoutGenerator.generate(
        TextLayoutOptions(
            textOptions = TextMatrixOptions(
                text = "Maria",
                heightMm = 12f,
                style = TextStitchStyle.RUNNING
            )
        )
    ).getOrThrow()

    @Test
    fun `salva e reabre FLP v11 com mesma sequencia de pontadas da fonte Android`() {
        val root = Files.createTempDirectory("brother-windows-fiel-flp-")
        val file = root.resolve("nome-Maria.flp")
        try {
            val original = sample()
            DesktopFlpProjectFiles.saveNew(original, file)
            assertTrue(Files.isRegularFile(file))
            val reopened = DesktopFlpProjectFiles.open(file)
            assertEquals(original.points, reopened.points)
            assertEquals(original.hoopProfile, reopened.hoopProfile)
            assertEquals(original.sourceYAxisDown, reopened.sourceYAxisDown)
            assertEquals(original.bounds, reopened.bounds)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `nao sobrescreve projeto existente nem quando chamado novamente`() {
        val root = Files.createTempDirectory("brother-flp-no-overwrite-")
        val file = root.resolve("original.flp")
        try {
            val design = sample()
            DesktopFlpProjectFiles.saveNew(design, file)
            val firstBytes = Files.readAllBytes(file)
            assertFailsWith<java.nio.file.FileAlreadyExistsException> {
                DesktopFlpProjectFiles.saveNew(design, file)
            }
            assertContentEquals(firstBytes, Files.readAllBytes(file))
            assertEquals(design.points, DesktopFlpProjectFiles.open(file).points)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `recusa extensao errada vazio e conteudo corrompido sem modificar original`() {
        val root = Files.createTempDirectory("brother-flp-invalid-")
        val bad = root.resolve("arquivo.flp")
        val wrong = root.resolve("arquivo.exe")
        try {
            val design = sample()
            assertFailsWith<IllegalArgumentException> {
                DesktopFlpProjectFiles.saveNew(design, wrong)
            }
            assertFalse(Files.exists(wrong))
            assertFailsWith<IllegalArgumentException> {
                DesktopFlpProjectFiles.open(wrong)
            }
            Files.write(bad, byteArrayOf(0, 1, 2, 3))
            assertFailsWith<Exception> {
                DesktopFlpProjectFiles.open(bad)
            }
            assertContentEquals(byteArrayOf(0, 1, 2, 3), Files.readAllBytes(bad))
        } finally {
            Files.deleteIfExists(wrong)
            Files.deleteIfExists(bad)
            Files.deleteIfExists(root)
        }
    }
}
