package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameEditorPlacementTest {
    private fun portrait(sourceYAxisDown: Boolean) = EmbroideryDesign(
        fileName = "moldura-sintetica.pes",
        format = "PES",
        label = null,
        points = listOf(
            EmbroideryPoint(-820, -691, StitchCommand.JUMP, 0),
            EmbroideryPoint(234, 885, StitchCommand.STITCH, 0),
            EmbroideryPoint(234, 885, StitchCommand.END, 0)
        ),
        bounds = EmbroideryBounds(-820, 234, -691, 885),
        stitchCount = 1, jumpCount = 1, colorChanges = 0,
        endFound = true, sourceBytes = ByteArray(0),
        sourceYAxisDown = sourceYAxisDown
    )

    @Test fun fontPreviewStrokeUsesThreadScaleNotThickUiLine() {
        // 13x18 cm em tela pequena: cada linha deve ter espessura
        // aproximada da linha de bordado, não 1,35dp ampliados.
        assertEquals(.65f, nameStitchPreviewWidthPx(.25f), .0001f)
        assertEquals(1.2f, nameStitchPreviewWidthPx(.5f), .0001f)
        assertEquals(1.3f, nameStitchPreviewWidthPx(2f), .0001f)
    }

    @Test fun chipAtTrueCenterOfVertical13By18Matrix() {
        val position = NameEditorPlacement.screenOffset(
            portrait(true), HoopProfile.H130X180,
            xUnits = -293, yUnits = 97,
            canvasWidth = 360, canvasHeight = 620,
            paddingPixels = 36f
        )
        assertEquals(0f, position.x, .01f)
        assertEquals(0f, position.y, .01f)
        assertTrue(position.unitsPerPixel > 0.1f)
    }

    @Test fun draggingOneFingerPreservesVerticalAndHorizontalSign() {
        val downward = NameEditorPlacement.dragUnits(30f, 20f, 0.25f, 1f)
        assertEquals(120, downward.first)
        assertEquals(80, downward.second)
        val upward = NameEditorPlacement.dragUnits(30f, 20f, 0.25f, -1f)
        assertEquals(120, upward.first)
        assertEquals(-80, upward.second)
    }

    @Test fun screenOffsetRespectsPesAxisDirection() {
        val down = NameEditorPlacement.screenOffset(
            portrait(true), HoopProfile.H130X180,
            xUnits = -293, yUnits = 197,
            canvasWidth = 360, canvasHeight = 620, paddingPixels = 36f
        )
        val up = NameEditorPlacement.screenOffset(
            portrait(false), HoopProfile.H130X180,
            xUnits = -293, yUnits = 197,
            canvasWidth = 360, canvasHeight = 620, paddingPixels = 36f
        )
        assertEquals(down.x, up.x, .01f)
        assertEquals(down.y, -up.y, .01f)
    }
}
