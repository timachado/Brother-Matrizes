package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumImageCrossStitchPreviewTest {
    @Test fun separatedTwoColorImageCreatesTrueXShapesAndNoMachinePES() {
        val size=60
        val argb=IntArray(size*size){i->
            val x=i%size;val y=i/size
            when {
                x in 9..25 && y in 8..24 -> 0xffe3ab45.toInt()
                x in 30..48 && y in 32..49 -> 0xfff0f1f3.toInt()
                else -> 0
            }
        }
        val unchanged=argb.copyOf()
        val d=PremiumImageCrossStitchPreview.create(
            size,size,argb,55f,HoopProfile.H100X100,2
        ).getOrThrow()
        assertEquals("QA_IMAGE_VISUAL",d.format)
        assertTrue(d.stitchCount>20)
        assertTrue(d.threadColors.size>=2)
        assertEquals(d.stitchCount,d.points.count {
            it.command==StitchCommand.STITCH
        })
        assertEquals(d.jumpCount,d.points.count {
            it.command==StitchCommand.JUMP
        })
        assertEquals(d.stitchCount,d.jumpCount)
        assertTrue(d.points.any { it.command==StitchCommand.TRIM })
        assertTrue(d.endFound)
        assertTrue(argb.contentEquals(unchanged))
        assertTrue(HoopValidator.validate(d,HoopProfile.H100X100).fits)
        assertTrue(MatrixConverter.convert(d,"PES").isFailure)
        assertTrue(MatrixConverter.convert(d,"DST").isFailure)
        assertTrue(PremiumImagePhysicalLab.pesForBench(d).isFailure)
    }

    @Test fun transparentAndOversizedDataAreRejected() {
        val empty=IntArray(60*60)
        assertTrue(PremiumImageCrossStitchPreview.create(
            60,60,empty,55f,HoopProfile.H100X100
        ).isFailure)
        assertTrue(PremiumImageCrossStitchPreview.create(
            60,60,empty,140f,HoopProfile.H100X100
        ).isFailure)
    }
}
