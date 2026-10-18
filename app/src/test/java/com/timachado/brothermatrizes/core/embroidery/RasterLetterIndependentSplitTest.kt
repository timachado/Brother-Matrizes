package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterLetterIndependentSplitTest {
    private val color=0xffca7028.toInt()
    private val width=90f
    private val hoop=HoopProfile.H130X180

    private fun fixture():Triple<RasterImagePixels,RasterSatinObject,
        RasterLetterSatinDraft> {
        val w=160
        val h=100
        val pixels=IntArray(w*h) { i->
            if(i%w in 22..30 && i/w in 12..83) color else 0
        }
        val source=RasterImagePixels(w,h,pixels,"haste Satin longa")
        val part=RasterSatinObject(
            id=1,color=color,pixelCount=9*72,
            mask=SeparatedColorArtwork(
                colors=listOf(color),
                pixels=IntArray(pixels.size) {index->
                    if(pixels[index]!=0)0 else -1
                },
                minX=22,maxX=30,minY=12,maxY=83
            )
        )
        val section=ManualSatinSection(
            rails=(0..20).map {i->
                val v=.06f+.88f*i/20f
                SatinRailPair(.09f,v,.91f,v)
            }
        )
        val design=RasterSatinObjectEditor.preview(
            source,part,listOf(section),width,hoop,
            minimumCoverage=.68f
        ).getOrThrow()
        val fidelity=RasterStitchFidelityAudit.compare(
            source,part,design,width
        ).getOrThrow()
        val topology=RasterLetterTopologyReview.inspect(
            source,part
        ).getOrThrow()
        val draft=RasterLetterSatinDraft(
            sections=listOf(section),preview=design,
            coverage=fidelity.coverage,
            offInkFraction=fidelity.offInkFraction,
            diagnosis=topology
        )
        return Triple(source,part,draft)
    }

    @Test fun splittingSatinUsesTwoOriginalRailFragmentsAndRealTrimJump() {
        val (source,part,original)=fixture()
        val pixels=source.argb.copyOf()
        val result=RasterLetterEditableSatinReview.proposeIndependentSplit(
            source,part,original,0,10,width,hoop
        )
        assertTrue("Divisão da haste não foi auditável: "+
            result.exceptionOrNull()?.message,result.isSuccess)
        val split=result.getOrThrow()
        assertEquals(2,split.sections.size)
        assertEquals(original.sections[0].rails.take(11),
            split.sections[0].rails)
        assertEquals(original.sections[0].rails.drop(10),
            split.sections[1].rails)
        assertFalse(split.sections[1].connectToPrevious)
        assertEquals(original.preview.points.count {
            it.command==StitchCommand.TRIM
        }+1,split.preview.points.count {
            it.command==StitchCommand.TRIM
        })
        assertTrue(split.preview.jumpCount>original.preview.jumpCount)
        assertTrue(split.preview.stitchCount>0)
        assertEquals(0,split.revision)
        assertEquals(split.sections,split.baselineSections)
        assertFalse(split.exportPermitted)
        assertTrue(split.changeSummary().contains("Antes/depois"))
        assertTrue(source.argb.contentEquals(pixels))
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun tooShortSectionsAndWrongIndicesFailWithoutMutatingPrevious() {
        val (source,part,original)=fixture()
        val old=original.preview.points.toList()
        for (cut in listOf(0,1,4,16,18,20)) {
            assertTrue(RasterLetterEditableSatinReview.proposeIndependentSplit(
                source,part,original,0,cut,width,hoop
            ).isFailure)
        }
        assertTrue(RasterLetterEditableSatinReview.proposeIndependentSplit(
            source,part,original,9,10,width,hoop
        ).isFailure)
        assertEquals(old,original.preview.points)
        assertEquals(1,original.sections.size)
    }

    @Test fun changedSourceAndAlteredStitchesCannotBeSplit() {
        val (source,part,original)=fixture()
        val changed=source.argb.copyOf()
        changed[22*160+26]=0xff225599.toInt()
        val modified=RasterImagePixels(160,100,changed,"fonte alterada")
        assertTrue(RasterLetterEditableSatinReview.proposeIndependentSplit(
            modified,part,original,0,10,width,hoop
        ).isFailure)
        val points=original.preview.points.toMutableList()
        val stitchIndex=points.indexOfFirst {
            it.command==StitchCommand.STITCH
        }
        points[stitchIndex]=points[stitchIndex].copy(
            xUnits=points[stitchIndex].xUnits+1
        )
        assertTrue(RasterLetterEditableSatinReview.proposeIndependentSplit(
            source,part,original.copy(
                preview=original.preview.copy(points=points)
            ),0,10,width,hoop
        ).isFailure)
    }

    @Test fun alreadySplitOrInvalidHoopCannotBeAcceptedAsAnotherCut() {
        val (source,part,original)=fixture()
        val split=RasterLetterEditableSatinReview.proposeIndependentSplit(
            source,part,original,0,10,width,hoop
        ).getOrThrow()
        assertTrue(RasterLetterEditableSatinReview.proposeIndependentSplit(
            source,part,split,0,1,width,hoop
        ).isFailure)
        assertTrue(RasterLetterEditableSatinReview.proposeIndependentSplit(
            source,part,original,0,10,180f,HoopProfile.H100X100
        ).isFailure)
    }
}
