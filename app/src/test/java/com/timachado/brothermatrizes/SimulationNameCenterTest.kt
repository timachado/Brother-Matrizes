package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.NamePersonalizationLayer
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationNameCenterTest {
    private fun frame() = EmbroideryDesign(
        fileName = "MOLDURA FLORES BORBOLETAS 13X18.pes",
        format = "PES",
        label = "Moldura",
        points = listOf(
            EmbroideryPoint(-820, -691, StitchCommand.JUMP, 0),
            EmbroideryPoint(234, 885, StitchCommand.STITCH, 0),
            EmbroideryPoint(234, 885, StitchCommand.END, 0)
        ),
        bounds = EmbroideryBounds(-820, 234, -691, 885),
        stitchCount = 1,
        jumpCount = 1,
        colorChanges = 0,
        endFound = true,
        sourceBytes = ByteArray(0),
        sourceYAxisDown = true
    )

    @Test fun simulatorCentersPersonalizedNonzeroPesLikeViewer() {
        val base = frame()
        val personalized = base.copy(
            isModified = true,
            editableName = NamePersonalizationLayer(
                original = base, options = TextMatrixOptions("Maria"),
                centerXUnits = -293, centerYUnits = 97
            )
        )
        assertEquals(-293f, simulationCenterUnits(personalized, -820, 234, false))
        assertEquals(97f, simulationCenterUnits(personalized, -691, 885, false))
    }

    @Test fun otherModifiedDesignsKeepLegacyOriginSemantics() {
        assertEquals(0f, simulationCenterUnits(frame().copy(isModified = true), -820, 234, false))
    }
}
