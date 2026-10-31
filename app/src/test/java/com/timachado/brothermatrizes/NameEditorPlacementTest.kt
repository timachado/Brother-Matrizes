package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.HoopOrientation
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

    @Test fun directTouchOnSatinNameSelectsNameAndFlowersSelectMatrix() {
        val source = com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
            .normalize(com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign(
                fileName = "flores.pes", format = "PES", label = null,
                points = listOf(
                    EmbroideryPoint(-220, -600, StitchCommand.JUMP, 0),
                    EmbroideryPoint(-220, 600, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-195, 600, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-195, -600, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-220, -600, StitchCommand.END, 0)
                ),
                bounds = EmbroideryBounds(0, 0, 0, 0),
                stitchCount = 0, jumpCount = 0, colorChanges = 0,
                endFound = false, sourceBytes = byteArrayOf(1),
                sourceYAxisDown = true,
                hoopProfile = HoopProfile.H130X180
            )).getOrThrow().design
        val named = com.timachado.brothermatrizes.core.embroidery.NamePersonalization
            .add(source,
                com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions(
                    text = "Maria", heightMm = 8f,
                    style = com.timachado.brothermatrizes.core.embroidery.TextStitchStyle.SATIN,
                    hoopProfile = HoopProfile.H130X180, outputFormat = "PES"
                ),
                centerXUnits = 175, centerYUnits = 40
            ).getOrThrow()
        val layer = requireNotNull(named.editableName)
        val nameStitches = named.points.drop(layer.original.points.size + 2)
            .filter { it.command == StitchCommand.STITCH }
        val minX = nameStitches.minOf { it.xUnits }
        val maxX = nameStitches.maxOf { it.xUnits }
        val minY = nameStitches.minOf { it.yUnits }
        val maxY = nameStitches.maxOf { it.yUnits }
        val p1 = NameEditorPlacement.screenOffset(layer.original,
            HoopProfile.H130X180, minX, minY, 360, 600, 36f)
        val p2 = NameEditorPlacement.screenOffset(layer.original,
            HoopProfile.H130X180, maxX, maxY, 360, 600, 36f)
        val centerX = 180f + (p1.x + p2.x) / 2f
        val centerY = 300f + (p1.y + p2.y) / 2f
        assertEquals(com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning.Target.NAME, NameEditorPlacement.targetAtTouch(named,
            HoopProfile.H130X180, centerX, centerY, 360, 600, 36f))
        assertEquals(com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning.Target.MATRIX, NameEditorPlacement.targetAtTouch(named,
            HoopProfile.H130X180, 12f, 300f, 360, 600, 36f))
        val locked = com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning
            .prepare(named, HoopProfile.H130X180).getOrThrow()
        val anchored = requireNotNull(locked.editableName)
        val fixedPoints = locked.points.drop(anchored.original.points.size + 2)
            .filter { it.command == StitchCommand.STITCH }
        val a = NameEditorPlacement.screenOffset(anchored.original,
            HoopProfile.H130X180,
            fixedPoints.minOf { it.xUnits }, fixedPoints.minOf { it.yUnits },
            360, 600, 36f)
        val b = NameEditorPlacement.screenOffset(anchored.original,
            HoopProfile.H130X180,
            fixedPoints.maxOf { it.xUnits }, fixedPoints.maxOf { it.yUnits },
            360, 600, 36f)
        assertEquals(com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning.Target.NAME, NameEditorPlacement.targetAtTouch(
            locked, HoopProfile.H130X180,
            180f + (a.x + b.x) / 2f, 300f + (a.y + b.y) / 2f,
            360, 600, 36f
        ))
    }

    @Test fun autoHoopOrientationIsFrozenBeforeLetterTransformation() {
        val vertical = portrait(true).copy(hoopOrientation = HoopOrientation.AUTO)
        assertEquals(HoopOrientation.PORTRAIT,
            NameEditorPlacement.frozenHoopOrientation(vertical, HoopProfile.H130X180))
        val horizontal = vertical.copy(
            bounds = EmbroideryBounds(-820, 820, -400, 400)
        )
        assertEquals(HoopOrientation.LANDSCAPE,
            NameEditorPlacement.frozenHoopOrientation(horizontal, HoopProfile.H130X180))
        assertEquals(HoopOrientation.LANDSCAPE,
            NameEditorPlacement.frozenHoopOrientation(
                vertical.copy(hoopOrientation = HoopOrientation.LANDSCAPE),
                HoopProfile.H130X180
            ))
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

    @Test fun twoFingerPinchIsUniformAndRotationIsIndependent() {
        val larger = NameEditorPlacement.twoFingerTransform(
            -20f, 0f, 20f, 0f,
            -25f, 0f, 25f, 0f
        )
        assertEquals(1.25f, larger.scale, .001f)
        assertEquals(0f, larger.rotationDegrees, .001f)
        val quarterTurn = NameEditorPlacement.twoFingerTransform(
            -20f, 0f, 20f, 0f,
            0f, -20f, 0f, 20f
        )
        assertEquals(1f, quarterTurn.scale, .001f)
        assertEquals(90f, quarterTurn.rotationDegrees, .001f)
        val smaller = NameEditorPlacement.twoFingerTransform(
            0f, -25f, 0f, 25f,
            0f, -20f, 0f, 20f
        )
        assertEquals(.8f, smaller.scale, .001f)
        val zero = NameEditorPlacement.twoFingerTransform(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
        )
        assertEquals(1f, zero.scale, .001f)
        assertEquals(0f, zero.rotationDegrees, .001f)
    }

}
