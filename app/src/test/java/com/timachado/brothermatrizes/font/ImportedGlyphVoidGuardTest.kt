package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedGlyphVoidGuardTest {
    private fun rectangle(x: Float, y: Float, width: Float, height: Float) =
        listOf(
            CleanRoomSatinEngine.V(x, y),
            CleanRoomSatinEngine.V(x + width, y),
            CleanRoomSatinEngine.V(x + width, y + height),
            CleanRoomSatinEngine.V(x, y + height)
        )

    private fun at(x: Int, y: Int, command: StitchCommand) =
        EmbroideryPoint(x, y, command, 0)

    @Test
    fun originalLetterCounterCannotBecomeSewnConnection() {
        val contours = listOf(
            rectangle(0f, 0f, 80f, 80f),
            rectangle(20f, 20f, 40f, 40f).asReversed()
        )
        val points = mutableListOf(
            at(10, 40, StitchCommand.JUMP),
            at(10, 43, StitchCommand.STITCH),
            at(70, 43, StitchCommand.STITCH),
            at(72, 43, StitchCommand.STITCH)
        )
        val fixed = ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours)
        assertEquals("O vazio central da letra não deve receber linha costurada.", 1, fixed)
        assertEquals(StitchCommand.JUMP, points[2].command)
        assertEquals(StitchCommand.STITCH, points[3].command)
        assertEquals("A geometria original não deve mudar.", 70, points[2].xUnits)
        assertEquals("A contagem de comandos não deve mudar.", 4, points.size)
    }

    @Test
    fun detachedDecorativePartsMustRemainSeparated() {
        val contours = listOf(
            rectangle(0f, 0f, 20f, 50f),
            rectangle(50f, 0f, 20f, 50f)
        )
        val points = mutableListOf(
            at(10, 15, StitchCommand.JUMP),
            at(15, 15, StitchCommand.STITCH),
            at(60, 15, StitchCommand.STITCH),
            at(65, 15, StitchCommand.STITCH)
        )
        val fixed = ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours)
        assertEquals(1, fixed)
        assertEquals(StitchCommand.JUMP, points[2].command)
        assertEquals(StitchCommand.STITCH, points[3].command)
    }

    @Test
    fun wideValidSatinInsideLetterRemainsStitch() {
        val contours = listOf(rectangle(0f, 0f, 120f, 100f))
        val points = mutableListOf(
            at(5, 50, StitchCommand.JUMP),
            at(70, 50, StitchCommand.STITCH),
            at(115, 50, StitchCommand.STITCH)
        )
        assertEquals(0, ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours))
        assertTrue(points.drop(1).all { it.command == StitchCommand.STITCH })
    }

    @Test
    fun smallPullCompensationOutsideEdgeDoesNotDeleteSatin() {
        val contours = listOf(rectangle(0f, 0f, 70f, 70f))
        val points = mutableListOf(
            at(-2, 35, StitchCommand.JUMP),
            at(72, 35, StitchCommand.STITCH)
        )
        assertEquals(0, ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours))
        assertEquals(StitchCommand.STITCH, points[1].command)
    }

    @Test
    fun smallValidDecorativeHairlineIsNotErasedBySafetyGuard() {
        val contours = listOf(rectangle(0f, 0f, 5f, 40f))
        val points = mutableListOf(
            at(2, 2, StitchCommand.JUMP),
            at(3, 10, StitchCommand.STITCH),
            at(3, 22, StitchCommand.STITCH)
        )
        assertEquals(0, ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours))
    }

    @Test
    fun subpixelStemRemainsSewn() {
        val contours = listOf(rectangle(3.22f, 0f, 0.2f, 80f))
        val points = mutableListOf(
            at(3, 4, StitchCommand.JUMP),
            at(3, 60, StitchCommand.STITCH)
        )
        assertEquals(0, ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours))
        assertEquals(StitchCommand.STITCH, points[1].command)
    }

    @Test
    fun sameCoordinatesAndCommandsOtherThanSewingAreAlwaysPreserved() {
        val contours = listOf(rectangle(0f, 0f, 30f, 30f))
        val points = mutableListOf(
            at(3, 3, StitchCommand.JUMP),
            at(90, 3, StitchCommand.JUMP),
            at(90, 3, StitchCommand.TRIM),
            at(5, 6, StitchCommand.STITCH)
        )
        val old = points.toList()
        assertEquals(0, ImportedGlyphVoidGuard.preventVoidConnections(points, 0, contours))
        assertEquals(old, points)
    }
}
