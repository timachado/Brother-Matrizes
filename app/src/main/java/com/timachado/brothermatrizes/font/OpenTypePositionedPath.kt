package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Desenha os runs tipográficos sequencialmente num único Path, a ser usado
 * tanto pela prévia vetorial quanto pelo digitizador Satin.
 *
 * O Android Paint não aceita features OpenType com índices de intervalo.
 * Por isso, a segmentação ocorre na borda da inicial/última letra; as
 * ligaturas internas continuam juntas e as de fronteira podem mudar.
 * Essa limitação é preferível a simular per-letter GSUB que a API não executa.
 */
internal object OpenTypePositionedPath {
    data class Result(val path: Path, val bounds: RectF, val advance: Float)

    fun compose(
        paint: Paint,
        text: String,
        selection: OpenTypePositionedStyle
    ): Result {
        require(selection.isEnabled) { "Selecione um floreio para a inicial ou final." }
        val originalSettings = paint.fontFeatureSettings
        try {
            var x = 0f
            val fullPath = Path()
            val runs = selection.runs(text)
            runs.forEachIndexed { index, run ->
                val settings = OpenTypeStyleChoices.fontSettings(run.featureTag)
                paint.fontFeatureSettings = settings
                val advance = paint.measureText(run.text)
                require(advance.isFinite() && advance >= 0f) {
                    "Fonte com avanço inválido para o floreio."
                }
                val glyph = Path()
                paint.getTextPath(run.text, 0, run.text.length, 0f, 0f, glyph)
                if (run.featureTag != null) {
                    require(!glyph.isEmpty && advance > 0f) {
                        "Floreio não produziu um desenho tipográfico válido."
                    }
                    val bounds = RectF()
                    glyph.computeBounds(bounds, true)
                    val overhang = OpenTypeOverhangPolicy.GlyphExtent(
                        advance = advance, left = bounds.left, right = bounds.right
                    )
                    val targetPosition = if (index == 0) 0 else text.codePointCount(0, text.length) - 1
                    require(!OpenTypeOverhangPolicy.isUnsafeForWholeWord(
                        overhang, targetPosition, text.codePointCount(0, text.length)
                    )) {
                        "Este floreio invade as outras letras. Escolha outra variante."
                    }
                }
                val translated = Path()
                paint.getTextPath(run.text, 0, run.text.length, x, 0f, translated)
                fullPath.addPath(translated)
                x += advance
            }
            require(!fullPath.isEmpty) { "A fonte não gerou contornos." }
            val bounds = RectF()
            fullPath.computeBounds(bounds, true)
            require(bounds.width().isFinite() && bounds.height().isFinite() &&
                bounds.width() > 0f && bounds.height() > 0f &&
                bounds.width() < 100_000f && bounds.height() < 100_000f) {
                "Os contornos deste floreio excedem a área segura."
            }
            return Result(fullPath, bounds, x)
        } finally {
            paint.fontFeatureSettings = originalSettings
        }
    }
}
