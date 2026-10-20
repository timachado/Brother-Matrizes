package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic source only. Not a real PANOBIANCO stitch-out. */
class RasterQaMicrodetailPhysicalGateTest {
    private val w=96
    private val h=80
    private fun sample():RasterQaMicrodetailReport {
        val argb=IntArray(w*h)
        for(y in 12..61)for(x in 20..73) {
            if(x in 29..64 && y in 22..52)continue
            argb[y*w+x]=0xffac6631.toInt()
        }
        for(y in 12..21) argb[y*w+45]=0xc3ac6631.toInt()
        val image=RasterImagePixels(w,h,argb,"furo original sintético")
        val audit=RasterAlphaThresholdSweep.inspect(image).getOrThrow()
        val closure=RasterQaCounterClosureStudy.prepare(audit).getOrThrow()
        return RasterQaMicrodetailReview.inspect(
            closure,audit.counterLeakEvidence!!,96f
        ).getOrThrow()
    }

    @Test fun unclassifiedVisualIntentBlocksAutoStitchAndPes() {
        val detail=sample()
        val original=detail.source.argb.copyOf()
        val gate=RasterQaMicrodetailPhysicalGate.inspect(
            detail,RasterQaMicrodetailIntent.UNCLASSIFIED
        ).getOrThrow()
        assertEquals(detail.sourceFingerprint,gate.imageFingerprint)
        assertFalse(gate.intentReviewed)
        assertFalse(gate.mayAutoDigitize)
        assertFalse(gate.mayExportPes)
        assertTrue(gate.requiresPhysicalValidation)
        assertFalse(gate.physicalStitchOutVerified)
        assertFalse(gate.subMillimetreWidth)
        assertTrue(gate.summary().contains("Intenção visual não classificada"))
        assertTrue(detail.source.argb.contentEquals(original))
    }

    @Test fun markingAsIntentionalIsNotPhysicalEmbroideryApproval() {
        val detail=sample()
        for(intent in listOf(
            RasterQaMicrodetailIntent.APPEARS_INTENTIONAL,
            RasterQaMicrodetailIntent.POSSIBLE_ARTIFACT
        )) {
            val gate=RasterQaMicrodetailPhysicalGate.inspect(detail,intent)
                .getOrThrow()
            assertTrue(gate.intentReviewed)
            assertFalse(gate.mayAutoDigitize)
            assertFalse(gate.mayExportPes)
            assertFalse(gate.physicalStitchOutVerified)
        }
        assertEquals(
            RasterQaMicrodetailIntent.UNCLASSIFIED,
            RasterQaMicrodetailIntent.fromCaption("outro texto")
        )
    }

    @Test fun descriptiveSubMillimetreTagNeverBecomesMinimumStitchRule() {
        val detail=sample()
        // Synthetic geometry emulates 1x5 pixels: 96 mm across 96 px
        // and then 0.4 mm/px at a 38.4 mm wide image.
        val small=detail.copy(
            sourceWidthMm=38.4f,pixelSizeMm=.4f,
            holeMinX=45,holeMaxX=45,
            holeMinY=23,holeMaxY=27,sourceHolePixels=5
        )
        val gate=RasterQaMicrodetailPhysicalGate.inspect(
            small,RasterQaMicrodetailIntent.APPEARS_INTENTIONAL
        ).getOrThrow()
        assertTrue(gate.subMillimetreWidth)
        assertEquals(.4f,gate.detailWidthMm,.00001f)
        assertEquals(2f,gate.detailHeightMm,.00001f)
        assertTrue(gate.summary().contains("alerta descritivo"))
        assertFalse(gate.mayAutoDigitize)
        assertFalse(gate.mayExportPes)
    }

    @Test fun stalePixelsInvalidOrMissingRestorationFailClosed() {
        val detail=sample()
        assertTrue(RasterQaMicrodetailPhysicalGate.inspect(
            detail.copy(sourceFingerprint="outro"),
            RasterQaMicrodetailIntent.UNCLASSIFIED
        ).isFailure)
        assertTrue(RasterQaMicrodetailPhysicalGate.inspect(
            detail.copy(restoredPixels=listOf(0)),
            RasterQaMicrodetailIntent.UNCLASSIFIED
        ).isFailure)
        assertTrue(RasterQaMicrodetailPhysicalGate.inspect(
            detail.copy(counterRestoredInMask=false),
            RasterQaMicrodetailIntent.UNCLASSIFIED
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
