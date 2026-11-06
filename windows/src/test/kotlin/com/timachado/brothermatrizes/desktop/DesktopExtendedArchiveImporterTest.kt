package com.timachado.brothermatrizes.desktop

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopExtendedArchiveImporterTest {
    @Test
    fun `7Z com matriz real abre seletivamente sem modificar pacote`() {
        val dir = Files.createTempDirectory("brother-7z-qa-")
        try {
            val generated = DesktopMatrixService.createName("QA", 12f)
            val dst = DesktopMatrixService.saveNew(
                generated, "DST", dir.resolve("matriz.dst")
            )
            val archive = dir.resolve("collection.7z")
            SevenZOutputFile(archive.toFile()).use { out ->
                val entry = out.createArchiveEntry(dst.toFile(), "colecao/QA.dst")
                out.putArchiveEntry(entry)
                out.write(Files.readAllBytes(dst))
                out.closeArchiveEntry()
            }
            val raw = Files.readAllBytes(archive)
            val entries = DesktopArchiveImporter.inspect(archive)
            assertEquals(1, entries.size)
            assertEquals("colecao/QA.dst", entries.single().relativePath)
            val reopened = DesktopArchiveImporter.openMatrix(archive, entries.single())
            assertTrue(reopened.current.endFound)
            assertTrue(reopened.current.stitchCount > 0)
            assertEquals("DST", reopened.current.format)
            assertTrue(raw.contentEquals(Files.readAllBytes(archive)))
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveImporter.openMatrix(
                    archive, entries.single().copy(relativePath = "../QA.dst")
                )
            }
        } finally {
            Files.list(dir).use { it.forEach { file -> Files.deleteIfExists(file) } }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `pacote falso e RAR5 truncado sao recusados sem escrever dados`() {
        val dir = Files.createTempDirectory("brother-rar-signature-")
        try {
            val wrong = dir.resolve("wrong.7z")
            Files.write(wrong, byteArrayOf(0x50, 0x4b, 0x03, 0x04))
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveImporter.inspect(wrong)
            }
            val truncated = dir.resolve("truncado.rar")
            Files.write(truncated, byteArrayOf(
                0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00
            ))
            assertFailsWith<Exception> {
                DesktopArchiveImporter.inspect(truncated)
            }
            assertTrue(!Files.exists(dir.resolve("QA.dst")))
        } finally {
            Files.list(dir).use { it.forEach { file -> Files.deleteIfExists(file) } }
            Files.deleteIfExists(dir)
        }
    }
}
