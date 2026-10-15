package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoader
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedGlyphExportRoundTripTest {
    private fun v(x: Float, y: Float) = CleanRoomSatinEngine.V(x, y)
    private fun rect(x: Float, y: Float, w: Float, h: Float) =
        listOf(v(x,y), v(x+w,y), v(x+w,y+h), v(x,y+h))

    @Test
    fun completeSecondStemSurvivesRealPesJefDstConversions() {
        val m = listOf(
            rect(0f, 0f, 23f, 155f),
            listOf(v(15f, 0f), v(29f, 0f), v(86f, 83f), v(72f, 101f)),
            listOf(v(70f, 83f), v(121f, 0f), v(135f, 0f), v(86f, 101f)),
            rect(124f, 0f, 23f, 155f)
        )
        val planned = CleanRoomSatinEngine.planContours(m, 0.4f)
        val points = CleanRoomSatinEngine.stitchPlannedGlyphForTest(planned).toMutableList()
        assertTrue(points.any { it.command == StitchCommand.STITCH })
        points += points.last().copy(command = StitchCommand.END)
        val nonTerminal = points.filter { it.command != StitchCommand.END }
        val bounds = EmbroideryBounds(
            nonTerminal.minOf { it.xUnits }, nonTerminal.maxOf { it.xUnits },
            nonTerminal.minOf { it.yUnits }, nonTerminal.maxOf { it.yUnits }
        )
        val design = EmbroideryDesign(
            fileName = "m-hastes.pes", format = "PES", label = "M",
            points = points, bounds = bounds,
            stitchCount = points.count { it.command == StitchCommand.STITCH },
            jumpCount = points.count { it.command == StitchCommand.JUMP },
            colorChanges = 0, endFound = true, sourceBytes = ByteArray(0),
            threadColors = listOf(0xEE4455), sourceYAxisDown = true, isModified = true
        )
        assertEquals("PES", MatrixConverter.preferredBrotherFormat)
        for (format in listOf("PES", "JEF", "DST")) {
            val encoded = MatrixConverter.convert(design, format, "topologia").getOrThrow()
            assertTrue("Formato $format gerou arquivo vazio", encoded.bytes.size > 100)
            val opened = EmbroideryLoader.loadBytes(encoded.fileName, encoded.bytes)
            assertTrue("O formato $format não pôde ser reaberto: $opened",
                opened is EmbroideryLoadResult.Success)
            val restored = (opened as EmbroideryLoadResult.Success).design
            val sewn = restored.points.filter { it.command == StitchCommand.STITCH }
            assertTrue("$format perdeu todas as pontadas", sewn.size > 30)
            val minX = sewn.minOf { it.xUnits }
            val maxX = sewn.maxOf { it.xUnits }
            val minY = sewn.minOf { it.yUnits }
            val maxY = sewn.maxOf { it.yUnits }
            val rangeX = maxX - minX
            val rangeY = maxY - minY
            assertTrue("$format perdeu a parte inferior da segunda haste do M",
                sewn.any { it.xUnits > minX + rangeX * 0.82f &&
                    it.yUnits > minY + rangeY * 0.77f })
            assertTrue("$format perdeu o topo da segunda haste do M",
                sewn.any { it.xUnits > minX + rangeX * 0.82f &&
                    it.yUnits < minY + rangeY * 0.23f })
        }
    }
}
