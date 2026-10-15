package com.timachado.brothermatrizes.font

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sequência de pontos é um contrato diferente da cobertura raster.
 * Teste de reprovação para a versão experimental 1.10.12-QA.
 */
class ImportedGlyphSequenceRegressionTest {
    private fun rectangle(
        x: Float, y: Float, w: Float, h: Float
    ): List<ImportedGlyphTatamiPlanner.P> = listOf(
        ImportedGlyphTatamiPlanner.P(x, y),
        ImportedGlyphTatamiPlanner.P(x + w, y),
        ImportedGlyphTatamiPlanner.P(x + w, y + h),
        ImportedGlyphTatamiPlanner.P(x, y + h)
    )

    @Test
    fun topmostRightGlyphMustNotBeTreatedAsFirstReadingGlyph() {
        // Representa uma letra inicial mais baixa e outra à direita mais alta.
        // O algoritmo V2 varre y e começa no glifo da DIREITA: regressão observada.
        val outlines = listOf(
            rectangle(0f, 20f, 30f, 110f),
            rectangle(135f, 0f, 30f, 130f)
        )
        val route = ImportedGlyphTatamiPlanner.plan(outlines)
        val issue = ImportedGlyphSequenceSafety.diagnoseEntry(outlines, route)
        assertFalse(
            "O detector deixou passar o início na letra da direita: $issue",
            issue.acceptableReadingEntry
        )
        assertTrue(
            "A regressão de início foi reproduzida",
            issue.horizontalDistanceToFirstInkUnits > 100f
        )
        assertTrue(
            "Não reativar o planejador só porque ele possui geometria válida",
            ImportedGlyphSequenceSafety.EXPERIMENTAL_ROUTE_BLOCKED
        )
    }

    @Test
    fun entryDiagnosticCanRecognizeAnInitialLeftGlyph() {
        val outlines = listOf(rectangle(0f, 0f, 30f, 65f))
        val manualStart = listOf(
            ImportedGlyphTatamiPlanner.Needle(
                ImportedGlyphTatamiPlanner.P(0.4f, 1f),
                ImportedGlyphTatamiPlanner.Move.JUMP
            )
        )
        val issue = ImportedGlyphSequenceSafety.diagnoseEntry(outlines, manualStart)
        assertTrue(issue.acceptableReadingEntry)
    }
}
