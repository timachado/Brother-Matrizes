package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterTatamiShapePreviewTest {
    private val navy = 0xff1c405e.toInt()
    private val gold = 0xffcb943c.toInt()
    private val white = 0xffffffff.toInt()

    private fun picture(f: (Int, Int)->Int): RasterImagePixels {
        val w=180;val h=130
        return RasterImagePixels(w,h,IntArray(w*h) { i ->
            f(i%w,i/w)
        },"imagem com contraformas")
    }
    private fun part(img: RasterImagePixels) =
        RasterSatinObjectEditor.detect(img).getOrThrow().single()

    @Test fun enclosedCounterUsesTrimJumpInsteadOfSewingAcrossItsHole() {
        val img = picture { x,y ->
            if (x in 40..129 && y in 30..97 &&
                !(x in 70..99 && y in 52..75)) navy else white
        }
        val component = part(img)
        val design = RasterTatamiShapePreview.create(
            img,component,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_TATAMI_REVIEW",design.format)
        assertTrue(design.stitchCount>100)
        assertTrue(design.points.count {
            it.command == StitchCommand.TRIM
        } > 0)
        assertTrue(design.points.count {
            it.command == StitchCommand.JUMP
        } > 1)
        val scale = 90.0/img.width
        fun coordinate(p: EmbroideryPoint) = Pair(
            p.xUnits/10.0/scale+img.width/2.0,
            img.height/2.0-p.yUnits/10.0/scale
        )
        var last: EmbroideryPoint? = null
        for (p in design.points) {
            when (p.command) {
                StitchCommand.JUMP -> last=p
                StitchCommand.STITCH -> {
                    val a=last ?: error("STITCH without a JUMP")
                    val (ax,ay)=coordinate(a)
                    val (bx,by)=coordinate(p)
                    val steps=ceil(hypot(bx-ax,by-ay)*5).toInt().coerceAtLeast(1)
                    for (n in 0..steps) {
                        val t=n.toDouble()/steps
                        val x=floor(ax+(bx-ax)*t).toInt()
                        val y=floor(ay+(by-ay)*t).toInt()
                        assertFalse("Stitch crossed hole at $x,$y",
                            x in 70..99 && y in 52..75)
                    }
                    last=p
                }
                StitchCommand.TRIM,StitchCommand.COLOR_CHANGE,
                StitchCommand.STOP,StitchCommand.END,
                StitchCommand.SEQUIN -> last=null
            }
        }
        val fidelity=RasterStitchFidelityAudit.compare(
            img,component,design,90f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>=.76)
        assertTrue("outside=${fidelity.offInkFraction}",
            fidelity.offInkFraction<=.10)
        RasterNeedleRiskAudit.inspect(design,HoopProfile.H130X180)
            .getOrThrow()
        val density = RasterThreadDensityPreflight.inspect(design).getOrThrow()
        assertEquals(design.stitchCount,density.sewnSegments)
        assertTrue(density.cellsObserved>0)
        assertTrue(design.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun stepShapedInkHasRealPointsWithoutCrossingBackground() {
        val img=picture { x,y ->
            if (x in 40..129 && y in 30..97 &&
                (y<63 || x<=106)) navy else white
        }
        val component=part(img)
        val result=RasterTatamiShapePreview.create(
            img,component,90f,HoopProfile.H130X180
        ).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            img,component,result,90f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>=.76)
        assertTrue("outside=${fidelity.offInkFraction}",
            fidelity.offInkFraction<=.10)
    }

    @Test fun narrowSinglePixelNeckFailsClosed() {
        val img=picture { x,y ->
            if (x in 40..129 && y in 30..97 &&
                (y !in 62..66 || x in 83..84)) navy else white
        }
        assertTrue(RasterTatamiShapePreview.create(
            img,part(img),90f,HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun composerAcceptsStitchesFromHoleWithoutMachineExport() {
        val img=picture { x,y ->
            when {
                x in 40..129 && y in 30..97 &&
                    !(x in 70..99 && y in 52..75) -> gold
                x in 20..111 && y in 10..19 -> navy
                else -> white
            }
        }
        val result=RasterAutomaticLogoPreview.create(
            img,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",result.format)
        assertTrue(result.points.any {
            it.command==StitchCommand.COLOR_CHANGE
        })
        assertTrue(result.points.count {
            it.command==StitchCommand.TRIM
        }>=2)
        assertTrue(result.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
    @Test fun tamperedMaskCannotBeUsedAsIfItWereTheImportedSource() {
        val img=picture { x,y ->
            if (x in 40..129 && y in 30..97 &&
                !(x in 70..99 && y in 52..75)) navy else white
        }
        val valid=part(img)
        // Invent foreground inside the original counter while preserving
        // the same dimensions and metadata to bypass simple size checks.
        val changedPixels=valid.mask.pixels.copyOf()
        changedPixels[60*img.width+80]=0
        val forged=valid.copy(mask=valid.mask.copy(pixels=changedPixels))
        assertTrue(RasterTatamiShapePreview.create(
            img,forged,90f,HoopProfile.H130X180
        ).isFailure)
        // The source and its genuine segmentation must still work.
        assertTrue(RasterTatamiShapePreview.create(
            img,valid,90f,HoopProfile.H130X180
        ).isSuccess)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun changingPhotoAfterComponentDetectionInvalidatesTatamiDraft() {
        val img=picture { x,y ->
            if (x in 40..129 && y in 30..97) navy else white
        }
        val stale=part(img)
        val changed=img.copy(argb=img.argb.copyOf().apply {
            this[50*img.width+54]=white
        })
        assertTrue(RasterTatamiShapePreview.create(
            changed,stale,90f,HoopProfile.H130X180
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }


}
