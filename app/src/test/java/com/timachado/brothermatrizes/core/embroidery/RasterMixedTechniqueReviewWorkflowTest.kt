package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterMixedTechniqueReviewWorkflowTest {
    private val navy = 0xff203857.toInt()
    private val gold = 0xffd17929.toInt()
    private val width = 90f
    private val hoop = HoopProfile.H130X180

    private data class Art(
        val image: RasterImagePixels,
        val layers: List<RasterSatinObject>
    )

    private fun source(): Art {
        val w=160
        val h=100
        val image=RasterImagePixels(
            w,h,IntArray(w*h) { i ->
                val x=i%w
                val y=i/w
                when {
                    x in 16..48 && y in 15..38 -> navy
                    x in 70..132 && y==68 -> gold
                    else -> 0
                }
            },"imagem com duas tecnicas reais"
        )
        fun part(
            id:Int,color:Int,minX:Int,maxX:Int,minY:Int,maxY:Int
        ): RasterSatinObject {
            val separated=IntArray(w*h) {i->
                val x=i%w
                val y=i/w
                if(x in minX..maxX && y in minY..maxY) 0 else -1
            }
            return RasterSatinObject(
                id=id,color=color,
                pixelCount=(maxX-minX+1)*(maxY-minY+1),
                mask=SeparatedColorArtwork(
                    colors=listOf(color),pixels=separated,
                    minX=minX,maxX=maxX,minY=minY,maxY=maxY
                )
            )
        }
        return Art(image,listOf(
            part(11,navy,16,48,15,38),
            part(12,gold,70,132,68,68)
        ))
    }

    @Test fun actualTatamiAndRunningCanBePreparedAndComposedInUserOrder() {
        val art=source()
        val tatami=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.TATAMI,width,hoop
        ).getOrThrow()
        val running=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        val review=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            art.image,art.layers,listOf(
                RasterReviewedTechniqueLayer(
                    art.layers[1],running,RasterTechniqueKind.RUNNING,1
                ),
                RasterReviewedTechniqueLayer(
                    art.layers[0],tatami,RasterTechniqueKind.TATAMI,2
                )
            ),width,hoop
        ).getOrThrow()
        assertEquals(listOf(12,11),review.componentOrder)
        assertEquals(1,review.colorChanges)
        assertTrue(review.trims>=1)
        assertEquals("QA_AUTO_STITCH",review.design.format)
        assertEquals(1,review.design.points.count {
            it.command==StitchCommand.END
        })
        assertFalse(review.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun incompleteSelectionFailsEvenIfAllSelectedLayersAreValid() {
        val art=source()
        val running=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            art.image,art.layers,listOf(
                RasterReviewedTechniqueLayer(
                    art.layers[1],running,RasterTechniqueKind.RUNNING,0
                )
            ),width,hoop
        ).isFailure)
    }

    @Test fun mismatchedTechniqueAndColorNeverProduceDraft() {
        val art=source()
        assertTrue(RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.RUNNING,width,hoop
        ).isFailure)
        assertTrue(RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.TATAMI,width,hoop
        ).isFailure)
        val changed=art.layers[0].copy(color=gold)
        assertTrue(RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,changed,RasterTechniqueKind.TATAMI,width,hoop
        ).isFailure)
    }

    @Test fun copiedOrStaleMaskCannotBeSubstitutedForConfirmedPart() {
        val art=source()
        val first=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.TATAMI,width,hoop
        ).getOrThrow()
        val second=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        val altered=art.layers[0].mask.pixels.copyOf()
        altered[20*160+30]=-1
        val wrong=art.layers[0].copy(
            mask=art.layers[0].mask.copy(pixels=altered)
        )
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            art.image,art.layers,listOf(
                RasterReviewedTechniqueLayer(
                    wrong,first,RasterTechniqueKind.TATAMI,0
                ),
                RasterReviewedTechniqueLayer(
                    art.layers[1],second,RasterTechniqueKind.RUNNING,1
                )
            ),width,hoop
        ).isFailure)
    }

    @Test fun extraLetterInOriginalCannotBeSilentlyDroppedAfterConfirmation() {
        val art=source()
        val tatami=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.TATAMI,width,hoop
        ).getOrThrow()
        val running=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        val newImage=art.image.argb.copyOf()
        // Third valid component; the two previously reviewed layers still
        // match their original coordinates and colors.
        for(y in 15..36) for(x in 95..116)
            newImage[y*160+x]=0xff247e58.toInt()
        val extra=RasterImagePixels(160,100,newImage,"terceira letra real")
        assertEquals(3,
            RasterExactColorComponentDetector.detect(extra).getOrThrow().size)
        val before=listOf(
            RasterReviewedTechniqueLayer(
                art.layers[0],tatami,RasterTechniqueKind.TATAMI,0
            ),
            RasterReviewedTechniqueLayer(
                art.layers[1],running,RasterTechniqueKind.RUNNING,1
            )
        )
        val failure=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            extra,art.layers,before,width,hoop
        )
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty()
            .contains("ausentes"))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun evenSinglePixelAccentCannotBeOmittedByExistingConfirmations() {
        val art=source()
        val tatami=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.TATAMI,width,hoop
        ).getOrThrow()
        val running=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        val image=art.image.argb.copyOf()
        image[53*160+60]=navy
        val accent=RasterImagePixels(160,100,image,"acento ausente")
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            accent,art.layers,listOf(
                RasterReviewedTechniqueLayer(
                    art.layers[0],tatami,RasterTechniqueKind.TATAMI,0
                ),
                RasterReviewedTechniqueLayer(
                    art.layers[1],running,RasterTechniqueKind.RUNNING,1
                )
            ),width,hoop
        ).isFailure)
    }

    @Test fun detectedMaskCannotBeSwappedEvenWithSameComponentCount() {
        val art=source()
        val tatami=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.TATAMI,width,hoop
        ).getOrThrow()
        val running=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        val tampered=art.layers[0].mask.pixels.copyOf()
        tampered[20*160+20]=-1
        val wrong=art.layers[0].copy(
            mask=art.layers[0].mask.copy(pixels=tampered)
        )
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            art.image,listOf(wrong,art.layers[1]),listOf(
                RasterReviewedTechniqueLayer(
                    wrong,tatami,RasterTechniqueKind.TATAMI,0
                ),
                RasterReviewedTechniqueLayer(
                    art.layers[1],running,RasterTechniqueKind.RUNNING,1
                )
            ),width,hoop
        ).isFailure)
    }

    @Test fun changedHoopOrOriginalImageRejectsPreviouslyPreparedPaths() {
        val art=source()
        val first=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[0],RasterTechniqueKind.TATAMI,width,hoop
        ).getOrThrow()
        val second=RasterMixedTechniqueReviewWorkflow.prepare(
            art.image,art.layers[1],RasterTechniqueKind.RUNNING,width,hoop
        ).getOrThrow()
        val layers=listOf(
            RasterReviewedTechniqueLayer(
                art.layers[0],first,RasterTechniqueKind.TATAMI,0
            ),
            RasterReviewedTechniqueLayer(
                art.layers[1],second,RasterTechniqueKind.RUNNING,1
            )
        )
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            art.image,art.layers,layers,180f,HoopProfile.H100X100
        ).isFailure)
        val blank=RasterImagePixels(
            160,100,IntArray(160*100),"referencia alterada"
        )
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            blank,art.layers,layers,width,hoop
        ).isFailure)
    }
}
