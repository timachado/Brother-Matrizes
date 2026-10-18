package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterLetterTopologyReviewTest {
    private val color = 0xffcb762a.toInt()
    private val width = 120
    private val height = 100

    private fun fixture(ink: (Int,Int)->Boolean):
        Pair<RasterImagePixels,RasterSatinObject> {
        val argb=IntArray(width*height) { index ->
            if(ink(index%width,index/width)) color else 0
        }
        val source=RasterImagePixels(
            width,height,argb,"letra real de QA"
        )
        val count=argb.count {it ushr 24 >= 180}
        require(count>=15)
        var left=width
        var top=height
        var right=0
        var bottom=0
        for (i in argb.indices) if(argb[i]!=0) {
            val x=i%width
            val y=i/width
            left=minOf(left,x)
            top=minOf(top,y)
            right=maxOf(right,x)
            bottom=maxOf(bottom,y)
        }
        val mask=SeparatedColorArtwork(
            colors=listOf(color),
            pixels=IntArray(argb.size) {i->if(argb[i]!=0)0 else -1},
            minX=left,maxX=right,minY=top,maxY=bottom
        )
        return source to RasterSatinObject(1,color,count,mask)
    }

    @Test fun simpleSolidLetterStemHasNoFalseCounters() {
        val (source,part)=fixture {x,y->
            x in 20..42 && y in 15..80
        }
        val r=RasterLetterTopologyReview.inspect(source,part).getOrThrow()
        assertEquals(0,r.enclosedCounters)
        assertEquals(0,r.smallestCounterPixels)
        assertEquals(0,r.ambiguousDiagonalContacts)
        assertEquals(4,r.contourCornerCount)
        assertTrue(r.vectorOutlineValid)
        assertFalse(r.manualGeometryRequired)
        assertFalse(r.exportPermitted)
    }

    @Test fun letterOHasOneCounterWithOriginalHoleUnpainted() {
        val (source,part)=fixture {x,y->
            x in 20..69 && y in 16..80 &&
                !(x in 30..58 && y in 28..65)
        }
        val r=RasterLetterTopologyReview.inspect(source,part).getOrThrow()
        assertEquals(1,r.enclosedCounters)
        assertEquals(29*38,r.smallestCounterPixels)
        assertEquals(0,r.ambiguousDiagonalContacts)
        assertTrue(r.vectorOutlineValid)
        assertTrue(r.guidance().contains("proteger o centro"))
        assertEquals(0,source.argb[40*width+44])
    }

    @Test fun letterBWithTwoCountersRequiresIndependentReview() {
        val (source,part)=fixture {x,y->
            x in 18..77 && y in 10..88 &&
                !(x in 31..62 && y in 21..39) &&
                !(x in 31..62 && y in 57..77)
        }
        val r=RasterLetterTopologyReview.inspect(source,part).getOrThrow()
        assertEquals(2,r.enclosedCounters)
        assertEquals(32*19,r.smallestCounterPixels)
        assertTrue(r.guidance().contains("2 vazado"))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun tinyLetterCounterIsFlaggedBeforeGeneratingSatin() {
        val (source,part)=fixture {x,y->
            x in 20..69 && y in 16..80 &&
                !(x in 35..37 && y in 40..42)
        }
        val r=RasterLetterTopologyReview.inspect(source,part).getOrThrow()
        assertEquals(1,r.enclosedCounters)
        assertEquals(9,r.smallestCounterPixels)
        assertTrue(r.manualGeometryRequired)
        assertTrue(r.guidance().contains("menor que 16 px"))
    }

    @Test fun diagonalOnlyContactCannotBecomeAutomaticCurve() {
        val (source,part)=fixture {x,y->
            (x in 20..34 && y in 20..34) ||
                (x in 35..49 && y in 35..49)
        }
        val r=RasterLetterTopologyReview.inspect(source,part).getOrThrow()
        assertTrue(r.ambiguousDiagonalContacts>0)
        assertFalse(r.vectorOutlineValid)
        assertTrue(r.manualGeometryRequired)
        assertTrue(r.guidance().contains("separar manualmente"))
    }

    @Test fun changedColorOrCountIsAlwaysRejected() {
        val (source,part)=fixture {x,y->
            x in 20..69 && y in 16..80
        }
        assertTrue(RasterLetterTopologyReview.inspect(
            source,part.copy(color=0xff317cc2.toInt())
        ).isFailure)
        assertTrue(RasterLetterTopologyReview.inspect(
            source,part.copy(pixelCount=part.pixelCount-1)
        ).isFailure)
        assertTrue(RasterLetterTopologyReview.inspect(
            source,part.copy(mask=part.mask.copy(
                pixels=part.mask.pixels.copyOf().also {
                    it[25*width+25]=-1
                }
            ))
        ).isFailure)
    }
}
