package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedGlyphVectorGeometryTest {
    @Test fun curvedFontOutlineUsesFinerSamplingThanPublic108() {
        val length = 260f
        val previous = kotlin.math.ceil(length / 2f).toInt()
        val improved = ImportedGlyphVectorGeometry.sampleCount(length)
        assertEquals(400, improved)
        assertTrue("Evitar quinas serrilhadas na letra ornamental", improved > previous)
    }

    @Test fun tinyAccentsAreNotDiscarded() {
        assertEquals(12, ImportedGlyphVectorGeometry.sampleCount(2f))
        assertEquals(12, ImportedGlyphVectorGeometry.sampleCount(6f))
    }

    @Test fun unreasonableContoursAreBounded() {
        assertEquals(0, ImportedGlyphVectorGeometry.sampleCount(0f))
        assertEquals(0, ImportedGlyphVectorGeometry.sampleCount(Float.NaN))
        assertEquals(0, ImportedGlyphVectorGeometry.sampleCount(Float.POSITIVE_INFINITY))
        assertEquals(12_000, ImportedGlyphVectorGeometry.sampleCount(Float.MAX_VALUE))
    }

    @Test fun fullRunContoursKeepTheirOwnCoordinatesAndAssociateWithGlyphs() {
        val centers = listOf(8f, 24f, 42f, 61f)
        assertEquals(0, ImportedGlyphVectorGeometry.nearestGlyphIndex(0f, centers))
        assertEquals(1, ImportedGlyphVectorGeometry.nearestGlyphIndex(26f, centers))
        assertEquals(2, ImportedGlyphVectorGeometry.nearestGlyphIndex(43f, centers))
        assertEquals(3, ImportedGlyphVectorGeometry.nearestGlyphIndex(75f, centers))
        // O anel interno (contraforma) pertence ao mesmo glifo quando
        // seu centro está mais próximo desse glifo.
        assertEquals(2, ImportedGlyphVectorGeometry.nearestGlyphIndex(42f, centers))
    }

    @Test fun overlappingLettersAndLigaturesNeverInventPositions() {
        val centers = listOf(11f, 11f, 28f)
        assertEquals(0, ImportedGlyphVectorGeometry.nearestGlyphIndex(11f, centers))
        assertEquals(2, ImportedGlyphVectorGeometry.nearestGlyphIndex(31f, centers))
    }
}
