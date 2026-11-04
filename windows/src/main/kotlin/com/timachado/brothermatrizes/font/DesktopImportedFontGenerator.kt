package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Path
import java.util.Locale

/**
 * Ponto de integração de plataforma do motor Satin oficial.
 *
 * Reutiliza CleanRoomSatinEngine Android INTEGRALMENTE. Apenas os
 * contornos e o teste raster de cobertura são adaptadores Windows/Java2D.
 * Saídas permanecem experimentais até ensaio de costura e PE-DESIGN 11.
 */
internal object DesktopImportedFontGenerator {
    fun generate(
        fontFile: Path,
        text: String,
        heightMm: Float,
        densityMm: Float = 0.4f
    ): EmbroideryDesign {
        val clean = text.trim()
        require(clean.isNotEmpty() && clean.length <= 24) { "Nome deve ter até 24 caracteres." }
        require(heightMm.isFinite() && heightMm in 4f..60f) {
            "Altura válida: 4 a 60 mm."
        }
        require(densityMm.isFinite() && densityMm in 0.3f..1.2f) {
            "Densidade Satin válida: 0,3 a 1,2 mm."
        }
        val name = fontFile.fileName.toString()
        val imported = ImportedFont(
            id = "windows-qa-local",
            displayName = name,
            fileName = name,
            extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT),
            absolutePath = fontFile.toAbsolutePath().normalize().toString()
        )
        val options = TextMatrixOptions(
            text = clean,
            heightMm = heightMm,
            spacingMm = 0f,
            style = TextStitchStyle.SATIN,
            satinDensityMm = densityMm,
            outputFormat = "DST",
            enforceHoop = false
        )
        return CleanRoomSatinEngine.generate(
            font = imported, sourceText = clean,
            options = options, filePrefix = "fonte-windows-qa"
        ).getOrThrow()
    }
}
