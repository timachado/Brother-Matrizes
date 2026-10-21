package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RasterTatamiRectanglePreviewTest {
    private val navy = 0xff183c59.toInt()
    private val gold = 0xffca963a.toInt()
    private val white = 0xffffffff.toInt()
    private fun image(
        w: Int = 180, h: Int = 130, paint: (Int,Int)->Int
    ): RasterImagePixels = RasterImagePixels(
        w,h,IntArray(w*h) { i -> paint(i%w,i/w) },
        "tatami retangular QA"
    )
    @Test fun solidRectangleCreatesSewnRowsWithNoPESOrFalseLongStitches() {
        val img = image { x,y ->
            if (x in 48..132 && y in 35..90) navy else white
        }
        val part = RasterSatinObjectEditor.detect(img).getOrThrow().single()
        val preview = RasterTatamiRectanglePreview.create(
            img, part, 90f, HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_TATAMI_REVIEW",preview.format)
        assertEquals(StitchCommand.JUMP,preview.points.first().command)
        assertEquals(StitchCommand.END,preview.points.last().command)
        assertTrue(preview.stitchCount > 200)
        assertTrue(preview.sourceBytes.isEmpty())
        assertTrue(preview.isModified)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        val maximum = preview.points.zipWithNext()
            .filter { (_, next) -> next.command == StitchCommand.STITCH }
            .maxOf { (a,b) ->
                hypot((a.xUnits-b.xUnits).toDouble(),
                    (a.yUnits-b.yUnits).toDouble()) / 10.0
            }
        assertTrue("pitch mm=$maximum",maximum <= 2.8)
        val measured = RasterStitchFidelityAudit.compare(
            img,part,preview,90f
        ).getOrThrow()
        assertTrue("coverage=${measured.coverage}",measured.coverage>=.76)
        assertTrue("off=${measured.offInkFraction}",
            measured.offInkFraction<=.10)
        RasterNeedleRiskAudit.inspect(preview,HoopProfile.H130X180).getOrThrow()
        val density=RasterThreadDensityPreflight.inspect(preview).getOrThrow()
        assertEquals(preview.stitchCount,density.sewnSegments)
        assertTrue(density.cellsObserved>0)
    }

    @Test fun rectangularCounterIsNeverFilledByAutomatedTatami() {
        val img = image { x,y ->
            if (x in 48..132 && y in 35..90 &&
                !(x in 72..106 && y in 52..76)) navy else white
        }
        val part = RasterSatinObjectEditor.detect(img).getOrThrow().single()
        val result = RasterTatamiRectanglePreview.create(
            img,part,90f,HoopProfile.H130X180
        )
        assertTrue("Hole must be rejected",result.isFailure)
    }

    @Test fun steppedOrCurvedFillIsNotFakedAsRectangle() {
        val img = image { x,y ->
            if (x in 48..132 && y in 35..90 &&
                (y < 60 || x <= 111)) navy else white
        }
        val part = RasterSatinObjectEditor.detect(img).getOrThrow().single()
        assertTrue(
            RasterTatamiRectanglePreview.create(
                img,part,90f,HoopProfile.H130X180
            ).isFailure
        )
    }

    @Test fun tooLargeTatamiAreaMustBeSplitAndReviewed() {
        val img = image { x,y ->
            if (x in 12..167 && y in 10..115) navy else white
        }
        val part = RasterSatinObjectEditor.detect(img).getOrThrow().single()
        assertTrue(
            RasterTatamiRectanglePreview.create(
                img,part,90f,HoopProfile.H130X180
            ).isFailure
        )
    }

    @Test fun mixedLogoCombinesTatamiSatinAndThinRunningWithoutPES() {
        val img = image { x,y ->
            when {
                x in 12..85 && y in 12..20 -> navy
                x in 112..170 && y in 40..102 -> gold
                x in 15..90 && y == 114 -> navy
                else -> white
            }
        }
        val assembled = RasterAutomaticLogoPreview.create(
            img,90f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_AUTO_STITCH",assembled.format)
        assertTrue(assembled.stitchCount>300)
        assertTrue(assembled.colorChanges>=1)
        assertTrue(assembled.points.any {
            it.command == StitchCommand.COLOR_CHANGE
        })
        assertTrue(assembled.sourceBytes.isEmpty())
        RasterNeedleRiskAudit.inspect(
            assembled,HoopProfile.H130X180
        ).getOrThrow()
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
    @Test fun staleSolidRectangleCannotFillNewHoleInOriginalImage() {
        val original=image { x,y ->
            if(x in 48..132 && y in 35..90) navy else white
        }
        val selected=RasterSatinObjectEditor.detect(original).getOrThrow().single()
        val changedPixels=original.argb.copyOf().apply {
            for (y in 54..75) for (x in 73..104)
                this[y*original.width+x]=white
        }
        val withCounter=original.copy(argb=changedPixels)
        assertTrue(RasterTatamiRectanglePreview.create(
            withCounter,selected,90f,HoopProfile.H130X180
        ).isFailure)
        // No path is generated through the new empty counter.
        assertTrue(RasterTatamiRectanglePreview.create(
            original,selected,90f,HoopProfile.H130X180
        ).isSuccess)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun forgedRectangularMaskCannotInventSolidForeground() {
        val source=image { x,y ->
            if(x in 48..132 && y in 35..90 &&
                !(x in 72..106 && y in 52..76)) navy else white
        }
        val part=RasterSatinObjectEditor.detect(source).getOrThrow().single()
        val forgedPixels=part.mask.pixels.copyOf()
        for(y in 52..76) for(x in 72..106)
            forgedPixels[y*source.width+x]=0
        val forged=part.copy(
            pixelCount=(part.mask.maxX-part.mask.minX+1)*
                (part.mask.maxY-part.mask.minY+1),
            mask=part.mask.copy(pixels=forgedPixels)
        )
        assertTrue(RasterTatamiRectanglePreview.create(
            source,forged,90f,HoopProfile.H130X180
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }


    @Test fun freshInkOutsideRectangleNeedsReselectionBeforeFill() {
        val original=image { x,y ->
            if (x in 48..132 && y in 35..90) navy else white
        }
        val selected=RasterSatinObjectEditor.detect(original)
            .getOrThrow().single()
        val altered=original.copy(argb=original.argb.copyOf().apply {
            this[60*original.width+133]=navy
        })
        assertTrue(RasterTatamiRectanglePreview.create(
            altered,selected,90f,HoopProfile.H130X180
        ).isFailure)
        assertTrue(RasterTatamiRectanglePreview.create(
            original,selected,90f,HoopProfile.H130X180
        ).isSuccess)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }


}
