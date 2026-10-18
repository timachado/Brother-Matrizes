package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSatinBoundedRepairTrialTest {
    private val thread = 0xffd87924.toInt()
    private val hoop = HoopProfile.H130X180
    private val width = 90f

    private fun fixture(
        ink: (Int,Int)->Boolean,
        missing: (Int,Int)->Boolean
    ): Triple<RasterImagePixels,RasterSatinObject,RasterUncoveredRegionReport> {
        val w = 160
        val h = 100
        val argb = IntArray(w*h) {i->
            if(ink(i%w,i/w)) thread else 0
        }
        val source = RasterImagePixels(w,h,argb,"teste de ajuste QA")
        var minX=w;var minY=h;var maxX=-1;var maxY=-1
        var count=0
        for(i in argb.indices) if(argb[i] ushr 24 >= 180) {
            val x=i%w;val y=i/w
            minX=minOf(minX,x);minY=minOf(minY,y)
            maxX=maxOf(maxX,x);maxY=maxOf(maxY,y)
            count++
        }
        val separated = IntArray(argb.size) {i->
            if(argb[i] ushr 24 >= 180) 0 else -1
        }
        val part = RasterSatinObject(
            id=1,color=thread,pixelCount=count,
            mask=SeparatedColorArtwork(
                colors=listOf(thread),pixels=separated,
                minX=minX,maxX=maxX,minY=minY,maxY=maxY
            )
        )
        val mw=maxX-minX+1
        val mh=maxY-minY+1
        val covered=BooleanArray(mw*mh)
        var sewn=0
        for(y in 0 until mh) for(x in 0 until mw) {
            val globalX=x+minX
            val globalY=y+minY
            if(separated[globalY*w+globalX] == 0 &&
                !missing(globalX,globalY)) {
                covered[y*mw+x]=true;sewn++
            }
        }
        val fidelity = RasterStitchFidelity(
            inkPixels=count,coveredInkPixels=sewn,
            stitchSamples=500,offInkSamples=0,
            coveredMask=covered,maskWidth=mw,maskHeight=mh
        )
        val report=RasterUncoveredRegionAudit.inspect(
            source,part,fidelity,width
        ).getOrThrow()
        return Triple(source,part,report)
    }

    private fun dummyRails(): List<ManualSatinSection> = listOf(
        ManualSatinSection(
            listOf(
                SatinRailPair(.15f,.40f,.15f,.60f),
                SatinRailPair(.50f,.40f,.50f,.60f),
                SatinRailPair(.85f,.40f,.85f,.60f)
            )
        )
    )

    @Test fun tatamiGapNeverTriggersSatinNudge() {
        val (source,part,report)=fixture(
            ink={x,y->x in 15..135 && y in 12..87},
            missing={x,y->x in 56..82 && y in 32..62}
        )
        assertEquals(RasterGapReviewRoute.TATAMI_REVIEW,
            report.regions.single().suggestedReview)
        val rails=dummyRails()
        val result=RasterSatinBoundedRepairTrial.suggest(
            source,part,rails,report,report.regions.single().id,
            width,hoop,0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("outra técnica"))
        assertEquals(3,rails.single().rails.size)
    }

    @Test fun isolatedOnePixelAccentNeedsRunningReviewNotSatin() {
        val (source,part,report)=fixture(
            ink={x,y->x in 18..135 && y in 12..82},
            missing={x,y->x==70 && y==44}
        )
        assertEquals(RasterGapReviewRoute.RUNNING_REVIEW,
            report.regions.single().suggestedReview)
        assertTrue(RasterSatinBoundedRepairTrial.suggest(
            source,part,dummyRails(),report,1,width,hoop,0
        ).isFailure)
    }

    @Test fun missingOrForgedRegionIdRefusedBeforeAnyNeedleWork() {
        val (source,part,report)=fixture(
            ink={x,y->x in 15..135 && y in 24..64},
            missing={x,y->x in 75..78 && y in 28..59}
        )
        assertEquals(RasterGapReviewRoute.SATIN_REVIEW,
            report.regions.single().suggestedReview)
        assertTrue(RasterSatinBoundedRepairTrial.suggest(
            source,part,dummyRails(),report,999,width,hoop,0
        ).isFailure)
        val fake=report.copy(regionIds=IntArray(report.regionIds.size))
        assertTrue(RasterSatinBoundedRepairTrial.suggest(
            source,part,dummyRails(),fake,1,width,hoop,0
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun disjointInkHasNoValidSatinBridge() {
        val (source,part,report)=fixture(
            ink={x,y->(x in 10..22 || x in 100..109) && y in 22..70},
            missing={x,y->x in 100..109}
        )
        assertEquals(1,report.totalRegions)
        val guidance=RasterGapRepairGuidance.propose(
            source,part,report,width
        ).getOrThrow().advice.single()
        assertEquals(RasterGapRepairAction.REVIEW_SATIN_RAILS,
            guidance.action)
        assertEquals(null,guidance.nearestCoveredDistanceMm)
        val result=RasterSatinBoundedRepairTrial.suggest(
            source,part,dummyRails(),report,1,width,hoop,0
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("desconectadas"))
    }

    @Test fun sourceAndUserEditedRailsAreImmutableEvenWhenProposalRejected() {
        val (source,part,report)=fixture(
            ink={x,y->x in 15..135 && y in 24..64},
            missing={x,y->x in 75..78 && y in 28..59}
        )
        val sourceCopy=source.argb.copyOf()
        val sections=dummyRails()
        val railsCopy=sections.single().rails.toList()
        val result=RasterSatinBoundedRepairTrial.suggest(
            source,part,sections,report,1,width,hoop,0
        )
        // The QA draft may fail for an unsuitable initial rail layout;
        // no silent application is permitted in either outcome.
        assertTrue(source.argb.contentEquals(sourceCopy))
        assertEquals(railsCopy,sections.single().rails)
        if(result.isSuccess) {
            val proposal=result.getOrThrow()
            assertTrue(proposal.displacementMm <= .35+1e-9)
            assertTrue(proposal.candidateRegionMissingPixels <
                proposal.originalRegionMissingPixels)
            assertTrue(proposal.candidateTotalMissingPixels <
                proposal.originalTotalMissingPixels)
            assertFalse(proposal.exportPermitted)
            assertTrue(proposal.requiresHumanReview)
        } else {
            assertTrue(result.exceptionOrNull()?.message.orEmpty().isNotEmpty())
        }
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun invalidScaleAndOutOfListGapAreRejected() {
        val (source,part,report)=fixture(
            ink={x,y->x in 15..135 && y in 24..64},
            missing={x,y->x in 75..78 && y in 28..59}
        )
        for (invalid in listOf(Float.NaN,2f,300f)) {
            assertTrue(RasterSatinBoundedRepairTrial.suggest(
                source,part,dummyRails(),report,1,invalid,hoop,0
            ).isFailure)
        }
        assertTrue(RasterSatinBoundedRepairTrial.suggest(
            source,part,emptyList(),report,1,width,hoop,0
        ).isFailure)
        assertTrue(RasterSatinBoundedRepairTrial.suggest(
            source,part,dummyRails(),report,1,width,hoop,3
        ).isFailure)
    }
}
