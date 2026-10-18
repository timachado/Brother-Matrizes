package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterLetterEditableSatinReviewTest {
    private val thread = 0xffcf7c29.toInt()
    private val width = 90f
    private val hoop = HoopProfile.H130X180

    private fun fixture(ink:(Int,Int)->Boolean):
        Pair<RasterImagePixels,RasterSatinObject> {
        val w=160
        val h=100
        val pixels=IntArray(w*h) { index ->
            if (ink(index%w,index/w)) thread else 0
        }
        val source=RasterImagePixels(w,h,pixels,"haste QA")
        val marked=pixels.indices.filter {pixels[it]!=0}
        require(marked.size>=15)
        val minX=marked.minOf {it%w}
        val maxX=marked.maxOf {it%w}
        val minY=marked.minOf {it/w}
        val maxY=marked.maxOf {it/w}
        val part=RasterSatinObject(
            id=1,color=thread,pixelCount=marked.size,
            mask=SeparatedColorArtwork(
                colors=listOf(thread),
                pixels=IntArray(pixels.size) {index ->
                    if(pixels[index]!=0) 0 else -1
                },
                minX=minX,maxX=maxX,minY=minY,maxY=maxY
            )
        )
        return source to part
    }

    @Test fun editableRailProposalPreservesSourceAndNeverExportsPes() {
        val (source,part)=fixture {x,y ->
            x in 22..30 && y in 12..83
        }
        val original=source.argb.copyOf()
        val proposal=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        )
        assertTrue("A thin stem should be previewable: " +
            proposal.exceptionOrNull()?.message,proposal.isSuccess)
        val result=proposal.getOrThrow()
        assertTrue(result.sections.isNotEmpty())
        assertTrue(result.preview.stitchCount>0)
        assertTrue(result.preview.sourceBytes.isEmpty())
        assertEquals("QA_SATIN_REVIEW",result.preview.format)
        assertTrue(result.coverage>=.78)
        assertTrue(result.reviewRequired)
        assertFalse(result.exportPermitted)
        assertTrue(source.argb.contentEquals(original))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun diagonalOnlyJunctionRefusesToInventSatinBridge() {
        val (source,part)=fixture {x,y ->
            (x in 20..35 && y in 20..35) ||
                (x in 36..51 && y in 36..51)
        }
        val result=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("Junção diagonal"))
    }

    @Test fun tinyClosedCounterCannotBeFilledBySuggestedSatin() {
        val (source,part)=fixture {x,y ->
            x in 18..53 && y in 10..86 &&
                !(x in 32..34 && y in 43..45)
        }
        val result=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("vazado estreito"))
    }

    @Test fun illegalNudgeRangeAndOutOfListPairAreRejectedWithoutMutation() {
        val (source,part)=fixture {x,y ->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val railsBefore=draft.sections.toList()
        for(delta in listOf(0f,.25f,Float.NaN,1f)) {
            assertTrue(RasterLetterEditableSatinReview.nudge(
                source,part,draft,0,0,
                RasterLetterRailEdge.A,
                RasterLetterRailAxis.X,delta,width,hoop
            ).isFailure)
        }
        assertTrue(RasterLetterEditableSatinReview.nudge(
            source,part,draft,999,0,
            RasterLetterRailEdge.A,
            RasterLetterRailAxis.X,.1f,width,hoop
        ).isFailure)
        assertTrue(RasterLetterEditableSatinReview.nudge(
            source,part,draft,0,999,
            RasterLetterRailEdge.A,
            RasterLetterRailAxis.Y,.1f,width,hoop
        ).isFailure)
        assertEquals(railsBefore,draft.sections)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun nudgeDoesNotPretendThatUnchangedQuantizedStitchesImproved() {
        val (source,part)=fixture {x,y ->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val original=source.argb.copyOf()
        val attempt=RasterLetterEditableSatinReview.nudge(
            source,part,draft,0,0,
            RasterLetterRailEdge.A,RasterLetterRailAxis.X,.1f,width,hoop
        )
        if(attempt.isSuccess) {
            val updated=attempt.getOrThrow()
            assertEquals(1,updated.revision)
            assertTrue(updated.preview.points!=draft.preview.points)
            assertTrue(updated.preview.stitchCount>0)
            assertFalse(updated.exportPermitted)
        } else {
            assertTrue(attempt.exceptionOrNull()?.message.orEmpty().isNotEmpty())
        }
        assertTrue(source.argb.contentEquals(original))
    }

    @Test fun wrongOriginalSourceColorIsNeverAcceptedAsRailGuide() {
        val (source,part)=fixture {x,y ->
            x in 22..30 && y in 12..83
        }
        val recolored=source.argb.copyOf()
        recolored[35*160+25]=0xff125c95.toInt()
        val other=RasterImagePixels(160,100,recolored,"alterado")
        assertTrue(RasterLetterEditableSatinReview.propose(
            other,part,width,hoop
        ).isFailure)
    }
}
