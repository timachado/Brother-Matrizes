package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.NamePersonalizationLayer
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.desktop.platform.DesktopMatrixMouseRotation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMatrixMouseRotationTest {
    private val points = listOf(
        EmbroideryPoint(0, 0, StitchCommand.STITCH, 0),
        EmbroideryPoint(100, 0, StitchCommand.STITCH, 0),
        EmbroideryPoint(100, 100, StitchCommand.STITCH, 0),
        EmbroideryPoint(100, 100, StitchCommand.END, 0)
    )
    private fun design() = EmbroideryDesign(
        fileName = "original.dst", format = "DST", label = "Matriz",
        points = points, bounds = EmbroideryBounds(0, 100, 0, 100),
        stitchCount = 3, jumpCount = 0, colorChanges = 0, endFound = true,
        sourceBytes = byteArrayOf(5, 8),
        guidePoints = listOf(EmbroideryPoint(0, 0, StitchCommand.JUMP, 0)),
        threadColors = listOf(0xff11cc),
        hoopProfile = HoopProfile.H100X100
    )

    @Test
    fun turnsAnyImportedMatrixWithoutChangingStitchOrderOrSource() {
        val source = design()
        val rotated = DesktopMatrixMouseRotation.rotate(
            source, 90f, HoopProfile.H100X100
        ).getOrThrow()
        assertEquals(100, rotated.points.first().xUnits)
        assertEquals(0, rotated.points.first().yUnits)
        assertEquals(source.points.map { it.command }, rotated.points.map { it.command })
        assertEquals(100, rotated.guidePoints.single().xUnits)
        assertTrue(rotated.isModified)
        assertTrue(rotated.sourceBytes.isEmpty())
        assertContentEquals(byteArrayOf(5, 8), source.sourceBytes)
        assertEquals(points, source.points)
    }

    @Test
    fun preservesEditableNameAndItsAnchorWhenMatrixIsTurned() {
        val source = design()
        val composite = source.copy(
            editableName = NamePersonalizationLayer(
                original = source.copy(fileName = "original-base.dst"),
                options = TextMatrixOptions(text = "Maria", rotationDegrees = 0f),
                centerXUnits = 50, centerYUnits = 0
            )
        )
        val turned = DesktopMatrixMouseRotation.rotate(
            composite, 90f, HoopProfile.H100X100
        ).getOrThrow()
        val layer = requireNotNull(turned.editableName)
        assertEquals(100, layer.centerXUnits)
        assertEquals(50, layer.centerYUnits)
        assertEquals(90f, layer.options.rotationDegrees)
        assertEquals(100, layer.original.points.first().xUnits)
        assertTrue(layer.original.isModified)
        assertTrue(composite.editableName != null)
    }

    @Test
    fun mapsMouseDragCorrectlyForBothYOrientations() {
        assertEquals(-50f, DesktopMatrixMouseRotation.mouseAngle(100f, false))
        assertEquals(50f, DesktopMatrixMouseRotation.mouseAngle(100f, true))
        assertTrue(DesktopMatrixMouseRotation.rotate(
            design(), Float.NaN, HoopProfile.H100X100
        ).isFailure)
    }
}