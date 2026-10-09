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
    @Test
    fun decodesAllBalancedTernaryWeightsWithoutMisclassifyingStitches() {
        val header = ByteArray(512) { 0x20 }
        "LA:DST_BITS\\r".toByteArray(Charsets.US_ASCII).copyInto(header)
        val body = byteArrayOf(
            0xA0.toByte(), 0xA0.toByte(), 0x23, // Y +1+9+3+27+81 => -121 (Y interno)
            0x50, 0x50, 0x13, // Y -1-9-3-27-81 => +121
            0x05, 0x05, 0x07, // X +1+9+3+27+81 => +121
            0x0A, 0x0A, 0x0B, // X -1-9-3-27-81 => -121
            0x00, 0x00, 0xF3.toByte()
        )
        val result = DstParser.parse("pesos.dst", header + body)
        assertTrue(result is EmbroideryLoadResult.Success)
        val design = (result as EmbroideryLoadResult.Success).design
        assertEquals(4, design.stitchCount)
        assertEquals(0, design.jumpCount)
        assertEquals(0, design.colorChanges)
        assertEquals(
            listOf(
                0 to -121, 0 to 0, 121 to 0, 0 to 0
            ),
            design.points.filter { it.command == StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }
        )
        assertTrue(design.endFound)
    }

    @Test
    fun dstControlFlagsDistinguishJumpColorChangeSequinAndEnd() {
        val header = ByteArray(512) { 0x20 }
        "LA:CONTROL\\r".toByteArray(Charsets.US_ASCII).copyInto(header)
        val body = byteArrayOf(
            0x01, 0x00, 0x03, // Stitch x=+1
            0x01, 0x00, 0x83.toByte(), // Jump x=+1
            0x00, 0x00, 0xC3.toByte(), // Color change, no movement
            0x00, 0x00, 0x43, // Sequin mode, not Jump
            0x01, 0x00, 0x03, // Stitch x=+1
            0x00, 0x00, 0xF3.toByte() // End
        )
        val result = DstParser.parse("controles.dst", header + body)
        assertTrue(result is EmbroideryLoadResult.Success)
        val design = (result as EmbroideryLoadResult.Success).design
        assertEquals(
            listOf(
                StitchCommand.STITCH, StitchCommand.JUMP,
                StitchCommand.COLOR_CHANGE, StitchCommand.SEQUIN,
                StitchCommand.STITCH, StitchCommand.END
            ),
            design.points.map { it.command }
        )
        assertEquals(listOf(1, 2, 2, 2, 3, 3), design.points.map { it.xUnits })
        assertEquals(2, design.stitchCount)
        assertEquals(1, design.jumpCount)
        assertEquals(1, design.colorChanges)
        assertTrue(design.endFound)
    }

}
