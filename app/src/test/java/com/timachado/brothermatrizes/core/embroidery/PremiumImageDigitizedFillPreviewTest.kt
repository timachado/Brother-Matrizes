package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Original implementation of documented Artspira-style workflows, QA only. */
class PremiumImageDigitizedFillPreviewTest {
    private val w=80
    private val h=80
    private fun design(
        image:IntArray,colors:Int=4
    ):EmbroideryDesign {
        val r=PremiumImageDigitizedFillPreview.create(
            w,h,image,60f,HoopProfile.H100X100,colors
        )
        assertTrue(r.exceptionOrNull()?.stackTraceToString(),r.isSuccess)
        return r.getOrThrow()
    }

    @Test fun separatedColorsPreserveOrderHolesAndPhysicalExportGuard() {
        val source=IntArray(w*h){i->
            val x=i%w
            val y=i/w
            when {
                x in 20..57 && y in 8..32 &&
                    !(x in 32..43 && y in 15..25) ->
                    0xffffa332.toInt()
                x in 8..70 && y in 48..61 &&
                    ((x-8)%10 in 0..6) -> 0xfff2eeee.toInt()
                else -> 0
            }
        }
        val unchanged=source.copyOf()
        val d=design(source,2)
        assertEquals("QA_IMAGE_VISUAL",d.format)
        assertTrue(d.stitchCount>80)
        assertTrue(d.jumpCount>=4)
        assertTrue(d.threadColors.size>=2)
        assertTrue(d.colorChanges>=1)
        assertEquals(d.stitchCount,d.points.count {
            it.command==StitchCommand.STITCH
        })
        assertEquals(d.jumpCount,d.points.count {
            it.command==StitchCommand.JUMP
        })
        assertTrue(source.contentEquals(unchanged))
        assertTrue(d.points.any {it.command==StitchCommand.TRIM})
        assertTrue(HoopValidator.validate(d,HoopProfile.H100X100).fits)
        for(format in listOf("PES","DST","JEF")) {
            assertTrue(MatrixConverter.convert(d,format).isFailure)
        }
        assertTrue(PremiumImagePhysicalLab.pesForBench(d).isFailure)
    }

    @Test fun fillOrientationUsesComponentShapeNotGlobalHorizontalLines() {
        val tall=IntArray(w*h){i->
            val x=i%w;val y=i/w
            if(x in 35..44 && y in 10..65)0xffd6aa44.toInt() else 0
        }
        val wide=IntArray(w*h){i->
            val x=i%w;val y=i/w
            if(x in 10..65 && y in 35..44)0xffd6aa44.toInt() else 0
        }
        fun directions(d:EmbroideryDesign):Pair<Int,Int> {
            var last:EmbroideryPoint?=null
            var horizontal=0;var vertical=0
            for(p in d.points){
                when(p.command) {
                    StitchCommand.STITCH -> {
                        val prev=last
                        if(prev!=null) {
                            val x=abs(p.xUnits-prev.xUnits)
                            val y=abs(p.yUnits-prev.yUnits)
                            if(x>y*2 && x>0)horizontal++
                            if(y>x*2 && y>0)vertical++
                        }
                        last=p
                    }
                    StitchCommand.JUMP -> last=p
                    else -> last=null
                }
            }
            return Pair(horizontal,vertical)
        }
        val a=directions(design(tall))
        val b=directions(design(wide))
        assertTrue("Vertical stem should get horizontal crosswise stitches: $a",
            a.first > a.second)
        assertTrue("Horizontal glyph should get vertical crosswise stitches: $b",
            b.second > b.first)
    }

    @Test fun opaqueImageAndInvalidPaletteAreRejected() {
        val opaque=IntArray(w*h){0xff99704b.toInt()}
        assertTrue(PremiumImageDigitizedFillPreview.create(
            w,h,opaque,60f,HoopProfile.H100X100,4
        ).isFailure)
        val transparent=IntArray(w*h)
        assertTrue(PremiumImageDigitizedFillPreview.create(
            w,h,transparent,60f,HoopProfile.H100X100,4
        ).isFailure)
        assertTrue(PremiumImageDigitizedFillPreview.create(
            w,h,opaque,60f,HoopProfile.H100X100,9
        ).isFailure)
    }
}
