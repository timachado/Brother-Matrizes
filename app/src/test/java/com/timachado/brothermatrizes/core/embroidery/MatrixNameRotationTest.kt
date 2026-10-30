package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixNameRotationTest {
    private fun sample(): EmbroideryDesign {
        val points = listOf(
            EmbroideryPoint(-250, -150, StitchCommand.JUMP, 0),
            EmbroideryPoint(250, -150, StitchCommand.STITCH, 0),
            EmbroideryPoint(250, 150, StitchCommand.STITCH, 0),
            EmbroideryPoint(-250, 150, StitchCommand.STITCH, 1),
            EmbroideryPoint(-250, -150, StitchCommand.END, 1)
        )
        return EmbroideryDesign(
            fileName = "brasao-original.pes",
            format = "PES",
            label = "Brasão",
            points = points,
            bounds = EmbroideryBounds(-250, 250, -150, 150),
            stitchCount = 3,
            jumpCount = 1,
            colorChanges = 0,
            endFound = true,
            sourceBytes = byteArrayOf(1, 2, 3),
            guidePoints = listOf(EmbroideryPoint(250, -150, StitchCommand.STITCH, 0)),
            threadColors = listOf(0x111111, 0xAAAAAA)
        )
    }

    @Test fun ninetyDegreeRotationTransformsRealStitchesAndNameTogether() {
        val original = sample()
        val actual = MatrixNameRotation.rotate(
            original, 100, 0, 15f, 90f, HoopProfile.H100X100
        ).getOrThrow()
        assertEquals(30f, actual.background.bounds.widthMm, 0.11f)
        assertEquals(50f, actual.background.bounds.heightMm, 0.11f)
        assertEquals(0, actual.nameXUnits)
        assertEquals(100, actual.nameYUnits)
        assertEquals(105f, actual.nameRotationDegrees, 0.01f)
        assertEquals(original.points.map { it.command },
            actual.background.points.map { it.command })
        assertEquals(original.points.map { it.colorIndex },
            actual.background.points.map { it.colorIndex })
        assertEquals(150, actual.background.guidePoints.single().xUnits)
        assertEquals(250, actual.background.guidePoints.single().yUnits)
        assertTrue(actual.background.sourceBytes.isEmpty())
        assertEquals(original.points.first().xUnits, -250)
        assertFalse(actual.background.fileName == original.fileName)
    }

    @Test fun ninetyDegreeFourTimesReturnsToOriginalGeometry() {
        val source = sample()
        var state = MatrixNameRotation.Rotation(source, 100, 0, 0f)
        repeat(4) {
            state = MatrixNameRotation.rotate(state.background,
                state.nameXUnits, state.nameYUnits,
                state.nameRotationDegrees, 90f, HoopProfile.H100X100
            ).getOrThrow()
        }
        assertEquals(source.points, state.background.points)
        assertEquals(100, state.nameXUnits)
        assertEquals(0, state.nameYUnits)
        assertEquals(0f, state.nameRotationDegrees, 0.01f)
    }

    @Test fun failsClosedIfRotationExceedsUsableHoopSize() {
        val source = sample()
        // 98 mm não cabe no bastidor 100x100 com 5 mm de margem por lado.
        val tooLarge = source.copy(
            bounds = EmbroideryBounds(-490, 490, -100, 100),
            points = listOf(
                EmbroideryPoint(-490, -100, StitchCommand.JUMP, 0),
                EmbroideryPoint(490, -100, StitchCommand.STITCH, 0),
                EmbroideryPoint(490, 100, StitchCommand.STITCH, 0),
                EmbroideryPoint(490, 100, StitchCommand.END, 0)
            )
        )
        assertTrue(MatrixNameRotation.rotate(
            tooLarge, 0, 0, 0f, 90f, HoopProfile.H100X100
        ).isFailure)
    }

    @Test fun normalizesAnglesForRotatingMatrixAndName() {
        assertEquals(-90f, MatrixNameRotation.normalizeDegrees(270f))
        assertEquals(180f, MatrixNameRotation.normalizeDegrees(180f))
        assertEquals(-165f, MatrixNameRotation.normalizeDegrees(195f))
        assertEquals(0f, MatrixNameRotation.normalizeDegrees(360f))
    }

    @Test fun letterResizePreservesCenterStitchCommandsAndGuides() {
        val original = sample().copy(hoopProfile = HoopProfile.H130X180,
            hoopOrientation = HoopOrientation.LANDSCAPE)
        val smaller = MatrixNameRotation.resizeLetter(
            original, 0.85f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(42.5f, smaller.bounds.widthMm, .12f)
        assertEquals(25.5f, smaller.bounds.heightMm, .12f)
        assertEquals(original.points.map { it.command },
            smaller.points.map { it.command })
        assertEquals(original.points.map { it.colorIndex },
            smaller.points.map { it.colorIndex })
        assertEquals(213, smaller.guidePoints.single().xUnits)
        assertEquals(-128, smaller.guidePoints.single().yUnits)
        assertTrue(smaller.isModified)
        assertTrue(smaller.sourceBytes.isEmpty())
        assertEquals(HoopOrientation.LANDSCAPE, smaller.hoopOrientation)
        assertEquals(original.points.first().xUnits, -250)
    }

    @Test fun letterResizeRejectsUnsafeHoopAndInvalidScale() {
        assertTrue(MatrixNameRotation.resizeLetter(sample(), Float.NaN,
            HoopProfile.H100X100).isFailure)
        assertTrue(MatrixNameRotation.resizeLetter(sample(), 0.5f,
            HoopProfile.H100X100).isFailure)
        val oversized = sample().copy(
            bounds = EmbroideryBounds(-480, 480, -400, 400),
            points = listOf(
                EmbroideryPoint(-480,-400, StitchCommand.JUMP,0),
                EmbroideryPoint(480,-400, StitchCommand.STITCH,0),
                EmbroideryPoint(480,400, StitchCommand.STITCH,0),
                EmbroideryPoint(480,400, StitchCommand.END,0)
            )
        )
        assertTrue(MatrixNameRotation.resizeLetter(oversized, 1.10f,
            HoopProfile.H100X100).isFailure)
    }


}
