package com.timachado.brothermatrizes.desktop.platform

import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Primeira etapa de portabilidade de geometria das fontes: extrai e valida
 * contornos reais TTF/OTF pelo Skia usado no Compose Desktop.
 *
 * Ainda NÃO gera matriz nem substitui o ImportedFontMatrixGenerator Android,
 * que tem algoritmos complexos de satin, sequência, underlay e fixação.
 */
internal data class DesktopGlyphOutlineReport(
    val family: String,
    val glyphCount: Int,
    val visibleOutlineCount: Int,
    val missingGlyphCount: Int,
    val advanceWidthAt100: Float
)

internal object DesktopFontGlyphProbe {
    fun inspect(fontFile: Path, text: String): DesktopGlyphOutlineReport {
        require(text.isNotBlank() && text.length <= 64) {
            "Use de 1 a 64 caracteres para investigar contornos da fonte."
        }
        require(text.none { Character.isISOControl(it) }) {
            "Texto possui caracteres de controle inválidos."
        }
        require(!Files.isSymbolicLink(fontFile) &&
            Files.isRegularFile(fontFile, LinkOption.NOFOLLOW_LINKS)) {
            "Arquivo da fonte não está disponível."
        }
        require(Files.size(fontFile) in 4..(12L * 1024L * 1024L)) {
            "Fonte vazia ou acima do limite de 12 MB."
        }
        val typeface = FontMgr.default.makeFromFile(fontFile.toString())
            ?: throw IllegalArgumentException("Skia não reconheceu o arquivo de fonte.")
        try {
            val skiaFont = Font(typeface, 100f)
            try {
                val glyphs = skiaFont.getStringGlyphs(text)
                require(glyphs.isNotEmpty()) {
                    "A fonte não produziu glifos para o texto."
                }
                var contours = 0
                var missing = 0
                glyphs.forEachIndexed { index, glyph ->
                    if (glyph.toInt() == 0 && !text[index].isWhitespace()) missing++
                    val path = skiaFont.getPath(glyph)
                    if (path != null) {
                        try {
                            if (path.pointsCount > 2) contours++
                        } finally {
                            path.close()
                        }
                    }
                }
                val width = skiaFont.measureTextWidth(text)
                require(width.isFinite() && width > 0f) {
                    "Geometria da fonte sem largura válida."
                }
                return DesktopGlyphOutlineReport(
                    family = typeface.familyName,
                    glyphCount = glyphs.size,
                    visibleOutlineCount = contours,
                    missingGlyphCount = missing,
                    advanceWidthAt100 = width
                )
            } finally {
                skiaFont.close()
            }
        } finally {
            typeface.close()
        }
    }
}
