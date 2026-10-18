package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSatinCurveHotspotReviewTest {
    private val thread=0xffcf7c29.toInt()
    private val hoop=HoopProfile.H130X180
    private val width=90f

    private fun fixture():Pair<RasterImagePixels,RasterSatinObject> {
        val w=160
        val h=100
        val px=IntArray(w*h) {i->
            if(i%w in 22..30 && i/w in 12..83) thread else 0
        }
        val source=RasterImagePixels(w,h,px,"haste sem curva")
        val mask=SeparatedColorArtwork(
            colors=listOf(thread),
            pixels=IntArray(px.size) {i->if(px[i]!=0)0 else -1},
            minX=22,maxX=30,minY=12,maxY=83
        )
        return source to RasterSatinObject(
            id=1,color=thread,pixelCount=9*72,mask=mask
        )
    }

    @Test fun realStitchPreviewIsRequiredBeforeAnyHotspotGuidance() {
        val (source,part)=fixture()
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val report=RasterSatinCurveHotspotReview.inspect(
            source,part,draft,width,hoop
        ).getOrThrow()
        assertEquals(draft.sections.size,report.sectionCount)
        assertEquals(draft.sections.sumOf {it.rails.size},report.reviewedPairs)
        assertEquals(draft.preview.stitchCount,report.sourceStitchCount)
        assertTrue(report.message().contains("Nenhum ajuste"))
        assertTrue(report.requiresOperatorReview)
        assertFalse(report.exportPermitted)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun geometryRanksSharpCurveWithoutSilentlyMovingRails() {
        // Centers form a clear L bend; this is a GEOMETRY diagnosis,
        // not an assertion that these rails make valid machine stitches.
        fun rail(cx:Float,cy:Float): SatinRailPair =
            SatinRailPair(cx-.045f,cy,cx+.045f,cy)
        val sections=listOf(ManualSatinSection(listOf(
            rail(.22f,.15f),rail(.35f,.15f),rail(.48f,.15f),
            rail(.55f,.28f),rail(.55f,.41f),rail(.55f,.54f)
        )))
        val sourceRails=sections.first().rails.toList()
        val review=RasterSatinCurveHotspotReview.rankGeometry(
            sections,20.0,20.0
        ).getOrThrow()
        assertTrue(review.any {
            it.type==RasterSatinHotspotType.SHARP_CURVE &&
                it.turnDegrees>=26.0
        })
        assertEquals(sourceRails,sections.first().rails)
        assertTrue(review.all {it.sectionIndex==0})
    }

    @Test fun abruptWidthTransitionIsReportedAtCorrectPair() {
        fun rail(cx:Float,cy:Float,radius:Float)=
            SatinRailPair(cx-radius,cy,cx+radius,cy)
        val sections=listOf(ManualSatinSection(listOf(
            rail(.4f,.2f,.045f),rail(.4f,.33f,.045f),
            rail(.4f,.46f,.080f),rail(.4f,.59f,.080f)
        )))
        val report=RasterSatinCurveHotspotReview.rankGeometry(
            sections,20.0,20.0
        ).getOrThrow()
        assertTrue(report.any {
            it.type==RasterSatinHotspotType.WIDTH_TRANSITION &&
                it.pairIndex in 1..2
        })
    }

    @Test fun straightConstantWidthStemHasNoFalseHotspot() {
        fun rail(y:Float)=SatinRailPair(.4f,y,.52f,y)
        val sections=listOf(ManualSatinSection(listOf(
            rail(.10f),rail(.20f),rail(.30f),
            rail(.40f),rail(.50f),rail(.60f)
        )))
        assertTrue(RasterSatinCurveHotspotReview.rankGeometry(
            sections,20.0,20.0
        ).getOrThrow().isEmpty())
    }

    @Test fun staleSourceOrForgedStitchesCannotClaimCurvesAreAudited() {
        val (source,part)=fixture()
        val draft=RasterLetterEditableSatinReview.propose(
            source,part,width,hoop
        ).getOrThrow()
        val stitches=draft.preview.points.toMutableList()
        val index=stitches.indexOfFirst {
            it.command==StitchCommand.STITCH
        }
        stitches[index]=stitches[index].copy(
            xUnits=stitches[index].xUnits+1
        )
        assertTrue(RasterSatinCurveHotspotReview.inspect(
            source,part,draft.copy(
                preview=draft.preview.copy(points=stitches)
            ),width,hoop
        ).isFailure)
        val sourceCopy=source.argb.copyOf()
        sourceCopy[40*160+25]=0xff203c55.toInt()
        assertTrue(RasterSatinCurveHotspotReview.inspect(
            RasterImagePixels(160,100,sourceCopy,"outra cor"),
            part,draft,width,hoop
        ).isFailure)
    }

    @Test fun invalidCoordinateAndGeometryDimensionsCannotBeApproved() {
        val section=ManualSatinSection(listOf(
            SatinRailPair(.1f,.1f,.3f,.1f),
            SatinRailPair(Float.NaN,.2f,.3f,.2f)
        ))
        assertTrue(RasterSatinCurveHotspotReview.rankGeometry(
            listOf(section),15.0,15.0
        ).isFailure)
        assertTrue(RasterSatinCurveHotspotReview.rankGeometry(
            listOf(section.copy(rails=listOf(
                SatinRailPair(.1f,.1f,.3f,.1f),
                SatinRailPair(.1f,.2f,.3f,.2f)
            ))),Double.NaN,15.0
        ).isFailure)
    }
}
