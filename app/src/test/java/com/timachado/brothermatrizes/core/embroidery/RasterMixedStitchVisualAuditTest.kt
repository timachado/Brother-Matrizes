package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterMixedStitchVisualAuditTest {
    private val fillColor = 0xff244366.toInt()
    private val lineColor = 0xffd17a24.toInt()
    private val hoop = HoopProfile.H130X180
    private val width = 90f

    private data class Art(
        val original: RasterImagePixels,
        val parts: List<RasterSatinObject>
    )

    private fun art(): Art {
        val w=160
        val h=100
        val original = RasterImagePixels(w,h,IntArray(w*h) {i ->
            val x=i%w
            val y=i/w
            when {
                x in 17..47 && y in 13..39 -> fillColor
                x in 69..132 && y == 68 -> lineColor
                else -> 0
            }
        },"QA fonte com duas técnicas")
        fun part(id:Int,color:Int,minX:Int,maxX:Int,minY:Int,maxY:Int):
            RasterSatinObject {
            val mask=IntArray(w*h) {i ->
                val x=i%w;val y=i/w
                if(x in minX..maxX && y in minY..maxY) 0 else -1
            }
            return RasterSatinObject(
                id=id,color=color,
                pixelCount=(maxX-minX+1)*(maxY-minY+1),
                mask=SeparatedColorArtwork(
                    colors=listOf(color),pixels=mask,
                    minX=minX,maxX=maxX,minY=minY,maxY=maxY
                )
            )
        }
        return Art(original,listOf(
            part(1,fillColor,17,47,13,39),
            part(2,lineColor,69,132,68,68)
        ))
    }

    private fun prepare(
        art: Art, index:Int,kind:RasterTechniqueKind
    ): EmbroideryDesign =
        RasterMixedTechniqueReviewWorkflow.prepare(
            art.original,art.parts[index],kind,width,hoop
        ).getOrThrow()

    @Test fun realTatamiAndRunningStitchPathsYieldGroundedCoverageMaps() {
        val art=art()
        val modes=listOf(RasterTechniqueKind.TATAMI,RasterTechniqueKind.RUNNING)
        for ((index,technique) in modes.withIndex()) {
            val design=prepare(art,index,technique)
            val originalPixels=art.original.argb.copyOf()
            val maskPixels=art.parts[index].mask.pixels.copyOf()
            val result=RasterMixedStitchVisualAudit.inspect(
                art.original,art.parts[index],design,width,hoop
            ).getOrThrow()
            assertTrue(result.originalInkPixels>0)
            assertEquals(
                result.originalInkPixels,
                result.stitchedInkPixels+result.uncoveredInkPixels
            )
            assertTrue(result.stitchSamples>0)
            assertTrue(result.coverage>.70)
            assertEquals(160,result.coverageOverlay.width)
            assertEquals(100,result.sourceOverlay.height)
            assertEquals(0,result.coverageOverlay.argb[4*160+5])
            assertEquals(0,result.sourceOverlay.argb[4*160+5])
            assertEquals(art.parts[index].pixelCount,
                result.sourceOverlay.argb.count { it ushr 24 >= 180 })
            assertEquals(result.stitchedInkPixels,
                result.coverageOverlay.argb.count { it == 0xff7a8288.toInt() })
            assertEquals(result.uncoveredInkPixels,
                result.coverageOverlay.argb.count { it == 0xffff8f2d.toInt() })
            assertTrue(result.brief().contains("STITCH"))
            assertTrue(result.brief().contains("não é PES"))
            assertFalse(result.exportPermitted)
            assertTrue(art.original.argb.contentEquals(originalPixels))
            assertTrue(art.parts[index].mask.pixels.contentEquals(maskPixels))
        }
    }

    @Test fun colorOrSourceChangesInvalidateAlreadyGeneratedStitches() {
        val art=art()
        val design=prepare(art,0,RasterTechniqueKind.TATAMI)
        val recolored=art.parts[0].copy(color=lineColor)
        assertTrue(RasterMixedStitchVisualAudit.inspect(
            art.original,recolored,design,width,hoop
        ).isFailure)
        val alteredPixels=art.original.argb.copyOf()
        alteredPixels[20*160+25]=lineColor
        val stale=RasterImagePixels(
            160,100,alteredPixels,"referência modificada"
        )
        assertTrue(RasterMixedStitchVisualAudit.inspect(
            stale,art.parts[0],design,width,hoop
        ).isFailure)
    }

    @Test fun importedPesCannotMasqueradeAsVisualReview() {
        val art=art()
        val design=prepare(art,0,RasterTechniqueKind.TATAMI)
        assertTrue(RasterMixedStitchVisualAudit.inspect(
            art.original,art.parts[0],
            design.copy(
                format="PES",sourceBytes=byteArrayOf(0x50,0x45,0x53)
            ),width,hoop
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun switchedHoopOrWidthRequiresFreshInspection() {
        val art=art()
        val design=prepare(art,1,RasterTechniqueKind.RUNNING)
        assertTrue(RasterMixedStitchVisualAudit.inspect(
            art.original,art.parts[1],design,180f,
            HoopProfile.H100X100
        ).isFailure)
        assertTrue(RasterMixedStitchVisualAudit.inspect(
            art.original,art.parts[1],design,Float.NaN,hoop
        ).isFailure)
    }

    @Test fun noVisualPreviewCanPromoteQAtoPhysicalCertification() {
        val art=art()
        val preview=prepare(art,1,RasterTechniqueKind.RUNNING)
        val result=RasterMixedStitchVisualAudit.inspect(
            art.original,art.parts[1],preview,width,hoop
        ).getOrThrow()
        assertFalse(result.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertEquals("QA_RUNNING_REVIEW",preview.format)
        assertTrue(preview.sourceBytes.isEmpty())
    }
}
