package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterFabricUnderlayReviewTest {
    private val navy=0xff193b56.toInt()
    private val white=0xffffffff.toInt()
    private val hoop=HoopProfile.H130X180

    private fun stripe(): RasterImagePixels {
        val w=160
        val h=110
        return RasterImagePixels(w,h,IntArray(w*h) { i ->
            val x=i%w
            val y=i/w
            if(x in 60..69 && y in 18..89)navy else white
        },"Satin fabric underlay QA")
    }
    private fun ring(): RasterImagePixels {
        val w=160
        val h=110
        return RasterImagePixels(w,h,IntArray(w*h) { i ->
            val x=i%w
            val y=i/w
            if(x in 25..119 && y in 15..94 &&
                (x<=36 || x>=108 || y<=26 || y>=83))navy else white
        },"QA thick ring underlay")
    }

    private fun plan(img:RasterImagePixels):Pair<RasterSatinObject,List<ManualSatinSection>> {
        val part=RasterSatinObjectEditor.detect(img).getOrThrow().single()
        val list=if(img.label.contains("ring")) {
            RasterSatinTopologyPlanner.propose(img,part,80f).getOrThrow()
        } else listOf(
            RasterAutoSatinRails.propose(img,part,80f).getOrThrow()
        )
        return part to list
    }

    private val context=RasterUnderlayReviewContext(
        fabric=RasterFabricClass.STABLE_WOVEN,
        stabilizer=RasterStabilizerClass.CUT_AWAY,
        machineModel="Brother de referência para QA",
        qaExperimentAcknowledged=true
    )

    @Test fun underlayIsActualNeedleDownStitchesButNeverCommercialPES() {
        val img=stripe()
        val (part,sections)=plan(img)
        val check=RasterFabricUnderlayReview.compare(
            img,part,sections,80f,hoop,context
        ).getOrThrow()
        assertEquals("QA_SATIN_REVIEW",check.preview.format)
        assertTrue(check.report.addedStitches>0)
        assertTrue(check.report.reviewedLineMm>check.report.baseLineMm)
        assertTrue(check.preview.jumpCount>=2)
        assertTrue(check.report.coverage>=.78)
        assertTrue(check.report.offInkFraction<=.10)
        assertTrue(check.report.requiresPhysicalStitchOut)
        assertFalse(check.report.exportPermitted)
        assertTrue(check.preview.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun unknownMaterialAndUnspecifiedBackingCannotPass() {
        val img=stripe()
        val (part,sections)=plan(img)
        assertTrue(RasterFabricUnderlayReview.compare(
            img,part,sections,80f,hoop,
            RasterUnderlayReviewContext(
                machineModel="Brother teste",
                qaExperimentAcknowledged=true
            )
        ).isFailure)
    }

    @Test fun noMachineModelOrNoAcknowledgementFailsClosed() {
        val img=stripe()
        val (part,sections)=plan(img)
        assertTrue(RasterFabricUnderlayReview.compare(
            img,part,sections,80f,hoop,
            context.copy(machineModel=null)
        ).isFailure)
        assertTrue(RasterFabricUnderlayReview.compare(
            img,part,sections,80f,hoop,
            context.copy(qaExperimentAcknowledged=false)
        ).isFailure)
    }

    @Test fun stretchAndTerryAreNotSilentlyTreatedAsWoven() {
        val img=stripe()
        val (part,sections)=plan(img)
        for(fabric in listOf(
            RasterFabricClass.STRETCH_KNIT,
            RasterFabricClass.TERRY_PILE
        )) assertTrue(RasterFabricUnderlayReview.compare(
            img,part,sections,80f,hoop,
            context.copy(fabric=fabric)
        ).isFailure)
    }

    @Test fun ringWithCounterRequiresIndependentNeedleHoleAudit() {
        val img=ring()
        val (part,sections)=plan(img)
        assertEquals(1,RasterCounterSatinGate.count(
            img,part
        ).getOrThrow())
        val result=RasterFabricUnderlayReview.compare(
            img,part,sections,80f,hoop,context
        )
        if(result.isSuccess) {
            val approved=result.getOrThrow()
            RasterCounterSatinGate.inspect(
                img,part,approved.preview,80f
            ).getOrThrow()
            assertFalse(approved.report.exportPermitted)
        } else {
            assertTrue(result.exceptionOrNull()?.message
                .orEmpty().isNotBlank())
        }
    }

    @Test fun connectedSatinSectionsNeedSeparateManualUnderlayReview() {
        val img=stripe()
        val (part,sections)=plan(img)
        val linked=sections.mapIndexed { i,section ->
            section.copy(connectToPrevious=i>0)
        }
        if(linked.size==1) {
            // Invalid first section: never permit a hidden underlay stitch.
            assertTrue(RasterFabricUnderlayReview.compare(
                img,part,linked.map { it.copy(connectToPrevious=true) },
                80f,hoop,context
            ).isFailure)
        }
    }
}
