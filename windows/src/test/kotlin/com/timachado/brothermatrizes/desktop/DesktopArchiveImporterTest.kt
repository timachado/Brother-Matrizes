package com.timachado.brothermatrizes.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopArchiveImporterTest {
    private fun archive(target: Path, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(Files.newOutputStream(target)).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    @Test
    fun `importa somente entrada selecionada usando motor Android`() {
        val dir = Files.createTempDirectory("brother-archive-qa-")
        try {
            val original = DesktopMatrixService.createName("QA", 12f)
            val dst = DesktopMatrixService.saveNew(
                original, "DST", dir.resolve("original.dst")
            )
            val bytes = Files.readAllBytes(dst)
            val zip = dir.resolve("matrizes.zip")
            archive(zip, listOf(
                "colecao/QA.dst" to bytes,
                "colecao/README.txt" to "nao importar".toByteArray(),
                "segunda.dst" to bytes
            ))
            val inventory = DesktopArchiveImporter.inspect(zip)
            assertEquals(2, inventory.size)
            assertEquals("colecao/QA.dst", inventory[0].relativePath)
            val reopened = DesktopArchiveImporter.openMatrix(zip, inventory[1])
            assertTrue(reopened.current.stitchCount > 0)
            assertTrue(reopened.current.endFound)
            assertEquals("DST", reopened.current.format)
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveImporter.openMatrix(
                    zip, inventory[0].copy(relativePath = "../malicioso.dst")
                )
            }
            assertTrue(Files.exists(dst))
            assertTrue(Files.exists(zip))
        } finally {
            Files.list(dir).use { items -> items.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `bloqueia nomes duplicados e caminhos perigosos sem extrair no disco`() {
        val dir = Files.createTempDirectory("brother-archive-invalid-")
        try {
            val dup = dir.resolve("duplicado.zip")
            archive(dup, listOf(
                "A/QA.dst" to byteArrayOf(1),
                "a/qa.dst" to byteArrayOf(2)
            ))
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveImporter.inspect(dup)
            }
            val malicious = dir.resolve("travessia.zip")
            archive(malicious, listOf(
                "../externo.dst" to byteArrayOf(1),
                "ok/invalido.txt" to byteArrayOf(2)
            ))
            assertTrue(DesktopArchiveImporter.inspect(malicious).isEmpty())
            assertTrue(!Files.exists(dir.parent.resolve("externo.dst")))
        } finally {
            Files.list(dir).use { items -> items.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(dir)
        }
    }
}
