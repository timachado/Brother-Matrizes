package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedNameBlockSelectionTest {
    private fun floral(): EmbroideryDesign {
        val points = listOf(
            EmbroideryPoint(-300, -250, StitchCommand.JUMP, 0),
            EmbroideryPoint(-300, 250, StitchCommand.STITCH, 0),
            EmbroideryPoint(300, 250, StitchCommand.STITCH, 0),
            EmbroideryPoint(300, -250, StitchCommand.STITCH, 0),
            EmbroideryPoint(-300, -250, StitchCommand.STITCH, 0),
            EmbroideryPoint(-300, -250, StitchCommand.END, 0)
        )
        return EmbroideryIntegrity.normalize(
            EmbroideryDesign(
                fileName = "moldura-flores.pes", format = "PES",
                label = null, points = points,
                bounds = EmbroideryBounds(-300, 300, -250, 250),
                stitchCount = 4, jumpCount = 1,
                colorChanges = 0, endFound = true,
                sourceBytes = ByteArray(0),
                threadColors = listOf(0xFF336699.toInt()),
                hoopProfile = HoopProfile.H100X100
            )
        ).getOrThrow().design
    }

    private fun importedWithName(): EmbroideryDesign {
        val personalized = NamePersonalization.add(
            floral(), TextMatrixOptions(
                text = "Maria",
                heightMm = 10f,
                style = TextStitchStyle.SATIN
            ), 0, 0
        ).getOrThrow()
        // Simula que a matriz já foi exportada/reaberta: sem metadados de texto.
        return personalized.copy(editableName = null)
    }

    @Test
    fun removesOnlyFinalSelectedColorBlock() {
        val imported = importedWithName()
        val area = StitchSelectionRect(-250, 250, -200, 200)
        val removed = ImportedNameBlockSelection
            .isolateFinalColorBlock(imported, area).getOrThrow()
        assertEquals(floral().points, removed.originalWithoutBlock.points)
        assertEquals(floral().threadColors, removed.originalWithoutBlock.threadColors)
        assertTrue(removed.removedStitchCount > 0)
        assertEquals(0, removed.centerXUnits)
        assertEquals(0, removed.centerYUnits)
        assertEquals(1, removed.originalWithoutBlock.points.count { it.command == StitchCommand.END })
    }

    @Test
    fun selectionMustEncloseEntireColorBlock() {
        val res = ImportedNameBlockSelection
            .isolateFinalColorBlock(importedWithName(), StitchSelectionRect(-25, 25, -25, 25))
        assertTrue(res.isFailure)
    }

    @Test
    fun selectingFloralStitchesMustFail() {
        val res = ImportedNameBlockSelection
            .isolateFinalColorBlock(importedWithName(), StitchSelectionRect(-350, 350, -300, 300))
        assertTrue(res.isFailure)
    }

    @Test
    fun sameColorWithoutIsolatedFinalColorBlockCannotBeRemoved() {
        val res = ImportedNameBlockSelection
            .isolateFinalColorBlock(floral(), StitchSelectionRect(-400, 400, -300, 300))
        assertTrue(res.isFailure)
    }

    @Test
    fun alreadyEditableNamesAreNotDestructivelyModified() {
        val generated = NamePersonalization.add(
            floral(), TextMatrixOptions(text = "Maria"), 0, 0
        ).getOrThrow()
        val res = ImportedNameBlockSelection
            .isolateFinalColorBlock(generated, StitchSelectionRect(-250, 250, -200, 200))
        assertTrue(res.isFailure)
    }
}
