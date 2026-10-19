package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pixel-exact synthetic topology QA, not the user's PANOBIANCO image. */
class RasterAlphaComponentLineageTest {
    private val w=96
    private val h=80
    private val dense=0xeed07632.toInt()
    private val fragile=0xd2d07632.toInt()

    private fun splitAndLoss():RasterImagePixels {
        val p=IntArray(w*h)
        // A thick region with a translucent bridge between two halves.
        for(y in 10..29)for(x in 10..19)p[y*w+x]=dense
        for(y in 10..29)for(x in 25..34)p[y*w+x]=dense
        for(y in 18..21)for(x in 20..24)p[y*w+x]=fragile
        // Separate island that disappears at 224.
        for(y in 44..55)for(x in 58..69)p[y*w+x]=fragile
        return RasterImagePixels(w,h,p,"split + disappearance QA")
    }

    @Test fun unchangedTotalComponentCountCanConcealSplitAndDisappearance() {
        val source=splitAndLoss()
        val unchanged=source.argb.copyOf()
        val r=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        val lineage=r.componentLineage!!
        val before=r.slices.single { it.threshold==208 }
        val after=r.slices.single { it.threshold==224 }
        assertEquals(2,before.components)
        assertEquals(2,after.components)
        val transition=lineage.transitions.single {
            it.fromThreshold==208 && it.toThreshold==224
        }
        assertEquals(2,transition.baseComponents)
        assertEquals(2,transition.survivingComponents)
        assertEquals(1,transition.parentsSplit)
        assertEquals(1,transition.parentsLost)
        assertEquals(2,transition.changes.size)
        assertEquals(2,transition.changes.first { it.split }.children)
        assertEquals(0,transition.changes.first { it.disappeared }.children)
        assertEquals(10,transition.changes.first { it.split }.minX)
        assertEquals(44,transition.changes.first { it.disappeared }.minY)
        assertTrue(transition.summary().contains("(10,10)"))
        assertTrue(lineage.summary().contains("208→224"))
        assertTrue(unchanged.contentEquals(source.argb))
        assertFalse(r.exportPermitted)
        assertFalse(lineage.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun differenceOverlayHighlightsOnlyPixelsActuallyLost() {
        val source=splitAndLoss()
        val audit=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        val overlay=audit.componentLineage!!.lossPreview(208,224)
        assertEquals(0,overlay.argb[1*w+1])
        assertEquals(0xff979fa8.toInt(),overlay.argb[12*w+15])
        assertEquals(0xffffa447.toInt(),overlay.argb[20*w+22])
        assertEquals(0xffffa447.toInt(),overlay.argb[50*w+62])
        val expected=source.argb.count {
            val alpha=it ushr 24
            alpha in 208..223
        }
        assertEquals(expected,overlay.argb.count {
            it==0xffffa447.toInt()
        })
        assertEquals(
            source.argb.count { it ushr 24>=208 },
            overlay.argb.count { it ushr 24 > 0 }
        )
        assertTrue(audit.componentLineage!!.reviewRequired)
    }

    @Test fun stableRegionsUnder208CanStillErodeWithoutSplitting() {
        val source=splitAndLoss()
        val r=RasterAlphaThresholdSweep.inspect(source).getOrThrow()
        val transition=r.componentLineage!!.transitions.single {
            it.fromThreshold==180 && it.toThreshold==208
        }
        assertEquals(2,transition.baseComponents)
        assertEquals(2,transition.survivingComponents)
        assertEquals(0,transition.parentsSplit)
        assertEquals(0,transition.parentsLost)
        assertEquals(0,transition.pixelsRemoved)
        assertFalse(transition.hasStructuralChange)
    }

    @Test fun higherAlphaMustNotReintroduceOrJoinLostComponents() {
        val source=splitAndLoss()
        val r=RasterAlphaComponentLineage.inspect(
            source,listOf(180,208,224,240,255)
        ).getOrThrow()
        assertEquals(listOf(180,208,224,240),
            r.transitions.map { it.fromThreshold })
        assertTrue(r.transitions.all {
            it.pixelsRemoved>=0 && it.parentsSplit>=0 && it.parentsLost>=0
        })
        assertTrue(runCatching { r.lossPreview(224,208) }.isFailure)
        assertTrue(runCatching { r.lossPreview(208,240) }.isFailure)
        assertFalse(r.exportPermitted)
    }

    @Test fun noStitchedGeometryIsCreatedFromAlphaLineage() {
        val source=splitAndLoss()
        val before=source.argb.copyOf()
        val r=RasterAlphaComponentLineage.inspect(source,listOf(180,208,224))
            .getOrThrow()
        assertEquals(2,r.transitions.size)
        assertFalse(r.exportPermitted)
        assertTrue(r.reviewRequired)
        assertTrue(source.argb.contentEquals(before))
        assertTrue(r.summary().contains("Não são pontos"))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
