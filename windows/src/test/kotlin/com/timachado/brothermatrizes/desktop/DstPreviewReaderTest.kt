package com.timachado.brothermatrizes.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DstPreviewReaderTest {
    private val header = ByteArray(512) { 0x20 }

    @Test
    fun `le arquivo basico sem modificar bytes`() {
        val bytes = header + byteArrayOf(
            0x01, 0x00, 0x03,             // STITCH: X +1
            0x00, 0x00, 0xF3.toByte()    // END
        )
        val snapshot = bytes.copyOf()
        val doc = DstPreviewReader.read("exemplo.DST", bytes)
        assertEquals(1, doc.stitchCount)
        assertEquals(1, doc.bounds.maxX)
        assertEquals(0.1, doc.bounds.widthMm)
        assertEquals(PreviewCommand.END, doc.points.last().command)
        assertTrue(snapshot.contentEquals(bytes))
    }

    @Test
    fun `conta trocas de cor sem criar costura falsa`() {
        val bytes = header + byteArrayOf(
            0x01, 0x00, 0x03,
            0x00, 0x00, 0xC3.toByte(),
            0x01, 0x00, 0x03,
            0x00, 0x00, 0xF3.toByte()
        )
        val doc = DstPreviewReader.read("cores.dst", bytes)
        assertEquals(2, doc.stitchCount)
        assertEquals(1, doc.colorChanges)
        assertEquals(1, doc.points[2].colorIndex)
    }

    @Test
    fun `recusa DST sem terminador`() {
        val bytes = header + byteArrayOf(0x01, 0x00, 0x03)
        assertFailsWith<IllegalArgumentException> {
            DstPreviewReader.read("incompleto.dst", bytes)
        }
    }

    @Test
    fun `recusa formato nao implementado em QA`() {
        val bytes = header + byteArrayOf(0x01, 0x00, 0x03, 0x00, 0x00, 0xF3.toByte())
        assertFailsWith<IllegalArgumentException> {
            DstPreviewReader.read("arquivo.pes", bytes)
        }
    }
}
