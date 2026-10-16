package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterPaletteFidelityTest {
    @Test fun removesOrangeSpeckleInsideWhiteButPreservesAlphaAndEdges() {
        val w=20
        val h=20
        val labels=IntArray(w*h) {-1}
        for(y in 3..14) for(x in 3..14) labels[y*w+x]=1
        labels[8*w+8]=0
        labels[0]=0
        labels[17*w+17]=0
        val out=RasterPaletteFidelity.cleanIsolatedColorBleed(labels,w,h,2)
        assertEquals(1,out[8*w+8])
        assertEquals(-1,out[1*w+1])
        assertEquals(0,out[0])
        assertEquals(0,out[17*w+17])
        assertEquals(1,out[4*w+4])
    }

    @Test fun retainsThinColoredStrokesInsteadOfErasingThem() {
        val w=24;val h=20
        val labels=IntArray(w*h) {-1}
        for(y in 2..13) labels[y*w+12]=0
        for(y in 3..14) for(x in 18..22) labels[y*w+x]=1
        val out=RasterPaletteFidelity.cleanIsolatedColorBleed(labels,w,h,2)
        for(y in 2..13) assertEquals(0,out[y*w+12])
        assertEquals(-1,out[8*w+15])
    }

    @Test fun invalidPixelsDoNotCorruptArtwork() {
        assertTrue(runCatching {
            RasterPaletteFidelity.cleanIsolatedColorBleed(intArrayOf(-1,0,3,0),2,2,2)
        }.isFailure)
    }

    @Test fun multicoloRasterRemovesSingleColorBleedBeforeFill() {
        val w=80; val h=56
        val pixels=IntArray(w*h) { 0 }
        for(y in 9..36) for(x in 10..38) pixels[y*w+x]=0xFFF28B1F.toInt()
        for(y in 12..43) for(x in 49..72) pixels[y*w+x]=0xFFF9F9F9.toInt()
        pixels[25*w+57]=0xFFF28B1F.toInt()
        val artwork=MultiColorRasterDigitizer.separate(
            RasterImagePixels(w,h,pixels,"PANOBIANCO"),
            MultiColorRasterOptions(maxColors=2)
        ).getOrThrow()
        val white=artwork.pixels[22*w+57]
        val orange=artwork.pixels[22*w+18]
        assertEquals(2,artwork.colors.size)
        assertTrue(white>=0 && orange>=0 && white!=orange)
        assertEquals(white,artwork.pixels[25*w+57])
        assertEquals(-1,artwork.pixels[2*w+2])
    }

    @Test fun fineTextModeRetainsOnePixelHorizontalSegments() {
        val w=100;val h=45
        val pixels=IntArray(w*h) { 0 }
        for(y in 7..31) for(x in 5..32) pixels[y*w+x]=0xFFF28B1F.toInt()
        for(y in 7..31) for(x in 55..83) pixels[y*w+x]=0xFFF9F9F9.toInt()
        val source=RasterImagePixels(w,h,pixels,"PANOBIANCO")
        val separated=MultiColorRasterDigitizer.separate(
            source,MultiColorRasterOptions(widthMm=68f,maxColors=2)
        ).getOrThrow()
        val normal=MultiColorRasterDigitizer.generateSeparated(
            source,separated,MultiColorRasterOptions(widthMm=68f,maxColors=2)
        ).getOrThrow()
        val fine=MultiColorRasterDigitizer.generateSeparated(
            source,separated,MultiColorRasterOptions(
                widthMm=68f,maxColors=2,preserveFineDetails=true
            )
        ).getOrThrow()
        assertEquals(normal.threadColors.size,fine.threadColors.size)
        assertTrue(fine.stitchCount>=normal.stitchCount)
    }

    @Test fun fineStrokePreflightReportsRiskWithoutBlockingManualReview() {
        val w=100;val h=55
        val p=IntArray(w*h) {-1}
        for(y in 8..42) for(x in 8..73 step 3) p[y*w+x]=0
        val art=SeparatedColorArtwork(
            colors=listOf(0xFFFFFF),pixels=p,minX=8,maxX=74,minY=8,maxY=42
        )
        val report=RasterStitchReadiness.inspect(art,w,h,30f)
        assertTrue(report.fineSegments>0)
        assertTrue(report.warnings.isNotEmpty())
        assertTrue(report.shortSegmentFraction>0.4f)
    }
}
