package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeFlowTraversalTest {

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
