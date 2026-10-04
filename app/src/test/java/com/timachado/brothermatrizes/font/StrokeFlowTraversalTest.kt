package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeFlowTraversalTest {

    @Test
    fun branchesAreCoveredPostOrderBeforeParentSatin() {
        val plan =
            ImportedFontMatrixGenerator
                .debugStrokeFlowPostOrder()

        assertEquals(
            listOf(
                "CENTER_RUN:0,0->0,20",
                "CENTER_RUN:0,20->20,40",
                "SATIN_RETURN:20,40->0,20",
                "CENTER_RUN:0,20->-20,40",
                "SATIN_RETURN:-20,40->0,20",
                "SATIN_RETURN:0,20->0,0"
            ),
            plan
        )
    }
}
