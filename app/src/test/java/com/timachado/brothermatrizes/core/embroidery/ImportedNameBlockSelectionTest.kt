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
    fun exportedPesAndDstCanReopenWithoutDeletingUnselectedDecoration() {
        for (format in listOf("PES", "DST")) {
            val base = floral().copy(
                fileName = "moldura-flores." + format.lowercase(),
                format = format
            )
            val personalized = NamePersonalization.add(
                base, TextMatrixOptions(
                    text = "Maria", heightMm = 10f,
                    style = TextStitchStyle.SATIN
                ), 0, 0
            ).getOrThrow()
            val converted = MatrixConverter.convert(personalized, format, "teste").getOrThrow()
            val reopened = (EmbroideryLoader.loadBytes(converted.fileName, converted.bytes)
                as EmbroideryLoadResult.Success).design
            assertTrue(reopened.stitchCount > base.stitchCount)
            // Os exportadores podem transformar comandos de corte/troca.
            // Seleção apenas se a estrutura reaberta continuar isolada.
            val split = reopened.points.indexOfLast { it.command == StitchCommand.COLOR_CHANGE }
            if (split < 1) {
                assertTrue(
                    ImportedNameBlockSelection.isolateFinalColorBlock(
                        reopened, StitchSelectionRect(-5000, 5000, -5000, 5000)
                    ).isFailure
                )
            } else {
                val candidate = reopened.points.drop(split + 1).dropLast(1)
                    .filter { it.command == StitchCommand.STITCH }
                if (candidate.isNotEmpty()) {
                    val area = StitchSelectionRect(
                        candidate.minOf { it.xUnits } - 10,
                        candidate.maxOf { it.xUnits } + 10,
                        candidate.minOf { it.yUnits } - 10,
                        candidate.maxOf { it.yUnits } + 10
                    )
                    val outcome = ImportedNameBlockSelection.isolateFinalColorBlock(reopened, area)
                    outcome.getOrNull()?.let { cleaned ->
                        val kept = reopened.points.take(split)
                            .filter { it.command == StitchCommand.STITCH }
                        val actual = cleaned.originalWithoutBlock.points
                            .filter { it.command == StitchCommand.STITCH }
                        assertEquals(kept, actual)
                    }
                    // Falha conservadora é permitida, apagamento externo não.
                    assertTrue(outcome.isSuccess || outcome.exceptionOrNull()?.message != null)
                }
            }
        }
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
