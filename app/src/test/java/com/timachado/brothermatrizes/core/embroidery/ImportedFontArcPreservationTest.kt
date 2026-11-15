package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedFontArcPreservationTest {

    private val options =
        TextMatrixOptions(
            text = "Maria",
            heightMm = 12f,
            style = TextStitchStyle.SATIN,
            font = EmbroideryFontPreset.ELEGANT,
            hoopProfile = null,
            enforceHoop = false
        )

    @Test
    fun importedCursiveArcUsesCompleteTextWithoutBreakingStitchBlocks() {
        val original = TextMatrixGenerator.generate(options).getOrThrow()
        var glyphCalls = 0
        var wholeTextCalls = 0

        val provider =
            TextGlyphProvider(
                preserveCase = true,
                generate = { _, _ ->
                    glyphCalls++
                    Result.failure(IllegalStateException("Letras isoladas não devem ser geradas"))
                },
                generateText = { _, _ ->
                    wholeTextCalls++
                    Result.success(original)
                },
                preserveWholeTextSequenceForSatin = true
            )

        fun curve(mode: TextLayoutMode) =
            TextLayoutGenerator.generate(
                TextLayoutOptions(
                    textOptions = options,
                    glyphProvider = provider,
                    layoutMode = mode,
                    arcHeightMm = 8f
                )
            ).getOrThrow()

        val up = curve(TextLayoutMode.ARC_UP)
        val down = curve(TextLayoutMode.ARC_DOWN)

        assertEquals(0, glyphCalls)
        assertEquals(2, wholeTextCalls)
        assertEquals(original.points.size, up.points.size)
        assertEquals(original.points.size, down.points.size)
        assertEquals(original.points.map { it.command }, up.points.map { it.command })
        assertEquals(original.points.map { it.command }, down.points.map { it.command })
        assertEquals(original.stitchCount, up.stitchCount)
        assertEquals(original.stitchCount, down.stitchCount)
        assertTrue(up.points != down.points)

        listOf(up, down).forEach { result ->
            assertTrue(kotlin.math.abs(
                (result.bounds.minXUnits + result.bounds.maxXUnits) / 2f
            ) <= 1f)
            assertTrue(kotlin.math.abs(
                (result.bounds.minYUnits + result.bounds.maxYUnits) / 2f
            ) <= 1f)
        }
    }

    @Test
    fun zeroCurvaturePreservesWholeTextGeometry() {
        val original = TextMatrixGenerator.generate(options).getOrThrow()
        val provider =
            TextGlyphProvider(
                preserveCase = true,
                generate = { _, _ ->
                    Result.failure(IllegalStateException("Não gerar glifos separadamente"))
                },
                generateText = { _, _ -> Result.success(original) },
                preserveWholeTextSequenceForSatin = true
            )

        val result = TextLayoutGenerator.generate(
            TextLayoutOptions(
                textOptions = options,
                layoutMode = TextLayoutMode.ARC_UP,
                arcHeightMm = 0f,
                glyphProvider = provider
            )
        ).getOrThrow()

        assertEquals(original.points, result.points)
        assertEquals(original.bounds, result.bounds)
    }
}
