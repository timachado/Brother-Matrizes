package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.RasterArtworkAutoCrop
import com.timachado.brothermatrizes.core.embroidery.RasterImagePixels
import com.timachado.brothermatrizes.core.embroidery.RasterPhotoPreparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAutoHoopFlowTest {
    private fun logo(): RasterImagePixels {
        val w = 160; val h = 110
        val pixels = IntArray(w * h) { 0xFF09131B.toInt() }
        for (y in 24..72) for (x in 30..120) {
            pixels[y * w + x] =
                if (x < 67) 0xFFF08B28.toInt() else 0xFFF8F8F8.toInt()
        }
        return RasterImagePixels(w, h, pixels, "logo")
    }

    @Test fun detectsArtworkAndShowsCleanedImageWithoutManualConfirm() {
        val src = logo()
        val region = RasterArtworkAutoCrop.detect(src)
        assertTrue(region.left > 0f)
        assertTrue(region.right < 1f)
        val art = RasterPhotoPreparation.prepare(src, region, true, 55).getOrThrow()
        assertTrue(art.image.width < src.width)
        assertTrue(art.image.height < src.height)
        assertTrue(art.image.argb.count { (it ushr 24) == 0 } > 0)
    }

    @Test fun changingFromSmallToBigHoopExpandsTheArtworkAutomatically() {
        val src = logo()
        val small = ImageHoopSizing.recommendedWidthMm(src.width, src.height, HoopProfile.H100X100)
        val large = ImageHoopSizing.recommendedWidthMm(src.width, src.height, HoopProfile.H130X180)
        assertTrue("Portrait hoop rotates for landscape logo", large > small)
        assertTrue(ImageHoopSizing.fits(src.width,src.height,small,HoopProfile.H100X100))
        assertTrue(ImageHoopSizing.fits(src.width,src.height,large,HoopProfile.H130X180))
    }

    @Test fun sizeSliderChangesLiveMmAndRespectsAspectRatio() {
        val src = logo()
        val maximum = ImageHoopSizing.maximumWidthMm(src.width, src.height, HoopProfile.H130X180)
        val full = maximum * .92f
        val half = maximum * .46f
        assertEquals(full / 2f, half, .001f)
        assertEquals(
            ImageHoopSizing.heightMm(src.width,src.height,full) / 2f,
            ImageHoopSizing.heightMm(src.width,src.height,half), .001f
        )
    }

    @Test fun transparentArtFramingUsesAlpha() {
        val w=80;val h=70;val pixels=IntArray(w*h) { 0 }
        for(y in 18..52) for(x in 12..55) pixels[y*w+x]=0xFFEF8428.toInt()
        val rect = RasterArtworkAutoCrop.detect(RasterImagePixels(w,h,pixels,"alpha"))
        assertTrue(rect.left > 0f && rect.top > 0f &&
            rect.right < 1f && rect.bottom < 1f)
    }
}
