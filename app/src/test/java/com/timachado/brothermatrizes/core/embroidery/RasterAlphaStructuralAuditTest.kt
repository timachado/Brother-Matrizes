package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic masks for geometric regression. They are NOT the 313x266
 * PANOBIANCO artwork, physical needle paths or production PES files.
 */
class RasterAlphaStructuralAuditTest {
    private val w=100
    private val h=80
    private val ink=0x00bb7725

    private fun source(withSplit:Boolean=true,withHole:Boolean=true):
        RasterImagePixels {
        val pixels=IntArray(w*h)
        if(withSplit) {
            // At 208 one letter-shaped region; at 224 it splits into two.
            for(y in 8..28)for(x in 7..21)
                pixels[y*w+x]=(235 shl 24) or ink
            for(y in 8..28)for(x in 27..41)
                pixels[y*w+x]=(235 shl 24) or ink
            for(y in 17..19)for(x in 22..26)
                pixels[y*w+x]=(215 shl 24) or ink
        }
        if(withHole) {
            // A rectangular O-like counter with a weaker top stroke.
            // Its internal hole opens onto the exterior at alpha 224.
            for(y in 39..68)for(x in 57..88) {
                if(x in 65..79 && y in 47..60)continue
                pixels[y*w+x]=(235 shl 24) or ink
            }
            for(y in 39..46) pixels[y*w+72]=(215 shl 24) or ink
        }
        return RasterImagePixels(w,h,pixels,"topologia alfa QA ilustrativa")
    }

    @Test fun fragileConnectorIsHighlightedWithExactSourceCoordinates() {
        val image=source(withHole=false)
        val original=image.argb.copyOf()
        val report=RasterAlphaStructuralAudit.inspect(
            image,listOf(208 to 224)
        ).getOrThrow()
        val transition=report.intervals.single()
        assertEquals(1,transition.splitConnectorCandidates.size)
        val bridge=transition.splitConnectorCandidates.single()
        assertEquals(22,bridge.minX)
        assertEquals(26,bridge.maxX)
        assertEquals(17,bridge.minY)
        assertEquals(19,bridge.maxY)
        assertEquals(15,bridge.connectorPixels)
        assertEquals(2,bridge.survivingRegionsTouched)
        assertEquals(0,transition.holesBefore)
        assertEquals(0,transition.holesAfter)
        val preview=report.preview(208,224)
        assertEquals(0xffff45ba.toInt(),preview.argb[18*w+24])
        assertEquals(0xff9ba4ad.toInt(),preview.argb[18*w+12])
        assertEquals(0,preview.argb[2*w+2])
        assertEquals(15,preview.argb.count {it==0xffff45ba.toInt()})
        assertTrue(image.argb.contentEquals(original))
        assertFalse(report.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun enclosedCounterOpeningCanHappenWithoutAComponentSplit() {
        val image=source(withSplit=false)
        val report=RasterAlphaStructuralAudit.inspect(
            image,listOf(208 to 224)
        ).getOrThrow()
        val interval=report.intervals.single()
        assertEquals(1,interval.holesBefore)
        assertEquals(0,interval.holesAfter)
        assertTrue(interval.splitConnectorCandidates.isEmpty())
        val preview=report.preview(208,224)
        assertEquals(0xffffa447.toInt(),preview.argb[42*w+72])
        assertEquals(0,preview.argb[52*w+72])
        assertTrue(report.summary().contains("vazados internos 1→0"))
    }

    @Test fun sameImageCanHaveSeparateBridgeAndCounterLossAlerts() {
        val image=source()
        val audit=RasterAlphaThresholdSweep.inspect(image).getOrThrow()
        val report=audit.structuralAudit!!
        val interval=report.intervals.single {
            it.fromThreshold==208 && it.toThreshold==224
        }
        assertEquals(1,interval.splitConnectorCandidates.size)
        assertEquals(1,interval.holesBefore)
        assertEquals(0,interval.holesAfter)
        val parent=audit.componentLineage!!.transitions.single {
            it.fromThreshold==208 && it.toThreshold==224
        }
        assertEquals(1,parent.parentsSplit)
        assertEquals(0,parent.parentsLost)
        assertTrue(audit.summary().contains("Auditoria espacial alfa QA"))
        assertTrue(audit.summary().contains("conexão alfa QA"))
        assertFalse(audit.exportPermitted)
        assertFalse(report.exportPermitted)
    }

    @Test fun intactSolidCounterNeverGeneratesSpuriousFragileBridge() {
        val image=source(withSplit=false,withHole=true)
        val report=RasterAlphaStructuralAudit.inspect(
            image,listOf(160 to 180,180 to 208)
        ).getOrThrow()
        assertEquals(2,report.intervals.size)
        assertTrue(report.intervals.all {
            it.holesBefore==1 && it.holesAfter==1 &&
                it.splitConnectorCandidates.isEmpty()
        })
    }

    @Test fun unknownAndReversedIntervalPreviewsFailClosed() {
        val image=source()
        val report=RasterAlphaStructuralAudit.inspect(
            image,listOf(208 to 224)
        ).getOrThrow()
        assertTrue(runCatching {report.preview(224,208)}.isFailure)
        assertTrue(runCatching {report.preview(160,180)}.isFailure)
        assertTrue(RasterAlphaStructuralAudit.inspect(
            image,listOf(224 to 208)
        ).isFailure)
        assertTrue(report.reviewRequired)
        assertFalse(report.exportPermitted)
    }
}
