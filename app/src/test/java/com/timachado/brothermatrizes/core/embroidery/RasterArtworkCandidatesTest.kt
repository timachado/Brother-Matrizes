package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterArtworkCandidatesTest {
    private fun logoPanels(): RasterImagePixels {
        val w = 180
        val h = 110
        val argb = IntArray(w*h) { 0xFF171717.toInt() }
        for (y in 18..47) for (x in 12..43) argb[y*w+x] = 0xFFF28A25.toInt()
        for (y in 18..47) for (x in 80..112) argb[y*w+x] = 0xFFFFFFFF.toInt()
        for (y in 70..91) for (x in 135..165) argb[y*w+x] = 0xFFD34A3C.toInt()
        return RasterImagePixels(w,h,argb,"logo 3 partes")
    }

    @Test fun findsSeparatedColoredObjectsOnFabric() {
        val regions = RasterArtworkCandidates.detect(logoPanels())
        assertEquals(3,regions.size)
        assertEquals(3,regions.map { it.id }.distinct().size)
        assertTrue(regions.all { it.bounds.left < it.bounds.right && it.bounds.top < it.bounds.bottom })
        val orange = regions.firstOrNull { 25f/180 in it.bounds.left..it.bounds.right &&
            30f/110 in it.bounds.top..it.bounds.bottom }
        val white = regions.firstOrNull { 90f/180 in it.bounds.left..it.bounds.right &&
            30f/110 in it.bounds.top..it.bounds.bottom }
        val red = regions.firstOrNull { 150f/180 in it.bounds.left..it.bounds.right &&
            80f/110 in it.bounds.top..it.bounds.bottom }
        assertTrue(orange != null && white != null && red != null)
        assertTrue(orange != white && red != white && orange != red)
    }

    @Test fun findsTransparentPngFragments() {
        val w=120
        val h=85
        val pixels=IntArray(w*h) { 0x00000000 }
        for (y in 12..41) for (x in 11..35) pixels[y*w+x] = 0xFFE38B22.toInt()
        for (y in 45..70) for (x in 82..110) pixels[y*w+x] = 0xFFE4E4E4.toInt()
        assertEquals(2,RasterArtworkCandidates.detect(
            RasterImagePixels(w,h,pixels,"png com transparência")
        ).size)
    }

    @Test fun uniformBackgroundOrMalformedInputHasNoFalseDetection() {
        assertTrue(RasterArtworkCandidates.detect(RasterImagePixels(
            120,110,IntArray(120*110) { 0xFF111111.toInt() },"fundo"
        )).isEmpty())
        assertTrue(RasterArtworkCandidates.detect(
            RasterImagePixels(50,50,intArrayOf(1,2,3),"incompleto")
        ).isEmpty())
    }

    @Test fun candidateBoundsCanCropRealRasterWithoutProducingWholePhoto() {
        val source=logoPanels()
        val item=RasterArtworkCandidates.detect(source).first()
        val cropped=RasterPhotoPreparation.prepare(
            source,item.bounds,false
        ).getOrThrow().image
        assertTrue(cropped.width < source.width)
        assertTrue(cropped.height < source.height)
        assertTrue(cropped.argb.any { it != 0xFF171717.toInt() })
    }
}
