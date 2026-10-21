package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preview-only golden tests for the ghost + progressive visual Satin UI. */
class PremiumImageSatinVisualPreviewTest {
    private fun logo():IntArray {
        val w=156
        val h=156
        return IntArray(w*h){ i ->
            val x=i%w
            val y=i/w
            when {
                // orange emblem with a transparent counter, at top
                x in 54..101 && y in 14..69 &&
                    !(x in 67..88 && y in 26..56) ->
                    0xffffa22e.toInt()
                // PANOBIANCO-like separated letters below
                y in 88..110 && x in 10..145 &&
                    ((x-10)%14 in 0..8) ->
                    0xffeeeeee.toInt()
                // ACADEMIAS-like smaller separated glyphs
                y in 121..131 && x in 29..132 &&
                    (x%10 in 0..5) ->
                    0xffefefef.toInt()
                else -> 0
            }
        }
    }

    @Test fun panobiancoLikeArtProducesSatinCoverageInOriginalLayout() {
        val original=logo()
        val snapshot=original.copyOf()
        val result=PremiumImageSatinVisualPreview.create(
            156,156,original,60f,HoopProfile.H130X180
        ).getOrThrow()
        assertEquals("QA_IMAGE_VISUAL",result.format)
        assertTrue(result.stitchCount>400)
        assertTrue(result.threadColors.size>=2)
        assertTrue(result.colorChanges>=1)
        assertTrue(result.jumpCount>4)
        assertTrue(result.points.any { it.command==StitchCommand.TRIM })
        assertEquals(result.stitchCount,result.points.count {
            it.command==StitchCommand.STITCH
        })
        assertEquals(result.jumpCount,result.points.count {
            it.command==StitchCommand.JUMP
        })
        assertTrue(original.contentEquals(snapshot))
        // The first stitch is from the upper logo. Layout-driven order
        // starts at the emblem, then lettering, without hardcoding brand.
        val first=result.points.first { it.command==StitchCommand.JUMP }
        assertTrue(first.yUnits>0)
        assertTrue(result.points.last().command==StitchCommand.END)
        assertTrue(HoopValidator.validate(result,HoopProfile.H130X180).fits)
        assertTrue(MatrixConverter.convert(result,"PES").isFailure)
        assertTrue(MatrixConverter.convert(result,"DST").isFailure)
        assertTrue(MatrixConverter.convert(result,"JEF").isFailure)
        assertTrue(PremiumImagePhysicalLab.pesForBench(result).isFailure)
    }

    @Test fun satinLikeZigzagStitchesFillBothRailsAndAvoidTransparentHole() {
        val width=80
        val pixels=IntArray(width*80){i->
            val x=i%width
            val y=i/width
            if(x in 12..67 && y in 12..67 &&
                !(x in 30..49 && y in 30..49))
                0xffe7ad4e.toInt() else 0
        }
        val result=PremiumImageSatinVisualPreview.create(
            width,80,pixels,60f,HoopProfile.H100X100
        ).getOrThrow()
        val stitches=result.points.filter { it.command==StitchCommand.STITCH }
        assertTrue(stitches.size>120)
        // Stitches arrive at BOTH horizontal sides of the outer band,
        // unlike the obsolete single-pixel-width perimeter-only tracer.
        assertTrue(stitches.any{it.xUnits < -180 && it.yUnits > 0})
        assertTrue(stitches.any{it.xUnits > 180 && it.yUnits > 0})
        // The center of the 20x20 transparent counter is never stitched.
        assertFalse(stitches.any {
            it.xUnits in -50..50 && it.yUnits in -50..50
        })
        assertTrue(result.jumpCount>=2)
        assertTrue(MatrixConverter.convert(result,"PES").isFailure)
    }

    @Test fun photosAndHoopOverflowStayExcludedFromVisualSatin() {
        assertTrue(PremiumImageSatinVisualPreview.create(
            80,80,IntArray(6400){0xff7ab938.toInt()},
            60f,HoopProfile.H100X100
        ).isFailure)
        assertTrue(PremiumImageSatinVisualPreview.create(
            156,156,logo(),100f,HoopProfile.H100X100
        ).isFailure)
    }
}
