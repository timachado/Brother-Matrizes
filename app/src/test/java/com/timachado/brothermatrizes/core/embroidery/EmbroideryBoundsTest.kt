package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryBoundsTest {
    @Test
    fun extremeCoordinatesDoNotOverflowDimensions() {
        val bounds =
            EmbroideryBounds(
                minXUnits =
                    Int.MIN_VALUE,
                maxXUnits =
                    Int.MAX_VALUE,
                minYUnits =
                    Int.MIN_VALUE,
                maxYUnits =
                    Int.MAX_VALUE
            )

        assertTrue(
            bounds.widthMm >
                0f
        )

        assertTrue(
            bounds.heightMm >
                0f
        )
    }
}
