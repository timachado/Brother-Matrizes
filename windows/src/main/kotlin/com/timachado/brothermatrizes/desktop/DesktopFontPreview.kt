package com.timachado.brothermatrizes.desktop

import java.awt.Font
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
        val font = inspectBytes(name, bytes)
        val family = font.getFamily(Locale.forLanguageTag("pt-BR")).take(120)
        val postScript = font.getPSName().take(120)
        require(family.isNotBlank()) { "A fonte não possui nome válido." }
        val missing = sample.filter { !font.canDisplay(it) }.toSet().joinToString("")
        return DesktopFontPreview(name, family, postScript, font, missing,
            file.toAbsolutePath().normalize())
    }

    /** Validação em memória antes de qualquer escrita de fonte extraída. */
    fun inspectBytes(fileName: String, bytes: ByteArray): Font {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension == "ttf" || extension == "otf") {
            "A entrada do pacote não é fonte TTF/OTF."
        }
        require(bytes.size >= 12 && bytes.size.toLong() <= MAX_FONT_BYTES) {
            "Fonte no pacote vazia, truncada ou acima de 12 MiB."
        }
        val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        require(magic == MAGIC_TTF || magic == MAGIC_OTF || magic == MAGIC_TRUE) {
            "A assinatura binária não corresponde a fonte TTF/OTF."
        }
        val font = ByteArrayInputStream(bytes).use {
            Font.createFont(Font.TRUETYPE_FONT, it)
        }
        require(font.getFamily(Locale.forLanguageTag("pt-BR")).isNotBlank()) {
            "A fonte do pacote não possui família válida."
        }
        return font
    }

    /**
     * Salva EXCLUSIVAMENTE após escolha explícita de arquivo pelo usuário.
     * CREATE_NEW impede sobrescrita mesmo se o destino surgir simultaneamente.
     * Não instala fontes nem extrai outras entradas do pacote.
     */
    fun saveArchiveFontNew(
        archiveName: String, bytes: ByteArray, destination: Path
    ): DesktopFontPreview {
        inspectBytes(archiveName, bytes)
        require(destination.isAbsolute) { "Escolha destino absoluto para fonte." }
        val originalExt = archiveName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val destinationExt = destination.fileName.toString()
            .substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(destinationExt == originalExt) {
            "Destino deve manter a extensão .$originalExt da fonte."
        }
        val parent = destination.parent
        require(parent != null &&
            Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(parent) && Files.isWritable(parent)) {
            "Pasta de destino inválida, redirecionada ou sem permissão."
        }
        require(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            "Esta fonte já existe no destino. Escolha outro nome."
        }
        var created = false
        try {
            Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { stream ->
                created = true
                stream.write(bytes)
            }
            return open(destination)
        } catch (failure: Exception) {
            // Apagar somente o novo arquivo criado pela tentativa atual.
            if (created) runCatching { Files.deleteIfExists(destination) }
            throw failure
        }
    }
}
