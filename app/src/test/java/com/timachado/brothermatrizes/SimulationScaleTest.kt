package com.timachado.brothermatrizes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationScaleTest {
    @Test
    fun usesPhysicalHoopScaleWhenHoopIsSelected() {
        val scale =
            simulationScale(
                availableWidth = 1000f,
                availableHeight = 1000f,
                designWidthUnits = 858f,
                designHeightUnits = 984f,
                hoopWidthUnits = 1000f,
                hoopHeightUnits = 1000f
            )

        assertEquals(
            1f,
            scale,
            0.0001f
        )
    }

    @Test
    fun largerHoopMakesSameDesignVisuallySmaller() {
        val scale100 =
            simulationScale(
                availableWidth = 1000f,
                availableHeight = 1000f,
                designWidthUnits = 858f,
                designHeightUnits = 984f,
                hoopWidthUnits = 1000f,
                hoopHeightUnits = 1000f
            )

        val scale160x260 =
            simulationScale(
                availableWidth = 1000f,
                availableHeight = 1000f,
                designWidthUnits = 858f,
                designHeightUnits = 984f,
                hoopWidthUnits = 1600f,
                hoopHeightUnits = 2600f
            )

        assertTrue(
            scale160x260 <
                scale100
        )
    }

    @Test
    fun noHoopFallsBackToDesignFit() {
        val scale =
            simulationScale(
                availableWidth = 858f,
                availableHeight = 984f,
                designWidthUnits = 858f,
                designHeightUnits = 984f,
                hoopWidthUnits = null,
                hoopHeightUnits = null
            )

        assertEquals(
            1f,
            scale,
            0.0001f
        )
    }
}
