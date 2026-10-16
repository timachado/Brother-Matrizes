package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterCounterSatinGateTest {
    private val color=0xffdf8934.toInt()
    private val imageWidth=140
    private val imageHeight=100
    private val widthMm=70f
    private val hoop=HoopProfile.H130X180

    private fun bitmap(draw:(Int,Int)->Boolean) = RasterImagePixels(
        imageWidth,imageHeight,
        IntArray(imageWidth*imageHeight){i->
            if(draw(i%imageWidth,i/imageWidth))color else 0x00000000
        },"contraforma da letra QA"
    )
    private fun ring()=bitmap {x,y->
        x in 30..106 && y in 13..89 &&
            (x<=39 || x>=97 || y<=22 || y>=80)
    }
    private fun letterP()=bitmap {x,y->
        (x in 30..41 && y in 13..90) ||
            (x in 30..100 && y in 13..56 &&
                !(x in 49..87 && y in 26..43))
    }
    private fun letterB()=bitmap {x,y->
        x in 30..108 && y in 13..90 &&
            !(x in 48..94 && y in 24..43) &&
            !(x in 48..94 && y in 58..78)
    }
    private fun part(image:RasterImagePixels)=
        RasterSatinObjectEditor.detect(image).getOrThrow().single()
    private fun point(x:Double,y:Double,cmd:StitchCommand)=
        EmbroideryPoint(
            ((x-imageWidth/2.0)*widthMm/imageWidth*10).roundToInt(),
            ((imageHeight/2.0-y)*widthMm/imageWidth*10).roundToInt(),
            cmd,0
        )
    private fun draft(vararg points:EmbroideryPoint):EmbroideryDesign {
        val p=points.toList()
        return EmbroideryDesign(
            fileName="counter-no-pes.qa", format="QA_SATIN_REVIEW",
            label="counter gate QA",points=p,
            bounds=EmbroideryBounds(
                p.minOf { it.xUnits },p.maxOf { it.xUnits },
                p.minOf { it.yUnits },p.maxOf { it.yUnits }
            ),
            stitchCount=p.count { it.command==StitchCommand.STITCH },
            jumpCount=p.count { it.command==StitchCommand.JUMP },
            colorChanges=0,endFound=true,
            sourceBytes=ByteArray(0),threadColors=listOf(color),
            isModified=true,hoopProfile=hoop
        )
    }

    @Test fun detectsOneOAndPAndTwoBCounters() {
        assertEquals(1,RasterCounterSatinGate.count(ring(),part(ring())).getOrThrow())
        assertEquals(1,RasterCounterSatinGate.count(letterP(),part(letterP())).getOrThrow())
        assertEquals(2,RasterCounterSatinGate.count(letterB(),part(letterB())).getOrThrow())
    }

    @Test fun existingTopologyProposedOSatinCanBeReviewedWithoutPES() {
        val img=ring()
        val obj=part(img)
        val sections=RasterSatinTopologyPlanner.propose(
            img,obj,widthMm
        ).getOrThrow()
        assertTrue(sections.size>=4)
        val preview=RasterSatinObjectEditor.preview(
            img,obj,sections,widthMm,hoop,minimumCoverage=.90f
        ).getOrThrow()
        val report=RasterCounterSatinGate.inspect(
            img,obj,preview,widthMm
        ).getOrThrow()
        assertEquals(1,report.counterCount)
        assertTrue(report.largestCounterPixels>100)
        assertEquals(preview.stitchCount,report.sewnSegmentsInspected)
        assertEquals(0,report.counterCoreSamples)
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun deliberatelySewnChordThroughCounterIsBlocked() {
        val img=ring()
        val stream=draft(
            point(36.5,50.5,StitchCommand.JUMP),
            point(49.5,50.5,StitchCommand.STITCH),
            point(49.5,50.5,StitchCommand.END)
        )
        val outcome=RasterCounterSatinGate.inspect(
            img,part(img),stream,widthMm
        )
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()?.message.orEmpty()
            .contains("contraforma"))
    }

    @Test fun trimAndJumpAcrossHoleMustNotBeMisreadAsSewing() {
        val img=ring()
        val stream=draft(
            point(32.5,50.5,StitchCommand.JUMP),
            point(35.5,50.5,StitchCommand.STITCH),
            point(35.5,50.5,StitchCommand.TRIM),
            point(99.5,50.5,StitchCommand.JUMP),
            point(102.5,50.5,StitchCommand.STITCH),
            point(102.5,50.5,StitchCommand.END)
        )
        val result=RasterCounterSatinGate.inspect(
            img,part(img),stream,widthMm
        ).getOrThrow()
        assertEquals(2,result.sewnSegmentsInspected)
        assertEquals(1,result.counterCount)
    }

    @Test fun noCounterAndImportedPESAreRejectedByThisGate() {
        val noHole=bitmap {x,y->x in 30..106 && y in 13..89}
        val s=draft(
            point(32.5,50.5,StitchCommand.JUMP),
            point(35.5,50.5,StitchCommand.STITCH),
            point(35.5,50.5,StitchCommand.END)
        )
        assertEquals(0,RasterCounterSatinGate.count(
            noHole,part(noHole)
        ).getOrThrow())
        assertTrue(RasterCounterSatinGate.inspect(
            noHole,part(noHole),s,widthMm
        ).isFailure)
        assertTrue(RasterCounterSatinGate.inspect(
            ring(),part(ring()),s.copy(format="PES"),widthMm
        ).isFailure)
    }
}
