package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorContourFinishGeneratorTest {
    private fun rect(left: Int, top: Int, right: Int, bottom: Int) =
        listOf(left to top, right to top, right to bottom, left to bottom)

    @Test
    fun closedContoursUseRunningStitchesUpToTwoMillimeters() {
        val finish = VectorContourFinishGenerator.generate(
            contours = listOf(rect(-250, -200, 250, 200))
        ).getOrThrow()
        assertEquals(StitchCommand.JUMP, finish.first().command)
        assertTrue(finish.count { it.command == StitchCommand.STITCH } >= 90)
        assertTrue(finish.all { it.xUnits in -250..250 && it.yUnits in -200..200 })
        assertEquals(StitchCommand.STITCH, finish.last().command)
        val stitched = finish.filter { it.command == StitchCommand.STITCH }
        assertTrue(stitched.zipWithNext().all { (a, b) ->
            hypot((a.xUnits - b.xUnits).toDouble(), (a.yUnits - b.yUnits).toDouble()) <= 20.1
        })
    }

    @Test
    fun disconnectedContoursNeverGetStitchedAcrossEmptySpace() {
        val points = VectorContourFinishGenerator.generate(
            listOf(rect(-300, -300, -150, -100), rect(150, 100, 300, 300))
        ).getOrThrow()
        assertEquals(2, points.count { it.command == StitchCommand.JUMP })
        assertEquals(1, points.count { it.command == StitchCommand.TRIM })
        assertTrue(points.zipWithNext().none { (a, b) ->
            a.command == StitchCommand.STITCH && b.command == StitchCommand.STITCH &&
                hypot((a.xUnits - b.xUnits).toDouble(),
                    (a.yUnits - b.yUnits).toDouble()) > 25.0
        })
    }

    @Test
    fun duplicatedReversedAndRotatedPathsAreOnlyStitchedOnce() {
        val square = rect(0, 0, 100, 100)
        val rotated = square.drop(1) + square.first()
        val paths = VectorContourFinishGenerator.generate(
            listOf(square, rotated, square.asReversed())
        ).getOrThrow()
        assertEquals(1, paths.count { it.command == StitchCommand.JUMP })
        assertEquals(0, paths.count { it.command == StitchCommand.TRIM })
    }

    @Test
    fun tinyClosedDetailsAreNotOverstitched() {
        val points = VectorContourFinishGenerator.generate(
            listOf(rect(0, 0, 120, 80), rect(150, 150, 153, 153))
        ).getOrThrow()
        assertEquals(1, points.count { it.command == StitchCommand.JUMP })
        assertFalse(points.any { it.xUnits >= 150 && it.yUnits >= 150 })
    }

    @Test
    fun combinedTatamiAddsOnlyOptionalFinishAndKeepsDefaultStable() {
        val artwork = VectorArtwork(
            label = "Contorno Teste",
            paths = listOf(
                VectorPath(
                    points = rect(0, 0, 100, 80).map {
                        VectorPoint(it.first.toFloat(), it.second.toFloat())
                    },
                    closed = true
                )
            )
        )
        val options = VectorMatrixOptions(
            widthMm = 60f,
            fillMode = VectorFillMode.TATAMI,
            tatamiRowSpacingMm = .5f,
            hoopProfile = HoopProfile.H100X100,
            outputFormat = "PES"
        )
        val base = VectorMatrixGenerator.generate(artwork, options).getOrThrow()
        val detail = VectorMatrixGenerator.generate(
            artwork, options.copy(tatamiContourFinish = true)
        ).getOrThrow()
        assertTrue(detail.stitchCount > base.stitchCount)
        assertTrue(detail.points.count { it.command == StitchCommand.JUMP } >
            base.points.count { it.command == StitchCommand.JUMP })
        assertTrue(detail.points.count { it.command == StitchCommand.TRIM } >
            base.points.count { it.command == StitchCommand.TRIM })
        assertTrue(detail.endFound)
        assertTrue(HoopValidator.validate(detail, HoopProfile.H100X100).fits)
        for (format in listOf("PES", "DST", "JEF")) {
            assertTrue(MatrixConverter.convert(detail, format, "tatami-contour")
                .getOrThrow().bytes.isNotEmpty())
        }
        // Desativado deve retornar exatamente o mesmo design Tatami anterior.
        val again = VectorMatrixGenerator.generate(
            artwork, options.copy(tatamiContourFinish = false)
        ).getOrThrow()
        assertEquals(base.points, again.points)
    }

    @Test
    fun emptyOrMicroscopicOnlyFinishFailsCleanly() {
        assertTrue(VectorContourFinishGenerator.generate(
            listOf(rect(0, 0, 2, 2))
        ).isFailure)
    }
}
