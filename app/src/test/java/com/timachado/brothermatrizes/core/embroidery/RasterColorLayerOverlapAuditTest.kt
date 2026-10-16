package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterColorLayerOverlapAuditTest {
    private fun commands(colors: Int, spatiallySeparated: Boolean): EmbroideryDesign {
        val points=ArrayList<EmbroideryPoint>()
        for (i in 0 until colors) {
            val start=if (spatiallySeparated) i*130 else i
            if (i>0) {
                val end=points.last()
                points+=EmbroideryPoint(
                    end.xUnits,end.yUnits,StitchCommand.TRIM,i-1)
                points+=EmbroideryPoint(
                    end.xUnits,end.yUnits,StitchCommand.COLOR_CHANGE,i)
            }
            points+=EmbroideryPoint(start,0,StitchCommand.JUMP,i)
            points+=EmbroideryPoint(start+20,0,StitchCommand.STITCH,i)
        }
        val last=points.last()
        points+=last.copy(command=StitchCommand.END)
        return EmbroideryDesign(
            fileName="overlap-test.qa",format="QA_AUTO_STITCH",
            label="intersections",
            points=points,
            bounds=EmbroideryBounds(0,points.maxOf { it.xUnits },0,0),
            stitchCount=colors,jumpCount=colors,
            colorChanges=colors-1,endFound=true,
            sourceBytes=ByteArray(0),isModified=true,
            threadColors=(0 until colors).map { 0xff002200.toInt()+it },
            hoopProfile=HoopProfile.H130X180
        )
    }

    @Test fun threeDifferentColorsInSameCellAreBlocked() {
        val out=RasterColorLayerOverlapAudit.inspect(
            commands(3,spatiallySeparated=false)
        )
        assertTrue(out.isFailure)
        assertTrue(out.exceptionOrNull()?.message.orEmpty()
            .contains("Três cores"))
    }

    @Test fun twoColorsAreReportedButNotCertifiedForMachine() {
        val result=RasterColorLayerOverlapAudit.inspect(
            commands(2,spatiallySeparated=false)
        ).getOrThrow()
        assertEquals(2,result.maximumDistinctColorsPerCell)
        assertEquals(2,result.sewnSegments)
        assertTrue(result.cellsObserved>0)
        assertTrue(!RasterEmbroideryApproval.canExportFromImageTool())
    }

    @Test fun distantDifferentColorsAreNotAnOverlap() {
        val result=RasterColorLayerOverlapAudit.inspect(
            commands(3,spatiallySeparated=true)
        ).getOrThrow()
        assertEquals(1,result.maximumDistinctColorsPerCell)
    }

    @Test fun originalPESIsNeverReinterpretedAsAnExperimentalPreview() {
        val design=commands(3,spatiallySeparated=true).copy(format="PES")
        assertTrue(RasterColorLayerOverlapAudit.inspect(design).isFailure)
    }
}
