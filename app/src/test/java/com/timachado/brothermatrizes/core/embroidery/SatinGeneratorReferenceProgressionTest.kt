package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinGeneratorReferenceProgressionTest {

    @Test
    fun centerUnderlayRunsForwardAndBackBeforeFullSatinCoverage() {
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
            "O center-run precisa alcançar o fim do traço antes do Satin.",
            foundation.any {
                it.xUnits >=
                    95 &&
                abs(
                    it.yUnits
                ) <=
                    2
            }
        )

        assertTrue(
            "O center-run precisa voltar para a entrada antes da cobertura.",
            foundation
                .zipWithNext()
                .any {
                        pair ->
                    pair.second.xUnits <
                        pair.first.xUnits -
                            10
                }
        )

        assertTrue(
            "Depois da fundação, a cobertura deve atingir os dois lados do traço.",
            points
                .drop(
                    firstWideIndex
                )
                .any {
                    it.command ==
                        StitchCommand.STITCH &&
                    abs(
                        it.yUnits
                    ) >=
                        18
                }
        )
    }

    @Test
    fun eachSatinSampleUsesBothEdgesAndKeepsConnectedStrokeWithoutTrim() {
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
                    100 to 0,
                    100 to 100
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
                SatinUnderlayMode.CENTER
        )

        assertTrue(
            points.none {
                it.command ==
                    StitchCommand.TRIM
            }
        )

        val sewn =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A cobertura A/B deve gerar uma quantidade densa de pontos.",
            sewn.size >
                100
        )

        assertTrue(
            sewn.any {
                abs(
                    it.xUnits -
                        100
                ) <=
                    25 &&
                it.yUnits >=
                    70
            }
        )
    }
}
