package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryCanvasPlacementTest {

    private fun design(
        bounds: EmbroideryBounds
    ): EmbroideryDesign =
        EmbroideryDesign(
            fileName = "name-Maria-simulado.pes",
            format = "PES",
            label = "Maria",
            points = emptyList(),
            bounds = bounds,
            stitchCount = 1300,
            jumpCount = 0,
            colorChanges = 0,
            endFound = true,
            sourceBytes = byteArrayOf(1),
            isModified = true,
            hoopProfile = HoopProfile.H100X100
        )

    @Test
    fun reopenedPositivePesCoordinatesAreCenteredForPreview() {
        val reopened =
            design(
                EmbroideryBounds(
                    minXUnits = 0,
                    maxXUnits = 693,
                    minYUnits = 0,
                    maxYUnits = 252
                )
            )

        assertFalse(
            "69,3 × 25,2 mm cabe na área segura 90 × 90, mas 0..693 não representa uma posição válida em um bastidor centrado; o Viewer deve centralizar a prévia.",
            shouldPreserveHoopPosition(
                design = reopened,
                hoop = HoopProfile.H100X100,
                rotateHoop = false
            )
        )
    }

    @Test
    fun explicitEditedPositionInsideSafeAreaIsPreserved() {
        val edited =
            design(
                EmbroideryBounds(
                    minXUnits = -346,
                    maxXUnits = 347,
                    minYUnits = -126,
                    maxYUnits = 126
                )
            )

        assertTrue(
            "Uma edição já posicionada dentro da área segura deve manter a posição escolhida pelo usuário.",
            shouldPreserveHoopPosition(
                design = edited,
                hoop = HoopProfile.H100X100,
                rotateHoop = false
            )
        )
    }
}
