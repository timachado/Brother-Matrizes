package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end software proof for a split Satin glyph + another color layer
 * entering the SAME command stream used by Simular Bordado.
 * These are synthetic test shapes, NOT stitch-out machine certification.
 */
class RasterSplitMixedSimulatorIntegrationTest {
    private val orange=0xffca7028.toInt()
    private val blue=0xff294c8b.toInt()
    private val width=90f
    private val hoop=HoopProfile.H130X180

    private data class Fixture(
        val source:RasterImagePixels,
        val parts:List<RasterSatinObject>,
        val layers:List<RasterReviewedTechniqueLayer>,
        val originalSatin:EmbroideryDesign,
        val splitSatin:EmbroideryDesign
    )

    private fun fixture():Fixture {
        val w=160
        val h=100
        val img=RasterImagePixels(w,h,IntArray(w*h) {i ->
            val x=i%w
            val y=i/w
            when {
                x in 22..30 && y in 12..83 -> orange
                x in 75..135 && y==65 -> blue
                else -> 0
            }
        },"referência QA com duas cores")
        val parts=RasterExactColorComponentDetector.detect(img)
            .getOrThrow()
        assertEquals(2,parts.size)
        val bar=parts.single {it.color==orange}
        val line=parts.single {it.color==blue}
        val rail=ManualSatinSection((0..20).map {i ->
            val v=.06f+.88f*i/20f
            SatinRailPair(.09f,v,.91f,v)
        })
        val original=RasterSatinObjectEditor.preview(
            img,bar,listOf(rail),width,hoop,
            minimumCoverage=.68f
        ).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            img,bar,original,width
        ).getOrThrow()
        val draft=RasterLetterSatinDraft(
            sections=listOf(rail),preview=original,
            coverage=fidelity.coverage,
            offInkFraction=fidelity.offInkFraction,
            diagnosis=RasterLetterTopologyReview.inspect(
                img,bar
            ).getOrThrow()
        )
        val split=RasterLetterEditableSatinReview
            .proposeIndependentSplit(
                img,bar,draft,0,10,width,hoop
            ).getOrThrow()
        val running=RasterRunningOutlinePreview.create(
            img,line,width,hoop
        ).getOrThrow()
        val layers=listOf(
            RasterReviewedTechniqueLayer(
                bar,split.preview,RasterTechniqueKind.SATIN,0
            ),
            RasterReviewedTechniqueLayer(
                line,running,RasterTechniqueKind.RUNNING,1
            )
        )
        return Fixture(img,parts,layers,original,split.preview)
    }

    @Test fun dividedSatinAndSecondColorReachFullSimulatorWithAllCuts() {
        val f=fixture()
        val sewn=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,width,hoop
        ).getOrThrow()
        val report=sewn.integrityReport ?: error("Prova de integridade ausente")
        assertEquals(listOf(RasterTechniqueKind.SATIN,
            RasterTechniqueKind.RUNNING),sewn.techniqueOrder)
        assertEquals(1,report.layerBoundaryTrims)
        assertEquals(1,report.internalTrims)
        assertEquals(1,report.internalTravelJumps)
        assertEquals(1,report.layerBoundaryColorChanges)
        assertEquals(2,sewn.trims)
        assertEquals(1,sewn.colorChanges)
        assertEquals(2,sewn.design.threadColors.size)
        assertEquals(f.layers.sumOf {it.design.stitchCount},
            sewn.design.stitchCount)
        assertEquals(f.layers.sumOf {it.design.jumpCount},
            sewn.design.jumpCount)
        assertEquals(f.splitSatin.points.dropLast(1),
            sewn.design.points.take(f.splitSatin.points.size-1))
        assertEquals(1,sewn.design.points.count {
            it.command==StitchCommand.END
        })
        assertEquals("QA_AUTO_STITCH",sewn.design.format)
        assertTrue(sewn.design.sourceBytes.isEmpty())
        assertFalse(sewn.exportPermitted)
        assertTrue(sewn.needsPhysicalStitchOut)
        assertTrue(sewn.message().contains("1 corte(s) interno(s)"))
        assertTrue(RasterThreadSequenceAudit.inspect(
            sewn.design
        ).isSuccess)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun deletingInternalSatinTrimFailsEvenIfCommandCountsAreRepaired() {
        val f=fixture()
        val full=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,width,hoop
        ).getOrThrow().design
        val commands=full.points.toMutableList()
        val pos=commands.indexOfFirst {it.command==StitchCommand.TRIM}
        assertTrue(pos>0)
        commands.removeAt(pos)
        assertTrue(RasterMixedSequenceIntegrityAudit.inspect(
            full.copy(points=commands),f.layers
        ).isFailure)
    }

    @Test fun replacingSatinInternalJumpWithStitchIsAlwaysRejected() {
        val f=fixture()
        val full=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,width,hoop
        ).getOrThrow().design
        val commands=full.points.toMutableList()
        val cut=commands.indexOfFirst {it.command==StitchCommand.TRIM}
        assertEquals(StitchCommand.JUMP,commands[cut+1].command)
        commands[cut+1]=commands[cut+1].copy(
            command=StitchCommand.STITCH
        )
        assertTrue(RasterMixedSequenceIntegrityAudit.inspect(
            full.copy(points=commands),f.layers
        ).isFailure)
        assertTrue(RasterThreadSequenceAudit.inspect(
            full.copy(points=commands)
        ).isFailure)
    }

    @Test fun oldUndividedSatinCannotMasqueradeAsTheApprovedSplit() {
        val f=fixture()
        val full=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,width,hoop
        ).getOrThrow().design
        val forged=listOf(
            f.layers[0].copy(design=f.originalSatin),
            f.layers[1]
        )
        assertTrue(RasterMixedSequenceIntegrityAudit.inspect(
            full,forged
        ).isFailure)
        assertEquals(1,f.splitSatin.points.count {
            it.command==StitchCommand.TRIM
        })
    }

    @Test fun changingSewingOrderOrHoopInvalidatesTheWholeSequence() {
        val f=fixture()
        val assembled=RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,width,hoop
        ).getOrThrow().design
        val reorder=f.layers.map {
            it.copy(sewingOrder=1-it.sewingOrder)
        }
        assertTrue(RasterMixedSequenceIntegrityAudit.inspect(
            assembled,reorder
        ).isFailure)
        assertTrue(RasterMixedTechniqueReviewWorkflow.composeReviewed(
            f.source,f.parts,f.layers,180f,HoopProfile.H100X100
        ).isFailure)
    }
}
