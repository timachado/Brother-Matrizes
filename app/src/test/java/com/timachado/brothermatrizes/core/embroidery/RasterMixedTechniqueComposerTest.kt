package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterMixedTechniqueComposerTest {
    private val navy = 0xff1e3455.toInt()
    private val gold = 0xffd97a1c.toInt()
    private val width = 90f
    private val hoop = HoopProfile.H130X180

    private fun fixture(): Triple<RasterImagePixels,RasterReviewedTechniqueLayer,
        RasterReviewedTechniqueLayer> {
        val source = RasterImagePixels(160,100,IntArray(160*100) { i ->
            val x=i%160
            val y=i/160
            when {
                x in 15..45 && y in 15..35 -> navy
                x in 70..130 && y==65 -> gold
                else -> 0
            }
        },"QA de ordem de camadas")
        fun component(
            id:Int,color:Int,minX:Int,maxX:Int,minY:Int,maxY:Int
        ):RasterSatinObject {
            val mask=IntArray(source.argb.size) {i->
                val x=i%160
                val y=i/160
                if(x in minX..maxX && y in minY..maxY) 0 else -1
            }
            return RasterSatinObject(id,color,
                (maxX-minX+1)*(maxY-minY+1),
                SeparatedColorArtwork(
                    colors=listOf(color),pixels=mask,
                    minX=minX,maxX=maxX,minY=minY,maxY=maxY
                )
            )
        }
        val block=component(1,navy,15,45,15,35)
        val line=component(2,gold,70,130,65,65)
        val tatami=RasterTatamiRectanglePreview.create(
            source,block,width,hoop
        ).getOrThrow()
        val running=RasterRunningOutlinePreview.create(
            source,line,width,hoop
        ).getOrThrow()
        return Triple(
            source,
            RasterReviewedTechniqueLayer(
                block,tatami,RasterTechniqueKind.TATAMI,10
            ),
            RasterReviewedTechniqueLayer(
                line,running,RasterTechniqueKind.RUNNING,20
            )
        )
    }

    @Test fun genuineTatamiAndRunningComposeWithTrimAndColorChange() {
        val (source,tatami,running)=fixture()
        val result=RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(running,tatami)
        ).getOrThrow()
        assertEquals(listOf(1,2),result.componentOrder)
        assertEquals(listOf(RasterTechniqueKind.TATAMI,
            RasterTechniqueKind.RUNNING),result.techniqueOrder)
        assertEquals(1,result.colorChanges)
        assertTrue(result.trims >= 1)
        assertEquals(1,result.design.points.count {
            it.command==StitchCommand.COLOR_CHANGE
        })
        assertTrue(result.design.points.any {
            it.command==StitchCommand.TRIM
        })
        assertEquals(1,result.design.points.count {
            it.command==StitchCommand.END
        })
        assertEquals("QA_AUTO_STITCH",result.design.format)
        assertTrue(result.design.sourceBytes.isEmpty())
        assertTrue(result.needsPhysicalStitchOut)
        assertFalse(result.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
        assertTrue(RasterThreadSequenceAudit.inspect(result.design).isSuccess)
    }

    @Test fun explicitReverseSewingOrderIsPreservedNotSilentlyOptimized() {
        val (source,tatami,running)=fixture()
        val result=RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(
                tatami.copy(sewingOrder=100),
                running.copy(sewingOrder=1)
            )
        ).getOrThrow()
        assertEquals(listOf(2,1),result.componentOrder)
        assertEquals(listOf(RasterTechniqueKind.RUNNING,
            RasterTechniqueKind.TATAMI),result.techniqueOrder)
        assertEquals(gold,result.design.threadColors[0])
        assertTrue(result.trims >= 1)
    }

    @Test fun wrongTechniqueOrThreadColorCannotMasqueradeAsApprovedLayer() {
        val (source,tatami,running)=fixture()
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(
                tatami.copy(technique=RasterTechniqueKind.SATIN),running
            )
        ).isFailure)
        val altered=running.design.copy(threadColors=listOf(navy))
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(
                tatami,running.copy(design=altered)
            )
        ).isFailure)
    }

    @Test fun importedDesignCommandsCannotReachMixedComposer() {
        val (source,tatami,running)=fixture()
        val fake=tatami.design.copy(
            format="PES",sourceBytes=byteArrayOf(0x50,0x45,0x53)
        )
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(tatami.copy(design=fake),running)
        ).isFailure)
        val bad=running.design.copy(points=running.design.points.dropLast(1))
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(tatami,running.copy(design=bad))
        ).isFailure)
    }

    @Test fun duplicatePartsOrSewingPositionsAndInvalidHoopAreRejected() {
        val (source,tatami,running)=fixture()
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(tatami,tatami.copy(sewingOrder=30))
        ).isFailure)
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(tatami,running.copy(sewingOrder=10))
        ).isFailure)
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,180f,HoopProfile.H100X100,listOf(tatami,running)
        ).isFailure)
        assertTrue(RasterMixedTechniqueComposer.compose(
            source,width,hoop,listOf(tatami)
        ).isFailure)
    }

    @Test fun tamperedOriginalSourceMustFailTheStitchFidelityGate() {
        val (source,tatami,running)=fixture()
        val blank=RasterImagePixels(160,100,IntArray(160*100),"adulterado")
        assertTrue(RasterMixedTechniqueComposer.compose(
            blank,width,hoop,listOf(tatami,running)
        ).isFailure)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }
}
