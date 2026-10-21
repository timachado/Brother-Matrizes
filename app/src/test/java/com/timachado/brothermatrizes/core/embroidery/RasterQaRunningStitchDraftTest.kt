package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterQaRunningStitchDraftTest {
    private fun ring(): RasterImagePixels {
        val w = 12
        val h = 12
        val pixels = IntArray(w * h)
        for (y in 2..9) for (x in 2..9) {
            if (x in 5..6 && y in 5..6) continue
            pixels[y * w + x] = 0xffb35624.toInt()
        }
        return RasterImagePixels(w, h, pixels, "synthetic ring")
    }

    @Test fun actualQaCommandsPreserveCounterAndSeparateAllContoursByTrimAndJump() {
        val source = ring()
        val initial = source.argb.copyOf()
        val result = RasterQaRunningStitchDraft.create(
            source, 120f, HoopProfile.H130X180, maxWaypointSpacingMm = 2f
        ).getOrThrow()
        val design = result.design
        assertEquals("QA_RUNNING_REVIEW", design.format)
        assertEquals(2, result.outlineCount)
        assertEquals(1, result.counterCount)
        assertEquals(2, design.jumpCount)
        assertEquals(1, design.points.count { it.command == StitchCommand.TRIM })
        assertTrue(design.stitchCount > 20)
        assertTrue(result.needleRisk.stitchesExamined == design.stitchCount)
        assertEquals(design.stitchCount, result.threadDensity.sewnSegments)
        assertTrue(result.threadDensity.cellsObserved > 0)
        assertTrue(result.threadDensity.peakThreadLengthMmPerCell <= 20.0)
        assertTrue(design.points.first().command == StitchCommand.JUMP)
        assertTrue(design.points.last().command == StitchCommand.END)
        assertTrue(design.points.zipWithNext().any { (a,b) ->
            a.command == StitchCommand.TRIM && b.command == StitchCommand.JUMP
        })
        assertTrue(design.sourceBytes.isEmpty())
        assertFalse(result.exportPermitted)
        assertTrue(result.needsOperatorReview)
        assertTrue(result.needsMachineStitchOut)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(source.argb.contentEquals(initial))
    }

    @Test fun concaveCornersNeverBecomeUnintendedDiagonalStitches() {
        val colors = IntArray(12 * 12)
        for (y in 2..9) for (x in 2..4) colors[y * 12 + x] = 0xffb35624.toInt()
        for (y in 6..9) for (x in 2..9) colors[y * 12 + x] = 0xffb35624.toInt()
        val review = RasterQaRunningStitchDraft.create(
            RasterImagePixels(12, 12, colors, "L"),
            120f, HoopProfile.H130X180, maxWaypointSpacingMm = 2f
        ).getOrThrow()
        val design = review.design
        var previous: EmbroideryPoint? = null
        for (point in design.points) {
            when (point.command) {
                StitchCommand.JUMP -> previous = point
                StitchCommand.STITCH -> {
                    val a = previous ?: error("No preceding positioning")
                    assertTrue("Corte diagonal entre cantos do raster",
                        a.xUnits == point.xUnits || a.yUnits == point.yUnits)
                    previous = point
                }
                StitchCommand.TRIM -> previous = null
                StitchCommand.END -> Unit
                else -> error("Unsupported QA command")
            }
        }
        assertFalse(review.exportPermitted)
    }

    @Test fun invalidHoopAndAmbiguousDiagonalContactFailClosed() {
        val source = ring()
        assertTrue(RasterQaRunningStitchDraft.create(
            source, 120f, HoopProfile.H100X100
        ).isFailure)
        assertTrue(RasterQaRunningStitchDraft.create(
            source, 120f, HoopProfile.H130X180, threshold = 255
        ).isFailure)
        val diagonal = IntArray(12 * 12)
        diagonal[2 * 12 + 2] = 0xffb35624.toInt()
        diagonal[3 * 12 + 3] = 0xffb35624.toInt()
        assertTrue(RasterQaRunningStitchDraft.create(
            RasterImagePixels(12,12,diagonal,"ambiguous"),
            120f, HoopProfile.H130X180
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun opaquePhotographCannotMasqueradeAsAnEmbroideryOutline() {
        val opaque = RasterImagePixels(
            12, 12,
            IntArray(12 * 12) { 0xff777777.toInt() },
            "fotografia sem recorte"
        )
        assertTrue(RasterQaRunningStitchDraft.create(
            opaque, 120f, HoopProfile.H130X180
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun qaStitchPreviewCannotBePassedDirectlyToReleasedPesConverter() {
        val preview = RasterQaRunningStitchDraft.create(
            ring(), 120f, HoopProfile.H130X180
        ).getOrThrow().design
        assertTrue(MatrixConverter.convert(
            preview, "PES", "should-not-export"
        ).isFailure)
        assertTrue(preview.sourceBytes.isEmpty())
    }
}
