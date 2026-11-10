package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopOpenTypeFontNames

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class DesktopOpenTypeFontNamesTest {
    @Test fun displaysRealFontNameInsteadOfTechnicalDownloadFilename() {
        withFont(
            records = listOf(
                FontName(1, "Brother"),
                FontName(4, "Brother Matrix")
            )
        ) { file ->
            assertEquals("Brother Matrix", DesktopOpenTypeFontNames.readDisplayName(file))
        }
    }

    @Test fun fallsBackToFamilyWhenFullNameIsMissing() {
        withFont(records = listOf(FontName(16, "Coração Bordado"))) { file ->
            assertEquals("Coração Bordado", DesktopOpenTypeFontNames.readDisplayName(file))
        }
    }

    @Test fun prefersBrazilianPortugueseWhenMultipleNamesExist() {
        withFont(
            records = listOf(
                FontName(4, "Embroidery Script", 0x0409),
                FontName(4, "Escrita Bordada", 0x0416)
            )
        ) { file ->
            assertEquals("Escrita Bordada", DesktopOpenTypeFontNames.readDisplayName(file))
        }
    }

    @Test fun readsOpenTypeCffMetadata() {
        withFont(
            signature = 0x4F54544F,
            records = listOf(FontName(4, "Floral OTF"))
        ) { file ->
            assertEquals("Floral OTF", DesktopOpenTypeFontNames.readDisplayName(file))
        }
    }

    @Test fun rejectsCorruptFontWithoutThrowing() {
        val file = File.createTempFile("invalid-font", ".otf")
        try {
            file.writeBytes(byteArrayOf(0, 1, 2, 3))
            assertNull(DesktopOpenTypeFontNames.readDisplayName(file))
        } finally {
            file.delete()
        }
    }

    private data class FontName(
        val id: Int,
        val text: String,
        val language: Int = 0x0416
    )

    private fun withFont(
        records: List<FontName>,
        signature: Int = 0x00010000,
        test: (File) -> Unit
    ) {
        val encoded = records.map { it.text.toByteArray(Charsets.UTF_16BE) }
        val recordsBytes = records.size * 12
        val tableBytes = 6 + recordsBytes + encoded.sumOf { it.size }
        val buffer = ByteBuffer.allocate(28 + tableBytes).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(signature)
        buffer.putShort(1) // number of tables
        buffer.putShort(0).putShort(0).putShort(0)
        buffer.putInt(0x6E616D65) // name table
        buffer.putInt(0) // checksum
        buffer.putInt(28) // table offset
        buffer.putInt(tableBytes)
        buffer.putShort(0) // name table format
        buffer.putShort(records.size.toShort())
        buffer.putShort((6 + recordsBytes).toShort()) // string storage offset

        var offset = 0
        records.forEachIndexed { index, item ->
            buffer.putShort(3) // Windows platform
            buffer.putShort(1) // Unicode BMP
            buffer.putShort(item.language.toShort())
            buffer.putShort(item.id.toShort())
            buffer.putShort(encoded[index].size.toShort())
            buffer.putShort(offset.toShort())
            offset += encoded[index].size
        }
        encoded.forEach(buffer::put)

        val file = File.createTempFile("brother_matrix_27825975_5b71", ".ttf")
        try {
            file.writeBytes(buffer.array())
            test(file)
        } finally {
            file.delete()
        }
    }
}
