package com.timachado.brothermatrizes.font

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenTypeFeatureCatalogTest {
    @get:Rule val temp = TemporaryFolder()

    /** Apenas SFNT/GSUB sintético, nunca usar arquivos de fonte de terceiros no repositório. */
    private fun syntheticFont(vararg tags: String): File {
        val gsubLength = 10 + 2 + 6 * tags.size
        val buf = ByteBuffer.allocate(28 + gsubLength).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(0x4f54544f)
        buf.putShort(1).putShort(0).putShort(0).putShort(0)
        buf.putInt(0x47535542)
        buf.putInt(0).putInt(28).putInt(gsubLength)
        buf.putShort(1).putShort(0).putShort(0).putShort(10).putShort(0)
        buf.putShort(tags.size.toShort())
        tags.forEach { tag ->
            require(tag.length == 4)
            buf.put(tag.toByteArray(Charsets.US_ASCII))
            buf.putShort(0)
        }
        return temp.newFile("fixture-${tags.size}.otf").apply {
            writeBytes(buf.array())
        }
    }

    @Test fun detectsStylisticAndAlternateFeatures() {
        val file = syntheticFont("liga", "aalt", "ss01", "ss02", "ss03", "kern")
        assertEquals(setOf("liga", "aalt", "ss01", "ss02", "ss03"),
            OpenTypeFeatureCatalog.readOptionalFeatures(file))
    }

    @Test fun exposesOnlyRecognizedFeatureTags() {
        val file = syntheticFont("xxxx", "calt", "ss00", "ss10")
        assertEquals(setOf("calt", "ss10"),
            OpenTypeFeatureCatalog.readOptionalFeatures(file))
    }

    @Test fun rejectsZipRenamedToOtf() {
        val file = temp.newFile("fake.otf")
        file.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
        assertTrue(OpenTypeFeatureCatalog.readOptionalFeatures(file).isEmpty())
    }

    @Test fun rejectsTruncatedFileWithoutCrashing() {
        val file = syntheticFont("aalt")
        file.writeBytes(file.readBytes().copyOfRange(0, 24))
        assertTrue(OpenTypeFeatureCatalog.readOptionalFeatures(file).isEmpty())
    }
}
