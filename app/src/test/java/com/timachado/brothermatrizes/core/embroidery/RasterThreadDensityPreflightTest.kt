package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterThreadDensityPreflightTest {
    private fun p(
        x: Int, y: Int,
        command: StitchCommand,
        color: Int=0
    ) = EmbroideryPoint(x,y,command,color)

    private fun qa(
        commands: List<EmbroideryPoint>,
        format: String="QA_AUTO_STITCH"
    ): EmbroideryDesign {
        val sewn = commands.filter {
            it.command==StitchCommand.JUMP ||
                it.command==StitchCommand.STITCH
        }
        return EmbroideryDesign(
            fileName="density-qa",format=format,
            label="densidade experimental de linha",
            points=commands,
            bounds=EmbroideryBounds(
                sewn.minOf { it.xUnits },sewn.maxOf { it.xUnits },
                sewn.minOf { it.yUnits },sewn.maxOf { it.yUnits }
            ),
            stitchCount=commands.count { it.command==StitchCommand.STITCH },
            jumpCount=commands.count { it.command==StitchCommand.JUMP },
            colorChanges=0,endFound=true,
            sourceBytes=ByteArray(0),
            threadColors=listOf(0xff274360.toInt()),
            isModified=true,hoopProfile=HoopProfile.H130X180
        )
    }

    @Test fun threeMillimetreStitchIsSplitIntoThreeOneMillimetreCells() {
        val design=qa(listOf(
            p(0,5,StitchCommand.JUMP),
            p(30,5,StitchCommand.STITCH),
            p(30,5,StitchCommand.END)
        ))
        val report=RasterThreadDensityPreflight.inspect(design).getOrThrow()
        assertEquals(3,report.cellsObserved)
        assertEquals(1,report.sewnSegments)
        assertEquals(3.0,report.sewnThreadLengthMm,1e-8)
        assertEquals(1.0,report.peakThreadLengthMmPerCell,1e-8)
    }

    @Test fun manyNeedleDownPassesOfSameThreadGetBlocked() {
        val commands=ArrayList<EmbroideryPoint>()
        repeat(27) {
            commands+=p(0,5,StitchCommand.JUMP)
            commands+=p(8,5,StitchCommand.STITCH)
        }
        commands+=p(8,5,StitchCommand.END)
        val failure=RasterThreadDensityPreflight.inspect(qa(commands))
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty()
            .contains("Concentração de linha"))
        // Earlier colored-layer check would see just one color. It would
        // not catch accumulating 21.6 mm of the SAME thread in one cell.
        val old=RasterColorLayerOverlapAudit.inspect(qa(commands))
        assertTrue(old.isSuccess)
        assertEquals(1,old.getOrThrow().maximumDistinctColorsPerCell)
    }

    @Test fun jumpsAndTrimsDoNotAddThreadDensity() {
        val design=qa(listOf(
            p(0,5,StitchCommand.JUMP),
            p(100,5,StitchCommand.JUMP),
            p(108,5,StitchCommand.STITCH),
            p(108,5,StitchCommand.TRIM),
            p(-100,5,StitchCommand.JUMP),
            p(-92,5,StitchCommand.STITCH),
            p(-92,5,StitchCommand.END)
        ))
        val report=RasterThreadDensityPreflight.inspect(design).getOrThrow()
        assertEquals(2,report.sewnSegments)
        assertEquals(1.6,report.sewnThreadLengthMm,1e-8)
        assertEquals(2,report.cellsObserved)
        assertTrue(report.peakThreadLengthMmPerCell<=.8+1e-8)
    }

    @Test fun trimThenStitchWithoutJumpIsInvalid() {
        val design=qa(listOf(
            p(0,5,StitchCommand.JUMP),
            p(10,5,StitchCommand.STITCH),
            p(10,5,StitchCommand.TRIM),
            p(14,5,StitchCommand.STITCH),
            p(14,5,StitchCommand.END)
        ))
        assertTrue(RasterThreadDensityPreflight.inspect(design).isFailure)
    }

    @Test fun originalPESIsExcludedFromDensityExperiments() {
        val commands=listOf(
            p(0,5,StitchCommand.JUMP),
            p(15,5,StitchCommand.STITCH),
            p(15,5,StitchCommand.END)
        )
        assertTrue(RasterThreadDensityPreflight.inspect(
            qa(commands,format="PES")
        ).isFailure)
    }
}
