package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSourceRetentionAuditTest {
    private val navy = 0xff163955.toInt()
    private val gold = 0xffc99236.toInt()
    private val accent = 0xffed21dc.toInt()
    private val white = 0xffffffff.toInt()

    private fun logo(accentPixels: Boolean = false, noisePixel: Boolean = false):
        RasterImagePixels {
        val width=160
        val height=100
        return RasterImagePixels(
            width,height,IntArray(width*height) { i ->
                val x=i%width
                val y=i/width
                when {
                    x in 15..99 && y in 20..29 -> navy
                    x in 17..101 && y in 61..70 -> gold
                    // 6-pixel accent: visually meaningful, but older color
                    // clustering discarded color groups smaller than 10px.
                    accentPixels && x in 120..122 && y in 42..43 -> accent
                    // Isolated single-pixel JPEG noise is not comparable to
                    // a missing 3x2 punctuation/accent object.
                    noisePixel && x==120 && y==42 -> accent
                    else -> white
                }
            },"QA retaining source details"
        )
    }

    @Test fun cleanTwoColorLogoRetainsAllOriginalInk() {
        val report=RasterSourceRetentionAudit
            .requirePreserved(logo()).getOrThrow()
        assertTrue(report.preservesDetails)
        assertEquals(0,report.omittedForegroundPixels)
        assertEquals(0,report.heavilyRecoloredPixels)
        assertEquals(0,report.largestDamagedDetailPixels)
    }

    @Test fun tinyThirdColorAccentIsPreservedForLaterDigitizing() {
        val img=logo(accentPixels=true)
        val segmented=MultiColorRasterDigitizer.separate(
            img,MultiColorRasterOptions(
                maxColors=4,preserveFineDetails=true
            )
        ).getOrThrow()
        // Same pixel patch, original location and genuinely distinct third
        // color. An ink dot's existence is not proof it can be embroidered.
        assertEquals(3,segmented.colors.size)
        val accentIndex=segmented.pixels[42*160+121]
        assertTrue(accentIndex>=0)
        assertTrue(segmented.colors[accentIndex]!=segmented.colors[
            segmented.pixels[24*160+30]
        ])
        val report=RasterSourceRetentionAudit
            .requirePreserved(img).getOrThrow()
        assertTrue(report.preservesDetails)
        assertEquals(0,report.omittedForegroundPixels)
        assertEquals(0,report.heavilyRecoloredPixels)
        // Six pixels at this scale cannot yet be digitized as a tested
        // stitch geometry. The whole-logo composer MUST still fail closed.
        assertTrue(RasterAutomaticLogoPreview.create(
            img,90f,HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun commercialDefaultSegmentationBehaviorRemainsUnchanged() {
        val img=logo(accentPixels=true)
        val legacy=MultiColorRasterDigitizer.separate(
            img, MultiColorRasterOptions(maxColors=4)
        ).getOrThrow()
        // Legacy algorithm filters thread groups under ten pixels.
        // The new preservation policy is isolated behind the QA option.
        assertEquals(-1,legacy.pixels[42*160+121])
        assertEquals(2,legacy.colors.size)
    }

    @Test fun singleIsolatedNoisePixelDoesNotBlockAnOtherwiseCleanLogo() {
        val report=RasterSourceRetentionAudit.inspect(
            logo(noisePixel=true)
        ).getOrThrow()
        assertTrue("One off-color image pixel is not a punctuation object",
            report.preservesDetails)
        assertTrue(report.largestDamagedDetailPixels<4)
        val segmented=MultiColorRasterDigitizer.separate(
            logo(noisePixel=true),
            MultiColorRasterOptions(
                maxColors=4,preserveFineDetails=true
            )
        ).getOrThrow()
        assertEquals(2,segmented.colors.size)
    }

    @Test fun segmentedAndOriginalPixelLengthsMustMatch() {
        val bad=RasterImagePixels(
            160,100,IntArray(15) { navy },"invalid input"
        )
        assertTrue(RasterSourceRetentionAudit.inspect(bad).isFailure)
    }
}
