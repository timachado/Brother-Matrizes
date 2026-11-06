package com.timachado.brothermatrizes.desktop

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopArchiveFontImporterTest {
    private fun systemFont(): Path? {
        val windows = System.getenv("WINDIR") ?: return null
        return listOf("arial.ttf", "segoeui.ttf", "calibri.ttf")
            .map { Path.of(windows, "Fonts", it) }
            .firstOrNull { Files.isRegularFile(it) &&
                Files.size(it) <= DesktopFontPreviewService.MAX_FONT_BYTES }
    }

    @Test
    fun `ZIP fonte real permite selecionar salvar novo e gerar preview`() {
        val font = systemFont() ?: return
        val dir = Files.createTempDirectory("brother-archive-font-")
        try {
            val zip = dir.resolve("fontes.zip")
            val source = Files.readAllBytes(font)
            ZipOutputStream(Files.newOutputStream(zip)).use { out ->
                out.putNextEntry(ZipEntry("fonts/exemplo.ttf"))
                out.write(source)
                out.closeEntry()
                out.putNextEntry(ZipEntry("../nao-importar.ttf"))
                out.write(byteArrayOf(1))
                out.closeEntry()
            }
            val before = Files.readAllBytes(zip)
            assertEquals(0, DesktopArchiveImporter.inspect(zip).size)
            val fonts = DesktopArchiveImporter.inspectFonts(zip)
            assertEquals(1, fonts.size)
            assertEquals("exemplo.ttf", fonts.single().fileName)
            val bytes = DesktopArchiveImporter.readFontBytes(zip, fonts.single())
            assertTrue(bytes.contentEquals(source))
            val dest = dir.resolve("importada.ttf")
            val preview = DesktopFontPreviewService.saveArchiveFontNew(
                "exemplo.ttf", bytes, dest
            )
            assertEquals(dest, preview.sourcePath)
            assertTrue(preview.family.isNotBlank())
            assertTrue(source.contentEquals(Files.readAllBytes(dest)))
            assertTrue(before.contentEquals(Files.readAllBytes(zip)))
            assertFailsWith<IllegalArgumentException> {
                DesktopFontPreviewService.saveArchiveFontNew(
                    "exemplo.ttf", bytes, dest
                )
            }
            assertTrue(source.contentEquals(Files.readAllBytes(dest)))
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveImporter.readFontBytes(
                    zip, fonts.single().copy(relativePath = "../exemplo.ttf")
                )
            }
        } finally {
            Files.list(dir).use { it.forEach { p -> Files.deleteIfExists(p) } }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `7Z com fonte real permite leitura sem alterar pacote`() {
        val font = systemFont() ?: return
        val dir = Files.createTempDirectory("brother-font-7z-")
        try {
            val archive = dir.resolve("fontes.7z")
            SevenZOutputFile(archive.toFile()).use { out ->
                val entry = out.createArchiveEntry(font.toFile(), "selecao/minha.ttf")
                out.putArchiveEntry(entry)
                out.write(Files.readAllBytes(font))
                out.closeArchiveEntry()
            }
            val original = Files.readAllBytes(archive)
            assertTrue(DesktopArchiveImporter.inspect(archive).isEmpty())
            val candidates = DesktopArchiveImporter.inspectFonts(archive)
            assertEquals(1, candidates.size)
            val bytes = DesktopArchiveImporter.readFontBytes(archive, candidates.single())
            assertTrue(bytes.contentEquals(Files.readAllBytes(font)))
            assertTrue(original.contentEquals(Files.readAllBytes(archive)))
        } finally {
            Files.list(dir).use { it.forEach { p -> Files.deleteIfExists(p) } }
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `fonte inventariada mas com assinatura falsa e bloqueada antes de salvar`() {
        val dir = Files.createTempDirectory("brother-font-false-")
        try {
            val zip = dir.resolve("fontes.zip")
            ZipOutputStream(Files.newOutputStream(zip)).use { out ->
                out.putNextEntry(ZipEntry("falsificada.ttf"))
                out.write(ByteArray(32) { 0x11 })
                out.closeEntry()
            }
            val candidates = DesktopArchiveImporter.inspectFonts(zip)
            assertEquals(1, candidates.size)
            assertFailsWith<IllegalArgumentException> {
                DesktopArchiveImporter.readFontBytes(zip, candidates.single())
            }
            val target = dir.resolve("impossivel.ttf")
            assertTrue(!Files.exists(target))
            assertFailsWith<IllegalArgumentException> {
                DesktopFontPreviewService.saveArchiveFontNew(
                    "falsificada.ttf", ByteArray(32), target
                )
            }
            assertTrue(!Files.exists(target))
        } finally {
            Files.list(dir).use { it.forEach { p -> Files.deleteIfExists(p) } }
            Files.deleteIfExists(dir)
        }
    }
}
