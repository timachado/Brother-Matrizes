package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PesOrderedStitchReadbackTest {
    private fun point(x:Int,y:Int,command:StitchCommand) =
        EmbroideryPoint(x,y,command,0)

    private fun design(stitches:List<Pair<Int,Int>>):EmbroideryDesign {
        val jump = point(0,0,StitchCommand.JUMP)
        val points = listOf(jump) +
            stitches.map { (x,y) -> point(x,y,StitchCommand.STITCH) } +
            point(stitches.last().first,stitches.last().second,
                StitchCommand.END)
        return EmbroideryDesign(
            fileName = "ordem-QA.pes",format = "PES",
            label = "teste de sequência de agulha",points = points,
            bounds = EmbroideryBounds(
                0,stitches.maxOf { it.first },
                0,stitches.maxOf { it.second }
            ),
            stitchCount = stitches.size,jumpCount = 1,
            colorChanges = 0,endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(0xff1b3c5a.toInt()),
            sourceYAxisDown = true,
            isModified = true,hoopProfile = HoopProfile.H130X180
        )
    }
    private val route = listOf(
        10 to 10, 30 to 10, 30 to 40, 50 to 40
    )

    @Test fun exactPathRetainsEveryNeedleDownInOrder() {
        val src=design(route)
        val result=PesOrderedStitchReadback.compare(src,src)
            .getOrThrow()
        assertTrue(result.orderedNeedlePathPreserved)
        assertEquals(4,result.matchedStitchesInOrder)
        assertFalse(result.externalDecoderVerified)
        assertFalse(result.machineCertified)
    }

    @Test fun sameNeedleLocationsInWrongOrderMustFail() {
        val source=design(route)
        val scrambled=design(listOf(
            route[0],route[2],route[1],route[3]
        ))
        assertEquals(
            source.points.filter { it.command==StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }.toSet(),
            scrambled.points.filter { it.command==StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }.toSet()
        )
        assertTrue(PesOrderedStitchReadback.compare(
            source,scrambled
        ).isFailure)
    }

    @Test fun missingRepeatedPassFailsEvenWhenSetOfCoordinatesIsUnchanged() {
        val repeated=design(listOf(
            route[0],route[1],route[0],route[1],route[2],route[3]
        ))
        val deduplicated=design(route)
        assertEquals(
            repeated.points.filter { it.command==StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }.toSet(),
            deduplicated.points.filter { it.command==StitchCommand.STITCH }
                .map { it.xUnits to it.yUnits }.toSet()
        )
        assertTrue(PesOrderedStitchReadback.compare(
            repeated,deduplicated
        ).isFailure)
    }

    @Test fun legitimateInterpolatedStitchesAndInitialPositioningAreAllowed() {
        val extra=design(listOf(
            0 to 0, route[0], 20 to 10, route[1],
            30 to 25, route[2], 40 to 40, route[3]
        ))
        val report=PesOrderedStitchReadback.compare(
            design(route),extra
        ).getOrThrow()
        assertEquals(4,report.expectedStitches)
        assertEquals(8,report.reopenedStitches)
        assertEquals(4,report.matchedStitchesInOrder)
    }

    @Test fun qaRasterDesignCannotUsePesOnlyReadback() {
        val source=design(route)
        val qaSource=source.copy(format="QA_AUTO_STITCH")
        assertTrue(PesOrderedStitchReadback.compare(
            qaSource,source
        ).isFailure)
        assertTrue(PesOrderedStitchReadback.compare(
            source,source.copy(format="DST")
        ).isFailure)
    }

    @Test fun toleranceGreaterThanOneIntegerUnitIsRejected() {
        assertTrue(PesOrderedStitchReadback.compare(
            design(route),design(route),toleranceUnits=2
        ).isFailure)
    }
}
