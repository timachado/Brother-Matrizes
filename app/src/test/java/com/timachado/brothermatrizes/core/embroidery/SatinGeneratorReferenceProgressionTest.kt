package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class SatinGeneratorReferenceProgressionTest {

    @Test
    fun firstLocalSegmentIsFilledBeforeAdvancingThroughTheNextSegment() {
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

        val firstFilledEdge =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.STITCH &&
                abs(
                    it.yUnits
                ) >=
                    15
            }

        val deepIntoSecondSegment =
            points.indexOfFirst {
                it.command ==
                    StitchCommand.STITCH &&
                it.yUnits >=
                    50 &&
                abs(
                    it.xUnits -
                        100
                ) <=
                    6
            }

        assertTrue(
            "O Satin do primeiro trecho precisa começar antes de o underlay percorrer o trecho seguinte.",
            firstFilledEdge >=
                0 &&
                deepIntoSecondSegment >=
                    0 &&
                firstFilledEdge <
                    deepIntoSecondSegment
        )
    }

    @Test
    fun centerUnderlayIsLocalAndSatinCreatesRealWidth() {
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
                2f,
            shortStitches =
                true,
            underlayMode =
                SatinUnderlayMode.CENTER
        )

        val sewn =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            sewn.any {
                abs(
                    it.yUnits
                ) >=
                    18
            }
        )

        assertTrue(
            sewn.any {
                abs(
                    it.yUnits
                ) <=
                    2
            }
        )
    }
}
