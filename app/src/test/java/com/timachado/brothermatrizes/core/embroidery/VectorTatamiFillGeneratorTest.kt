package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class VectorTatamiFillGeneratorTest {
    private fun rectangle(
        left: Int, top: Int, right: Int, bottom: Int
    ) = listOf(
        left to top, right to top, right to bottom, left to bottom
    )

    @Test
    fun closedRectangleGeneratesContinuousTatamiWithinPolygon() {
        val points = VectorTatamiFillGenerator.generate(
            contours = listOf(rectangle(0, 0, 200, 120)),
            rowSpacingUnits = 5,
            maxStitchLengthUnits = 30
        ).getOrThrow()

        assertTrue(points.count { it.command == StitchCommand.STITCH } > 100)
        assertEquals(1, points.count { it.command == StitchCommand.JUMP })
        assertTrue(points.all {
            it.xUnits in 0..200 && it.yUnits in 0..120
        })
        assertTrue(points.none { it.command == StitchCommand.END })
        // Nenhuma distância entre perfurações consecutivas excede 4 mm.
        val needlePoints = points.filter { it.command == StitchCommand.STITCH }
        assertTrue(needlePoints.zipWithNext().all { (a, b) ->
            hypot(
                (a.xUnits - b.xUnits).toDouble(),
                (a.yUnits - b.yUnits).toDouble()
            ) <= 40.0
        })
    }

    @Test
    fun donutOppositeWindingKeepsHoleFreeOfStitches() {
        val outer = rectangle(0, 0, 200, 200)
        // Orientação inversa no anel interno (winding não-zero).
        val inner = rectangle(60, 60, 140, 140).asReversed()
        val points = VectorTatamiFillGenerator.generate(
            contours = listOf(outer, inner),
            rowSpacingUnits = 5,
            maxStitchLengthUnits = 30
        ).getOrThrow()

        assertTrue(points.none {
            it.command == StitchCommand.STITCH &&
                it.xUnits in 65..135 && it.yUnits in 65..135
        })
        assertTrue(points.count { it.command == StitchCommand.JUMP } > 1)
        // As trocas de lados passam com JUMP, não por STITCH cruzando o furo.
        var from: EmbroideryPoint? = null
        points.forEach { to ->
            val before = from
            if (to.command == StitchCommand.STITCH && before != null &&
                before.command == StitchCommand.STITCH) {
                val midpointX = (before.xUnits + to.xUnits) / 2
                val midpointY = (before.yUnits + to.yUnits) / 2
                assertFalse(midpointX in 65..135 && midpointY in 65..135)
            }
            from = to
        }
    }

    @Test
    fun concaveGeometryNeverStitchesAcrossCentralGap() {
        val uShape = listOf(
            0 to 0, 200 to 0, 200 to 200, 150 to 200,
            150 to 70, 50 to 70, 50 to 200, 0 to 200
        )
        val points = VectorTatamiFillGenerator.generate(
            contours = listOf(uShape), rowSpacingUnits = 5,
            maxStitchLengthUnits = 30
        ).getOrThrow()

        assertTrue(points.none { point ->
            point.command == StitchCommand.STITCH &&
                point.xUnits in 60..140 && point.yUnits in 90..190
        })
    }

    @Test
    fun degenerateAndExcessivelyComplexPolygonsAreRejected() {
        assertTrue(VectorTatamiFillGenerator.generate(
            listOf(listOf(0 to 0, 20 to 0, 40 to 0)), 5, 30
        ).isFailure)
        assertTrue(VectorTatamiFillGenerator.generate(
            listOf(rectangle(0, 0, 100, 100)), 1, 30
        ).isFailure)
        assertTrue(VectorTatamiFillGenerator.generate(
            listOf(rectangle(0, 0, 100, 100)), 5, 90
        ).isFailure)
    }

    @Test
    fun modeIsOptInAndLegacyRunningSatinRemainUsable() {
        val artwork = VectorArtwork(
            label = "Retangulo",
            paths = listOf(
                VectorPath(
                    points = rectangle(0, 0, 100, 80).map {
                        VectorPoint(it.first.toFloat(), it.second.toFloat())
                    },
                    closed = true
                )
            )
        )
        val running = VectorMatrixGenerator.generate(
            artwork, VectorMatrixOptions(widthMm = 50f, stitchStyle = TextStitchStyle.RUNNING)
        ).getOrThrow()
        val satin = VectorMatrixGenerator.generate(
            artwork, VectorMatrixOptions(widthMm = 50f, stitchStyle = TextStitchStyle.SATIN)
        ).getOrThrow()
        val tatami = VectorMatrixGenerator.generate(
            artwork,
            VectorMatrixOptions(
                widthMm = 50f, fillMode = VectorFillMode.TATAMI,
                tatamiRowSpacingMm = .5f, outputFormat = "PES"
            )
        ).getOrThrow()

        assertTrue(running.stitchCount > 0)
        assertTrue(satin.stitchCount > 0)
        assertTrue(tatami.stitchCount > running.stitchCount)
        assertTrue(tatami.bounds.widthMm <= 50.1f)
        assertTrue(tatami.bounds.heightMm <= 41f)
        assertTrue(tatami.endFound)
        assertTrue(HoopValidator.validate(tatami, HoopProfile.H100X100).fits)
        assertTrue(MatrixConverter.convert(tatami, "DST", "tatami").getOrThrow().bytes.isNotEmpty())
        assertTrue(MatrixConverter.convert(tatami, "JEF", "tatami").getOrThrow().bytes.isNotEmpty())
    }

    @Test
    fun fillRefusesOpenPathsRatherThanMisrepresentingDrawing() {
        val artwork = VectorArtwork(
            paths = listOf(
                VectorPath(
                    listOf(VectorPoint(0f, 0f), VectorPoint(100f, 80f)),
                    closed = false
                )
            )
        )
        val result = VectorMatrixGenerator.generate(
            artwork,
            VectorMatrixOptions(fillMode = VectorFillMode.TATAMI)
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("feche") == true)
    }
}
