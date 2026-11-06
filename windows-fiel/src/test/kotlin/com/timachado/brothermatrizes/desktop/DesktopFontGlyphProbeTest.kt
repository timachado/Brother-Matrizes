package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopFontGlyphProbe
import com.timachado.brothermatrizes.desktop.platform.DesktopImportedFontLibrary
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopFontGlyphProbeTest {
    private fun fontFixture(): Path {
        val win = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        val possible = listOf(
            win.resolve("arial.ttf"),
            win.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        )
        return possible.firstOrNull(Files::isRegularFile)
            ?: error("Sem fonte TTF de sistema para o teste de contorno.")
    }

    @Test
    fun `Skia extrai contornos verdadeiros para nome Maria sem gerar pontos falsos`() {
        val root = Files.createTempDirectory("brother-glyph-paths-qa-")
        val fonts = root.resolve("fonts")
        try {
            val imported = DesktopImportedFontLibrary(fonts)
                .importFile(fontFixture(), "Teste contornos.ttf")
            val report = DesktopFontGlyphProbe.inspect(
                Path.of(imported.absolutePath), "Maria"
            )
            assertTrue(report.family.isNotBlank())
            assertEquals(5, report.glyphCount)
            assertEquals(5, report.visibleOutlineCount)
            assertEquals(0, report.missingGlyphCount)
            assertTrue(report.advanceWidthAt100 > 0f)
            assertTrue(report.advanceWidthAt100.isFinite())
        } finally {
            if (Files.isDirectory(fonts)) {
                Files.list(fonts).use { stream ->
                    stream.forEach { font -> Files.deleteIfExists(font) }
                }
                Files.deleteIfExists(fonts)
            }
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun `recusa texto vazio e caracteres de controle no desenho de fonte`() {
        val fixture = fontFixture()
        for (text in listOf("", "   ", "a\nb", "a\u0000b", "x".repeat(65))) {
            assertFailsWith<IllegalArgumentException> {
                DesktopFontGlyphProbe.inspect(fixture, text)
            }
        }
    }
}
