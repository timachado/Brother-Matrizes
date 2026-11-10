package com.timachado.brothermatrizes.desktop.platform

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/**
 * Inspeção local de fonte TTF/OTF SEM IMPORTAÇÃO e SEM autorização comercial.
 *
 * Nenhuma cópia, persistência, registro de fonte, projeto de bordado,
 * reserva/finalização de cota, sessão Google ou chamada de rede é efetuada.
 * Extrai só metadados e contornos via Skia já usado no Windows Desktop.
 */
internal object DesktopReadOnlyFontAnalyzer {
    private const val MAX_BYTES = 12L * 1024L * 1024L
    private const val PREVIEW_TEXT = "Brother Matrizes 123"

    data class Report(
        val sourceName: String,
        val familyName: String,
        val format: String,
        val byteSize: Long,
        val testedGlyphs: Int,
        val visibleOutlines: Int,
        val missingGlyphs: Int
    )

    fun inspect(sourceFile: Path): Report {
        val source = sourceFile.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(source) &&
            Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            "Selecione um arquivo TTF/OTF regular, sem link simbólico."
        }
        val name = source.fileName.toString()
        require(name.length in 5..200 &&
            name.none { it.isISOControl() || it in "/\\:" }) {
            "Nome de fonte inválido."
        }
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in setOf("ttf", "otf")) {
            "A análise aceita somente fontes TTF ou OTF."
        }
        val size = Files.size(source)
        require(size in 4..MAX_BYTES) {
            "Fonte vazia ou acima do limite de segurança de 12 MB."
        }
        val signature = ByteArray(4)
        Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
            var offset = 0
            while (offset < signature.size) {
                val n = input.read(signature, offset, signature.size - offset)
                require(n > 0) { "Não foi possível ler a assinatura TTF/OTF." }
                offset += n
            }
        }
        val actualFormat = when {
            signature.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                String(signature, Charsets.US_ASCII) == "true" -> "ttf"
            String(signature, Charsets.US_ASCII) == "OTTO" -> "otf"
            else -> error("Assinatura não corresponde a uma fonte TTF/OTF.")
        }
        require(actualFormat == extension) {
            "Extensão TTF/OTF não corresponde à assinatura do arquivo."
        }

        // O probe NÃO cria arquivos e não gera pontadas. Mesmo com fonte
        // legível, o motor Satin Android e o gate comercial seguem isolados.
        val geometry = DesktopFontGlyphProbe.inspect(source, PREVIEW_TEXT)
        require(geometry.family.isNotBlank() &&
            geometry.glyphCount > 0 && geometry.advanceWidthAt100 > 0f) {
            "Fonte sem contornos legíveis pelo motor Skia."
        }
        return Report(
            sourceName = name,
            familyName = geometry.family,
            format = actualFormat.uppercase(Locale.ROOT),
            byteSize = size,
            testedGlyphs = geometry.glyphCount,
            visibleOutlines = geometry.visibleOutlineCount,
            missingGlyphs = geometry.missingGlyphCount
        )
    }
}
