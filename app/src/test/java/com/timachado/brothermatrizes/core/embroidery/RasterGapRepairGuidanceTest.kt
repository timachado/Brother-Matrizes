package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterGapRepairGuidanceTest {
    private val thread = 0xffd67820.toInt()
    private val fullWidth = 90f
    private val imageWidth = 160
    private val imageHeight = 100

    private fun art(
        ink: (Int, Int) -> Boolean
    ): Pair<RasterImagePixels, RasterSatinObject> {
        var minX = imageWidth
        var minY = imageHeight
        var maxX = -1
        var maxY = -1
        var total = 0
        val pixels = IntArray(imageWidth*imageHeight) { i ->
            val x = i % imageWidth
            val y = i / imageWidth
            if (ink(x,y)) {
                total++
                minX = minOf(minX,x); minY = minOf(minY,y)
                maxX = maxOf(maxX,x); maxY = maxOf(maxY,y)
                thread
            } else 0
        }
        require(total > 0)
        val original = RasterImagePixels(
            imageWidth,imageHeight,pixels,"fonte original para revisao"
        )
        val mask = SeparatedColorArtwork(
            colors=listOf(thread),
            pixels=IntArray(pixels.size) { i ->
                if (pixels[i] ushr 24 >= 180) 0 else -1
            },
            minX=minX,maxX=maxX,minY=minY,maxY=maxY
        )
        return original to RasterSatinObject(
            id=1,color=thread,pixelCount=total,mask=mask
        )
    }

    private fun gaps(
        source: RasterImagePixels,
        part: RasterSatinObject,
        missing: (Int,Int) -> Boolean
    ): RasterUncoveredRegionReport {
        val mw = part.mask.maxX-part.mask.minX+1
        val mh = part.mask.maxY-part.mask.minY+1
        var ink = 0
        var sewn = 0
        val covered = BooleanArray(mw*mh)
        for(y in 0 until mh) for(x in 0 until mw) {
            val globalX = x+part.mask.minX
            val globalY = y+part.mask.minY
            val p = globalY*source.width+globalX
            if(part.mask.pixels[p] != 0) continue
            ink++
            if(!missing(globalX,globalY)) {
                covered[y*mw+x] = true
                sewn++
            }
        }
        val evidence = RasterStitchFidelity(
            inkPixels=ink,coveredInkPixels=sewn,
            stitchSamples=500,offInkSamples=0,
            coveredMask=covered,maskWidth=mw,maskHeight=mh
        )
        return RasterUncoveredRegionAudit.inspect(
            source,part,evidence,fullWidth
        ).getOrThrow()
    }

    @Test fun longUnsewnStripRecommendsReviewingSatinRailsNotAutoSewing() {
        val (source,part)=art {x,y->x in 20..125 && y in 20..64}
        val report=gaps(source,part) {x,y->x in 75..78 && y in 25..59}
        val plan=RasterGapRepairGuidance.propose(
            source,part,report,fullWidth
        ).getOrThrow()
        assertEquals(1,plan.totalGapRegions)
        assertEquals(1,plan.advice.size)
        assertEquals(RasterGapRepairAction.REVIEW_SATIN_RAILS,
            plan.advice.single().action)
        assertNotNull(plan.advice.single().nearestCoveredDistanceMm)
        assertTrue(plan.advice.single().nearestCoveredDistanceMm!!>0)
        assertTrue(plan.advice.single().guidance().contains("Nenhum ponto novo"))
        assertFalse(plan.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun wideUnsewnPatchOnlySuggestsTatamiManualReview() {
        val (source,part)=art {x,y->x in 20..125 && y in 12..87}
        val report=gaps(source,part) {x,y->x in 56..81 && y in 35..64}
        val plan=RasterGapRepairGuidance.propose(
            source,part,report,fullWidth
        ).getOrThrow()
        assertEquals(RasterGapRepairAction.REVIEW_TATAMI_FILL,
            plan.advice.single().action)
        assertFalse(plan.advice.single().exportPermitted)
    }

    @Test fun tinyUncoveredDotIsStillAssignedRunningReview() {
        val (source,part)=art {x,y->x in 20..120 && y in 18..80}
        val report=gaps(source,part) {x,y->x==70 && y==51}
        val plan=RasterGapRepairGuidance.propose(
            source,part,report,fullWidth
        ).getOrThrow()
        assertEquals(1,plan.originalUncoveredPixels)
        assertEquals(RasterGapRepairAction.REVIEW_RUNNING_STROKE,
            plan.advice.single().action)
        assertEquals(3,plan.advice.single().priority)
    }

    @Test fun disconnectedStrokeCannotBorrowCoverageThroughTransparentSpace() {
        val (source,part)=art {x,y->
            (x in 20..30 && y in 20..51) ||
                (x in 85..95 && y in 20..51)
        }
        val report=gaps(source,part) {x,y->x in 85..95}
        val plan=RasterGapRepairGuidance.propose(
            source,part,report,fullWidth
        ).getOrThrow()
        assertEquals(1,plan.totalGapRegions)
        assertNull(plan.advice.single().nearestCoveredDistanceMm)
        assertNull(plan.advice.single().nearestCoveredX)
        assertEquals(1,plan.advice.single().priority)
        assertTrue(plan.advice.single().guidance()
            .contains("não criar atalho"))
    }

    @Test fun letterCounterCannotBeUsedAsShortcutForCoverage() {
        val (source,part)=art {x,y->
            x in 20..120 && y in 10..89 &&
                !(x in 42..93 && y in 30..69)
        }
        val report=gaps(source,part) {x,y->x in 94..113 && y in 38..61}
        val plan=RasterGapRepairGuidance.propose(
            source,part,report,fullWidth
        ).getOrThrow()
        assertTrue(plan.advice.isNotEmpty())
        val inOriginalHole=(50-part.mask.minX)+
            (48-part.mask.minY)*report.localWidth
        assertEquals(0,report.regionIds[inOriginalHole])
        val anchorX=plan.advice.first().nearestCoveredX
        val anchorY=plan.advice.first().nearestCoveredY
        assertTrue(anchorX!=null && anchorY!=null)
        assertFalse(anchorX!! in 42..93 && anchorY!! in 30..69)
    }

    @Test fun moreThan24GapsRetainHiddenPixelAndRegionTotals() {
        val (source,part)=art {x,y->x in 12..149 && y in 10..89}
        val report=gaps(source,part) {x,y->
            x in 15..147 && y in 13..87 &&
                x%7==0 && y%7==0
        }
        val plan=RasterGapRepairGuidance.propose(
            source,part,report,fullWidth
        ).getOrThrow()
        assertTrue(plan.totalGapRegions>24)
        assertEquals(24,plan.advice.size)
        assertEquals(report.totalRegions-24,plan.unlistedRegions)
        assertEquals(report.hiddenRegionPixels,plan.unlistedGapPixels)
        assertEquals(report.uncoveredPixels,plan.originalUncoveredPixels)
        assertTrue(plan.summary().contains("pendentes"))
    }

    @Test fun alteredOriginalOrTamperedRegionEvidenceIsRejected() {
        val (source,part)=art {x,y->x in 12..119 && y in 10..81}
        val report=gaps(source,part) {x,y->x in 55..59 && y in 28..68}
        val altered=source.argb.copyOf()
        altered[40*imageWidth+60]=0
        val modified=RasterImagePixels(
            imageWidth,imageHeight,altered,"origem adulterada"
        )
        assertTrue(RasterGapRepairGuidance.propose(
            modified,part,report,fullWidth
        ).isFailure)
        assertTrue(RasterGapRepairGuidance.propose(
            source,part,report.copy(regionIds=IntArray(report.regionIds.size)),
            fullWidth
        ).isFailure)
        assertTrue(RasterGapRepairGuidance.propose(
            source,part,report.copy(originX=0),fullWidth
        ).isFailure)
        assertTrue(RasterGapRepairGuidance.propose(
            source,part,report,Float.NaN
        ).isFailure)
    }
}
