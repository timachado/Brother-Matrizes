package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.MonogramStyle
import com.timachado.brothermatrizes.core.project.ProjectCodec
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * O MESMO ProjectCodec do Android, extraído do arquivo-fonte oficial.
 * Testes de integração do arquivo nativo .flp (versão 11).
 */
class DesktopProjectServiceTest {
    @Test
    fun `projeto editavel FLP preserva coordenadas, cores e metadados Android`() {
        val initial = DesktopMatrixService.createMonogram("BM", MonogramStyle.CLASSIC, 20f)
        val edited = DesktopMatrixService.edit(
            initial, EditTransform(scale = 1.2f, rotationDegrees = 14f, offsetXUnits = 30)
        )
        val bytes = DesktopProjectService.encode(edited)
        assertTrue(bytes.isNotEmpty())
        val decodedByAndroidCodec = ProjectCodec.decode(bytes)
        assertEquals(edited.current.points, decodedByAndroidCodec.points)
        assertEquals(edited.current.threadColors, decodedByAndroidCodec.threadColors)
        assertEquals(edited.current.label, decodedByAndroidCodec.label)
        assertEquals(edited.current.hoopProfile, decodedByAndroidCodec.hoopProfile)
        val decoded = DesktopProjectService.decode(bytes)
        assertEquals(edited.current.points, decoded.points)
    }

    @Test
    fun `pode salvar e reabrir FLP mas nunca substituir arquivo existente`() {
        val doc = DesktopMatrixService.createName("ANA", 12f)
        val folder = Files.createTempDirectory("bm-desktop-flp-")
        val file = folder.resolve("nome.flp")
        try {
            DesktopProjectService.saveNew(doc, file)
            assertTrue(Files.size(file) > 24)
            val previous = Files.readAllBytes(file)
            assertFailsWith<FileAlreadyExistsException> {
                DesktopProjectService.saveNew(doc, file)
            }
            assertTrue(previous.contentEquals(Files.readAllBytes(file)))
            val loaded = DesktopProjectService.open(file)
            assertEquals(doc.current.points, loaded.current.points)
            assertEquals(loaded.original.points, loaded.current.points)
            assertEquals(doc.current.fileName, loaded.current.fileName)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `projetos FLP aparecem na biblioteca sem percorrer subpastas`() {
        val doc = DesktopMatrixService.createName("BM", 14f)
        val folder = Files.createTempDirectory("bm-desktop-library-flp-")
        val file = folder.resolve("projeto.flp")
        try {
            DesktopProjectService.saveNew(doc, file)
            val entries = DesktopLocalLibrary.list(folder)
            assertEquals(1, entries.size)
            assertEquals("FLP", entries.single().format)
            assertEquals(doc.current.points, DesktopProjectService.open(entries.single().path).current.points)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `rejeita projeto truncado ou extensao invalida`() {
        val folder = Files.createTempDirectory("bm-desktop-invalid-flp-")
        val bad = folder.resolve("dados.flp")
        try {
            Files.write(bad, byteArrayOf(1, 2, 3, 4))
            assertFailsWith<Exception> { DesktopProjectService.open(bad) }
            assertFailsWith<IllegalArgumentException> {
                DesktopProjectService.decode(ByteArray(0))
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopProjectService.decode(ByteArray(32 * 1024 * 1024 + 1))
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopProjectService.open(folder.resolve("invalido.pes"))
            }
        } finally {
            Files.deleteIfExists(bad)
            Files.deleteIfExists(folder)
        }
    }
}
