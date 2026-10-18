package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterExactColorComponentDetectorTest {
    private val ink = 0xffc57920.toInt()
    private val white = 0xfff8f8f8.toInt()
    private fun image(
        w:Int = 100,h:Int = 80,
        color:(Int,Int)->Int
    )=RasterImagePixels(
        w,h,IntArray(w*h) {i->color(i%w,i/w)},"original exato QA"
    )

    @Test fun transparentTwoColorComponentsKeepEverySourcePixelAndIdentity() {
        val source=image {x,y->
            when {
                x in 10..38 && y in 12..31 -> ink
                x in 55..86 && y in 52..67 -> white
                else -> 0
            }
        }
        val result=RasterExactColorComponentDetector.detect(source).getOrThrow()
        assertEquals(2,result.size)
        assertEquals(listOf(ink,white),result.map {it.color})
        val pixels=result.sumOf {it.pixelCount}
        assertEquals(source.argb.count { it ushr 24>=180 },pixels)
        for(part in result) {
            assertEquals(part.pixelCount,part.mask.pixels.count {it==0})
            for(i in part.mask.pixels.indices)
                if(part.mask.pixels[i]==0) assertEquals(part.color,source.argb[i])
        }
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun opaqueFlatBackgroundIsNotCountedAsEmbroidery() {
        val source=image {x,y->
            when {
                x in 10..38 && y in 12..31 -> ink
                x in 55..86 && y in 52..67 -> 0xff234765.toInt()
                else -> white
            }
        }
        val parts=RasterExactColorComponentDetector.detect(source).getOrThrow()
        assertEquals(2,parts.size)
        assertTrue(parts.none {it.color==white})
    }

    @Test fun textureOrMoreThanFourExactShadesMustNotBecomeFakeNeedleObjects() {
        val source=image {x,y->
            if(x in 10..80 && y in 13..57) {
                val shade=(x+y)%7
                0xff000000.toInt() or (shade shl 16) or 0x003322
            } else 0
        }
        assertTrue(RasterExactColorComponentDetector.detect(source).isFailure)
    }

    @Test fun tinyAccentFailsWholeLogoRatherThanBeingDropped() {
        val source=image {x,y->
            when {
                x in 10..38 && y in 12..31 -> ink
                x in 55..86 && y in 52..67 -> white
                x == 48 && y == 44 -> ink
                else -> 0
            }
        }
        val result=RasterExactColorComponentDetector.detect(source)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("pequeno"))
    }

    @Test fun inconsistentOpacityAndComplexCanvasAreRejected() {
        val partial=image {x,y->
            when {
                x in 10..38 && y in 12..31 -> ink
                x in 55..86 && y in 52..67 -> 0x4400ff00
                else -> 0
            }
        }
        assertTrue(RasterExactColorComponentDetector.detect(partial).isFailure)
        assertTrue(RasterExactColorComponentDetector.detect(
            image {x,y->
                if(x in 10..80 && y in 13..57) ink else 0
            }
        ).isFailure)
    }
}
