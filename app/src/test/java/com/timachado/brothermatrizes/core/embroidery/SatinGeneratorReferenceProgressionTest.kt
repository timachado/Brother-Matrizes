package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinGeneratorReferenceProgressionTest {

    @Test
    fun centerWalkReachesBranchTipBeforeReverseSatinStarts() {
        val points =
            mutableListOf<
                EmbroideryPoint
            >()

        SatinGenerator.append(
            points =
                points,
            stroke =
                listOf(
                    0 to 0,
                    100 to 0
                ),
            currentX =
                0,
            currentY =
                0,
            widthUnits =
                40f,
            stepUnits =
                4f,
            pullCompensationUnits =
                0f,
            shortStitches =
                true,
            underlayMode =
                SatinUnderlayMode.BOTH
        )

        val firstWideIndex =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.STITCH &&
                abs(
                    it.yUnits
                ) >=
                    15
            }

        assertTrue(
            firstWideIndex >
                0
        )

        val foundation =
            points
                .take(
                    firstWideIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertTrue(
            "A passada central precisa avançar somente até a ponta do ramo.",
            foundation
                .zipWithNext()
                .all {
                        pair ->
                    pair.second.xUnits >=
                        pair.first.xUnits
                }
        )

        assertEquals(
            "O underlay deve chegar ao fim do ramo antes do Satin.",
            100,
            foundation.last()
                .xUnits
        )

        assertEquals(
            "O primeiro ponto largo deve começar na mesma ponta, sem salto.",
            StitchCommand.STITCH,
            points[firstWideIndex]
                .command
        )

        assertTrue(
            "Depois da ponta, o Satin precisa retornar pelo mesmo ramo.",
            points
                .drop(
                    firstWideIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }
                .any {
                    it.xUnits <
                        80
                }
        )
    }

    @Test
    fun bothDoesNotAddTheOldThirdGlobalPass() {
        val points =
            mutableListOf<
                EmbroideryPoint
            >()

        SatinGenerator.append(
            points =
                points,
            stroke =
                listOf(
                    0 to 0,
                    100 to 0
                ),
            currentX =
                0,
            currentY =
                0,
            widthUnits =
                40f,
            stepUnits =
                4f,
            pullCompensationUnits =
                0f,
            shortStitches =
                true,
            underlayMode =
                SatinUnderlayMode.BOTH
        )

        val firstWideIndex =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.STITCH &&
                abs(
                    it.yUnits
                ) >=
                    15
            }

        assertTrue(
            firstWideIndex >
                0
        )

        assertTrue(
            "Antes do Satin deve existir somente a linha central de fundação.",
            points
                .take(
                    firstWideIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }
                .all {
                    abs(
                        it.yUnits
                    ) <=
                        1
                }
        )
    }
}
