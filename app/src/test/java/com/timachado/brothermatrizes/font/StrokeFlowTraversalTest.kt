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
    fun progressiveCoverageFinishesAtNextNodeInsteadOfReturningToEntry() {
        val plan = ImportedFontMatrixGenerator.debugStrokeFlowPostOrder()
        assertEquals(
            listOf(
                "CENTER_RUN:0,0->0,20",
                "CENTER_RUN:0,20->20,40",
                "CENTER_RUN:0,20->-20,40"
            ),
            plan
        )
    }

    @Test
    fun interleavedSatinNeverWalksBackwardAlongCompletedSegment() {
        val (stitchedX, jumps) =
            ImportedFontMatrixGenerator.debugProgressiveStrokeFlow()
        assertEquals(1, jumps)
        assertEquals(0, stitchedX.first())
        assertEquals(120, stitchedX.last())
        assertEquals(
            "Underlay e cobertura devem progredir pela area sem ida/volta integral.",
            stitchedX.sorted(),
            stitchedX
        )
    }

    @Test
    fun requestedInnerFootBecomesRealGraphNodeAndGoesTowardTheLetter() {
        val (foot, firstExitX) =
            ImportedFontMatrixGenerator.debugSeedAtInnerFoot()
        assertEquals(32 to 88, foot)
        assertEquals(100, firstExitX)
    }
}
