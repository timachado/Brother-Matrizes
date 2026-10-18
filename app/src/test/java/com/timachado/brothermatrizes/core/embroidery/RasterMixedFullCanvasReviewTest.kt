package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterMixedFullCanvasReviewTest {
    private val orange=0xffca762e.toInt()
    private val blue=0xff245ca2.toInt()
    private val hoop=HoopProfile.H130X180
    private val width=90f

    private data class Fixture(
        val source:RasterImagePixels,
        val parts:List<RasterSatinObject>,
        val layers:List<RasterReviewedTechniqueLayer>,
        val finalDesign:EmbroideryDesign
    )
    private fun fixture():Fixture {
        val w=160
        val h=100
        val source=RasterImagePixels(w,h,IntArray(w*h) {i->
            val x=i%w
            val y=i/w
            when {
                x in 17..47 && y in 13..39 -> orange
                x in 69..132 && y==68 -> blue
                else -> 0
            }
        },"imagem qa duas cores")
        val parts=RasterExactColorComponentDetector.detect(source).getOrThrow()
        val layers=parts.mapIndexed {index,part ->
            val technique=if(part.color==orange)
                RasterTechniqueKind.TATAMI else RasterTechniqueKind.RUNNING
            val design=RasterMixedTechniqueReviewWorkflow.prepare(
                source,part,technique,width,hoop
            ).getOrThrow()
            RasterReviewedTechniqueLayer(part,design,technique,index)
        }
        val full=RasterMixedTechniqueComposer.compose(
            source,width,hoop,layers
        ).getOrThrow()
        return Fixture(source,parts,layers,full.design)
    }

    @Test fun finalWholeImageOverlayConservesEveryColoredOriginalPixel() {
        val f=fixture()
        val sourceCopy=f.source.argb.copyOf()
        val result=RasterMixedFullCanvasReview.inspect(
            f.source,f.layers,f.finalDesign,width,hoop
        ).getOrThrow()
        assertEquals(2,result.parts.size)
        assertEquals(f.parts.sumOf {it.pixelCount},result.originalInkPixels)
        assertEquals(
            result.originalInkPixels,
            result.coveredInkPixels+result.uncoveredInkPixels
        )
        assertEquals(
            result.coveredInkPixels,
            result.coverageOverlay.argb.count {it==0xff7a8288.toInt()}
        )
        assertEquals(
            result.uncoveredInkPixels,
            result.coverageOverlay.argb.count {it==0xffff8f2d.toInt()}
        )
        assertEquals(0,result.coverageOverlay.argb[60*160+60])
        assertEquals(0,result.coverageOverlay.argb[0])
        assertTrue(result.coverage>=.70)
        assertTrue(result.summary().contains("não bordado físico"))
        assertTrue(result.sourceInkPixelsAreUntouched(f.source))
        assertTrue(f.source.argb.contentEquals(sourceCopy))
        assertFalse(result.exportPermitted)
        assertTrue(result.requiresPhysicalStitchOut)
    }

    private fun RasterMixedFullCanvasReport.sourceInkPixelsAreUntouched(
        source:RasterImagePixels
    ):Boolean {
        for(i in source.argb.indices) {
            if(source.argb[i]==0 && coverageOverlay.argb[i]!=0)return false
        }
        return true
    }

    @Test fun missingColorComponentIsRejectedEvenIfOtherColorStillSews() {
        val f=fixture()
        assertTrue(RasterMixedFullCanvasReview.inspect(
            f.source,f.layers.take(1),f.finalDesign,width,hoop
        ).isFailure)
        val removed=f.source.argb.copyOf()
        removed[55*160+80]=blue
        val sourceWithAccent=RasterImagePixels(
            160,100,removed,"terceiro elemento faltante"
        )
        assertTrue(RasterMixedFullCanvasReview.inspect(
            sourceWithAccent,f.layers,f.finalDesign,width,hoop
        ).isFailure)
    }

    @Test fun assembledStitchOrderOrColorIndexTamperingInvalidatesEvidence() {
        val f=fixture()
        val changed=f.finalDesign.points.toMutableList()
        val first=changed.indexOfFirst {it.command==StitchCommand.STITCH}
        changed[first]=changed[first].copy(xUnits=changed[first].xUnits+1)
        assertTrue(RasterMixedFullCanvasReview.inspect(
            f.source,f.layers,f.finalDesign.copy(points=changed),
            width,hoop
        ).isFailure)
        val newOrder=f.layers.map {it.copy(sewingOrder=1-it.sewingOrder)}
        assertTrue(RasterMixedFullCanvasReview.inspect(
            f.source,newOrder,f.finalDesign,width,hoop
        ).isFailure)
    }

    @Test fun originalMasksAndColorsAreNeverSilentlyRelabeled() {
        val f=fixture()
        val altered=f.parts[0].copy(color=blue)
        val layers=listOf(f.layers[0].copy(component=altered),f.layers[1])
        assertTrue(RasterMixedFullCanvasReview.inspect(
            f.source,layers,f.finalDesign,width,hoop
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun successfulReviewedCompositionIncludesWholeArtEvidence() {
        val f=fixture()
        val assembled=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,width,hoop
        ).getOrThrow()
        val report=assembled.fullCanvasReport ?:
            error("Mapa final da imagem não chegou ao simulador QA")
        assertEquals(f.parts.sumOf {it.pixelCount},report.originalInkPixels)
        assertEquals(2,report.parts.size)
        assertTrue(assembled.integrityReport!=null)
        assertFalse(report.exportPermitted)
        assertFalse(assembled.exportPermitted)
        assertEquals("QA_AUTO_STITCH",assembled.design.format)
    }
}
