package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterPhotoPreparationTest {
    private fun orangeLogoOnBlack(): RasterImagePixels {
        val w = 100
        val h = 74
        val pixels = IntArray(w * h) { 0xFF151515.toInt() }
        for (y in 15..54) for (x in 20..49) {
            if (x in 29..39 && y in 27..40) continue // fabric enclosed by logo
            pixels[y * w + x] = 0xFFF28A25.toInt()
        }
        for (y in 20..47) for (x in 61..82) {
            pixels[y * w + x] = 0xFFF7F7F7.toInt()
        }
        return RasterImagePixels(w,h,pixels,"Logo em tecido")
    }

    @Test fun removesConnectedFabricButPreservesLogoColorsAndInteriorCounter() {
        val original = orangeLogoOnBlack()
        val prepared = RasterPhotoPreparation.prepare(
            original, RasterCropBounds(0f,0f,1f,1f),true,55
        ).getOrThrow()
        assertTrue(prepared.removedBackgroundPixels > 3000)
        assertEquals(0, prepared.image.argb[2*100+2] ushr 24)
        assertEquals(0xFFF28A25.toInt(), prepared.image.argb[16*100+25])
        assertEquals(0xFFF7F7F7.toInt(), prepared.image.argb[25*100+65])
        assertEquals(0xFF151515.toInt(), prepared.image.argb[32*100+33])
        val layers = MultiColorRasterDigitizer.separate(
            prepared.image,MultiColorRasterOptions(widthMm = 68f, maxColors = 2)
        ).getOrThrow()
        assertEquals(2,layers.colors.size)
        assertEquals(-1,layers.pixels[2*100+2])
        assertTrue(layers.pixels[16*100+25] >= 0)
        assertTrue(layers.pixels[25*100+65] >= 0)
    }

    @Test fun transparentAlphaIsPreservedByCropWithoutRemoval() {
        val w=40
        val h=30
        val p=IntArray(w*h) { 0 }
        for(y in 8..18) for(x in 9..30) p[y*w+x] = 0xFFDD7722.toInt()
        val img=RasterImagePixels(w,h,p,"PNG alfa")
        val out=RasterPhotoPreparation.prepare(
            img,RasterCropBounds(.2f,.2f,.85f,.8f),false
        ).getOrThrow()
        assertEquals(26,out.image.width)
        assertEquals(18,out.image.height)
        assertEquals(0,out.removedBackgroundPixels)
        assertTrue(out.image.argb.any { it ushr 24 == 0 })
        assertTrue(out.image.argb.any { it ushr 24 == 255 })
    }

    @Test fun allowsJustCroppingWithoutBackgroundRemoval() {
        val image=orangeLogoOnBlack()
        val out=RasterPhotoPreparation.prepare(
            image,RasterCropBounds(.15f,.15f,.88f,.82f),false
        ).getOrThrow()
        assertEquals(73,out.image.width)
        assertEquals(50,out.image.height)
        assertEquals(0,out.removedBackgroundPixels)
        assertEquals(image.argb[12*image.width + 15],out.image.argb[0])
    }

    @Test fun rejectsInvalidBoundsNoDrawingAndTooSmallSelections() {
        val image=orangeLogoOnBlack()
        assertTrue(RasterPhotoPreparation.prepare(
            image,RasterCropBounds(.5f,.3f,.2f,.8f),true).isFailure)
        assertTrue(RasterPhotoPreparation.prepare(
            image,RasterCropBounds(.01f,.01f,.04f,.04f),true).isFailure)
        assertTrue(RasterPhotoPreparation.prepare(
            RasterImagePixels(40,40,IntArray(1600) { 0xFF090909.toInt() },"sem logo"),
            RasterCropBounds(0f,0f,1f,1f),true).isFailure)
    }

    @Test fun malformedInputNeverTriggersBackgroundRemoval() {
        assertTrue(RasterPhotoPreparation.prepare(
            RasterImagePixels(40,40,intArrayOf(1,2,3),"inválida"),
            RasterCropBounds(),true).isFailure)
    }

    @Test fun preparedPhotoProducesReopenablePes() {
        val prep=RasterPhotoPreparation.prepare(
            orangeLogoOnBlack(),RasterCropBounds(0f,0f,1f,1f),true
        ).getOrThrow()
        val design=MultiColorRasterDigitizer.generate(
            prep.image, MultiColorRasterOptions(widthMm = 70f, maxColors = 2)
        ).getOrThrow()
        assertEquals(2,design.threadColors.size)
        val opened=GeneratedMatrixPipeline.canonicalize(design,"crop-bg-qa")
        assertTrue(opened is EmbroideryLoadResult.Success)
        assertTrue((opened as EmbroideryLoadResult.Success).design.sourceBytes.isNotEmpty())
    }
}
