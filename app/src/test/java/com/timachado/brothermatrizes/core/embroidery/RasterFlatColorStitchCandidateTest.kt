package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterFlatColorStitchCandidateTest {
    private fun sample(
        width: Int = 160, height: Int = 100,
        pixels: (Int, Int) -> Int
    ): RasterImagePixels {
        val argb = IntArray(width * height) { index ->
            pixels(index % width, index / width)
        }
        return RasterImagePixels(width, height, argb, "QA trajetoria real")
    }
    private val orange = 0xffda761a.toInt()
    private val blue = 0xff193e6a.toInt()

    @Test fun simpleDisconnectedTransparentStrokesCanReachActualQaStitches() {
        val source = sample { x, y ->
            when {
                x in 15..95 && y in 23..31 -> orange
                x in 20..100 && y in 64..72 -> orange
                else -> 0
            }
        }
        val result = RasterFlatColorStitchCandidate.create(
            source, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH", result.design.format)
        assertEquals(0, result.sourceOutOfInkSamples)
        assertTrue(result.sampledSourceSegments > 10)
        assertTrue(result.design.stitchCount > 20)
        assertTrue(result.design.jumpCount >= 2)
        assertTrue(result.design.points.any { it.command == StitchCommand.TRIM })
        assertTrue(result.design.sourceBytes.isEmpty())
        assertFalse(result.canExportPes)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun twoFlatColorsMaintainActualColorChangeAndSeparateStrokes() {
        val source = sample { x, y ->
            when {
                x in 15..95 && y in 23..31 -> orange
                x in 20..100 && y in 64..72 -> blue
                else -> 0
            }
        }
        val review = RasterFlatColorStitchCandidate.create(
            source, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals(2, review.design.threadColors.size)
        assertEquals(1, review.design.colorChanges)
        assertEquals(1, review.design.points.count {
            it.command == StitchCommand.COLOR_CHANGE
        })
        assertEquals(0, review.sourceOutOfInkSamples)
        assertFalse(review.canExportPes)
    }

    @Test fun texturedEmbroideryPhotographIsNotAllowedToBypassManualReview() {
        val source = sample { x, y ->
            if (x in 20..115 && y in 22..76) {
                // A field of machine-thread shine/shadow shades, not one ink.
                val r = 105 + x % 100
                val g = 35 + (x * 2 + y) % 75
                val b = 10 + y % 60
                (255 shl 24) or (r shl 16) or (g shl 8) or b
            } else 0
        }
        assertTrue(RasterFlatColorStitchCandidate.create(
            source, 90f, HoopProfile.H130X180
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun opaquePhotoWithoutTransparentBackgroundMustNotBeDigitalized() {
        val source = sample { x,y ->
            if (x in 20..115 && y in 22..76) orange
            else 0xffffffff.toInt()
        }
        assertTrue(RasterFlatColorStitchCandidate.create(
            source, 90f, HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun croppedGeometryMustFitChosenHoop() {
        val source = sample { x,y ->
            if (x in 15..95 && y in 23..31) orange else 0
        }
        assertTrue(RasterFlatColorStitchCandidate.create(
            source, 190f, HoopProfile.H100X100
        ).isFailure)
    }

    @Test fun evenValidQaPathCannotExportEmbroideryPES() {
        val source = sample { x,y ->
            if (x in 15..95 && y in 23..31) orange else 0
        }
        val result = RasterFlatColorStitchCandidate.create(
            source, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(result.design.sourceBytes.isEmpty())
        assertEquals("QA_AUTO_STITCH", result.design.format)
        assertFalse(result.canExportPes)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
