package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterClosedRunningPreviewTest {
    private val navy = 0xff193b56.toInt()
    private val gold = 0xffcc963f.toInt()
    private val white = 0xffffffff.toInt()
    private val hoop = HoopProfile.H130X180

    private fun picture(
        gap:Boolean=false,
        branch:Boolean=false,
        thick:Boolean=false,
        otherColor:Boolean=false,
        secondLoop:Boolean=false
    ): RasterImagePixels {
        val width=160
        val height=110
        val px=IntArray(width*height) { i ->
            val x=i%width
            val y=i/width
            when {
                otherColor && x in 28..115 && y in 87..95 -> gold
                secondLoop && (
                    ((x==130 || x==143) && y in 30..60) ||
                    ((y==30 || y==60) && x in 130..143)
                ) -> navy
                branch && x==55 && y in 12..20 -> navy
                thick && (
                    (x in 20..21 || x in 109..110) && y in 20..75 ||
                    (y in 20..21 || y in 74..75) && x in 20..110
                ) -> navy
                !thick && !gap &&
                    ((x==20 || x==110) && y in 20..75 ||
                    (y==20 || y==75) && x in 20..110) -> navy
                !thick && gap &&
                    ((x==20 || x==110) && y in 20..75 ||
                    (y==20 || y==75) && x in 20..110 &&
                        !(y==20 && x==65)) -> navy
                else -> white
            }
        }
        return RasterImagePixels(width,height,px,"letra O contorno fino QA")
    }

    private fun mainPart(image:RasterImagePixels): RasterSatinObject =
        RasterSatinObjectEditor.detect(image).getOrThrow()
            .first { it.mask.minX == 20 }

    @Test fun closedOHasOneJumpOneRealNeedleDownClosingStitch() {
        val img=picture()
        val part=mainPart(img)
        val result=RasterClosedRunningPreview.create(
            img,part,60f,hoop
        ).getOrThrow()
        assertEquals("QA_RUNNING_REVIEW",result.format)
        assertEquals(1,result.jumpCount)
        assertEquals(0,result.colorChanges)
        assertEquals(StitchCommand.JUMP,result.points.first().command)
        assertEquals(StitchCommand.END,result.points.last().command)
        val first=result.points.first()
        val last=result.points[result.points.lastIndex-1]
        assertEquals(first.xUnits,last.xUnits)
        assertEquals(first.yUnits,last.yUnits)
        assertEquals(StitchCommand.STITCH,last.command)
        assertTrue(result.stitchCount>20)
        RasterNeedleRiskAudit.inspect(result,hoop).getOrThrow()
        RasterSewnMaskGate.inspect(img,part,result,60f).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            img,part,result,60f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>=.72)
        assertTrue("outside=${fidelity.offInkFraction}",
            fidelity.offInkFraction<=.15)
        assertTrue(result.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun completeAutomaticOOnlyRemainsQaAndDoesNotFillCounter() {
        val img=picture()
        val design=RasterAutomaticLogoPreview.create(
            img,60f,hoop
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",design.format)
        assertTrue(design.stitchCount>20)
        assertEquals(1,design.jumpCount)
        assertTrue(design.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun disconnectedBrokenRingIsNotClosed() {
        val img=picture(gap=true)
        assertTrue(RasterClosedRunningPreview.create(
            img,mainPart(img),60f,hoop
        ).isFailure)
    }

    @Test fun spurAndJunctionAreNotOneUnbranchedRing() {
        val img=picture(branch=true)
        assertTrue(RasterClosedRunningPreview.create(
            img,mainPart(img),60f,hoop
        ).isFailure)
    }

    @Test fun thickStrokeNeedsSatinOrOtherVerifiedTechnique() {
        val img=picture(thick=true)
        assertTrue(RasterClosedRunningPreview.create(
            img,mainPart(img),60f,hoop
        ).isFailure)
    }

    @Test fun multicoreArtworkPreservesColorChange() {
        val img=picture(otherColor=true)
        val design=RasterAutomaticLogoPreview.create(
            img,60f,hoop
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",design.format)
        assertEquals(2,design.threadColors.size)
        assertEquals(1,design.colorChanges)
        assertTrue(design.points.any {
            it.command==StitchCommand.COLOR_CHANGE
        })
        assertTrue(design.sourceBytes.isEmpty())
    }
}
