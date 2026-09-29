package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DstParserTest {
    @Test
    fun parsesSimpleSquareAndColorChange() {
        val header = ByteArray(512) { 0x20 }
        "LA:TESTE\r".toByteArray(Charsets.US_ASCII).copyInto(header)
        val body = byteArrayOf(
            0x08, 0x00, 0x03,
            0x20, 0x00, 0x03,
            0x04, 0x00, 0x03,
            0x10, 0x00, 0x03,
            0x00, 0x00, 0xC3.toByte(),
            0x00, 0x00, 0xF3.toByte()
        )
        val result = DstParser.parse("teste.dst", header + body)
        assertTrue(result is EmbroideryLoadResult.Success)
        val design = (result as EmbroideryLoadResult.Success).design
        assertEquals("TESTE", design.label)
        assertEquals(4, design.stitchCount)
        assertEquals(1, design.colorChanges)
        assertEquals(2, design.colorCount)
        assertEquals(0.9f, design.bounds.widthMm, 0.001f)
        assertEquals(0.9f, design.bounds.heightMm, 0.001f)
        assertTrue(design.sourceYAxisDown)

        val stitches =
            design.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertEquals(
            listOf(
                Pair(-9, 0),
                Pair(-9, -9),
                Pair(0, -9),
                Pair(0, 0)
            ),
            stitches.map {
                Pair(
                    it.xUnits,
                    it.yUnits
                )
            }
        )

        assertTrue(design.endFound)
    }
}
