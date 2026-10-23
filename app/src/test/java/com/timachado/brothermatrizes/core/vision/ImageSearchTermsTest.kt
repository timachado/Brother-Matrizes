package com.timachado.brothermatrizes.core.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSearchTermsTest {
    @Test
    fun translatesRecognizedSubjectsAndKeepsHighestConfidence() {
        val terms = ImageSearchTerms.fromLabels(
            listOf("Flower" to 0.7f, "Butterfly" to 0.9f, "butterfly" to 0.8f)
        )
        assertEquals(listOf("borboleta", "flor"), terms)
    }

    @Test
    fun ignoresWeakOrBlankLabelsAndLimitsToThree() {
        val terms = ImageSearchTerms.fromLabels(
            listOf(
                "Weak" to 0.44f, "" to 0.9f,
                "Cat" to 0.6f, "Dog" to 0.7f,
                "Rose" to 0.8f, "Star" to 0.5f
            )
        )
        assertEquals(listOf("rosa", "cachorro", "gato"), terms)
    }
}
