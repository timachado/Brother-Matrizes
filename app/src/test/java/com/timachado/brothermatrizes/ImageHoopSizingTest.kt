package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageHoopSizingTest {
    @Test fun widePhotoClampsToHoopUsableWidth() {
        val result = ImageHoopSizing.maximumWidthMm(200, 100, HoopProfile.H100X100)
        assertEquals(HoopProfile.H100X100.usableWidthMm, result, 0.01f)
        assertTrue(ImageHoopSizing.fits(200, 100, result, HoopProfile.H100X100))
        assertFalse(ImageHoopSizing.fits(200, 100, 110f, HoopProfile.H100X100))
    }

    @Test fun tallPhotoClampsWidthToUsableHeight() {
        val hoop = HoopProfile.H100X100
        val maximum = ImageHoopSizing.maximumWidthMm(60, 120, hoop)
        assertEquals(45f, maximum, .01f)
        assertEquals(90f, ImageHoopSizing.heightMm(60, 120, maximum), .01f)
        assertTrue(ImageHoopSizing.fits(60, 120, maximum, hoop))
        assertFalse(ImageHoopSizing.fits(60, 120, 60f, hoop))
    }

    @Test fun largerHoopAllowsLargerPreviewButStillLimitedByEngine() {
        val larger = HoopProfile.H200X300
        assertEquals(200f, ImageHoopSizing.maximumWidthMm(300, 100, larger), .01f)
        assertTrue(ImageHoopSizing.fits(300, 100, 200f, larger))
    }

    @Test fun panobiancoLandscapeFitsEffective130By180Hoop() {
        val hoop = HoopProfile.H130X180
        val (usableW, usableH) = ImageHoopSizing.usableFrameMm(313, 238, hoop)
        assertEquals(170f, usableW, .01f)
        assertEquals(120f, usableH, .01f)
        assertTrue(ImageHoopSizing.fits(313, 238, 145f, hoop))
        assertFalse(ImageHoopSizing.fits(313, 238, 175f, hoop))
    }

    @Test fun portraitArtworkUsesRotatedUsableArea() {
        val hoop = HoopProfile.H130X180
        val (usableW, usableH) = ImageHoopSizing.usableFrameMm(238, 313, hoop)
        assertEquals(120f, usableW, .01f)
        assertEquals(170f, usableH, .01f)
        assertFalse(ImageHoopSizing.fits(238, 313, 145f, hoop))
    }

    @Test fun artworkMustHavePositiveSizeAndFitSelectedHoop() {
        assertFalse(ImageHoopSizing.fits(100, 100, Float.NaN, HoopProfile.H100X100))
        assertTrue(runCatching {
            ImageHoopSizing.maximumWidthMm(0, 70, HoopProfile.H100X100)
        }.isFailure)
    }
}
