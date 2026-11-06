package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Testes reais TTF/OTF, sem usar arquivos de clientes ou contas. */
class DesktopImportedFontLibraryTest {
    private fun fontFixture(): Path {
        // GitHub Actions Windows fornece as fontes do próprio sistema.
        val windowsFonts = Path.of(
            System.getenv("WINDIR") ?: "C:\\Windows", "Fonts"
        )
        val candidates = listOf(
            windowsFonts.resolve("arial.ttf"),
            windowsFonts.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        )
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: error("Nenhuma fonte TTF pública do sistema disponível no runner.")
    }

    @Test
    fun `importa TTF verdadeira com identidade SHA e sem mudar original`() {
        val root = Files.createTempDirectory("brother-font-store-qa-")
        val folder = root.resolve("fonts")
        val fontSource = fontFixture()
        val original = Files.readAllBytes(fontSource)
        try {
            val library = DesktopImportedFontLibrary(folder)
            val imported = library.importFile(fontSource, "Fonte de teste.ttf")
            assertEquals("ttf", imported.extension)
            assertTrue(imported.fileName.matches(
                Regex("Fonte_de_teste-[a-f0-9]{10}\\.ttf")
            ))
            assertEquals("Fonte de teste", imported.displayName)
            assertContentEquals(original, Files.readAllBytes(Path.of(imported.absolutePath)))
            assertContentEquals(original, Files.readAllBytes(fontSource))
            assertEquals(listOf(imported), library.list())
            assertEquals(imported, library.importFile(fontSource, "Fonte de teste.ttf"))
            assertEquals(1, library.list().size)

            library.delete(imported.id)
            assertTrue(library.list().isEmpty())
            assertTrue(Files.exists(fontSource))
        } finally {
            if (Files.isDirectory(folder)) {
                Files.list(folder).use { items ->
                    items.forEach { item -> Files.deleteIfExists(item) }
                }
                Files.deleteIfExists(folder)
            }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `recusa fonte adulterada, extensão falsa, tamanho acima de 12 MB e travessia`() {
        val root = Files.createTempDirectory("brother-font-store-safety-")
        val folder = root.resolve("fonts")
        val bad = root.resolve("bad.ttf")
        val renamed = root.resolve("bad.otf")
        val oversize = root.resolve("too-large.ttf")
        try {
            val library = DesktopImportedFontLibrary(folder)
            Files.write(bad, byteArrayOf(1, 2, 3, 4, 5, 6))
            Files.write(renamed, Files.readAllBytes(fontFixture()))
            Files.newOutputStream(oversize).use {
                it.write(ByteArray(12 * 1024 * 1024 + 1))
            }
            assertFailsWith<IllegalArgumentException> { library.importFile(bad) }
            assertFailsWith<IllegalArgumentException> { library.importFile(renamed) }
            assertFailsWith<IllegalArgumentException> { library.importFile(oversize) }
            assertFailsWith<IllegalArgumentException> { library.importFile(bad, "malware.exe") }
            assertFailsWith<IllegalArgumentException> { library.delete("../outro.ttf") }
            assertTrue(library.list().isEmpty())
        } finally {
            for (p in listOf(bad, renamed, oversize)) Files.deleteIfExists(p)
            Files.deleteIfExists(folder)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `nunca confunde arquivo falso com fonte mesmo com assinatura TTF`() {
        val root = Files.createTempDirectory("brother-font-format-check-")
        val folder = root.resolve("fonts")
        val fake = root.resolve("fake.ttf")
        try {
            val library = DesktopImportedFontLibrary(folder)
            Files.write(fake, byteArrayOf(0, 1, 0, 0) + ByteArray(256))
            assertFailsWith<IllegalArgumentException> {
                library.importFile(fake)
            }
            assertTrue(library.list().isEmpty())
        } finally {
            Files.deleteIfExists(fake)
            Files.deleteIfExists(folder)
            Files.deleteIfExists(root)
        }
    }
}
