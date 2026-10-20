package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic topology, not the unavailable PANOBIANCO original bitmap.
 * Closed perimeter paths contain NO STITCH, Satin, Tatami or PES.
 */
class RasterQaContourGeometryTest {
    private val w=12
    private val h=12

    private fun fixture(openCounter:Boolean=false):RasterImagePixels {
        val image=IntArray(w*h)
        for(y in 2..9)for(x in 2..9) {
            if(x in 5..6 && y in 5..6)continue
            image[y*w+x]=0xf0a63829.toInt()
        }
        if(openCounter)for(y in 2..4) {
            image[y*w+5]=0xc3a63829.toInt()
        }
        return RasterImagePixels(
            w,h,image,"anel sintético de contornos QA"
        )
    }

    @Test fun closedRingKeepsOuterBoundaryAndInnerCounterWithSignedArea() {
        val img=fixture()
        val before=img.argb.copyOf()
        val report=RasterQaContourGeometry.inspect(img,120f,208)
            .getOrThrow()
        assertEquals(60,report.inkPixels)
        assertEquals(1,report.outerContours)
        assertEquals(1,report.counterContours)
        assertEquals(120L,report.loops.sumOf { it.signedTwiceAreaPixels })
        assertEquals(10f,report.mmPerPixel,.00001f)
        val external=report.loops.single { !it.isCounter }
        val internal=report.loops.single { it.isCounter }
        assertEquals(64.0,external.areaPixels,.00001)
        assertEquals(4.0,internal.areaPixels,.00001)
        assertTrue(external.signedTwiceAreaPixels>0L)
        assertTrue(internal.signedTwiceAreaPixels<0L)
        assertEquals(32,external.perimeterPixels)
        assertEquals(8,internal.perimeterPixels)
        assertTrue(external.vertices.first()==external.vertices.last())
        assertTrue(internal.vertices.first()==internal.vertices.last())
        assertEquals(RasterSourceTopologyDiagnostics.inspect(img)
            .getOrThrow().fingerprint,report.fingerprint)
        assertTrue(report.summary().contains("contornos-externos=1"))
        assertTrue(report.summary().contains("vazados=1"))
        val preview=report.boundaryPreview()
        for(i in preview.argb.indices)if((img.argb[i] ushr 24)<208)
            assertEquals(0,preview.argb[i])
        assertTrue(img.argb.contentEquals(before))
        assertFalse(report.exportPermitted)
        assertTrue(report.requiresOperatorReview)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun openingCounterAt208IsSeenOnlyInGeometryNotRepaired() {
        val img=fixture(openCounter=true)
        val before=img.argb.copyOf()
        val low=RasterQaContourGeometry.inspect(img,120f,180)
            .getOrThrow()
        val high=RasterQaContourGeometry.inspect(img,120f,208)
            .getOrThrow()
        assertEquals(1,low.counterContours)
        assertEquals(0,high.counterContours)
        assertEquals(60,low.inkPixels)
        assertEquals(57,high.inkPixels)
        assertTrue(img.argb.contentEquals(before))
        assertFalse(high.exportPermitted)
    }

    @Test fun diagonalInkTouchesFailClosedInsteadOfMergingContours() {
        val image=IntArray(w*h)
        image[3*w+3]=0xf0ffffff.toInt()
        image[4*w+4]=0xf0ffffff.toInt()
        val source=RasterImagePixels(w,h,image,"ponto diagonal")
        val r=RasterQaContourGeometry.inspect(source,50f)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains(
            "ambíguo"
        ))
    }

    @Test fun dimensionsAndEmptyImagesCannotBecomeContours() {
        val ring=fixture()
        assertTrue(RasterQaContourGeometry.inspect(
            ring,Float.NaN
        ).isFailure)
        assertTrue(RasterQaContourGeometry.inspect(
            ring,0f
        ).isFailure)
        assertTrue(RasterQaContourGeometry.inspect(
            ring,120f,255
        ).isFailure)
        val empty=RasterImagePixels(w,h,IntArray(w*h),"transparente")
        assertTrue(RasterQaContourGeometry.inspect(empty,120f).isFailure)
        val full=RasterImagePixels(w,h,IntArray(w*h){
            0xffffffff.toInt()
        },"opaco")
        assertTrue(RasterQaContourGeometry.inspect(full,120f).isFailure)
    }
}
