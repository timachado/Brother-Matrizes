package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinGeneratorReferenceProgressionTest {

    @Test
    fun centerUnderlayGoesForwardAndBackBeforeSatinCoverage() {
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
            "O underlay central precisa alcançar o fim do traço.",
            foundation.any {
                it.xUnits ==
                    100 &&
                abs(
                    it.yUnits
                ) <=
                    1
            }
        )

        assertEquals(
            "O underlay de referência retorna ao início antes da trava e do Satin.",
            0,
            foundation.last()
                .xUnits
        )

        assertTrue(
            "O underlay precisa conter a passada de retorno.",
            foundation
                .zipWithNext()
                .any {
                        pair ->
                    pair.second.xUnits <
                        pair.first.xUnits
                }
        )

        assertTrue(
            "Depois do underlay/trava, o Satin deve avançar pelo traço.",
            points
                .drop(
                    firstWideIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }
                .any {
                    it.xUnits >
                        80 &&
                    abs(
                        it.yUnits
                    ) >=
                        15
                }
        )
    }

    @Test
    fun bothUsesOnlyCenterUnderlayBeforeLockAndSatin() {
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
            "Antes da trava/Satin deve existir somente o center-run, sem zigue-zague extra.",
            beforeCoverage.all {
                abs(
                    it.yUnits
                ) <=
                    1
            }
        )

        assertTrue(
            beforeCoverage.any {
                it.xUnits ==
                    100
            }
        )
    }
}
