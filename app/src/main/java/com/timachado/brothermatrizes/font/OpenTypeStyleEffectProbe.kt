package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs

/**
 * O nome de um recurso em GSUB só diz que ele existe, NÃO que modifica a
 * palavra atual. Inspecionar o contorno retornado pelo mesmo Android Paint
 * que o motor Satin utiliza. É uma detecção conservadora: se o aparelho não
 * conseguir aplicar a feature no contorno, não oferecer aquela alternativa.
 *
 * Somente fontes locais; nenhuma alteração/upload de arquivo.
 */
internal object OpenTypeStyleEffectProbe {
    fun changingStyles(
        font: ImportedFont,
        text: String,
        candidates: List<OpenTypeStyleChoice>
    ): List<OpenTypeStyleChoice> {
        val fallback = listOf(OpenTypeStyleChoice(null, "Padrão"))
        if (text.isBlank() || text.length > 24 || candidates.size < 2) return fallback
        return runCatching {
            val typeface = Typeface.createFromFile(File(font.absolutePath))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 94f
                hinting = Paint.HINTING_OFF
            }
            fun contour(settings: String?): FloatArray {
                paint.fontFeatureSettings = settings
                val path = Path()
                paint.getTextPath(text, 0, text.length, 0f, 0f, path)
                return path.approximate(0.8f)
            }
            val original = contour(null)
            if (original.isEmpty()) return@runCatching fallback
            fun changed(a: FloatArray, b: FloatArray): Boolean {
                if (a.size != b.size) return true
                // (fração, x, y); ignorar fração e exigir deslocamento
                // geométrico, evitando opções visivelmente idênticas.
                var i = 0
                while (i + 2 < a.size) {
                    if (abs(a[i + 1] - b[i + 1]) > 0.13f ||
                        abs(a[i + 2] - b[i + 2]) > 0.13f) return true
                    i += 3
                }
                return false
            }
            // A Bertilda ss01/ss02 oferece floreios próprios de letras
            // iniciais/finais, mas o GSUB global aplica essas versões em
            // TODAS as ocorrências. Algumas curvas avançam 3x a largura
            // tipográfica e atravessam outras letras. Não oferecer estilo
            // inteiro quando os contornos invadem caracteres vizinhos.
            fun crossesNeighbourGlyphs(settings: String): Boolean {
                paint.fontFeatureSettings = settings
                val codePoints = text.codePoints().toArray()
                return codePoints.indices.any { index ->
                    val cp = codePoints[index]
                    if (Character.isWhitespace(cp)) return@any false
                    val glyph = String(Character.toChars(cp))
                    val advance = paint.measureText(glyph)
                    if (advance <= 0f) return@any false
                    val path = Path()
                    paint.getTextPath(glyph, 0, glyph.length, 0f, 0f, path)
                    if (path.isEmpty) return@any false
                    val bounds = RectF()
                    path.computeBounds(bounds, true)
                    OpenTypeOverhangPolicy.isUnsafeForWholeWord(
                        OpenTypeOverhangPolicy.GlyphExtent(
                            advance, bounds.left, bounds.right
                        ), index, codePoints.size
                    )
                }
            }

            val effective = candidates
                .asSequence()
                .filter { it.tag != null }
                .take(12)
                .filter { candidate ->
                    val other = contour(candidate.settings)
                    other.isNotEmpty() && changed(original, other) &&
                        !crossesNeighbourGlyphs(candidate.settings!!)
                }
                .toList()
            fallback + effective
        }.getOrDefault(fallback)
    }
}
