package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterVariableWidthSatinSectionsTest {
    private val navy=0xff193c58.toInt()
    private val white=0xffffffff.toInt()
    private val hoop=HoopProfile.H130X180
    private fun photo(
        uniform:Boolean=false,
        sharp:Boolean=false,
        gap:Boolean=false
    ): RasterImagePixels {
        val w=160
        val h=110
        val argb=IntArray(w*h) { index ->
            val x=index%w
            val y=index/w
            val radius=when {
                uniform -> 4
                sharp && y<52 -> 2
                sharp -> 5
                else -> 2+(y-14)*4/76
            }
            if(y in 14..90 && x in (80-radius)..(80+radius) &&
                !(gap && y in 51..55 && x in 78..82)
            ) navy else white
        }
        return RasterImagePixels(w,h,argb,"coluna Satin afunilada QA")
    }
    private fun only(img:RasterImagePixels):RasterSatinObject =
        RasterSatinObjectEditor.detect(img).getOrThrow().single()

    @Test fun variableWidthIsDecomposedWithoutDroppingASection() {
        val img=photo()
        val part=only(img)
        val normal=RasterAutoSatinRails.propose(img,part,80f)
        assertTrue("Single-column limit must remain conservative",
            normal.isFailure)
        val segmented=RasterVariableWidthSatinSections.propose(
            img,part,80f
        ).getOrThrow()
        assertTrue(segmented.size in 2..6)
        assertTrue(segmented.all { !it.connectToPrevious })
        assertTrue(segmented.all { it.rails.size >= 7 })
        val preview=RasterSatinObjectEditor.preview(
            img,part,segmented,80f,hoop,minimumCoverage=.90f
        ).getOrThrow()
        assertTrue(preview.points.count {
            it.command==StitchCommand.TRIM
        } >= segmented.size-1)
        assertTrue(preview.jumpCount>=segmented.size)
        val fidelity=RasterStitchFidelityAudit.compare(
            img,part,preview,80f
        ).getOrThrow()
        assertTrue("coverage=${fidelity.coverage}",fidelity.coverage>=.78)
        assertTrue("offInk=${fidelity.offInkFraction}",
            fidelity.offInkFraction<=.10)
        RasterNeedleRiskAudit.inspect(preview,hoop).getOrThrow()
        assertTrue(preview.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun variableWidthBandIsPartOfFullLogoQaWithoutPES() {
        val img=photo()
        val preview=RasterAutomaticLogoPreview.create(
            img,80f,hoop
        ).getOrThrow()
        assertTrue(preview.stitchCount>30)
        assertTrue(preview.jumpCount>=2)
        assertTrue(preview.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun constantWidthStillUsesExistingSimpleSatinPath() {
        val img=photo(uniform=true)
        val part=only(img)
        assertTrue(RasterAutoSatinRails.propose(img,part,80f)
            .isSuccess)
        assertTrue(RasterVariableWidthSatinSections.propose(
            img,part,80f
        ).isFailure)
    }

    @Test fun discontinuityOrAbruptExpansionNeedsManualReview() {
        val a=photo(gap=true)
        val b=photo(sharp=true)
        assertTrue(RasterVariableWidthSatinSections.propose(
            a,only(a),80f
        ).isFailure)
        assertTrue(RasterVariableWidthSatinSections.propose(
            b,only(b),80f
        ).isFailure)
    }
}
