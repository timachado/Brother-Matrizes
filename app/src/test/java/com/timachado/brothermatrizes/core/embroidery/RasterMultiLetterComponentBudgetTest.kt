package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic multi-line logo, not the user's real artwork. */
class RasterMultiLetterComponentBudgetTest {
    private val thread=0xffdf7625.toInt()
    private val width=240
    private val height=160
    private val hoop=HoopProfile.H130X180

    private fun source(elements:Int, tinyAccent:Boolean=false):RasterImagePixels {
        val argb=IntArray(width*height)
        for(i in 0 until elements) {
            val col=i%7
            val row=i/7
            val left=8+col*31
            val top=9+row*34
            for(y in top until top+15) for(x in left until left+13) {
                argb[y*width+x]=thread
            }
        }
        if(tinyAccent) argb[112*width+225]=thread
        return RasterImagePixels(width,height,argb,
            "logotipo com várias letras e duas linhas")
    }

    @Test fun twentyOneLettersPreserveEveryOriginalPixelAndMask() {
        val image=source(21)
        val parts=RasterExactColorComponentDetector.detect(
            image
        ).getOrThrow()
        assertEquals(21,parts.size)
        assertEquals(21,parts.map {it.id}.distinct().size)
        assertEquals(image.argb.count {it ushr 24>=180},
            parts.sumOf {it.pixelCount})
        for(p in parts) {
            assertEquals(p.pixelCount,p.mask.pixels.count {it==0})
            for(index in image.argb.indices) if(p.mask.pixels[index]==0)
                assertEquals(image.argb[index],p.color)
        }
        assertEquals(24,RasterQaComponentBudget.MAX_COMPONENTS)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun multiLetterTatamiWorkflowPreservesOrderCutsAndEnd() {
        val image=source(21)
        val parts=RasterExactColorComponentDetector.detect(
            image
        ).getOrThrow()
        val layers=parts.mapIndexed {order,part ->
            val design=RasterMixedTechniqueReviewWorkflow.prepare(
                image,part,RasterTechniqueKind.TATAMI,
                90f,hoop
            ).getOrThrow()
            RasterReviewedTechniqueLayer(
                part,design,RasterTechniqueKind.TATAMI,order
            )
        }
        val result=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            image,parts,layers,90f,hoop
        ).getOrThrow()
        val integrity=result.integrityReport ?: error("Sem auditoria")
        assertEquals(21,integrity.reviewedComponents)
        assertEquals(20,integrity.layerBoundaryTrims)
        assertEquals(0,integrity.layerBoundaryColorChanges)
        assertEquals(0,integrity.internalTrims)
        assertEquals(21,result.componentOrder.size)
        assertEquals(1,result.design.threadColors.size)
        assertEquals(layers.sumOf {it.design.stitchCount},
            integrity.originalStitches)
        assertEquals(1,result.design.points.count {
            it.command==StitchCommand.END
        })
        assertEquals("QA_AUTO_STITCH",result.design.format)
        assertTrue(result.design.sourceBytes.isEmpty())
        assertFalse(result.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun missingOneLetterCannotPassWholeImageGate() {
        val image=source(21)
        val parts=RasterExactColorComponentDetector.detect(
            image
        ).getOrThrow()
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            image,parts.dropLast(1),emptyList(),90f,hoop
        ).isFailure)
    }

    @Test fun extraTwentyFifthComponentFailsRatherThanBeingDropped() {
        val image=source(25)
        val result=RasterExactColorComponentDetector.detect(image)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("24 elementos"))
    }

    @Test fun detachedTinyAccentIsNotLostEvenWithinBudget() {
        val image=source(21,tinyAccent=true)
        val result=RasterExactColorComponentDetector.detect(image)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty()
            .contains("pequeno"))
    }
}
