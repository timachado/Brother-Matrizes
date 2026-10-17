package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterUncoveredRegionAuditTest {
    private val color = 0xffda781e.toInt()
    private val w = 160
    private val h = 100

    private fun fixture(ink: (Int, Int) -> Boolean):
        Pair<RasterImagePixels, RasterSatinObject> {
        val data = IntArray(w*h) { i ->
            if (ink(i%w,i/w)) color else 0
        }
        val full = RasterImagePixels(w,h,data,"qa lacunas reais")
        var minX=w;var minY=h;var maxX=-1;var maxY=-1
        var count=0
        val separated = IntArray(w*h) { i ->
            if (data[i] ushr 24 >= 180) {
                val x=i%w; val y=i/w
                minX=minOf(minX,x);minY=minOf(minY,y)
                maxX=maxOf(maxX,x);maxY=maxOf(maxY,y)
                count++
                0
            } else -1
        }
        val component=RasterSatinObject(
            id=1,color=color,pixelCount=count,
            mask=SeparatedColorArtwork(
                colors=listOf(color),pixels=separated,
                minX=minX,maxX=maxX,minY=minY,maxY=maxY
            )
        )
        return full to component
    }

    private fun check(
        art: Pair<RasterImagePixels,RasterSatinObject>,
        missing: (Int,Int) -> Boolean
    ): Result<RasterUncoveredRegionReport> {
        val (source,part)=art
        val mw=part.mask.maxX-part.mask.minX+1
        val mh=part.mask.maxY-part.mask.minY+1
        val coverage=BooleanArray(mw*mh)
        var covered=0
        var ink=0
        for(y in 0 until mh) for(x in 0 until mw) {
            val px=part.mask.minX+x
            val py=part.mask.minY+y
            val isInk=part.mask.pixels[py*w+px]==0
            if(isInk) ink++
            if(isInk && !missing(px,py)) {
                coverage[y*mw+x]=true;covered++
            }
        }
        val result=RasterStitchFidelity(
            inkPixels=ink,coveredInkPixels=covered,
            stitchSamples=200,offInkSamples=0,
            coveredMask=coverage,maskWidth=mw,maskHeight=mh
        )
        return RasterUncoveredRegionAudit.inspect(
            source,part,result,90f
        )
    }

    @Test fun twoThinMissingStrokesAreSeparatedForSatinReview() {
        val f=fixture { x,y -> x in 15..125 && y in 24..62 }
        val review=check(f) { x,y ->
            (x in 20..23 || x in 85..88) && y in 25..61
        }.getOrThrow()
        assertEquals(2,review.totalRegions)
        assertEquals(2,review.regions.size)
        assertTrue(review.regions.all {
            it.suggestedReview==RasterGapReviewRoute.SATIN_REVIEW
        })
        assertEquals(8*37,review.uncoveredPixels)
        assertEquals(0,review.hiddenRegionPixels)
        assertEquals(review.sourcePixels,
            review.coveredPixels+review.uncoveredPixels)
        assertFalse(review.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun largeSolidUncoveredAreaSuggestsTatamiReviewNotNewStitches() {
        val f=fixture {x,y->x in 20..120 && y in 15..83}
        val report=check(f) {x,y->x in 60..79 && y in 38..57}.getOrThrow()
        assertEquals(1,report.totalRegions)
        assertEquals(400,report.uncoveredPixels)
        assertEquals(RasterGapReviewRoute.TATAMI_REVIEW,
            report.regions.single().suggestedReview)
    }

    @Test fun missingOnePixelAccentIsNotLostOrAutoSewn() {
        val f=fixture {x,y->x in 20..120 && y in 15..83}
        val report=check(f) {x,y->x==55 && y==29}.getOrThrow()
        assertEquals(1,report.uncoveredPixels)
        assertEquals(RasterGapReviewRoute.RUNNING_REVIEW,
            report.regions.single().suggestedReview)
        assertTrue(report.summary().contains("não gera pontos"))
    }

    @Test fun emptyCounterInsideLetterIsNotReportedAsMissingInk() {
        val f=fixture {x,y->
            x in 20..110 && y in 10..82 &&
                !(x in 42..90 && y in 30..65)
        }
        val report=check(f) {_,_->false}.getOrThrow()
        assertEquals(0,report.totalRegions)
        assertEquals(0,report.uncoveredPixels)
        assertEquals(report.sourcePixels,report.coveredPixels)
        assertEquals(0,report.hiddenRegionPixels)
    }

    @Test fun fakeSewnPixelInsideLetterCounterIsRejected() {
        val f=fixture {x,y->
            x in 20..110 && y in 10..82 &&
                !(x in 42..90 && y in 30..65)
        }
        // A fake sewn pixel inside the transparent counter must be rejected.
        val (source,part)=f
        val mw=part.mask.maxX-part.mask.minX+1
        val mh=part.mask.maxY-part.mask.minY+1
        val covered=BooleanArray(mw*mh)
        for(y in 0 until mh) for(x in 0 until mw)
            covered[y*mw+x]=part.mask.pixels[
                (y+part.mask.minY)*w + part.mask.minX+x
            ]==0
        val cx=50-part.mask.minX
        val cy=45-part.mask.minY
        covered[cy*mw+cx]=true
        val bogus=RasterStitchFidelity(
            inkPixels=part.pixelCount,coveredInkPixels=part.pixelCount,
            stitchSamples=100,offInkSamples=0,
            coveredMask=covered,maskWidth=mw,maskHeight=mh
        )
        assertTrue(RasterUncoveredRegionAudit.inspect(
            source,part,bogus,90f
        ).isFailure)
    }

    @Test fun omittedRegionsCountAllOriginalPixels() {
        val f=fixture {x,y->x in 10..150 && y in 10..90}
        val result=check(f) {x,y->
            x in 12..147 && y in 12..87 &&
                x%7==0 && y%7==0
        }.getOrThrow()
        assertTrue(result.totalRegions>24)
        assertEquals(24,result.regions.size)
        assertTrue(result.hiddenRegionPixels>0)
        assertEquals(result.uncoveredPixels,
            result.regions.sumOf {it.pixelCount}+result.hiddenRegionPixels)
        assertTrue(result.summary().contains("sem descarte"))
    }

    @Test fun falsifiedCoverageCountsOrSourceMaskFailClosed() {
        val (source,part)=fixture {x,y->x in 20..110 && y in 14..72}
        val bad=RasterStitchFidelity(
            inkPixels=part.pixelCount+1,
            coveredInkPixels=0,stitchSamples=2,offInkSamples=0,
            coveredMask=BooleanArray(
                (part.mask.maxX-part.mask.minX+1)*
                    (part.mask.maxY-part.mask.minY+1)
            ),
            maskWidth=part.mask.maxX-part.mask.minX+1,
            maskHeight=part.mask.maxY-part.mask.minY+1
        )
        assertTrue(RasterUncoveredRegionAudit.inspect(
            source,part,bad,90f
        ).isFailure)
        assertTrue(RasterUncoveredRegionAudit.inspect(
            source,part,bad,Float.NaN
        ).isFailure)
    }
}
