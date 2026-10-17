package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterThreadTextureStudyTest {
    private fun image(
        width: Int = 80, height: Int = 80,
        pixel: (Int, Int) -> Int
    ): RasterImagePixels {
        val argb = IntArray(width * height) { index ->
            pixel(index % width, index / width)
        }
        return RasterImagePixels(width, height, argb, "Logotipo ilustrado QA")
    }

    @Test fun coloredEmblemAndWhiteLettersFlattenWithoutPaintingCounterHoles() {
        val source = image { x, y ->
            when {
                x in 11..31 && y in 10..40 &&
                    !(x in 16..26 && y in 17..33) ->
                    if ((x + y) % 2 == 0) 0xffed761b.toInt()
                    else 0xffa84b11.toInt()
                x in 43..69 && y in 50..64 ->
                    if ((x + y) % 3 == 0) 0xffc7c7c7.toInt()
                    else 0xffeeeeee.toInt()
                else -> 0x00000000
            }
        }
        val study = RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertEquals(2, study.suggestedColorFamilies)
        assertTrue(study.originalToneBuckets >= 3)
        assertTrue(study.originalAlphaHolesPreserved)
        assertEquals(0, study.flatPreview.argb[22 * 80 + 20])
        assertEquals(0, study.flatPreview.argb[5 * 80 + 5])
        for (index in source.argb.indices) {
            assertEquals(
                source.argb[index] ushr 24 >= 180,
                study.flatPreview.argb[index] ushr 24 >= 180
            )
        }
        assertTrue(study.reviewRequired)
        assertFalse(study.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun fourPixelDifferentColorAccentRemainsVisibleForReview() {
        val source = image { x, y ->
            when {
                x in 10..23 && y in 10..23 -> 0xffdd7719.toInt()
                x in 40..41 && y in 50..51 -> 0xff287cc0.toInt()
                else -> 0
            }
        }
        val study = RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertEquals(2, study.suggestedColorFamilies)
        assertTrue(study.flatPreview.argb[50 * 80 + 40] ushr 24 >= 180)
        assertTrue(study.flatPreview.argb[50 * 80 + 41] !=
            study.flatPreview.argb[15 * 80 + 15])
        assertFalse(study.exportPermitted)
    }

    @Test fun opaqueJpegMustNotPretendBackgroundWasRemoved() {
        val source = image { x, y ->
            if (x in 10..35 && y in 10..35) 0xffed7819.toInt()
            else 0xffffffff.toInt()
        }
        assertTrue(RasterThreadTextureStudy.inspect(source).isFailure)
    }

    @Test fun blankTransparentPictureIsRejected() {
        val source = image { _, _ -> 0 }
        assertTrue(RasterThreadTextureStudy.inspect(source).isFailure)
    }

    @Test fun tooMuchOpaqueForegroundRequiresManualReview() {
        val source = image { x, y ->
            if (x == 0 && y == 0) 0
            else 0xffeeeeee.toInt()
        }
        assertTrue(RasterThreadTextureStudy.inspect(source).isFailure)
    }

    @Test fun noEmbroideryCommandsOrPesBytesAreReturned() {
        val source = image { x, y ->
            if (x in 15..42 && y in 21..49) 0xffeeeeee.toInt()
            else 0x00000000
        }
        val result = RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertEquals(source.width, result.flatPreview.width)
        assertEquals(source.height, result.flatPreview.height)
        assertTrue(result.message().contains("NÃO é uma matriz PES"))
        assertFalse(result.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
