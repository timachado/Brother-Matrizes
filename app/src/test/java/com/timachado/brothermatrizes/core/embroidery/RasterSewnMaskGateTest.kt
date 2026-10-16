package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterSewnMaskGateTest {
    private val navy = 0xff204365.toInt()
    private val white = 0xffffffff.toInt()
    private val width = 180
    private val height = 130
    private val widthMm = 90f
    private val hoop = HoopProfile.H130X180

    private fun ring() = RasterImagePixels(
        width, height, IntArray(width*height) { i ->
            val x=i%width
            val y=i/width
            if (x in 40..129 && y in 30..97 &&
                !(x in 70..75 && y in 60..70)) navy else white
        },"QA pontos reais em contraforma"
    )

    private fun point(x: Double,y: Double,command: StitchCommand): EmbroideryPoint {
        val mmPx=widthMm/width
        return EmbroideryPoint(
            ((x-width/2.0)*mmPx*10).roundToInt(),
            ((height/2.0-y)*mmPx*10).roundToInt(),
            command,0
        )
    }

    private fun synthetic(points: List<EmbroideryPoint>) = EmbroideryDesign(
        fileName="qa-no-pes",
        format="QA_TATAMI_REVIEW",
        label="only test",
        points=points,
        bounds=EmbroideryBounds(
            points.minOf { it.xUnits },points.maxOf { it.xUnits },
            points.minOf { it.yUnits },points.maxOf { it.yUnits }
        ),
        stitchCount=points.count { it.command==StitchCommand.STITCH },
        jumpCount=points.count { it.command==StitchCommand.JUMP },
        colorChanges=0,endFound=true,
        sourceBytes=ByteArray(0),threadColors=listOf(navy),
        isModified=true,hoopProfile=hoop
    )

    @Test fun originalCounterAwareTatamiPassesFinalIntegerAudit() {
        val image=ring()
        val part=RasterSatinObjectEditor.detect(image).getOrThrow().single()
        val fill=RasterTatamiShapePreview.create(
            image,part,widthMm,hoop
        ).getOrThrow()
        val result=RasterSewnMaskGate.inspect(
            image,part,fill,widthMm
        ).getOrThrow()
        assertTrue(result.sewnSegments>50)
        assertTrue(result.sampledPositions>result.sewnSegments)
        assertTrue(result.jumpCommands>1)
        assertTrue(result.trimCommands>0)
        assertTrue(fill.sourceBytes.isEmpty())
        assertFalse(RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun aShortStitchAcrossHoleIsBlockedEvenWithSafeNominalPitch() {
        val image=ring()
        val part=RasterSatinObjectEditor.detect(image).getOrThrow().single()
        // The endpoints are ink. The 5 mm sewn line crosses x=70..75
        // and therefore is NOT an acceptable needle-down segment.
        val path=synthetic(listOf(
            point(68.5,65.5,StitchCommand.JUMP),
            point(78.5,65.5,StitchCommand.STITCH),
            point(78.5,65.5,StitchCommand.END)
        ))
        val outcome=RasterSewnMaskGate.inspect(
            image,part,path,widthMm
        )
        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()?.message.orEmpty()
            .contains("área vazia"))
    }

    @Test fun jumpingOverCounterIsValidButSewingIsNot() {
        val image=ring()
        val part=RasterSatinObjectEditor.detect(image).getOrThrow().single()
        val commands=listOf(
            point(63.5,65.5,StitchCommand.JUMP),
            point(67.5,65.5,StitchCommand.STITCH),
            point(67.5,65.5,StitchCommand.TRIM),
            point(79.5,65.5,StitchCommand.JUMP),
            point(83.5,65.5,StitchCommand.STITCH),
            point(83.5,65.5,StitchCommand.END)
        )
        val result=RasterSewnMaskGate.inspect(
            image,part,synthetic(commands),widthMm
        ).getOrThrow()
        assertEquals(2,result.sewnSegments)
        assertEquals(2,result.jumpCommands)
        assertEquals(1,result.trimCommands)
    }

    @Test fun fakePESCannotEnterExperimentalValidator() {
        val img=ring()
        val part=RasterSatinObjectEditor.detect(img).getOrThrow().single()
        val commands=listOf(
            point(63.5,65.5,StitchCommand.JUMP),
            point(67.5,65.5,StitchCommand.STITCH),
            point(67.5,65.5,StitchCommand.END)
        )
        assertTrue(RasterSewnMaskGate.inspect(
            img,part,synthetic(commands).copy(format="PES"),widthMm
        ).isFailure)
    }
}
