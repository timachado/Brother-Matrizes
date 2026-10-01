package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameReferenceSequenceTest {

    @Test
    fun foundationRunsBeforeSatinAndDoesNotTrimBetweenNearbyRegions() {
        val points =
            ImportedFontMatrixGenerator
                .debugTwoPhaseReferencePath()

        val foundation =
            points.takeWhile {
                it.colorIndex ==
                    0
            }

        val satin =
            points.drop(
                foundation.size
            )

        assertTrue(
            "A costura-base precisa existir antes do Satin.",
            foundation.any {
                it.command ==
                    StitchCommand.STITCH
            }
        )

        assertTrue(
            "A costura-base não deve cortar linha entre regiões próximas.",
            foundation.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )

        assertTrue(
            "O preenchimento Satin deve começar somente depois da base.",
            satin.any {
                it.command ==
                    StitchCommand.STITCH
            }
        )
    }
}
