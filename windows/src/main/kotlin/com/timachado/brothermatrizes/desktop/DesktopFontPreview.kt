package com.timachado.brothermatrizes.desktop

import java.awt.Font
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Carrega TTF/OTF SOMENTE para inspeção e pré-visualização local no Windows.
 * Esta classe apenas valida e pré-visualiza fontes. A conversão experimental
 * é separada e deve passar pelo motor Satin e pelo guarda de legibilidade.
 *
 * Não instala fontes, copia arquivos ou compartilha dados com a nuvem.
 */
internal data class DesktopFontPreview(
    val fileName: String,
    val family: String,
    val postScriptName: String,
    val awtFont: Font,
    val missingPreviewCharacters: String,
    val sourcePath: Path
)

internal object DesktopFontPreviewService {
    const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    private const val MAGIC_TTF = 0x00010000
    private const val MAGIC_OTF = 0x4F54544F // OTTO
    private const val MAGIC_TRUE = 0x74727565 // true (legacy TrueType)

    fun open(file: Path, sample: String = "Brother Matrizes 123"): DesktopFontPreview {
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file)) {
            "Selecione um arquivo regular TTF/OTF, sem atalhos."
        }
        require(Files.size(file) in 12L..MAX_FONT_BYTES) {
            "Fonte vazia, incompleta ou acima de 12 MiB."
        }
        val name = file.fileName.toString()
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension == "ttf" || extension == "otf") {
            "Somente fontes TTF/OTF são aceitas."
        }
        val bytes = Files.readAllBytes(file)
        require(bytes.size >= 12 && bytes.size.toLong() <= MAX_FONT_BYTES) {
            "Arquivo de fonte inválido."
        }
        val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        require(magic == MAGIC_TTF || magic == MAGIC_OTF || magic == MAGIC_TRUE) {
            "A assinatura binária não corresponde a uma fonte TTF/OTF."
        }

        val font = ByteArrayInputStream(bytes).use {
            Font.createFont(Font.TRUETYPE_FONT, it)
        }
        val family = font.getFamily(Locale.forLanguageTag("pt-BR")).take(120)
        val postScript = font.getPSName().take(120)
        require(family.isNotBlank()) { "A fonte não possui nome válido." }
        val missing = sample.filter { !font.canDisplay(it) }.toSet().joinToString("")
        return DesktopFontPreview(name, family, postScript, font, missing,
            file.toAbsolutePath().normalize())
    }
}
