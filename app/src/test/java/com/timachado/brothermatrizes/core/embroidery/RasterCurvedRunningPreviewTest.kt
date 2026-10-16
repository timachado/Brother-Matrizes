package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterCurvedRunningPreviewTest {
    private val blue=0xff193956.toInt()
    private val gold=0xffca963b.toInt()
    private val white=0xffffffff.toInt()
    private val hoop=HoopProfile.H130X180

    // A 1-pixel U-line bends twice and cannot be represented by a
    // single-valued horizontal/vertical cross-section scan.
    private fun picture(
        extraBranch:Boolean=false,
        closed:Boolean=false,
        thick:Boolean=false,
        secondColor:Boolean=false
    ):RasterImagePixels {
        val w=160
        val h=100
        return RasterImagePixels(w,h,IntArray(w*h) { i ->
            val x=i%w
            val y=i/w
            when {
                secondColor && x in 31..97 && y in 83..90 -> gold
                extraBranch && x==60 && y in 21..30 -> blue
                closed && x in 20..110 && y==76 -> blue
                thick && ((x in 20..21 && y in 20..75) ||
                    (x in 109..110 && y in 20..75) ||
                    (x in 20..110 && y in 20..21)) -> blue
                !thick && ((x==20 || x==110) && y in 20..75 ||
                    (y==20 && x in 20..110)) -> blue
                else -> white
            }
        },"U-line curved running QA")
    }

    private fun bluePart(source:RasterImagePixels):RasterSatinObject =
        // Palette clustering may return a color equivalent to, but not
        // bit-for-bit identical to, the source pixel RGB.
        RasterSatinObjectEditor.detect(source).getOrThrow()
            .first { it.mask.minY==20 }

    @Test fun onePixelCurvedUProducesRealNeedleDownStitches() {
        val image=picture()
        val part=bluePart(image)
        val design=RasterCurvedRunningPreview.create(
            image,part,60f,hoop
        ).getOrThrow()
        assertEquals("QA_RUNNING_REVIEW",design.format)
        assertEquals(StitchCommand.JUMP,design.points.first().command)
        assertEquals(StitchCommand.END,design.points.last().command)
        assertTrue(design.stitchCount>20)
        assertEquals(1,design.jumpCount)
        assertTrue(design.sourceBytes.isEmpty())
        RasterNeedleRiskAudit.inspect(design,hoop).getOrThrow()
        RasterSewnMaskGate.inspect(image,part,design,60f).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            image,part,design,60f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>=.72)
        assertTrue("offInk=${fidelity.offInkFraction}",
            fidelity.offInkFraction<=.15)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun curvedUCanEnterFullAutomaticPreviewButNotPES() {
        val image=picture()
        val design=RasterAutomaticLogoPreview.create(
            image,60f,hoop
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",design.format)
        assertTrue(design.stitchCount>20)
        assertTrue(design.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun aBranchMustNotBeMisreadAsAnOpenCurve() {
        val image=picture(extraBranch=true)
        assertTrue(RasterCurvedRunningPreview.create(
            image,bluePart(image),60f,hoop
        ).isFailure)
    }

    @Test fun closedLetterCounterIsNotAnOpenRunningCurve() {
        val image=picture(closed=true)
        assertTrue(RasterCurvedRunningPreview.create(
            image,bluePart(image),60f,hoop
        ).isFailure)
    }

    @Test fun thickStrokeMustUseAnotherTechniqueOrManualReview() {
        val image=picture(thick=true)
        assertTrue(RasterCurvedRunningPreview.create(
            image,bluePart(image),60f,hoop
        ).isFailure)
    }

    @Test fun compoundLogoKeepsCurvedLineAndSeparateColor() {
        val image=picture(secondColor=true)
        val result=RasterAutomaticLogoPreview.create(
            image,60f,hoop
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",result.format)
        assertTrue(result.colorChanges>=1)
        assertTrue(result.points.any {
            it.command==StitchCommand.COLOR_CHANGE
        })
        assertEquals(2,result.threadColors.size)
        assertTrue(result.sourceBytes.isEmpty())
    }
}
