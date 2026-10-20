package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic counter geometry. No test fixture claims to be the original
 * PANOBIANCO pixels or a machine embroidery stitch-out.
 */
class RasterAlphaCounterLeakPathTest {
    private val w=96
    private val h=80
    private val orange=0x00bd612d

    private fun ring(withOpening:Boolean=true):RasterImagePixels {
        val pixels=IntArray(w*h)
        for(y in 12..61)for(x in 20..73) {
            if(x in 29..64 && y in 22..52)continue
            pixels[y*w+x]=(240 shl 24) or orange
        }
        if(withOpening)for(y in 12..21) {
            pixels[y*w+45]=(195 shl 24) or orange
        }
        return RasterImagePixels(w,h,pixels,"vazado QA sintético")
    }

    @Test fun locatesActualOpeningPixelsAndPreservesOriginalInk() {
        val source=ring()
        val before=source.argb.copyOf()
        val report=RasterAlphaCounterLeakPath.inspect(
            source,listOf(160 to 180,180 to 208,208 to 224)
        ).getOrThrow()
        assertEquals(3,report.intervals.size)
        assertTrue(report.intervals[0].openedCounters.isEmpty())
        val opening=report.intervals[1].openedCounters.single()
        assertEquals(29,opening.holeMinX)
        assertEquals(22,opening.holeMinY)
        assertEquals(64,opening.holeMaxX)
        assertEquals(52,opening.holeMaxY)
        assertEquals(36*31,opening.holePixels)
        assertEquals(45,opening.openingMinX)
        assertEquals(45,opening.openingMaxX)
        assertEquals(12,opening.openingMinY)
        assertEquals(21,opening.openingMaxY)
        assertEquals(10,opening.openingPixels.size)
        assertTrue(report.intervals[2].openedCounters.isEmpty())

        val sweep=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        val audit=sweep.structuralAudit!!
        val structured=audit.intervals.single {
            it.fromThreshold==180 && it.toThreshold==208
        }
        assertEquals(1,structured.holesBefore)
        assertEquals(0,structured.holesAfter)
        assertEquals(0,structured.splitConnectorCandidates.size)
        val base=audit.preview(180,208)
        val overlay=report.overlay(180,208,base)
        for(y in 12..21) assertEquals(
            0xff14c9e5.toInt(),overlay.argb[y*w+45]
        )
        assertEquals(0xff9ba4ad.toInt(),overlay.argb[16*w+34])
        assertEquals(0,overlay.argb[4*w+4])
        assertTrue(source.argb.contentEquals(before))
        assertFalse(report.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun unchangedEnclosedCounterMustNotProduceAnOpeningPath() {
        val source=ring(withOpening=false)
        val report=RasterAlphaCounterLeakPath.inspect(
            source,listOf(180 to 208,208 to 224)
        ).getOrThrow()
        assertTrue(report.intervals.all { it.openedCounters.isEmpty() })
        assertTrue(report.summary().contains("0 vazado(s) aberto(s)"))
    }

    @Test fun invalidScaleOrMismatchedCanvasFailsClosed() {
        val image=ring()
        val report=RasterAlphaCounterLeakPath.inspect(
            image,listOf(180 to 208)
        ).getOrThrow()
        assertTrue(RasterAlphaCounterLeakPath.inspect(
            image,listOf(208 to 180)
        ).isFailure)
        assertTrue(runCatching {
            report.overlay(208,180,image)
        }.isFailure)
        val other=RasterImagePixels(64,64,IntArray(64*64),"outro")
        assertTrue(runCatching {
            report.overlay(180,208,other)
        }.isFailure)
        assertFalse(report.exportPermitted)
    }

    @Test fun copyingTheV3ReceiptRequiresCounterPathsFromSameSource() {
        val image=ring()
        val sweep=RasterAlphaThresholdSweep.inspect(image).getOrThrow()
        val receipt=RasterQaStructuralAuditReceipt.generate(sweep)
            .getOrThrow()
        assertTrue(receipt.startsWith("QA-ABERTURA-DE-VAZADOS-V3"))
        assertTrue(receipt.contains("vazados-com-rota-de-abertura=1"))
        assertTrue(receipt.contains("abertura em (45,12)–(45,21)"))
        assertTrue(receipt.contains("180→208: 1 vazado(s) aberto(s)"))
        assertTrue(RasterQaStructuralAuditReceipt.generate(
            sweep.copy(counterLeakEvidence=null)
        ).isFailure)
        assertFalse(sweep.exportPermitted)
    }
}
