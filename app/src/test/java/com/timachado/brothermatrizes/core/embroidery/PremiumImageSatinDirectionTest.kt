package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direction regression: the fill must FOLLOW the imported band instead of
 * imposing horizontal rows on every logo, letter, and curved shape.
 * These are simulator previews, never physical Satin approvals.
 */
class PremiumImageSatinDirectionTest {
    private fun preview(
        w:Int=80,h:Int=80,paint:(Int,Int)->Boolean
    ):EmbroideryDesign {
        val image=IntArray(w*h) { index ->
            if(paint(index%w,index/w)) 0xffdba544.toInt() else 0
        }
        val result=PremiumImageSatinVisualPreview.create(
            w,h,image,60f,HoopProfile.H100X100
        )
        assertTrue("Direção visual: " +
            result.exceptionOrNull()?.message,result.isSuccess)
        return result.getOrThrow().also {
            assertTrue(MatrixConverter.convert(it,"PES").isFailure)
            assertTrue(MatrixConverter.convert(it,"DST").isFailure)
        }
    }

    private fun sewnSegments(
        design:EmbroideryDesign
    ):List<Pair<Int,Int>> {
        var last:EmbroideryPoint?=null
        val moves=ArrayList<Pair<Int,Int>>()
        for(p in design.points) {
            when(p.command) {
                StitchCommand.STITCH -> {
                    val previous=last
                    if(previous!=null) moves+=Pair(
                        abs(p.xUnits-previous.xUnits),
                        abs(p.yUnits-previous.yUnits)
                    )
                    last=p
                }
                StitchCommand.JUMP -> last=p
                else -> last=null
            }
        }
        return moves.filter { it.first+it.second>=5 }
    }

    @Test fun verticalLetterStrokeHasPredominantlyTransverseHorizontalSatin() {
        val design=preview { x,y ->
            x in 34..42 && y in 12..66
        }
        val moves=sewnSegments(design)
        assertTrue(moves.size>20)
        val transverse=moves.count {it.first>it.second*2}
        assertTrue("Trilho vertical: Satin deve atravessar horizontalmente",
            transverse > moves.size/3)
    }

    @Test fun horizontalLetterStrokeHasPredominantlyVerticalSatin() {
        val design=preview { x,y ->
            x in 12..66 && y in 34..42
        }
        val moves=sewnSegments(design)
        assertTrue(moves.size>20)
        val transverse=moves.count {it.second>it.first*2}
        assertTrue("Trilho horizontal: Satin deve atravessar verticalmente",
            transverse > moves.size/3)
        // This MUST fail with the previous hardcoded row-scanning generator.
    }

    @Test fun diagonalLetterStrokeProducesTrueObliqueTransverseStitches() {
        val design=preview {x,y ->
            y in 12..65 && abs(x-y)<=4
        }
        val moves=sewnSegments(design)
        assertTrue(moves.size>20)
        val oblique=moves.count {it.first>2 && it.second>2}
        assertTrue("Diagonal local requires diagonal transverse needle path",
            oblique > moves.size/4)
    }

    @Test fun transparentCounterRemainsEmptyAndTheGhostArtworkIsNotModified() {
        val w=80
        val source=IntArray(w*w){index->
            val x=index%w
            val y=index/w
            if(x in 12..66 && y in 12..66 &&
                !(x in 30..48 && y in 30..48))
                0xffe4a42b.toInt() else 0
        }
        val immutable=source.copyOf()
        val result=PremiumImageSatinVisualPreview.create(
            w,w,source,60f,HoopProfile.H100X100
        ).getOrThrow()
        assertTrue(source.contentEquals(immutable))
        // All needle endpoints inside the original transparent opening
        // (coordinates about +/- 7 mm here) must remain absent.
        assertFalse(result.points.any {
            it.command==StitchCommand.STITCH &&
                it.xUnits in -45..45 && it.yUnits in -45..45
        })
        assertTrue(MatrixConverter.convert(result,"PES").isFailure)
        assertTrue(result.sourceBytes.isEmpty())
    }
}
