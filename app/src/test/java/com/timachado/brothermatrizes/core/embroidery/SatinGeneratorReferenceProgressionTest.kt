package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinGeneratorReferenceProgressionTest {

    @Test
    fun centerUnderlayRunsOnceToTheTipThenSatinReturns() {
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
            "A linha central precisa avançar em uma única direção até a ponta.",
            foundation
                .zipWithNext()
                .all {
                        pair ->
                    pair.second.xUnits >=
                        pair.first.xUnits
                }
        )

        assertEquals(
            "A linha central deve terminar no extremo antes do Satin.",
            100,
            foundation.last()
                .xUnits
        )

        assertTrue(
            "O Satin deve começar no mesmo extremo e retornar pelo traço.",
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
    fun bothDoesNotInsertASecondFoundationPass() {
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

        val beforeCoverage =
            points
                .take(
                    firstWideIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertTrue(
            "Antes da trava/Satin deve existir somente uma passada central.",
            beforeCoverage.all {
                abs(
                    it.yUnits
                ) <=
                    1
            }
        )

        assertTrue(
            "A passada central não pode voltar para o início antes da cobertura.",
            beforeCoverage
                .zipWithNext()
                .all {
                        pair ->
                    pair.second.xUnits >=
                        pair.first.xUnits
                }
        )
    }
}
