package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterImageSamplingAuditTest {
    private val white = 0xffffffff.toInt()
    private val ink = 0xff173c57.toInt()
    private val hash = "b".repeat(64)

    private fun image(
        width: Int, height: Int,
        pixel: (Int, Int) -> Int
    ): RasterImagePixels {
        val colors = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            colors[y * width + x] = pixel(x, y)
        }
        return RasterImagePixels(width, height, colors, "imagem QA")
    }

    @Test fun smallColoredDetailDiscardedInDownsamplingIsReported() {
        val high = image(128, 128) { x, y ->
            if (x in 40..41 && y in 40..41) ink else white
        }
        val low = image(64, 64) { _, _ -> white }
        val evidence = RasterImageSamplingAudit.inspect(
            high, low, 128, 128, hash
        ).getOrThrow()
        assertEquals(4, evidence.foregroundReferencePixels)
        assertEquals(4, evidence.potentiallyLostPixels)
        assertEquals(4, evidence.largestPotentiallyLostRegion)
        assertTrue(evidence.needsDetailReview)
        assertTrue(evidence.referenceIsFullResolution)
        assertFalse(evidence.workingIsFullResolution)
        assertFalse(evidence.isFullSourceProven)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun preservedSolidLogoDoesNotRaiseSpuriousDetailWarning() {
        val high = image(128, 128) { x, y ->
            if (x in 32..95 && y in 32..95) ink else white
        }
        val low = image(64, 64) { x, y ->
            if (x in 16..47 && y in 16..47) ink else white
        }
        val evidence = RasterImageSamplingAudit.inspect(
            high, low, 128, 128, hash
        ).getOrThrow()
        assertTrue(evidence.foregroundReferencePixels > 100)
        assertEquals(0, evidence.potentiallyLostPixels)
        assertFalse(evidence.needsDetailReview)
        assertFalse(evidence.isFullSourceProven)
    }

    @Test fun existingLowResolutionBitmapIsNotClaimedToProveSewnFidelity() {
        val source = image(64, 64) { x, y ->
            if (x in 20..42 && y in 20..42) ink else white
        }
        val result = RasterImageSamplingAudit.inspect(
            source, source, 64, 64, hash
        ).getOrThrow()
        assertTrue(result.workingIsFullResolution)
        assertTrue(result.referenceIsFullResolution)
        assertEquals(0, result.potentiallyLostPixels)
        assertFalse(result.isFullSourceProven)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun largeOriginalCannotMasqueradeAsFullResolutionReference() {
        val reference = image(128, 128) { x, y ->
            if (x in 30..90 && y in 30..90) ink else white
        }
        val working = image(64, 64) { x, y ->
            if (x in 15..45 && y in 15..45) ink else white
        }
        val report = RasterImageSamplingAudit.inspect(
            reference, working, 4096, 4096, hash
        ).getOrThrow()
        assertFalse(report.referenceIsFullResolution)
        assertFalse(report.isFullSourceProven)
    }

    @Test fun mismatchedAspectRatioOrFakeSignatureFailClosed() {
        val reference = image(128, 128) { _, _ -> white }
        val working = image(64, 64) { _, _ -> white }
        assertTrue(RasterImageSamplingAudit.inspect(
            reference, working, 128, 128, "not-sha"
        ).isFailure)
        assertTrue(RasterImageSamplingAudit.inspect(
            reference, working, 128, 100, hash
        ).isFailure)
        assertTrue(RasterImageSamplingAudit.inspect(
            working, reference, 128, 128, hash
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun lostDetailInSubtitleDoesNotBlockUnrelatedSelectedLogoRegion() {
        val high = image(128, 128) { x, y ->
            if ((x in 10..40 && y in 15..45) ||
                (x in 100..101 && y in 100..101)) ink else white
        }
        val low = image(64, 64) { x, y ->
            if (x in 5..20 && y in 7..22) ink else white
        }
        val full = RasterImageSamplingAudit.inspect(
            high, low, 128, 128, hash
        ).getOrThrow()
        assertTrue(full.needsDetailReview)
        val logoOnly = RasterImageSamplingAudit.inspectRegion(
            full, RasterCropBounds(0f, 0f, .5f, .5f)
        ).getOrThrow()
        assertTrue(logoOnly.foregroundReferencePixels > 100)
        assertEquals(0, logoOnly.potentiallyLostPixels)
        assertFalse(logoOnly.needsDetailReview)
        val subtitleOnly = RasterImageSamplingAudit.inspectRegion(
            full, RasterCropBounds(.70f, .70f, 1f, 1f)
        ).getOrThrow()
        assertEquals(4, subtitleOnly.potentiallyLostPixels)
        assertEquals(4, subtitleOnly.largestPotentiallyLostRegion)
        assertTrue(subtitleOnly.needsDetailReview)
        assertEquals(full.sha256, logoOnly.sha256)
        assertEquals(full.sourceWidth, logoOnly.sourceWidth)
        assertEquals("recorte selecionado", logoOnly.inspectedRegion)
        assertFalse(logoOnly.isFullSourceProven)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun invalidCropCannotHideEvidenceOfMissingDetails() {
        val high = image(128, 128) { x, y ->
            if (x in 100..101 && y in 100..101) ink else white
        }
        val low = image(64, 64) { _, _ -> white }
        val source = RasterImageSamplingAudit.inspect(
            high, low, 128, 128, hash
        ).getOrThrow()
        assertTrue(RasterImageSamplingAudit.inspectRegion(
            source, RasterCropBounds(Float.NaN, 0f, 1f, 1f)
        ).isFailure)
        assertTrue(RasterImageSamplingAudit.inspectRegion(
            source, RasterCropBounds(1f, 0f, 0f, 1f)
        ).isFailure)
    }

    @Test fun sameResolutionWithAlteredColorIsDetected() {
        val reference = image(64, 64) { x, y ->
            if (x in 15..25 && y in 15..25) ink else white
        }
        val different = image(64, 64) { _, _ -> white }
        val result = RasterImageSamplingAudit.inspect(
            reference, different, 64, 64, hash
        ).getOrThrow()
        assertTrue(result.needsDetailReview)
        assertTrue(result.potentiallyLostPixels > 0)
    }
}
