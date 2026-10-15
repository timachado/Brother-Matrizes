package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoader
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressão da rota Tatami QA após exportar/reabrir PES/JEF/DST. */
class ImportedGlyphTatamiExportRoundTripTest {
    private fun v(x: Float, y: Float) = CleanRoomSatinEngine.V(x, y)
    private fun rect(x: Float, y: Float, w: Float, h: Float) =
        listOf(v(x, y), v(x + w, y), v(x + w, y + h), v(x, y + h))

    @Test
    fun leftAndRightStemRemainAfterPesJefDstRoundTrips() {
        // M sintético com as DUAS hastes inteiras, sem depender de fonte
        // licenciada/privada. O QA com os TTF reais ocorre fora do repositório.
        val contours = listOf(
            rect(0f, 0f, 21f, 146f),
            listOf(v(15f, 0f), v(31f, 0f), v(89f, 74f), v(75f, 96f)),
            listOf(v(65f, 74f), v(121f, 0f), v(137f, 0f), v(81f, 96f)),
            rect(125f, 0f, 21f, 146f)
        )
        val emitted = ImportedGlyphTatamiAdapter.digitize(contours).toMutableList()
        assertTrue("Glifo de teste não gerou costura", emitted.any {
            it.command == StitchCommand.STITCH
        })
        emitted += emitted.last().copy(command = StitchCommand.END)
        val sewnOriginal = emitted.filter { it.command == StitchCommand.STITCH }
        assertTrue("Faltou a haste esquerda original", sewnOriginal.any { it.xUnits < 20 && it.yUnits > 95 })
        assertTrue("Faltou a haste direita original", sewnOriginal.any { it.xUnits > 125 && it.yUnits > 95 })

        val nonTerminal = emitted.filter { it.command != StitchCommand.END }
        val bounds = EmbroideryBounds(
            minXUnits = nonTerminal.minOf { it.xUnits },
            maxXUnits = nonTerminal.maxOf { it.xUnits },
            minYUnits = nonTerminal.minOf { it.yUnits },
            maxYUnits = nonTerminal.maxOf { it.yUnits }
        )
        val design = EmbroideryDesign(
            fileName = "m-tatami-qa.pes", format = "PES", label = "M",
            points = emitted, bounds = bounds,
            stitchCount = emitted.count { it.command == StitchCommand.STITCH },
            jumpCount = emitted.count { it.command == StitchCommand.JUMP },
            colorChanges = 0, endFound = true, sourceBytes = ByteArray(0),
            threadColors = listOf(0xE74C3C), sourceYAxisDown = true, isModified = true
        )
        for (format in listOf("PES", "JEF", "DST")) {
            val encoded = MatrixConverter.convert(design, format, "m-tatami-qa").getOrThrow()
            assertTrue("$format gerou arquivo vazio", encoded.bytes.size > 100)
            val opened = EmbroideryLoader.loadBytes(encoded.fileName, encoded.bytes)
            assertTrue("$format não reabriu: $opened", opened is EmbroideryLoadResult.Success)
            val actual = (opened as EmbroideryLoadResult.Success).design.points
                .filter { it.command == StitchCommand.STITCH }
            assertTrue("$format perdeu as pontadas", actual.size > 30)
            val minX = actual.minOf { it.xUnits }
            val maxX = actual.maxOf { it.xUnits }
            val minY = actual.minOf { it.yUnits }
            val maxY = actual.maxOf { it.yUnits }
            val deltaX = maxX - minX
            val deltaY = maxY - minY
            assertTrue("$format perdeu base da primeira haste do M",
                actual.any { it.xUnits < minX + deltaX * 0.18f &&
                    it.yUnits > minY + deltaY * 0.73f })
            assertTrue("$format perdeu base da segunda haste do M",
                actual.any { it.xUnits > minX + deltaX * 0.82f &&
                    it.yUnits > minY + deltaY * 0.73f })
            assertTrue("$format perdeu topo da segunda haste do M",
                actual.any { it.xUnits > minX + deltaX * 0.82f &&
                    it.yUnits < minY + deltaY * 0.27f })
        }
    }
}
