package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.ConvertedMatrix
import com.timachado.brothermatrizes.core.embroidery.DstParser
import com.timachado.brothermatrizes.core.embroidery.EditTransform
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIoParser
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.MatrixEditor
import com.timachado.brothermatrizes.core.embroidery.MonogramGenerator
import com.timachado.brothermatrizes.core.embroidery.MonogramOptions
import com.timachado.brothermatrizes.core.embroidery.MonogramStyle
import com.timachado.brothermatrizes.core.embroidery.PesExportProfile
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Porta Windows experimental do motor OFICIAL: sem parser/conversor alternativo.
 * Nunca regrava o arquivo de origem. Mantém o estado original para restauração.
 */
internal data class DesktopMatrixDocument(
    val original: EmbroideryDesign,
    val current: EmbroideryDesign
) {
    val edited: Boolean get() = current.isModified
}

internal object DesktopMatrixService {
    const val MAX_INPUT_BYTES: Long = 8L * 1024 * 1024
    private const val MAX_OUTPUT_BYTES: Int = 32 * 1024 * 1024
    val inputFormats: Set<String> = setOf("DST", "PES", "JEF")

    /**
     * Gera um NOVO projeto usando exatamente o motor TextMatrixGenerator do Android.
     * Não substitui o arquivo aberto; a UI pede uma ação explícita de criação.
     * Esta primeira UI usa fontes integradas (não fontes TTF/OTF importadas).
     */
    fun createName(
        text: String,
        heightMm: Float,
        style: TextStitchStyle = TextStitchStyle.RUNNING
    ): DesktopMatrixDocument {
        val clean = text.trim()
        require(clean.isNotEmpty() && clean.length <= 24) {
            "Digite de 1 a 24 caracteres para o nome."
        }
        require(heightMm.isFinite() && heightMm in 4f..60f) {
            "Altura do nome deve estar entre 4 e 60 mm."
        }
        val generated = TextMatrixGenerator.generate(
            TextMatrixOptions(
                text = clean,
                heightMm = heightMm,
                style = style,
                outputFormat = "DST"
            )
        ).getOrThrow()
        val design = EmbroideryIntegrity.normalize(generated).getOrThrow().design
        require(design.stitchCount > 0 && design.endFound) {
            "O motor não gerou um nome completo."
        }
        return DesktopMatrixDocument(original = design, current = design)
    }

    /**
     * Monogramas de 1 a 3 iniciais pelo mesmo MonogramGenerator do Android.
     * Cria uma matriz nova, em memória, sem modificar qualquer arquivo aberto.
     */
    fun createMonogram(
        initials: String,
        style: MonogramStyle = MonogramStyle.CLASSIC,
        heightMm: Float = 22f
    ): DesktopMatrixDocument {
        val clean = initials.trim()
        require(clean.length in 1..3 && clean.all { it.isLetter() }) {
            "Use de 1 a 3 letras para o monograma."
        }
        require(heightMm.isFinite() && heightMm in 8f..50f) {
            "Altura do monograma deve estar entre 8 e 50 mm."
        }
        val generated = MonogramGenerator.generate(
            MonogramOptions(
                initials = clean,
                style = style,
                heightMm = heightMm,
                outputFormat = "DST",
                enforceHoop = false
            )
        ).getOrThrow()
        val design = EmbroideryIntegrity.normalize(generated).getOrThrow().design
        require(design.stitchCount > 0 && design.endFound &&
                design.points.size <= 500_000) {
            "O monograma gerado não passou na validação de integridade."
        }
        return DesktopMatrixDocument(original = design, current = design)
    }

    fun openFromFile(file: Path): DesktopMatrixDocument {
        require(Files.isRegularFile(file)) { "Selecione um arquivo de matriz válido." }
        require(Files.size(file) in 1..MAX_INPUT_BYTES) {
            "O arquivo ultrapassa o limite desta versão de homologação (8 MiB)."
        }
        return open(file.fileName.toString(), Files.readAllBytes(file))
    }

    fun open(fileName: String, bytes: ByteArray): DesktopMatrixDocument {
        val extension = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
        require(extension in inputFormats) { "A prévia aceita somente DST, PES e JEF." }
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_INPUT_BYTES) {
            "Arquivo vazio ou acima de 8 MiB."
        }
        val safeBytes = bytes.copyOf()
        val design = parse(fileName, safeBytes)
        require(design.points.size <= 2_000_000 && design.stitchCount > 0 && design.endFound) {
            "Matriz sem pontadas válidas, incompleta ou excessivamente complexa."
        }
        return DesktopMatrixDocument(original = design, current = design)
    }

    fun edit(document: DesktopMatrixDocument, transform: EditTransform): DesktopMatrixDocument {
        require(transform.scale.isFinite() && transform.scale in 0.25f..3f) {
            "A escala deve ficar entre 25% e 300%."
        }
        require(transform.rotationDegrees.isFinite() && transform.rotationDegrees in -360f..360f) {
            "A rotação deve ficar entre -360 e +360 graus."
        }
        require(transform.offsetXUnits in -2000..2000 && transform.offsetYUnits in -2000..2000) {
            "Deslocamento máximo de 200 mm por operação."
        }
        val edited = MatrixEditor.apply(document.current, transform)
        val normalized = EmbroideryIntegrity.normalize(edited).getOrThrow().design
        require(normalized.stitchCount > 0 && normalized.endFound) {
            "Edição inválida: faltam pontadas ou comando de finalização."
        }
        return document.copy(current = normalized)
    }

    fun reset(document: DesktopMatrixDocument) = document.copy(current = document.original)

    fun preview(document: DesktopMatrixDocument): DstPreview {
        val d = document.current
        return DstPreview(
            fileName = d.fileName,
            label = d.label,
            points = d.points.map { p ->
                PreviewPoint(
                    x = p.xUnits, y = p.yUnits, colorIndex = p.colorIndex,
                    command = when (p.command) {
                        StitchCommand.STITCH -> PreviewCommand.STITCH
                        StitchCommand.JUMP -> PreviewCommand.JUMP
                        StitchCommand.TRIM -> PreviewCommand.TRIM
                        StitchCommand.STOP -> PreviewCommand.STOP
                        StitchCommand.COLOR_CHANGE -> PreviewCommand.COLOR_CHANGE
                        StitchCommand.SEQUIN -> PreviewCommand.SEQUIN
                        StitchCommand.END -> PreviewCommand.END
                    }
                )
            },
            bounds = PreviewBounds(
                d.bounds.minXUnits, d.bounds.minYUnits,
                d.bounds.maxXUnits, d.bounds.maxYUnits
            ),
            stitchCount = d.stitchCount,
            jumpCount = d.jumpCount,
            colorChanges = d.colorChanges
        )
    }

    /**
     * Valida a conversão por releitura com os parsers oficiais antes da gravação.
     * Não comprova aceitação no PE-DESIGN 11; exige homologação em computador real.
     */
    fun convert(
        document: DesktopMatrixDocument,
        targetFormat: String,
        pesPcProfile: Boolean = false
    ): ConvertedMatrix {
        val format = targetFormat.uppercase(Locale.ROOT)
        require(format in inputFormats) { "Formato de exportação inválido." }
        require(!pesPcProfile || format == "PES") { "Perfil PE-DESIGN requer PES." }
        val result = MatrixConverter.convert(
            design = document.current,
            targetFormat = format,
            outputSuffix = "windows_qa",
            pesExportProfile = if (pesPcProfile) PesExportProfile.PE_DESIGN_PC
                else PesExportProfile.BROTHER_MACHINE
        ).getOrThrow()
        require(result.bytes.isNotEmpty() && result.bytes.size <= MAX_OUTPUT_BYTES) {
            "O arquivo convertido é vazio ou muito grande."
        }
        // A escrita é bloqueada se o formato gerado não puder ser relido.
        val reopened = parse(result.fileName, result.bytes)
        require(reopened.stitchCount > 0 && reopened.endFound) {
            "A verificação da matriz convertida falhou."
        }
        return result
    }

    /** Salva NOVO arquivo apenas, nunca substitui arquivos da cliente. */
    fun saveNew(document: DesktopMatrixDocument, targetFormat: String, destination: Path,
                pesPcProfile: Boolean = false): Path {
        val format = targetFormat.uppercase(Locale.ROOT)
        val expectedSuffix = ".${format.lowercase(Locale.ROOT)}"
        require(destination.isAbsolute) { "Escolha um caminho absoluto." }
        require(destination.fileName.toString().lowercase(Locale.ROOT).endsWith(expectedSuffix)) {
            "A extensão precisa ser $expectedSuffix."
        }
        require(Files.isDirectory(destination.parent)) { "Pasta de destino indisponível." }

        // Completa toda a conversão e validação ANTES de criar o arquivo.
        val converted = convert(document, format, pesPcProfile)
        var created = false
        try {
            Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                .use { stream ->
                    created = true
                    stream.write(converted.bytes)
                    stream.flush()
                }
        } catch (error: Exception) {
            if (created) runCatching { Files.deleteIfExists(destination) }
            throw error
        }
        return destination
    }

    private fun parse(fileName: String, bytes: ByteArray): EmbroideryDesign {
        val outcome = when (fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)) {
            "DST" -> DstParser.parse(fileName, bytes)
            "PES", "JEF" -> EmbroideryIoParser.parse(fileName, bytes)
            else -> throw IllegalArgumentException("Formato de matriz não suportado.")
        }
        return when (outcome) {
            is EmbroideryLoadResult.Success -> outcome.design
            is EmbroideryLoadResult.Error -> throw IllegalArgumentException(outcome.userMessage)
        }
    }
}
