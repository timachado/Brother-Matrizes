package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterRunningOutlinePreviewTest {
    private val navy = 0xff163851.toInt()
    private val gold = 0xffc89235.toInt()
    private val white = 0xffffffff.toInt()
    private fun picture(paint: (Int, Int) -> Int): RasterImagePixels {
        val w = 160
        val h = 100
        val data = IntArray(w*h) { pos ->
            paint(pos % w, pos / w)
        }
        return RasterImagePixels(w,h,data,"ponto corrido 2D QA")
    }

    @Test fun lineCreatesOnlyRealRunningStitchesWithSafeLengths() {
        val art = picture { x,y ->
            if (x in 20..119 && y in 43..45) navy else white
        }
        val component = RasterSatinObjectEditor.detect(art).getOrThrow().single()
        val result = RasterRunningOutlinePreview.create(
            art,component,25f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_RUNNING_REVIEW", result.format)
        assertTrue(result.sourceBytes.isEmpty())
        assertEquals(StitchCommand.JUMP,result.points.first().command)
        assertEquals(StitchCommand.END,result.points.last().command)
        assertTrue(result.stitchCount >= 3)
        assertEquals(0,result.colorChanges)
        val quality = RasterStitchFidelityAudit.compare(
            art,component,result,25f
        ).getOrThrow()
        assertTrue("coverage=${quality.coverage}",quality.coverage>=.72)
        assertTrue("offInk=${quality.offInkFraction}",
            quality.offInkFraction<=.15)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun verticalThinLineAlsoCreatesRealStitches() {
        val art = picture { x,y ->
            if (x in 60..62 && y in 12..86) navy else white
        }
        val comp = RasterSatinObjectEditor.detect(art).getOrThrow().single()
        val result = RasterRunningOutlinePreview.create(
            art,comp,25f,HoopProfile.H130X180
        ).getOrThrow()
        assertTrue(result.stitchCount>=3)
        val quality = RasterStitchFidelityAudit.compare(
            art,comp,result,25f
        ).getOrThrow()
        assertTrue(quality.coverage>=.72)
    }

    @Test fun forkMustNotBeSilentlyConvertedIntoOneRunningLine() {
        val art = picture { x,y ->
            if ((x in 20..120 && y in 43..45) ||
                (x in 60..62 && y in 44..57)) navy else white
        }
        val comp = RasterSatinObjectEditor.detect(art).getOrThrow().single()
        assertTrue(
            RasterRunningOutlinePreview.create(
                art,comp,25f,HoopProfile.H130X180
            ).isFailure
        )
    }

    @Test fun autoComposerHandlesSatinAndRunningWithColorChange() {
        val art = picture { x,y ->
            when {
                x in 20..119 && y in 12..23 -> navy
                x in 25..121 && y in 64..66 -> gold
                else -> white
            }
        }
        val result = RasterAutomaticLogoPreview.create(
            art,25f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",result.format)
        assertEquals(1,result.colorChanges)
        assertEquals(2,result.threadColors.size)
        assertTrue(result.points.any { it.command==StitchCommand.COLOR_CHANGE })
        assertTrue(result.points.count { it.command==StitchCommand.TRIM }>=1)
        assertTrue(result.sourceBytes.isEmpty())
    }
}
