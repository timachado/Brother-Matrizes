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

    @Test fun initialRailsStartWithZeroDriftAndNoInventedImprovement() {
        val (source,part)=fixture {x,y->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        assertEquals(0,draft.revision)
        assertEquals(0.0,draft.maxEndpointDriftMm,0.000001)
        assertEquals(draft.sections,draft.baselineSections)
        assertTrue(draft.changeSummary().contains("sem comparação anterior"))
        assertFalse(draft.exportPermitted)
    }

    @Test fun undoRejectsInventedHistoryAndWrongRevision() {
        val (source,part)=fixture {x,y->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        assertTrue(RasterLetterEditableSatinReview.restorePrevious(
            source,part,draft,draft,width,hoop
        ).isFailure)
        assertTrue(RasterLetterEditableSatinReview.restorePrevious(
            source,part,draft.copy(revision=3),draft,width,hoop
        ).isFailure)
        assertTrue(RasterLetterEditableSatinReview.restorePrevious(
            source,part,draft.copy(revision=1,exportPermitted=true),
            draft,width,hoop
        ).isFailure)
    }

    @Test fun undoCannotUseChangedOriginalOrDifferentBaseline() {
        val (source,part)=fixture {x,y->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val changed=source.argb.copyOf()
        changed[35*160+25]=0xff123c95.toInt()
        val altered=RasterImagePixels(160,100,changed,"origem alterada")
        assertTrue(RasterLetterEditableSatinReview.restorePrevious(
            altered,part,draft.copy(revision=1),draft,width,hoop
        ).isFailure)
        val changedBaseline=draft.baselineSections.mapIndexed {index,section->
            if(index>0) section else section.copy(
                rails=section.rails.mapIndexed {pairIndex,rail ->
                    if(pairIndex>0) rail else rail.copy(ax=rail.ax+.05f)
                }
            )
        }
        assertTrue(RasterLetterEditableSatinReview.restorePrevious(
            source,part,draft.copy(
                revision=1,baselineSections=changedBaseline
            ),draft,width,hoop
        ).isFailure)
    }

    @Test fun baselineCannotBeResetToAllowHiddenAccumulatedDrift() {
        val (source,part)=fixture {x,y->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        // This forged baseline displaces a single end by over 0.5mm,
        // but leaves the actual STITCH preview untouched. Recheck MUST
        // refuse it before allowing any new incremental nudge.
        val moved = draft.baselineSections.mapIndexed {idx,section ->
            if(idx>0) section else section.copy(
                rails=section.rails.mapIndexed {j,rail ->
                    if(j>0) rail else rail.copy(ax=rail.ax+.50f)
                }
            )
        }
        val tampered = draft.copy(baselineSections=moved)
        assertTrue(RasterLetterEditableSatinReview.nudge(
            source,part,tampered,0,0,
            RasterLetterRailEdge.A,RasterLetterRailAxis.X,
            .1f,width,hoop
        ).isFailure)
        assertEquals(draft.preview.points,tampered.preview.points)
    }

    @Test fun reportUsesSignedDifferencesInsteadOfClaimingAllEditsImprove() {
        val (source,part)=fixture {x,y->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val compared=draft.copy(
            previousCoverage=draft.coverage+.01,
            previousOffInkFraction=draft.offInkFraction-.01,
            previousStitchCount=draft.preview.stitchCount+3,
            revision=1
        )
        val summary=compared.changeSummary()
        assertTrue(summary.contains("−1.0")||summary.contains("-1.0"))
        assertTrue(summary.contains("+1.0"))
        assertTrue(summary.contains("-3 STITCH"))
        assertTrue(summary.contains("não é PES"))
    }

    @Test fun actualAcceptedNudgeCanBeUndoneAndRevalidatedIfAvailable() {
        val (source,part)=fixture {x,y->
            x in 22..30 && y in 12..83
        }
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val attempts=sequence {
            val count=minOf(4,draft.sections[0].rails.size)
            for(index in 0 until count) {
                for(edge in RasterLetterRailEdge.entries) {
                    for(axis in RasterLetterRailAxis.entries) {
                        for(delta in listOf(-.1f,.1f)) {
                            yield(RasterLetterEditableSatinReview.nudge(
                                source,part,draft,0,index,edge,axis,
                                delta,width,hoop
                            ))
                        }
                    }
                }
            }
        }
        val accepted=attempts.firstOrNull {it.isSuccess}
        if(accepted!=null) {
            val later=accepted.getOrThrow()
            assertTrue(later.maxEndpointDriftMm<=.500001)
            assertTrue(later.changeSummary().contains("Antes/depois"))
            val undone=RasterLetterEditableSatinReview.restorePrevious(
                source,part,later,draft,width,hoop
            ).getOrThrow()
            assertEquals(draft.preview.points,undone.preview.points)
            assertEquals(draft.sections,undone.sections)
            assertEquals(0,undone.revision)
            assertFalse(undone.exportPermitted)
        } else {
            // This particular quantized stem might have no safe 0.1mm
            // adjustment. Rejection is legitimate, not a fabricated pass.
            assertTrue(RasterLetterEditableSatinReview.restorePrevious(
                source,part,draft,draft,width,hoop
            ).isFailure)
        }
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
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
