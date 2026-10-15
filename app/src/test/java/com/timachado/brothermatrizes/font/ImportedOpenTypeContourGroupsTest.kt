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
}
