package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Testes JVM do pré-processamento de fontes importadas.
 * Nenhuma rotina do motor Satin ou do simulador é invocada/alterada.
 * Validação visual do Paint.getTextPath() exige Android físico.
 */
class ImportedOpenTypeContourGroupsTest {
    private fun box(l: Float, t: Float, r: Float, b: Float) =
        ImportedOpenTypeContourGroups.Box(l, t, r, b)

    @Test
    fun counterBelongsToOuterLetterEvenWhenCloserToNextRunAdvance() {
        val rings = listOf(
            box(0f, 0f, 62f, 80f),
            box(45f, 20f, 53f, 55f),
            box(68f, 0f, 93f, 75f)
        )
        val owners = ImportedOpenTypeContourGroups.owners(
            rings, listOf(12f, 58f, 82f)
        )
        assertEquals(listOf(0, 0, 2), owners)
    }

    @Test
    fun nestedRingsStayInSamePlanAndDoNotBecomeAnotherLetter() {
        val rings = listOf(
            box(0f, 0f, 100f, 100f),
            box(30f, 20f, 80f, 90f),
            box(60f, 40f, 70f, 60f)
        )
        val owners = ImportedOpenTypeContourGroups.owners(
            rings, listOf(45f, 75f)
        )
        assertEquals(owners[0], owners[1])
        assertEquals(owners[1], owners[2])
    }

    @Test
    fun separatedContoursRetainTheirIndependentGlyphs() {
        val owners = ImportedOpenTypeContourGroups.owners(
            listOf(box(0f, 0f, 25f, 70f), box(60f, 0f, 90f, 70f)),
            listOf(10f, 75f)
        )
        assertEquals(listOf(0, 1), owners)
    }

    @Test
    fun invalidRunCentersAreRejectedRatherThanGeneratingBadGeometry() {
        assertThrows(IllegalArgumentException::class.java) {
            ImportedOpenTypeContourGroups.owners(
                listOf(box(0f, 0f, 10f, 10f)), listOf(Float.NaN)
            )
        }
    }

    @Test
    fun completeRunPreservesOverlappingLetterOutlinesAndHolesTogether() {
        // Ligação cursiva: o desenho exterior cruza a divisão nominal
        // entre letras; os contornos interiores devem herdar o MESMO raster.
        val connectedOuter = listOf(
            0f to 0f, 72f to 0f, 72f to 75f, 0f to 75f
        )
        val counter = listOf(
            52f to 20f, 52f to 50f, 62f to 50f, 62f to 20f
        )
        val nextLetter = listOf(
            65f to 5f, 95f to 5f, 95f to 70f, 65f to 70f
        )
        val input = listOf(connectedOuter, counter, nextLetter)
        val grouped = ImportedOpenTypeContourGroups.completeRun(input)
        assertEquals(1, grouped.size)
        assertEquals(input, grouped.single())
        // Cada anel original, incluindo seu winding, conserva a ordem.
        assertEquals(counter, grouped.single()[1])
    }

    @Test
    fun completeRunPreservesDisconnectedAccentsAsSubpaths() {
        val body = listOf(0 to 20, 30 to 20, 30 to 90, 0 to 90)
        val detachedDot = listOf(10 to 0, 20 to 0, 20 to 10, 10 to 10)
        val run = ImportedOpenTypeContourGroups.completeRun(
            listOf(body, detachedDot)
        )
        assertEquals(1, run.size)
        assertEquals(listOf(body, detachedDot), run.single())
    }

    @Test
    fun completeRunIgnoresOnlyEmptyInputAndRejectsIncompleteRing() {
        assertEquals(
            emptyList<List<List<Int>>>(),
            ImportedOpenTypeContourGroups.completeRun<Int>(emptyList())
        )
        assertThrows(IllegalArgumentException::class.java) {
            ImportedOpenTypeContourGroups.completeRun(listOf(listOf(1, 2)))
        }
    }

}
