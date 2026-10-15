package com.timachado.brothermatrizes.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedGlyphRailCoverageTest {
    private fun h(y: Float) = CleanRoomSatinEngine.Rail(
        CleanRoomSatinEngine.V(0f, y), CleanRoomSatinEngine.V(60f, y)
    )
    private fun v(x: Float) = CleanRoomSatinEngine.Rail(
        CleanRoomSatinEngine.V(x, 0f), CleanRoomSatinEngine.V(x, 36f)
    )
    private fun shifted(x: Float, y: Float) = CleanRoomSatinEngine.Rail(
        CleanRoomSatinEngine.V(x, y), CleanRoomSatinEngine.V(x + 15f, y)
    )

    @Test
    fun duplicateJunctionCoverageRemovesRedundantCrossBarsOnly() {
        // Duas orientações de colunas Satin cobrindo o mesmo quadrado:
        // manter a cobertura e descartar o segundo bordado redundante.
        val rows = (0..9).map { h(it * 4f) }
        val cols = (0..15).map { v(it * 4f) }
        val paths = listOf(
            rows.take(5), rows.drop(5), cols.take(8), cols.drop(8)
        )
        val clean = ImportedGlyphRailCoverage.suppressRedundant(paths, 0.4f)
        assertTrue("Faixas que já têm cobertura não devem ser costuradas de novo.",
            clean.sumOf { it.size } < paths.sumOf { it.size })
        assertTrue("A cobertura original deve continuar presente.",
            clean.flatten().containsAll(rows))
        assertTrue("Nunca criar novas barras nem distorcer coordenadas.",
            clean.flatten().all { it in paths.flatten() })
    }

    @Test
    fun singleTraditionalSatinStrokeIsBitwiseEquivalent() {
        val original = listOf((0..20).map { h(it * 4f) })
        assertEquals(original, ImportedGlyphRailCoverage.suppressRedundant(original, 0.4f))
    }

    @Test
    fun detachedPartsMustNotDisappearFromCoverageMap() {
        val paths = listOf(
            (0..4).map { shifted(0f, it * 4f) },
            (0..4).map { shifted(50f, it * 4f) },
            (0..4).map { shifted(100f, it * 4f) },
            (0..4).map { shifted(150f, it * 4f) }
        )
        val clean = ImportedGlyphRailCoverage.suppressRedundant(paths, 0.4f)
        assertEquals("Não há cobertura compartilhada entre os motivos.",
            paths.sumOf { it.size }, clean.sumOf { it.size })
    }

    @Test
    fun fineDetailStitchesAreProtectedFromDeduplication() {
        val short = CleanRoomSatinEngine.Rail(
            CleanRoomSatinEngine.V(5f, 5f), CleanRoomSatinEngine.V(10f, 5f)
        )
        val paths = listOf(
            List(8) { short },
            List(8) { short },
            List(8) { short },
            List(8) { short }
        )
        val result = ImportedGlyphRailCoverage.suppressRedundant(paths, 0.4f)
        assertEquals("Contornos finos e pinguinhos não podem ser descartados.",
            32, result.sumOf { it.size })
    }
}
