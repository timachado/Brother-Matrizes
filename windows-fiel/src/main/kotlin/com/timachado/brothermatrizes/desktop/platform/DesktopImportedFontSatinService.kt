package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.font.CleanRoomSatinEngine
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale

/**
 * Android imported-font Satin workflow on Windows, behind the mandatory
 * Google identity / brother-usage import_font transaction gate.
 *
 * Uses source-extracted Android Satin kernel, Windows Skia glyph contours,
 * and Android embroidery models. NEVER accepts an arbitrary unauthorised
 * font for design generation. Not yet a commercial release: contextual
 * ligatures, arbitrary transforms and real machine stitch-out need approval.
 */
internal class DesktopImportedFontSatinService(
    private val importGate: DesktopCommercialFontImportGate
) {
    data class AuthorizedDesign(
        val font: DesktopFontRecord,
        val design: EmbroideryDesign
    )

    suspend fun importAndGenerate(
        sourceFile: Path,
        textInput: String,
        options: TextMatrixOptions
    ): AuthorizedDesign {
        // Validate text and machine parameters BEFORE any server quota reserve.
        val text = textInput.trim()
        require(text.isNotBlank() && text.length <= 24 &&
            text.none { Character.isISOControl(it) || Character.isSurrogate(it) }) {
            "Digite um nome de 1 a 24 caracteres válidos."
        }
        require(options.style == TextStitchStyle.SATIN &&
            options.specialStitchMode == null) {
            "Somente Satin padrão é aceito para fontes importadas nesta QA."
        }
        require(options.heightMm.isFinite() && options.heightMm in 4f..60f &&
            options.spacingMm.isFinite() && options.spacingMm in -5f..20f) {
            "Altura ou espaçamento fora do limite seguro."
        }
        require(options.rotationDegrees == 0f &&
            options.widthScale == 1f && options.heightScale == 1f &&
            options.horizontalGlyphScale == 1f) {
            "Rotação ou escala avançada ainda não homologada no Windows."
        }
        require(options.satinDensityMm.isFinite() &&
            options.satinDensityMm in 0.3f..1.2f &&
            options.satinPullCompensationMm.isFinite() &&
            options.satinPullCompensationMm in 0f..1f &&
            options.satinWidthMm.isFinite() &&
            options.satinWidthMm in 1f..6f) {
            "Parâmetros Satin fora dos limites oficiais."
        }
        val format = options.outputFormat.uppercase(Locale.ROOT)
        require(format in MatrixConverter.supportedFormats) {
            "Formato da matriz não suportado."
        }

        // The gate performs verified Google token, reserve(import_font),
        // Skia/font validation, finalize and rollback on a quota sync error.
        val authorizedFont = importGate.importFont(sourceFile)
        val fontPath = Path.of(authorizedFont.absolutePath).toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(fontPath) &&
            !Files.isSymbolicLink(fontPath.parent) &&
            Files.isRegularFile(fontPath, LinkOption.NOFOLLOW_LINKS) &&
            fontPath.fileName.toString() == authorizedFont.id) {
            "A fonte autorizada foi alterada."
        }
        val bytes = Files.readAllBytes(fontPath)
        require(bytes.size in 4..(12 * 1024 * 1024))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .take(5).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(authorizedFont.id.lowercase(Locale.ROOT)
            .endsWith("-$hash.${authorizedFont.extension}")) {
            "Integridade da fonte importada não confirmada."
        }

        // Run the exact active Android 1.11.8 CleanRoomSatinEngine copied
        // by Gradle, not the legacy DesktopReferenceSatinKernel. The
        // AndroidSkiaOutlineExtractor / coverage guard have real JVM
        // implementations in DesktopAndroidSatinContract.kt and fail closed
        // on unsupported shaping or illegible stitches.
        val androidCompatibleFont = ImportedFont(
            id = authorizedFont.id,
            displayName = authorizedFont.displayName,
            fileName = authorizedFont.fileName,
            extension = authorizedFont.extension,
            absolutePath = authorizedFont.absolutePath
        )
        val design = CleanRoomSatinEngine.generate(
            font = androidCompatibleFont,
            sourceText = text,
            options = options,
            filePrefix = "nome"
        ).getOrThrow()
        check(design.stitchCount > 0 && design.endFound &&
            design.points.lastOrNull()?.command == StitchCommand.END) {
            "O Satin oficial não finalizou uma matriz segura."
        }
        return AuthorizedDesign(authorizedFont, design)
    }
}
