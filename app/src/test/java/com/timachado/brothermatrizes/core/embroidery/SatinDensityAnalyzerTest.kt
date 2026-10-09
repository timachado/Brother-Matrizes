package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinDensityAnalyzerTest {
    private fun p(
        x: Int,
        y: Int,
        cmd: StitchCommand = StitchCommand.STITCH,
        color: Int = 0
    ) = EmbroideryPoint(x, y, cmd, color)

    @Test
    fun satinSpacingOfFourTenthsOfAMillimeterMustNotBeDeleted() {
        val points = listOf(
            p(0, 0, StitchCommand.JUMP),
            p(4, 0),
            p(8, 0),
            p(12, 0),
            p(16, 0)
        )
        val report = SatinDensityAnalyzer.analyze(points)
        assertEquals(SatinDensityAnalyzer.Status.NORMAL, report.status)
        assertEquals(5, report.originalPointCount)
        assertEquals(5, report.preservedPointCount)
        assertEquals(4, report.analyzedSegments)
        assertEquals(0, report.shortSegmentCount)
        assertEquals(0, report.repeatedSegmentCount)
        assertEquals(0.4f, report.meanStitchLengthMm, 0.01f)
        assertEquals(points, points.toList())
    }

    @Test
    fun reverseRepeatedSatinRailCausesHighOverlapWarningWithoutRemovingPoints() {
        val points = buildList {
            repeat(42) {
                add(p(10, 0, StitchCommand.JUMP))
                add(p(10, 25))
                add(p(10, 25, StitchCommand.JUMP))
                add(p(10, 0))
            }
        }
        val report = SatinDensityAnalyzer.analyze(points)
        assertEquals(SatinDensityAnalyzer.Status.SOBREPOSICAO_ELEVADA, report.status)
        assertTrue(report.repeatedSegmentCount >= 40)
        assertTrue(report.maxRepeatedInFiveMmCell >= 18)
        assertEquals(points.size, report.preservedPointCount)
        assertEquals(84, report.stitchCount)
    }

    @Test
    fun jumpColorAndTrimAreNotIncludedAsSewingLength() {
        val points = listOf(
            p(0, 0, StitchCommand.JUMP),
            p(40, 0), // quatro mm
            p(500, 0, StitchCommand.JUMP),
            p(510, 0), // um mm
            p(510, 0, StitchCommand.COLOR_CHANGE, 1),
            p(520, 0, StitchCommand.STITCH, 1),
            p(520, 0, StitchCommand.TRIM, 1),
            p(530, 0, StitchCommand.STITCH, 1),
            p(530, 0, StitchCommand.END, 1)
        )
        val report = SatinDensityAnalyzer.analyze(points)
        assertEquals(4, report.stitchCount)
        assertEquals(4, report.analyzedSegments)
        assertEquals(0, report.longSegmentCount)
        assertEquals(0, report.repeatedSegmentCount)
        assertEquals(1.75f, report.meanStitchLengthMm, 0.01f)
        assertEquals(points.size, report.preservedPointCount)
    }

    @Test
    fun noStitchesAndLongStitchAreReportedWithoutFabricatingJumps() {
        val empty = SatinDensityAnalyzer.analyze(listOf(p(0, 0, StitchCommand.END)))
        assertEquals(0, empty.stitchCount)
        assertEquals(0f, empty.meanStitchLengthMm, 0f)
        val points = listOf(p(0, 0, StitchCommand.JUMP), p(100, 0))
        val result = SatinDensityAnalyzer.analyze(points)
        assertEquals(1, result.longSegmentCount)
        assertEquals(points.size, result.preservedPointCount)
        assertEquals(SatinDensityAnalyzer.Status.NORMAL, result.status)
    }
}
