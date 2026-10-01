package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameReferenceVideoSequenceTest {

    @Test
    fun oneWayUnderlayHandsOffToSatinAtSameEnd() {
        val points =
            ImportedFontMatrixGenerator
                .debugReferenceOneWayUnderlaySequence()

        val firstEdgeIndex =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.STITCH &&
                    it.xUnits !=
                        10
            }

        assertTrue(
            firstEdgeIndex >
                1
        )

        val centerPass =
            points
                .subList(
                    0,
                    firstEdgeIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertTrue(
            centerPass
                .zipWithNext()
                .all {
                        pair ->
                    pair.second.yUnits >=
                        pair.first.yUnits
                }
        )

        assertEquals(
            50,
            centerPass.last()
                .yUnits
        )

        assertEquals(
            50,
            points[firstEdgeIndex]
                .yUnits
        )

        assertTrue(
            points.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }
}
