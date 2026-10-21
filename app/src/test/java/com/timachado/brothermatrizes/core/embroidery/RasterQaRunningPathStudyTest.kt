package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A geometrical proof of candidate waypoints, NEVER an approval of actual
 * running machine stitches, machine settings, PES or physical embroidery.
 */
class RasterQaRunningPathStudyTest {
    private val width=12
    private val height=12

    private fun ring():RasterImagePixels {
        val colors=IntArray(width*height)
        for(y in 2..9)for(x in 2..9) {
            if(x in 5..6 && y in 5..6)continue
            colors[y*width+x]=0xf0a63829.toInt()
        }
        return RasterImagePixels(width,height,colors,"anel sintético QA")
    }

    @Test fun eachSourceLoopKeepsItsOwnClosedMillimetrePath() {
        val source=ring()
        val original=source.argb.copyOf()
        val geom=RasterQaContourGeometry.inspect(
            source,120f,208
        ).getOrThrow()
        val plan=RasterQaRunningPathStudy.prepare(
            geom,5f
        ).getOrThrow()
        assertEquals(2,plan.outlines.size)
        assertEquals(1,plan.outlines.count { it.isCounter })
        assertEquals(2,plan.outlines.count { it.isClosed })
        assertEquals(65,plan.outlines.single { !it.isCounter }.waypoints.size)
        assertEquals(17,plan.outlines.single { it.isCounter }.waypoints.size)
        assertEquals(geom.fingerprint,plan.sourceFingerprint)
        assertTrue(plan.needsConnectionPlanning)
        assertTrue(plan.needsOperatorReview)
        assertFalse(plan.exportPermitted)
        assertTrue(source.argb.contentEquals(original))
        for(outline in plan.outlines) {
            assertEquals(outline.waypoints.first(),outline.waypoints.last())
            assertTrue(outline.waypoints.all {
                it.x.isFinite() && it.y.isFinite() &&
                    it.x>=0f && it.y>=0f &&
                    it.x<=120f && it.y<=120f
            })
            outline.waypoints.zipWithNext().forEach { (a,b) ->
                assertTrue(
                    "A distância entre coordenadas QA não deve exceder " +
                        "a distância amostrada do contorno.",
                    hypot(
                        (a.x-b.x).toDouble(),(a.y-b.y).toDouble()
                    )<=5.001
                )
            }
        }
        assertTrue(plan.summary().contains("SEM STITCH/JUMP/TRIM/PES"))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun provenanceAndGeometryChangesAreRejected() {
        val source=ring()
        val geom=RasterQaContourGeometry.inspect(source,120f).getOrThrow()
        assertTrue(RasterQaRunningPathStudy.prepare(
            geom.copy(fingerprint="modificado")
        ).isFailure)
        assertTrue(RasterQaRunningPathStudy.prepare(
            geom.copy(mmPerPixel=1f)
        ).isFailure)
        assertTrue(RasterQaRunningPathStudy.prepare(
            geom.copy(loops=geom.loops.mapIndexed { index,loop ->
                if(index==0)loop.copy(signedTwiceAreaPixels=1L)
                else loop
            })
        ).isFailure)
        val stale=RasterImagePixels(width,height,source.argb.copyOf(),
            "outro bitmap")
        stale.argb[0]=0xffffffff.toInt()
        assertTrue(RasterQaRunningPathStudy.prepare(
            geom.copy(source=stale)
        ).isFailure)
    }

    @Test fun mustNeverAssumeOnePixelMicroDetailsAreStitchSafe() {
        val source=ring()
        val geometry=RasterQaContourGeometry.inspect(source,12f).getOrThrow()
        val report=RasterQaRunningPathStudy.prepare(
            geometry,1.5f
        ).getOrThrow()
        assertEquals(2,report.outlines.size)
        assertTrue(report.outlines.all { it.isClosed })
        assertFalse(report.exportPermitted)
        assertTrue(report.summary().contains("SEM STITCH"))
    }

    @Test fun unsupportedSpacingAndExcessivelyDensePathFailClosed() {
        val source=ring()
        val geometry=RasterQaContourGeometry.inspect(source,120f).getOrThrow()
        assertTrue(RasterQaRunningPathStudy.prepare(
            geometry,Float.NaN
        ).isFailure)
        assertTrue(RasterQaRunningPathStudy.prepare(
            geometry,0f
        ).isFailure)
        assertTrue(RasterQaRunningPathStudy.prepare(
            geometry,9f
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
