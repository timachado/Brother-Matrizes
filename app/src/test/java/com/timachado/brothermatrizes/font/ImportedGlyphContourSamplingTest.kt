package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedGlyphContourSamplingTest {
    @Test fun importedGlyphsRetainCurvesWithMoreSamplesThanTwoPixelSampling() {
        val perimeter = 340f
        val newSamples = ImportedGlyphContourSampling.sampleCount(perimeter)
        val oldSamples = kotlin.math.ceil(perimeter / 2f).toInt()
        assertTrue("Acurácia dos contornos curvos de fontes ornamentais.", newSamples > oldSamples)
        assertEquals(400, newSamples)
    }

    @Test fun tinyContoursStillHaveEnoughPoints() {
        assertEquals(12, ImportedGlyphContourSampling.sampleCount(1f))
        assertEquals(12, ImportedGlyphContourSampling.sampleCount(5f))
    }

    @Test fun malformedOrHugeContoursDoNotCauseOverflow() {
        assertEquals(0, ImportedGlyphContourSampling.sampleCount(0f))
        assertEquals(0, ImportedGlyphContourSampling.sampleCount(Float.NaN))
        assertEquals(0, ImportedGlyphContourSampling.sampleCount(Float.POSITIVE_INFINITY))
        assertEquals(0, ImportedGlyphContourSampling.sampleCount(-1f))
        assertEquals(12_000, ImportedGlyphContourSampling.sampleCount(Float.MAX_VALUE))
    }
}
