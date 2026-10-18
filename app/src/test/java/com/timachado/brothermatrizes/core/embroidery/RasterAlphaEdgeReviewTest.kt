package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic QA fixtures; they do not certify a real embroidery stitch-out. */
class RasterAlphaEdgeReviewTest {
    private val w=72
    private val h=48
    private val blue=0xff2040ad.toInt()
    private val red=0xffba472d.toInt()

    private fun artwork(background:Int=0):RasterImagePixels {
        val pixels=IntArray(w*h) {background}
        for (y in 12..31) for (x in 9..24) pixels[y*w+x]=blue
        for (y in 12..31) for (x in 39..54) pixels[y*w+x]=red
        return RasterImagePixels(w,h,pixels,"duas letras de QA")
    }

    @Test fun fullyTransparentBackgroundPreservesBothOpaqueComponents() {
        val source=artwork()
        val before=source.argb.copyOf()
        val report=RasterAlphaEdgeReview.inspect(source).getOrThrow()
        assertEquals(0,report.partialPixels)
        assertEquals(640,report.opaquePixels)
        assertEquals(w*h-640,report.transparentPixels)
        assertFalse(report.requiresManualReconstruction)
        assertEquals(2,RasterExactColorComponentDetector.detect(source)
            .getOrThrow().size)
        assertTrue(before.contentEquals(source.argb))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun weakAntiAliasingPixelCannotDisappearFromOriginal() {
        val source=artwork()
        val index=6*w+7
        source.argb[index]=0x7f2040ad
        val copy=source.argb.copyOf()
        val report=RasterAlphaEdgeReview.inspect(source).getOrThrow()
        assertEquals(1,report.weakEdgePixels)
        assertEquals(0,report.strongEdgePixels)
        assertEquals(7,report.firstPartialX)
        assertEquals(6,report.firstPartialY)
        assertTrue(report.guidance().contains("1..179"))
        val attempt=RasterExactColorComponentDetector.detect(source)
        assertTrue(attempt.isFailure)
        assertTrue(attempt.exceptionOrNull()?.message.orEmpty()
            .contains("semitransparentes"))
        assertTrue(source.argb.contentEquals(copy))
    }

    @Test fun highAlphaAntialiasingPixelsAlsoBlockFlattening() {
        for (alpha in listOf(180,220,254)) {
            val source=artwork()
            source.argb[6*w+7]=(alpha shl 24) or 0x002040ad
            val report=RasterAlphaEdgeReview.inspect(source).getOrThrow()
            assertEquals(0,report.weakEdgePixels)
            assertEquals(1,report.strongEdgePixels)
            assertEquals(1,report.partialPixels)
            assertTrue(RasterExactColorComponentDetector.detect(source).isFailure)
        }
    }

    @Test fun opaqueBackgroundWithExactColorsRemainsValid() {
        val original=artwork(background=0xfff5f5f5.toInt())
        val audit=RasterAlphaEdgeReview.inspect(original).getOrThrow()
        assertEquals(0,audit.transparentPixels)
        assertEquals(0,audit.partialPixels)
        assertEquals(w*h,audit.opaquePixels)
        val detected=RasterExactColorComponentDetector.detect(original)
            .getOrThrow()
        assertEquals(2,detected.size)
        assertEquals(blue,detected[0].color)
        assertEquals(red,detected[1].color)
    }

    @Test fun manualReferenceRequiresSeparateExactAlphaFreeSource() {
        val actual=artwork()
        actual.argb[6*w+7]=0xb42040ad.toInt()
        assertTrue(RasterExactColorComponentDetector.detect(actual).isFailure)
        val separatelyReviewed=artwork()
        assertEquals(2,RasterExactColorComponentDetector.detect(
            separatelyReviewed).getOrThrow().size)
        assertEquals(0xb42040ad.toInt(),actual.argb[6*w+7])
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
