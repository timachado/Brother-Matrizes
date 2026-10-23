package com.timachado.brothermatrizes.core.telegramvision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionTextCluesTest {
    @Test fun ocrExtractsDesignNameWithoutShowingGenericTags() {
        val terms = VisionTextClues.fromOcr("Matriz PRISCILA\nFlor girassol")
        assertTrue(terms.any { it.contains("priscila") })
        assertFalse(terms.contains("matriz"))
    }

    @Test fun visualLabelsNeverExposeConfusingCommonTerms() {
        val tags = VisionTextClues.fromVisualLabels(
            listOf("tableware" to 0.99f, "sunflower" to 0.95f, "flower" to 0.8f)
        )
        assertEquals(listOf("girassol", "flor"), tags)
    }

    @Test fun noReadableTextDoesNotCreateWords() {
        assertTrue(VisionTextClues.fromOcr("... 123").isEmpty())
    }

    @Test fun mergesAutomaticallyWithoutUserWordSelection() {
        assertEquals(
            listOf("priscila", "girassol"),
            VisionTextClues.combine(listOf("priscila"), listOf("girassol"))
        )
    }
}
