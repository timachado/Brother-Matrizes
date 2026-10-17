package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSatinParameterSensitivityTest {
    private val navy=0xff1a3b58.toInt()
    private val white=0xffffffff.toInt()
    private val hoop=HoopProfile.H130X180

    private fun stripe(): RasterImagePixels {
        val w=160;val h=110
        return RasterImagePixels(w,h,IntArray(w*h) { i->
            val x=i%w;val y=i/w
            if(x in 60..69 && y in 18..89)navy else white
        },"satin comparison")
    }

    private fun plan(src:RasterImagePixels):Pair<
        RasterSatinObject,List<ManualSatinSection>
    > {
        val p=RasterSatinObjectEditor.detect(src).getOrThrow().single()
        return p to listOf(
            RasterAutoSatinRails.propose(src,p,80f).getOrThrow()
        )
    }

    private val context=RasterUnderlayReviewContext(
        fabric=RasterFabricClass.STABLE_WOVEN,
        stabilizer=RasterStabilizerClass.CUT_AWAY,
        machineModel="Brother teste QA (não homologado)",
        qaExperimentAcknowledged=true
    )
    private val baseline=RasterSatinTrialSettings(
        .5f,.15f,false
    )
    private val spaced=RasterSatinTrialSettings(
        .7f,.15f,false
    )
    private val underlay=RasterSatinTrialSettings(
        .5f,.15f,true
    )

    @Test fun alternativesDescribeActualStitchesAndDoNotRecommendASetting() {
        val image=stripe()
        val (part,sections)=plan(image)
        val report=RasterSatinParameterSensitivity.compare(
            image,part,sections,80f,hoop,context,
            listOf(baseline,spaced,underlay)
        ).getOrThrow()
        assertEquals(3,report.trials.size)
        assertEquals(baseline,report.trials[0].settings)
        assertEquals(context.fabric,report.fabric)
        assertTrue(report.physicalStitchOutRequired)
        assertFalse(report.exportPermitted)
        assertNull(report.recommendedSettings)
        assertTrue(report.trials.all { !it.machineApproved })
        assertTrue(report.trials.first().passedGeometryQa)
        assertTrue(report.trials.first().stitchCount!!>15)
        assertTrue(report.trials.first().threadLengthMm!!>0.0)
        // A passing alternate may have a different path/pitch, but static
        // screen-space QA does not label it the best option for a fabric.
        val valid=report.trials.filter { it.passedGeometryQa }
        assertTrue(valid.size>=2)
        assertTrue(valid.any {
            it.settings==spaced && it.stitchCount !=
                report.trials.first().stitchCount
        } || valid.any {
            it.settings==underlay &&
                it.stitchCount!!>report.trials.first().stitchCount!!
        })
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun oldQaEditorDefaultsAreByteForByteEquivalentInCommands() {
        val img=stripe()
        val (part,sections)=plan(img)
        val old=RasterSatinObjectEditor.preview(
            img,part,sections,80f,hoop,
            minimumCoverage=.90f
        ).getOrThrow()
        val explicit=RasterSatinObjectEditor.preview(
            img,part,sections,80f,hoop,
            minimumCoverage=.90f,qaCenterUnderlay=false,
            qaDensityMm=.5f,qaPullCompensationMm=.15f
        ).getOrThrow()
        assertEquals(old.points,explicit.points)
        assertEquals(old.stitchCount,explicit.stitchCount)
        assertTrue(old.sourceBytes.isEmpty())
    }

    @Test fun unknownFabricAndBackingCannotBecomeAShoppingPreset() {
        val img=stripe()
        val (part,sections)=plan(img)
        for(bad in listOf(
            context.copy(fabric=RasterFabricClass.UNKNOWN),
            context.copy(fabric=RasterFabricClass.STRETCH_KNIT),
            context.copy(fabric=RasterFabricClass.TERRY_PILE),
            context.copy(stabilizer=RasterStabilizerClass.UNKNOWN),
            context.copy(machineModel=null),
            context.copy(qaExperimentAcknowledged=false)
        )) {
            assertTrue(RasterSatinParameterSensitivity.compare(
                img,part,sections,80f,hoop,bad,
                listOf(baseline,spaced)
            ).isFailure)
        }
    }

    @Test fun invalidDensityCompensationAndDuplicateTrialsAreRejected() {
        val img=stripe()
        val (part,sections)=plan(img)
        for(input in listOf(
            listOf(baseline,baseline),
            listOf(baseline,RasterSatinTrialSettings(.2f,.15f,false)),
            listOf(baseline,RasterSatinTrialSettings(.5f,.4f,true)),
            listOf(baseline,RasterSatinTrialSettings(Float.NaN,.15f,false))
        )) assertTrue(RasterSatinParameterSensitivity.compare(
            img,part,sections,80f,hoop,context,input
        ).isFailure)
    }

    @Test fun individuallyFailingVariantIsReportedAndDoesNotReleasePES() {
        val img=stripe()
        val (part,sections)=plan(img)
        val report=RasterSatinParameterSensitivity.compare(
            img,part,sections,80f,hoop,context,
            listOf(baseline,underlay)
        ).getOrThrow()
        assertTrue(report.trials.any { it.passedGeometryQa })
        assertFalse(report.exportPermitted)
        assertTrue(report.trials.all { !it.machineApproved })
    }
}
