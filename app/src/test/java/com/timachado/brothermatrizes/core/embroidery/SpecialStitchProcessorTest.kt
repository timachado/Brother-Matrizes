package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialStitchProcessorTest {

    private val base =
        listOf(
            EmbroideryPoint(
                0,
                0,
                StitchCommand.JUMP,
                0
            ),
            EmbroideryPoint(
                10,
                0,
                StitchCommand.STITCH,
                0
            ),
            EmbroideryPoint(
                20,
                0,
                StitchCommand.STITCH,
                0
            )
        )

    @Test
    fun beanRepeatsEachSegmentForwardBackForward() {
        val result =
            SpecialStitchProcessor
                .apply(
                    base,
                    SpecialStitchMode.BEAN
                )

        assertEquals(
            6,
            result.count {
                it.command ==
                    StitchCommand.STITCH
            }
        )

        assertEquals(
            0,
            result.first()
                .xUnits
        )
    }

    @Test
    fun tripleRunningTraversesPathThreeTimes() {
        val result =
            SpecialStitchProcessor
                .apply(
                    base,
                    SpecialStitchMode.TRIPLE_RUNNING
                )

        assertEquals(
            6,
            result.count {
                it.command ==
                    StitchCommand.STITCH
            }
        )

        assertEquals(
            20,
            result.last()
                .xUnits
        )
    }

    @Test
    fun motifCreatesDecorativeOffsetsAroundPath() {
        val result =
            SpecialStitchProcessor
                .apply(
                    base,
                    SpecialStitchMode.PROGRAMMED_MOTIF
                )

        val stitchPoints =
            result.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            stitchPoints.size >
                2
        )

        assertTrue(
            stitchPoints.any {
                it.yUnits !=
                    0
            }
        )
    }

    @Test
    fun nullModePreservesOriginalSequence() {
        val result =
            SpecialStitchProcessor
                .apply(
                    base,
                    null
                )

        assertEquals(
            base,
            result
        )
    }
}
