package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic samples only; never represent actual PANOBIANCO geometry. */
class RasterQaMicrodetailReviewTest {
    private val w=96
    private val h=80
    private val ink=0x00b96836

    private fun ring(slits:Int=1):RasterImagePixels {
        val p=IntArray(w*h)
        for(y in 12..61)for(x in 20..73) {
            if(x in 29..64 && y in 22..52)continue
            p[y*w+x]=(240 shl 24) or ink
        }
        if(slits>=1)for(y in 12..21) p[y*w+45]=(195 shl 24) or ink
        if(slits>=2)for(y in 12..21) p[y*w+57]=(195 shl 24) or ink
        return RasterImagePixels(w,h,p,"fixture de zoom QA")
    }

    @Test fun microdetailConvertsPixelsToMmWithoutMinimumStitchClaim() {
        val src=ring()
        val immutable=src.argb.copyOf()
        val sweep=RasterAlphaThresholdSweep.inspect(src).getOrThrow()
        val closure=RasterQaCounterClosureStudy.prepare(sweep).getOrThrow()
        val report=RasterQaMicrodetailReview.inspect(
            closure,sweep.counterLeakEvidence!!,96f
        ).getOrThrow()
        assertEquals(1f,report.pixelSizeMm,.000001f)
        assertEquals(36f,report.detailWidthMm,.000001f)
        assertEquals(31f,report.detailHeightMm,.000001f)
        assertEquals(36*31,report.sourceHolePixels)
        assertEquals(10,report.restoredPixels.size)
        assertEquals(45,report.centerX)
        assertEquals(12,report.centerY)
        assertTrue(report.summary().contains("largura-escolhida=96.000 mm"))
        assertTrue(report.summary().contains("caixa-fisica=36.000x31.000 mm"))
        assertTrue(report.summary().contains(
            "fingerprint=${RasterSourceTopologyDiagnostics.inspect(src)
                .getOrThrow().fingerprint}"
        ))
        assertFalse(report.exportPermitted)
        assertTrue(report.manualReviewRequired)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(src.argb.contentEquals(immutable))
    }

    @Test fun zoomIsNearestNeighbourOnlyAndRetainsOriginalPixels() {
        val src=ring()
        val closure=RasterQaCounterClosureStudy.prepare(
            RasterAlphaThresholdSweep.inspect(src).getOrThrow()
        ).getOrThrow()
        val leak=RasterAlphaCounterLeakPath.inspect(
            src,listOf(180 to 208)
        ).getOrThrow()
        val review=RasterQaMicrodetailReview.inspect(
            closure,leak,48f
        ).getOrThrow()
        val zoom=review.zoomPreview()
        assertEquals(w,zoom.width)
        assertEquals(h,zoom.height)
        assertTrue(zoom.argb.all {
            it==0xff60d58c.toInt() ||
                it==0xffb1bbc4.toInt() ||
                it==0xffefa75b.toInt() ||
                it==0xff2d3947.toInt()
        })
        assertEquals(0xff60d58c.toInt(),zoom.argb[(h/2)*w+(w/2)])
        assertTrue(zoom.argb.contains(0xff60d58c.toInt()))
        assertTrue(zoom.argb.contains(0xffb1bbc4.toInt()))
        assertTrue(review.summary().contains("escala=0.500 mm/px"))
        assertFalse(zoom.argb.contentEquals(src.argb))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun multipleOpeningsAreNotAnApprovedMicrodetail() {
        val source=ring(slits=2)
        val study=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        val closure=RasterQaCounterClosureStudy.prepare(study).getOrThrow()
        assertFalse(closure.candidateForVisualReview)
        assertTrue(RasterQaMicrodetailReview.inspect(
            closure,study.counterLeakEvidence!!,80f
        ).isFailure)
    }

    @Test fun wrongImageAndUnknownScaleFailClosed() {
        val source=ring()
        val sweep=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        val closure=RasterQaCounterClosureStudy.prepare(sweep).getOrThrow()
        val leak=sweep.counterLeakEvidence!!
        val clone=RasterImagePixels(w,h,source.argb.copyOf(),"other")
        assertTrue(RasterQaMicrodetailReview.inspect(
            closure,leak.copy(source=clone),80f
        ).isFailure)
        assertTrue(RasterQaMicrodetailReview.inspect(
            closure,leak,Float.NaN
        ).isFailure)
        assertTrue(RasterQaMicrodetailReview.inspect(
            closure,leak,0f
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
