package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinGeneratorReferenceProgressionTest {

    @Test
    fun satinCoverageStartsBeforeAFullCenterPassCrossesTheStroke() {
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
            firstWideIndex >=
                0
        )

        val beforeFirstWide =
            points
                .take(
                    firstWideIndex
                )
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }

        assertTrue(
            "A cobertura precisa começar localmente; não pode atravessar o traço inteiro como underlay antes do Satin.",
            beforeFirstWide.none {
                it.xUnits >=
                    80
            }
        )

        assertTrue(
            "O primeiro Satin deve começar próximo da entrada da coluna.",
            points[firstWideIndex]
                .xUnits <=
                20
        )

        assertTrue(
            "Depois a cobertura precisa continuar até o fim do traço.",
            points
                .drop(
                    firstWideIndex
                )
                .any {
                    it.command ==
                        StitchCommand.STITCH &&
                    it.xUnits >=
                        80 &&
                    abs(
                        it.yUnits
                    ) >=
                        15
                }
        )
    }

    @Test
    fun progressiveRouteDoesNotInsertTrimInsideOneConnectedStroke() {
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

        assertTrue(
            points.any {
                it.command ==
                    StitchCommand.STITCH &&
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
