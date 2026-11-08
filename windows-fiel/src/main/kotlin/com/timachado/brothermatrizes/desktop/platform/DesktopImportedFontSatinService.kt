package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

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

        val geometry = DesktopSkiaGlyphGeometry.sample(
            fontPath, text,
            targetHeightUnits = options.heightMm * 10f,
            spacingUnits = options.spacingMm * 10f
        )
        val raw = DesktopReferenceSatinKernel.generate(
            geometry.contoursByGlyph, options
        ).filter { it.command != StitchCommand.END }
        require(raw.size in 1..100_000 && raw.any {
            it.command == StitchCommand.STITCH
        }) { "O Satin não gerou pontadas úteis." }

        // Match Android recentering plus final END marker contract.
        val physical = raw.filter { it.command != StitchCommand.TRIM }
        require(physical.isNotEmpty())
        val cx = (physical.minOf { it.xUnits }.toLong() +
            physical.maxOf { it.xUnits }.toLong()) / 2.0
        val cy = (physical.minOf { it.yUnits }.toLong() +
            physical.maxOf { it.yUnits }.toLong()) / 2.0
        val centered = raw.map {
            it.copy(
                xUnits = (it.xUnits - cx).roundToInt(),
                yUnits = (it.yUnits - cy).roundToInt()
            )
        }
        require(centered.all {
            kotlin.math.abs(it.xUnits.toLong()) <= 1_000_000L &&
            kotlin.math.abs(it.yUnits.toLong()) <= 1_000_000L
        }) { "Pontadas fora do intervalo físico seguro." }
        val last = centered.last()
        val points: List<EmbroideryPoint> = centered +
            EmbroideryPoint(last.xUnits, last.yUnits, StitchCommand.END, last.colorIndex)
        val bounds = EmbroideryBounds(
            minXUnits = centered.minOf { it.xUnits },
            maxXUnits = centered.maxOf { it.xUnits },
            minYUnits = centered.minOf { it.yUnits },
            maxYUnits = centered.maxOf { it.yUnits }
        )
        val safeText = text.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .take(30).ifBlank { "nome" }
        val design = EmbroideryDesign(
            fileName = "nome-$safeText.${format.lowercase(Locale.ROOT)}",
            format = format,
            label = text,
            points = points,
            bounds = bounds,
            stitchCount = centered.count { it.command == StitchCommand.STITCH },
            jumpCount = centered.count { it.command == StitchCommand.JUMP },
            colorChanges = centered.count { it.command == StitchCommand.COLOR_CHANGE },
            endFound = true,
            sourceBytes = ByteArray(0),
            threadColors = listOf(options.color),
            isModified = true,
            hoopProfile = options.hoopProfile,
            fabricProfile = options.fabricProfile
        )
        if (options.enforceHoop && options.hoopProfile != null) {
            check(HoopValidator.validate(design, options.hoopProfile).fits) {
                "A matriz ultrapassa a área segura do bastidor."
            }
        }
        return AuthorizedDesign(authorizedFont, design)
    }
}
