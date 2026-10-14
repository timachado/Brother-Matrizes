package com.timachado.brothermatrizes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryRenderCoordinatesTest {
    @Test
    fun importedScreenCoordinatesKeepLargerYBelowCenter() {
        val center =
            renderScreenY(
                centerScreenY = 500f,
                centerYUnits = 100f,
                pointYUnits = 100f,
                scale = 1f,
                sourceYAxisDown = true
            )

        val below =
            renderScreenY(
                centerScreenY = 500f,
                centerYUnits = 100f,
                pointYUnits = 140f,
                scale = 1f,
                sourceYAxisDown = true
            )

        assertEquals(
            500f,
            center,
            0.0001f
        )
        assertTrue(
            below >
                center
        )
    }

    @Test
    fun brother_matrizesCartesianCoordinatesKeepLargerYAboveCenter() {
        val center =
            renderScreenY(
                centerScreenY = 500f,
                centerYUnits = 100f,
                pointYUnits = 100f,
                scale = 1f,
                sourceYAxisDown = false
            )

        val above =
            renderScreenY(
                centerScreenY = 500f,
                centerYUnits = 100f,
                pointYUnits = 140f,
                scale = 1f,
                sourceYAxisDown = false
            )

        assertTrue(
            above <
                center
        )
    }
}
