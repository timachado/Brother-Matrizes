package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the video report: a dense field used to issue TRIM/JUMP
 * on every stripe and every X-cell. A preview with hundreds of unnecessary
 * trims is NOT a believable or reviewed machine sewing sequence.
 */
class PremiumImageNeedleMotionRegressionTest {
    @Test fun digitizedSolidComponentUsesConnectedStripePlayback() {
        val w=80
        val image=IntArray(w*w){i ->
            val x=i%w;val y=i/w
            if(x in 13..65 && y in 12..65)0xffe4a33a.toInt() else 0
        }
        val design=PremiumImageDigitizedFillPreview.create(
            w,w,image,60f,HoopProfile.H100X100,2
        ).getOrThrow()
        val trims=design.points.count {it.command==StitchCommand.TRIM}
        assertTrue("O campo preenchido não pode cortar a cada faixa",trims<=3)
        assertTrue("As faixas adjacentes devem costurar em sequência",
            design.jumpCount<=6)
        assertTrue(design.stitchCount>100)
        assertTrue(MatrixConverter.convert(design,"PES").isFailure)
    }

    @Test fun separatedInkRegionsNeverConnectByLongSewnJump() {
        val w=80
        val image=IntArray(w*w){i ->
            val x=i%w;val y=i/w
            if((x in 10..25 || x in 52..68) && y in 12..68)
                0xffefa435.toInt() else 0
        }
        val design=PremiumImageDigitizedFillPreview.create(
            w,w,image,60f,HoopProfile.H100X100,2
        ).getOrThrow()
        assertTrue("Elementos separados exigem deslocamento não costurado",
            design.jumpCount>=2)
        var last:EmbroideryPoint?=null
        for(p in design.points) {
            if(p.command==StitchCommand.STITCH) {
                last?.let {
                    val motion=kotlin.math.abs(p.xUnits-it.xUnits)+
                        kotlin.math.abs(p.yUnits-it.yUnits)
                    assertTrue("Movimento costurado muito grande: $motion",
                        motion<=30)
                }
                last=p
            } else if(p.command==StitchCommand.JUMP ||
                p.command==StitchCommand.COLOR_CHANGE ||
                p.command==StitchCommand.TRIM) {
                last=null
            }
        }
        assertTrue(MatrixConverter.convert(design,"DST").isFailure)
    }

    @Test fun crossStitchRegionDoesNotTrimPerIndividualXCell() {
        val w=60
        val image=IntArray(w*w){i ->
            val x=i%w;val y=i/w
            if(x in 7..53 && y in 7..53)0xffc5a04e.toInt() else 0
        }
        val design=PremiumImageCrossStitchPreview.create(
            w,w,image,55f,HoopProfile.H100X100,2
        ).getOrThrow()
        val trims=design.points.count {it.command==StitchCommand.TRIM}
        assertEquals(2*design.jumpCount,2*design.stitchCount)
        assertTrue("Cortes excessivos entre células vizinhas",
            trims<design.stitchCount/6)
        assertTrue(MatrixConverter.convert(design,"PES").isFailure)
    }
}
