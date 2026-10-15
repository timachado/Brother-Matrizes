package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.MachineFinishing
import com.timachado.brothermatrizes.core.embroidery.MachineFinishingSettings
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** QA do acabamento antes de simular/exportar fontes importadas. */
class ImportedGlyphTatamiFinishingTest {
    private fun v(x: Float, y: Float) = CleanRoomSatinEngine.V(x, y)
    private fun rectangle(x: Float, y: Float, w: Float, h: Float) =
        listOf(v(x, y), v(x + w, y), v(x + w, y + h), v(x, y + h))

    @Test
    fun longTravelReceivesTrimWithoutChangingOriginalSimulator() {
        val points = ImportedGlyphTatamiAdapter.digitize(
            listOf(
                rectangle(0f, 0f, 30f, 100f),
                rectangle(230f, 0f, 30f, 100f)
            )
        ).toMutableList()
        points += points.last().copy(command = StitchCommand.END)
        val printable = points.filter { it.command != StitchCommand.END }
        val source = EmbroideryDesign(
            fileName = "tatami-finishing-qa.pes",
            format = "PES",
            label = "M-qa",
            points = points,
            bounds = EmbroideryBounds(
                printable.minOf { it.xUnits },
                printable.maxOf { it.xUnits },
                printable.minOf { it.yUnits },
                printable.maxOf { it.yUnits }
            ),
            stitchCount = points.count { it.command == StitchCommand.STITCH },
            jumpCount = points.count { it.command == StitchCommand.JUMP },
            colorChanges = 0,
            endFound = true,
            sourceBytes = ByteArray(0),
            sourceYAxisDown = true,
            isModified = true,
            threadColors = listOf(0xD74360)
        )
        val result = MachineFinishing.apply(
            source,
            MachineFinishingSettings(
                tieInEnabled = true,
                tieOffEnabled = true,
                autoTrimLongJumps = true,
                trimJumpThresholdMm = 6f,
                optimizeTravel = true
            )
        )
        val finalized = result.design.points
        assertTrue("Salto longo exige corte", result.report.autoTrimCount > 0)
        assertTrue(finalized.any { it.command == StitchCommand.TRIM })
        assertEquals(StitchCommand.END, finalized.last().command)
        assertEquals(0, finalized.count { it.command == StitchCommand.COLOR_CHANGE })
        var previous = finalized.first()
        for (i in 1 until finalized.size) {
            val next = finalized[i]
            val distance = hypot(
                (next.xUnits - previous.xUnits).toDouble(),
                (next.yUnits - previous.yUnits).toDouble()
            ) / 10.0
            if (next.command == StitchCommand.JUMP && distance >= 6.0) {
                assertEquals("Faltou TRIM antes de salto longo em indice $i",
                    StitchCommand.TRIM, previous.command)
            }
            previous = next
        }
        assertTrue("A haste esquerda foi removida",
            finalized.any { it.command == StitchCommand.STITCH && it.xUnits < 40 })
        assertTrue("A haste direita foi removida",
            finalized.any { it.command == StitchCommand.STITCH && it.xUnits > 220 })
    }
}
