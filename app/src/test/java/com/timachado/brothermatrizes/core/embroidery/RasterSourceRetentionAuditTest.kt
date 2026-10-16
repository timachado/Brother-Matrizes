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

    @Test fun tinyThirdColorDetailCannotSilentlyDisappear() {
        val img=logo(accentPixels=true)
        val report=RasterSourceRetentionAudit.inspect(img).getOrThrow()
        assertFalse("Small bright accent must not be silently removed",
            report.preservesDetails)
        assertTrue(report.largestDamagedDetailPixels>=4)
        assertTrue(report.omittedForegroundPixels +
            report.heavilyRecoloredPixels>=4)
        val denied=RasterSourceRetentionAudit.requirePreserved(img)
        assertTrue(denied.isFailure)
        assertTrue(denied.exceptionOrNull()?.message.orEmpty()
            .contains("detalhes"))
        // The integrated complete-logo compositor must also fail closed
        // even if the remaining two large bands can be stitched properly.
        assertTrue(RasterAutomaticLogoPreview.create(
            img,90f,HoopProfile.H130X180
        ).isFailure)
    }

    @Test fun singleIsolatedNoisePixelDoesNotBlockAnOtherwiseCleanLogo() {
        val report=RasterSourceRetentionAudit.inspect(
            logo(noisePixel=true)
        ).getOrThrow()
        assertTrue("One off-color image pixel is not a punctuation object",
            report.preservesDetails)
        assertTrue(report.largestDamagedDetailPixels<4)
    }

    @Test fun segmentedAndOriginalPixelLengthsMustMatch() {
        val bad=RasterImagePixels(
            160,100,IntArray(15) { navy },"invalid input"
        )
        assertTrue(RasterSourceRetentionAudit.inspect(bad).isFailure)
    }
}
