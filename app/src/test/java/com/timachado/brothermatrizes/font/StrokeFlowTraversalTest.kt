package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeFlowTraversalTest {

    @Test
    fun openInitialSwashStartsAtInnerLowerEndNotOuterLeftPoint() {
        val start = ImportedFontMatrixGenerator.debugOpenSwashEntry()
        assertEquals(55 to 80, start)
    }

    @Test
    fun openingLoopStillStartsAtRequestedEntryWhenConnectedToLetter() {
        val (firstPoint, resultingSegments) =
            ImportedFontMatrixGenerator.debugConnectedLoopEntrance()
        assertEquals(5 to 10, firstPoint)
        // O laco se dividiu no no de conexao com o restante do M.
        // Ainda assim o ponto inicial precisa sobreviver.
        assertEquals(3, resultingSegments)
    }

    @Test
    fun closedOpeningLoopStartsNearInnerLowerStrokeWithoutChangingItsEdges() {
        val (initialPoint, remainsClosed, sameEdges) =
            ImportedFontMatrixGenerator.debugClosedLoopEntrance()

        assertEquals(5 to 10, initialPoint)
        assertEquals(true, remainsClosed)
        assertEquals(true, sameEdges)
    }

    @Test
    fun startsAtLowerLeftEntryInsteadOfLongOuterContour() {
        val entry = ImportedFontMatrixGenerator.debugStrokeFlowLowerEntry()
        assertEquals(35 to 85, entry)
    }

    @Test
    fun eachAreaFinishesItsSatinBeforeMovingToNextBranch() {
        val plan =
            ImportedFontMatrixGenerator
                .debugStrokeFlowPostOrder()

        assertEquals(
            listOf(
                "CENTER_RUN:0,0->0,20",
                "SATIN_RETURN:0,20->0,0",
                "CENTER_RUN:0,20->20,40",
                "SATIN_RETURN:20,40->0,20",
                "CENTER_RUN:0,20->-20,40",
                "SATIN_RETURN:-20,40->0,20"
            ),
            plan
        )
        for (i in plan.indices step 2) {
            assertEquals("CENTER_RUN", plan[i].substringBefore(':'))
            assertEquals("SATIN_RETURN", plan[i + 1].substringBefore(':'))
            val started = plan[i].substringAfter(':')
            val returned = plan[i + 1].substringAfter(':')
            assertEquals(
                "O retorno Satin deve cobrir o mesmo segmento, no sentido inverso.",
                started.substringBefore("->"),
                returned.substringAfter("->")
            )
            assertEquals(started.substringAfter("->"), returned.substringBefore("->"))
        }
    }
}
