package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic source fixtures, not the original PANOBIANCO file or a Brother
 * machine stitch-out. No profile or overlay authorizes export.
 */
class RasterAlphaThresholdSweepTest {
    private val w=96
    private val h=80
    private val orange=0x00cb6b2c
    private val white=0x00eeeeee

    private fun translucentLetters():RasterImagePixels {
        val pixels=IntArray(w*h)
        for(y in 12..43) for(x in 10..31) {
            pixels[y*w+x]=(220 shl 24) or orange
        }
        for(y in 12..43) for(x in 53..74) {
            pixels[y*w+x]=(220 shl 24) or white
        }
        // Only four full-opacity pixels in an otherwise translucent logo.
        for((x,y) in listOf(12 to 15,28 to 36,55 to 15,71 to 36)) {
            pixels[y*w+x]=0xff000000.toInt() or
                if(x<40) orange else white
        }
        for(y in 11..44) for(x in 9..32) {
            val p=y*w+x
            if(pixels[p]==0)pixels[p]=(110 shl 24) or orange
        }
        for(y in 11..44) for(x in 52..75) {
            val p=y*w+x
            if(pixels[p]==0)pixels[p]=(110 shl 24) or white
        }
        return RasterImagePixels(w,h,pixels,"letras translúcidas QA")
    }

    @Test fun thresholdSweepCountsAndMasksAreMonotonicAndNondestructive() {
        val source=translucentLetters()
        val before=source.argb.copyOf()
        val audit=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        assertEquals(listOf(64,96,128,160,180,208,224,240,255),
            audit.slices.map { it.threshold })
        assertEquals(4,audit.fullyOpaquePixels)
        assertFalse(audit.alpha255AnchorReliable)
        assertEquals(4,audit.slices.last().inkPixels)
        assertEquals(2,audit.slices.single { it.threshold==180 }.components)
        assertTrue(audit.slices.zipWithNext().all { (a,b) ->
            a.inkPixels >= b.inkPixels
        })
        for(level in listOf(128,180,224,255)) {
            val preview=audit.silhouettePreview(level)
            val expected=source.argb.count { it ushr 24 >= level }
            assertEquals(expected,preview.argb.count { it ushr 24 >0 })
        }
        assertTrue(before.contentEquals(source.argb))
        assertTrue(audit.requiresOperatorReview)
        assertFalse(audit.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun sparseOpaqueCoresNoLongerBlockReviewOfIntactTranslucentLetters() {
        val source=translucentLetters()
        val original=source.argb.copyOf()
        val previous=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        assertFalse(previous.opaqueAnchorsReliable)
        assertTrue(previous.unanchoredThresholdIslands==0)
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        val candidate=RasterTextureReconstructionGate.prepare(
            source,study
        ).getOrThrow()
        assertEquals(2,candidate.disconnectedComponents)
        assertFalse(candidate.thresholdEvidence?.alpha255AnchorReliable ?: true)
        assertTrue(candidate.reviewRequired)
        assertFalse(candidate.exportPermitted)
        assertTrue(candidate.flatReference.argb.count { it ushr 24 >0 }>0)
        assertEquals(0,candidate.flatReference.argb[2*w+2])
        assertTrue(source.argb.contentEquals(original))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun supplied313By266AlphaHistogramIsNotProofOfFiveRealBridges() {
        val width=313
        val height=266
        val n=width*height
        assertEquals(83258,n)
        val argb=IntArray(n) {i ->
            when {
                i<55036 -> 0
                i<55036+6845 -> 0x6ea64428
                i<55036+6845+21349 -> 0xdca64428.toInt()
                else -> 0xffa64428.toInt()
            }
        }
        // Histogram matches the diagnostic numbers; geometry deliberately
        // DOES NOT match the user's image and must not be interpreted as it.
        val sample=RasterImagePixels(width,height,argb,
            "histograma sintético, não a foto real")
        val report=RasterAlphaThresholdSweep.inspect(sample).getOrThrow()
        assertEquals(28222,report.visiblePixels)
        assertEquals(28,report.fullyOpaquePixels)
        assertEquals(21377,report.slices.single {
            it.threshold==180
        }.inkPixels)
        assertEquals(28,report.slices.single {
            it.threshold==255
        }.inkPixels)
        assertFalse(report.alpha255AnchorReliable)
        assertTrue(report.summary().contains("Poucos núcleos"))
        assertFalse(report.exportPermitted)
    }

    @Test fun thinAlphaOnlyAccentsAreNeverApprovedAsStitches() {
        val source=translucentLetters()
        source.argb[3*w+3]=0x6ea64428
        val audit=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        assertTrue(audit.visiblePixels>
            audit.slices.single { it.threshold==180 }.inkPixels)
        // The detached faint accent must still be rejected by the separate
        // contour/fidelity gate; the sweep itself is only a preview.
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,study
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun highAlphaBridgeWithReliableOpaqueCoresStillFailsClosed() {
        val pixels=IntArray(w*h)
        for(y in 12..45) for(x in 8..27) pixels[y*w+x]=
            0xff000000.toInt() or orange
        for(y in 12..45) for(x in 47..66) pixels[y*w+x]=
            0xff000000.toInt() or white
        for(x in 28..46) pixels[30*w+x]=(220 shl 24) or orange
        val source=RasterImagePixels(w,h,pixels,"conexão real QA")
        val topology=RasterSourceTopologyDiagnostics.inspect(source)
            .getOrThrow()
        assertTrue(topology.opaqueAnchorsReliable)
        assertEquals(1,topology.highAlphaBridges)
        val study=RasterThreadTextureStudy.inspect(source).getOrThrow()
        assertTrue(RasterTextureReconstructionGate.prepare(
            source,study
        ).isFailure)
    }
}
