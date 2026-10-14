package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs

/**
 * Lista apenas substituições realmente presentes no desenho de cada letra
 * e seguras quando usadas na extremidade escolhida da palavra.
 *
 * Qualquer falha de leitura ou overhang inválido faz a opção desaparecer,
 * nunca inventa uma alternativa nem transforma um glifo silenciosamente.
 */
internal object OpenTypePositionedChoiceProbe {
    data class Options(
        val initial: List<OpenTypeStyleChoice>,
        val final: List<OpenTypeStyleChoice>
    )

    fun find(font: ImportedFont, text: String, styles: List<OpenTypeStyleChoice>): Options {
        val basic = listOf(OpenTypeStyleChoice(null, "Padrão"))
        if (text.isBlank() || text.codePointCount(0, text.length) !in 2..9)
            return Options(basic, basic)

        return runCatching {
            val fontFile = File(font.absolutePath)
            val typeface = Typeface.createFromFile(fontFile)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 94f
                hinting = Paint.HINTING_OFF
            }
            fun variants(atStart: Boolean): List<OpenTypeStyleChoice> {
                val cp = if (atStart) text.codePointAt(0) else text.codePointBefore(text.length)
                if (!Character.isLetter(cp) ||
                    Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN)
                    return basic
                val glyph = String(Character.toChars(cp))
                fun path(feature: String?): Path {
                    paint.fontFeatureSettings = OpenTypeStyleChoices.fontSettings(feature)
                    val p = Path()
                    paint.getTextPath(glyph, 0, glyph.length, 0f, 0f, p)
                    return p
                }
                val base = path(null)
                if (base.isEmpty) return basic
                val baseApprox = base.approximate(0.8f)
                val selected = mutableListOf<OpenTypeStyleChoice>()
                styles.asSequence().filter { it.tag != null }.take(12).forEach { candidate ->
                    val tag = candidate.tag!!
                    runCatching {
                        val alternative = path(tag)
                        require(!alternative.isEmpty)
                        val other = alternative.approximate(0.8f)
                        val changed = baseApprox.size != other.size || run {
                            var i = 0
                            var different = false
                            while (i + 2 < other.size && !different) {
                                different = abs(baseApprox[i + 1] - other[i + 1]) > .13f ||
                                    abs(baseApprox[i + 2] - other[i + 2]) > .13f
                                i += 3
                            }
                            different
                        }
                        require(changed)
                        // Compose valida tanto a borda inicial quanto a final
                        // com o MESMO guard de overhang do gerador de bordado.
                        val selection = if (atStart)
                            OpenTypePositionedStyle(initialTag = tag)
                        else OpenTypePositionedStyle(finalTag = tag)
                        OpenTypePositionedPath.compose(paint, text, selection)
                        candidate
                    }.onSuccess { selected.add(it) }
                }
                return basic + selected
            }
            Options(variants(true), variants(false))
        }.getOrDefault(Options(basic, basic))
    }
}
