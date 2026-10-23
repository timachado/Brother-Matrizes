package com.timachado.brothermatrizes.core.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFingerprintTest {
    @Test
    fun identicalThumbnailsYieldOneHundredPercentSimilarity() {
        val input = IntArray(72) { index -> (index * 37) % 256 }
        val signature = VisualFingerprint.fromGray9x8(input)
        assertEquals(1f, VisualFingerprint.similarity(signature, signature), 0.0001f)
    }

    @Test
    fun invertedHorizontalGradientsAreCompletelyDifferent() {
        val increasing = IntArray(72) { index -> (index % 9) * 25 }
        val decreasing = IntArray(72) { index -> 200 - (index % 9) * 25 }
        val first = VisualFingerprint.fromGray9x8(increasing)
        val second = VisualFingerprint.fromGray9x8(decreasing)
        assertEquals(0f, VisualFingerprint.similarity(first, second), 0.0001f)
    }

    @Test
    fun blankImageIsNotIndexed() {
        assertFalse(VisualFingerprint.hasEnoughDetail(IntArray(72) { 255 }))
        assertTrue(VisualFingerprint.hasEnoughDetail(IntArray(72) { i ->
            if (i % 2 == 0) 255 else 0
        }))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongThumbnailDimensions() {
        VisualFingerprint.fromGray9x8(IntArray(60))
    }
}
