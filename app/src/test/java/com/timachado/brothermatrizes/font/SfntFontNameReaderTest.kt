package com.timachado.brothermatrizes.font

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SfntFontNameReaderTest {
    @get:Rule val temporary = TemporaryFolder()

    /** SFNT minimamente estruturado, somente para validar metadados, não glifos. */
    private fun buildFont(nameId: Int, family: String, platform: Int = 3): java.io.File {
        val nameBytes = if (platform == 3) family.toByteArray(Charsets.UTF_16BE)
            else family.toByteArray(Charsets.ISO_8859_1)
        val table = ByteBuffer.allocate(18 + nameBytes.size).order(ByteOrder.BIG_ENDIAN)
            .putShort(0).putShort(1).putShort(18)
            .putShort(platform.toShort()).putShort(1)
            .putShort(0x0409).putShort(nameId.toShort())
            .putShort(nameBytes.size.toShort()).putShort(0)
            .put(nameBytes).array()
        val buf = ByteBuffer.allocate(28 + table.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(0x00010000)
        buf.putShort(1).putShort(0).putShort(0).putShort(0)
        buf.putInt(0x6e616d65)
        buf.putInt(0).putInt(28).putInt(table.size)
        buf.put(table)
        return temporary.newFile("sample-${family.hashCode()}.ttf").apply {
            writeBytes(buf.array())
        }
    }

    @Test fun identificaNomeInternoRealEmVezDoArquivoDoZip() {
        val file = buildFont(1, "Love by Mahima")
        assertEquals("Love by Mahima", SfntFontNameReader.familyName(file))
    }

    @Test fun aceitaNomeTipograficoQuandoPresente() {
        val file = buildFont(16, "Love Script")
        assertEquals("Love Script", SfntFontNameReader.familyName(file))
    }

    @Test fun naoExibeCodigoHexadecimalComoFamilia() {
        val file = buildFont(1, "a59e4c8e")
        assertNull(SfntFontNameReader.familyName(file))
    }

    @Test fun rejeitaArquivosQueNaoSaoFontes() {
        val zip = temporary.newFile("fonte.ttf")
        zip.writeBytes("PK\u0003\u0004".toByteArray())
        assertNull(SfntFontNameReader.familyName(zip))
    }

    @Test fun limparNomeMantemLinguasERejeitaControles() {
        assertEquals("Love Script", SfntFontNameReader.cleanName("  Love\u0000\nScript  "))
        assertEquals("Fonte Coração", SfntFontNameReader.cleanName("Fonte Coração"))
        assertNull(SfntFontNameReader.cleanName("00117381"))
        assertTrue(SfntFontNameReader.cleanName("Love")!!.isNotEmpty())
    }
}
