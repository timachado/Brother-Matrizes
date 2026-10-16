package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterBranchedRunningPreviewTest {
    private val navy=0xff1c3b58.toInt()
    private val gold=0xffcb963c.toInt()
    private val white=0xffffffff.toInt()
    private val hoop=HoopProfile.H130X180

    private fun artwork(
        secondJunction:Boolean=false,
        closeLoop:Boolean=false,
        thick:Boolean=false,
        secondColor:Boolean=false,
        shortStem:Boolean=false
    ):RasterImagePixels {
        val width=160
        val height=110
        return RasterImagePixels(width,height,IntArray(width*height) { i ->
            val x=i%width
            val y=i/width
            when {
                secondColor && x in 23..120 && y in 87..96 -> gold
                // Additional downward stem turns a T into a cross (degree4).
                secondJunction && x==60 && y in 46..68 -> navy
                // Bottom connector creates a loop with existing horizontal
                // and downward branches; not an unbranched 3-way tree.
                closeLoop && ((x==20 || x==100) && y in 46..69 ||
                    y==69 && x in 20..100) -> navy
                thick && x in 20..100 && y in 45..46 -> navy
                thick && x in 60..61 && y in 20..45 -> navy
                !thick && x in 20..100 && y==45 -> navy
                !thick && x==60 && y in (if(shortStem) 42 else 20)..45 -> navy
                else -> white
            }
        },"ornamento triplo de ponto corrido")
    }
    private fun component(img:RasterImagePixels):RasterSatinObject =
        RasterSatinObjectEditor.detect(img).getOrThrow()
            .first { it.mask.minY<=45 && it.mask.maxY>=45 }

    @Test fun threeWayJunctionSplitsIntoTwoSewnSections() {
        val img=artwork()
        val part=component(img)
        val draft=RasterBranchedRunningPreview.create(
            img,part,60f,hoop
        ).getOrThrow()
        assertEquals("QA_RUNNING_REVIEW",draft.format)
        assertEquals(2,draft.jumpCount)
        assertEquals(1,draft.points.count {
            it.command==StitchCommand.TRIM
        })
        assertEquals(StitchCommand.JUMP,draft.points.first().command)
        assertEquals(StitchCommand.END,draft.points.last().command)
        assertTrue(draft.stitchCount>=20)
        RasterSewnMaskGate.inspect(img,part,draft,60f).getOrThrow()
        RasterNeedleRiskAudit.inspect(draft,hoop).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            img,part,draft,60f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>=.72)
        assertTrue("outside=${fidelity.offInkFraction}",
            fidelity.offInkFraction<=.15)
        assertTrue(draft.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun composerIncludesBranchedRunningWithoutFakeSatin() {
        val img=artwork()
        val draft=RasterAutomaticLogoPreview.create(
            img,60f,hoop
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",draft.format)
        assertEquals(2,draft.jumpCount)
        assertEquals(1,draft.points.count {
            it.command==StitchCommand.TRIM
        })
        assertTrue(draft.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun fourWayCrossIsNotOneSafeThreeWaySection() {
        val img=artwork(secondJunction=true)
        assertTrue(RasterBranchedRunningPreview.create(
            img,component(img),60f,hoop
        ).isFailure)
    }

    @Test fun shortThirdArmRequiresManualReview() {
        val img=artwork(shortStem=true)
        assertTrue(RasterBranchedRunningPreview.create(
            img,component(img),60f,hoop
        ).isFailure)
    }

    @Test fun thickStrokesAreNotOnePixelRunningLines() {
        val img=artwork(thick=true)
        assertTrue(RasterBranchedRunningPreview.create(
            img,component(img),60f,hoop
        ).isFailure)
    }

    @Test fun loopOrCounterCannotBeInterpretedAsThreeWayTree() {
        val img=artwork(closeLoop=true)
        assertTrue(RasterBranchedRunningPreview.create(
            img,component(img),60f,hoop
        ).isFailure)
    }

    @Test fun twoColorLogoPreservesCutJumpAndExplicitColorChange() {
        val img=artwork(secondColor=true)
        val draft=RasterAutomaticLogoPreview.create(
            img,60f,hoop
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",draft.format)
        assertEquals(2,draft.threadColors.size)
        assertEquals(1,draft.colorChanges)
        assertTrue(draft.points.any {
            it.command==StitchCommand.COLOR_CHANGE
        })
        assertTrue(draft.points.count {
            it.command==StitchCommand.TRIM
        }>=2)
        assertTrue(draft.sourceBytes.isEmpty())
    }
}
