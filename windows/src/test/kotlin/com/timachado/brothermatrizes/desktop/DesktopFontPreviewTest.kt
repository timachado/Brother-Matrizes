package com.timachado.brothermatrizes.desktop

import java.awt.FontFormatException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopFontPreviewTest {
    @Test
    fun `arquivo que apenas se apresenta como TTF nao é aceito`() {
        val folder = Files.createTempDirectory("bm-font-qa-")
        val fake = folder.resolve("falsa.ttf")
        try {
            Files.write(fake, ByteArray(128) { 0x41 })
            assertFailsWith<IllegalArgumentException> {
                DesktopFontPreviewService.open(fake)
            }
            // Uma assinatura correta, mas com conteúdo malformado, não basta.
            val invalid = ByteArray(128)
            invalid[0] = 0
            invalid[1] = 1
            invalid[2] = 0
            invalid[3] = 0
            Files.write(fake, invalid)
            assertFailsWith<FontFormatException> {
                DesktopFontPreviewService.open(fake)
            }
        } finally {
            Files.deleteIfExists(fake)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `arquivo com extensao proibida nao é interpretado como fonte`() {
        val folder = Files.createTempDirectory("bm-font-ext-")
        val file = folder.resolve("fonte.exe")
        try {
            Files.write(file, ByteArray(32))
            assertFailsWith<IllegalArgumentException> {
                DesktopFontPreviewService.open(file)
            }
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun `fonte TrueType real do Windows pode ser lida sem instalar`() {
        val windowsDir = System.getenv("WINDIR") ?: return
        val path = Path.of(windowsDir, "Fonts", "arial.ttf")
        if (!Files.isRegularFile(path)) return
        val parsed = DesktopFontPreviewService.open(path)
        assertEquals("arial.ttf", parsed.fileName.lowercase())
        assertTrue(parsed.family.isNotBlank())
        assertTrue(parsed.awtFont.canDisplay('B'))
    }
}
