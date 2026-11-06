package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint

/**
 * Tipos Android necessários para compilar o CleanRoomSatinEngine original.
 *
 * Estes adaptadores NÃO são funcionalidades prontas: para a validação
 * comercial, a extração de contornos e o guard de silhueta precisam
 * reproduzir o Android 1.11.8. Nunca retornar resultado sintético ou
 * aprovar cobertura fictícia.
 */
internal data class ImportedFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val extension: String,
    val absolutePath: String
)

internal object AndroidSkiaOutlineExtractor {
    data class Point(val x: Float, val y: Float)

    data class Result(
        val glyphPolygons: List<List<List<Point>>>,
        val sourceWidth: Float,
        val sourceHeight: Float,
        val contextualShapingPreserved: Boolean = false
    )

    @Suppress("UNUSED_PARAMETER")
    fun extract(
        font: ImportedFont,
        text: String,
        targetCapHeightUnits: Float,
        spacingUnits: Float,
        rotationDegrees: Float
    ): Result = throw UnsupportedOperationException(
        "Extração TTF/OTF Android ainda não homologada numericamente no Windows."
    )
}

internal object ImportedFontCoverageGuard {
    data class Score(val precision: Float, val coverage: Float)

    @Suppress("UNUSED_PARAMETER")
    fun check(
        glyphs: List<List<List<AndroidSkiaOutlineExtractor.Point>>>,
        stitches: List<EmbroideryPoint>
    ): Score = throw UnsupportedOperationException(
        "Verificação da silhueta Android ainda não portada e homologada."
    )
}
