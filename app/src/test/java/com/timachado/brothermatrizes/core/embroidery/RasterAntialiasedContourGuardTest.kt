package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic alpha/ink fixtures; output remains an operator-reviewed source
 * reference, never production PES or evidence of a physical Brother sew-out.
 */
class RasterAntialiasedContourGuardTest {
    private val w=96
    private val h=80
    private val orange=0xffd97221.toInt()
    private val white=0xffe4e4e4.toInt()

    private fun outlinedLogo():RasterImagePixels {
        val pixels=IntArray(w*h)
        for(y in 14..51) for(x in 12..35) pixels[y*w+x]=orange
        for(y in 14..51) for(x in 57..80) pixels[y*w+x]=white
        // One-pixel anti-aliased perimeter on BOTH components.
        for(y in 13..52) for(x in 11..36) {
            val index=y*w+x
            if(pixels[index]==0) pixels[index]=0x77d97221
        }
        for(y in 13..52) for(x in 56..81) {
            val index=y*w+x
            if(pixels[index]==0) pixels[index]=0x77e4e4e4
        }
        return RasterImagePixels(w,h,pixels,"duas letras com franja QA")
    }

    @Test fun legitimateAntialiasingOverFivePercentCanReachReviewedStitchSource() {
        val source=outlinedLogo()
        val original=source.argb.copyOf()
        val evidence=RasterAntialiasedContourGuard.inspect(source)
            .getOrThrow()
        assertEquals(2,evidence.solidIslands)
        assertEquals(0,evidence.strongPartialAlphaPixels)
        assertEquals(1,evidence.maxWeakDistancePixels)
        assertEquals(1824,evidence.solidPixels)
        assertTrue(evidence.weakAlphaPixels>evidence.solidPixels*.05)
        assertTrue(evidence.weakAlphaPixels<evidence.solidPixels*.65)

        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        val ready=RasterTextureReconstructionGate.prepare(
            source,study
        ).getOrThrow()
        assertEquals(evidence.weakAlphaPixels,ready.antialiasedEdgePixels)
        assertEquals(evidence.weakAlphaPixels,
            ready.contourEvidence?.weakAlphaPixels)
        assertEquals(2,ready.disconnectedComponents)
        assertEquals(0,ready.flatReference.argb[13*w+25])
        assertEquals(0,ready.flatReference.argb[20*w+46])
        assertEquals(ready.sourceOpaquePixels,
            ready.flatReference.argb.count { it ushr 24>=180 })
        assertTrue(source.argb.contentEquals(original))
        assertFalse(ready.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun fullyOpaqueImageStrokeWithNoFringeStillPassesContourGate() {
        val source=outlinedLogo()
        val clean=RasterImagePixels(w,h,IntArray(w*h) { index ->
            val c=source.argb[index]
            if(c ushr 24<180) 0 else c
        },"logo sem anti-alias")
        val evidence=RasterAntialiasedContourGuard.inspect(clean)
            .getOrThrow()
        assertEquals(0,evidence.weakAlphaPixels)
        assertEquals(0,evidence.maxWeakDistancePixels)
        assertEquals(2,evidence.solidIslands)
        val study=RasterThreadTextureStudy.inspect(clean).getOrThrow()
        assertTrue(RasterTextureReconstructionGate.prepare(
            clean,study
        ).isSuccess)
    }

    @Test fun detachedFaintAccentCannotBeSilentlyDiscarded() {
        val source=outlinedLogo()
        source.argb[4*w+4]=0x77d97221
        val original=source.argb.copyOf()
        val report=RasterAntialiasedContourGuard.inspect(source)
        assertTrue(report.isFailure)
        assertTrue(report.exceptionOrNull()?.message.orEmpty()
            .contains("isolado"))
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,study
        ).isFailure)
        assertTrue(source.argb.contentEquals(original))
    }

    @Test fun weakAlphaCannotQuietlyBridgeTwoSeparateLetters() {
        val pixels=IntArray(w*h)
        for(y in 20..50) for(x in 8..20) pixels[y*w+x]=orange
        for(y in 20..50) for(x in 24..36) pixels[y*w+x]=white
        pixels[30*w+22]=0x77d97221
        val source=RasterImagePixels(w,h,pixels,"duas letras próximas")
        val attempt=RasterAntialiasedContourGuard.inspect(source)
        assertTrue(attempt.isFailure)
        assertTrue(attempt.exceptionOrNull()?.message.orEmpty()
            .contains("junta duas letras"))
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,RasterThreadTextureStudy.inspect(source).getOrThrow()
        ).isFailure)
    }

    @Test fun strongAlphaEdgesAreCountedButNeverEnablePes() {
        val source=outlinedLogo()
        source.argb[24*w+20]=0xdcd97221.toInt()
        val contour=RasterAntialiasedContourGuard.inspect(source)
            .getOrThrow()
        assertEquals(1,contour.strongPartialAlphaPixels)
        val candidate=RasterTextureReconstructionGate.prepare(
            source,RasterThreadTextureStudy.inspect(source).getOrThrow()
        ).getOrThrow()
        assertEquals(1,candidate.contourEvidence?.strongPartialAlphaPixels)
        assertEquals(0xff000000.toInt(),candidate.flatReference.argb[24*w+20] and 0xff000000.toInt())
        assertTrue(candidate.reviewRequired)
        assertFalse(candidate.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
