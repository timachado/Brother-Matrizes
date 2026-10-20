package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometric fixtures, NOT the user's original PANOBIANCO artwork.
 * A restored silhouette is neither STITCH geometry nor a PES approval.
 */
class RasterQaCounterClosureStudyTest {
    private val w=96
    private val h=80
    private val ink=0x00b96836

    private fun ring(slits:Int=1):RasterImagePixels {
        val pixels=IntArray(w*h)
        for(y in 12..61)for(x in 20..73) {
            if(x in 29..64 && y in 22..52)continue
            pixels[y*w+x]=(240 shl 24) or ink
        }
        if(slits>0)for(y in 12..21) {
            pixels[y*w+45]=(195 shl 24) or ink
        }
        if(slits>1)for(y in 12..21) {
            pixels[y*w+57]=(195 shl 24) or ink
        }
        return RasterImagePixels(w,h,pixels,"cavidade sintética")
    }

    @Test fun restoresOnlyOriginalWitnessPixelsAndVerifiesHoleAndRegions() {
        val original=ring()
        val before=original.argb.copyOf()
        val sweep=RasterAlphaThresholdSweep.inspect(original).getOrThrow()
        val report=RasterQaCounterClosureStudy.prepare(sweep).getOrThrow()
        assertEquals(1,report.sourceHoles)
        assertEquals(0,report.cutoffHoles)
        assertEquals(1,report.candidateHoles)
        assertEquals(1,report.cutoffRegions)
        assertEquals(1,report.candidateRegions)
        assertEquals(10,report.candidatePixels.size)
        assertTrue(report.holeRestored)
        assertTrue(report.regionTopologyPreserved)
        assertTrue(report.candidateForVisualReview)
        val preview=report.preview()
        assertEquals(0xff66db87.toInt(),preview.argb[15*w+45])
        assertEquals(0xff939da7.toInt(),preview.argb[15*w+25])
        assertEquals(0,preview.argb[30*w+45])
        assertTrue(original.argb.contentEquals(before))
        assertFalse(report.exportPermitted)
        assertTrue(report.requiresManualReview)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(report.summary().contains("Topologia global preservada"))
    }

    @Test fun singleShortestPathMustNotFalselyApproveMultipleOpenings() {
        val source=ring(slits=2)
        val report=RasterQaCounterClosureStudy.prepare(
            RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        ).getOrThrow()
        assertEquals(1,report.sourceHoles)
        assertEquals(0,report.cutoffHoles)
        assertEquals(0,report.candidateHoles)
        assertEquals(1,report.cutoffRegions)
        assertEquals(1,report.candidateRegions)
        assertFalse(report.holeRestored)
        assertFalse(report.candidateForVisualReview)
        assertFalse(report.exportPermitted)
        assertTrue(report.summary().contains("não utilizar"))
    }

    @Test fun intactCounterCannotBeMisclassifiedAsARepairCandidate() {
        val study=RasterAlphaThresholdSweep.inspect(ring(slits=0))
            .getOrThrow()
        assertTrue(RasterQaCounterClosureStudy.prepare(study).isFailure)
        assertFalse(study.exportPermitted)
    }

    @Test fun wrongSourceAndInventedBandPixelsAreRejected() {
        val study=RasterAlphaThresholdSweep.inspect(ring()).getOrThrow()
        val leak=study.counterLeakEvidence!!
        val stale=RasterImagePixels(w,h,study.source.argb.copyOf(),
            "clone de outra importação")
        assertTrue(RasterQaCounterClosureStudy.prepare(
            study.copy(counterLeakEvidence=leak.copy(source=stale))
        ).isFailure)
        val intervals=leak.intervals.map { interval ->
            if(interval.fromThreshold!=180 || interval.toThreshold!=208)
                interval
            else interval.copy(openedCounters=interval.openedCounters.map {
                it.copy(openingPixels=listOf(0))
            })
        }
        assertTrue(RasterQaCounterClosureStudy.prepare(
            study.copy(counterLeakEvidence=leak.copy(intervals=intervals))
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
