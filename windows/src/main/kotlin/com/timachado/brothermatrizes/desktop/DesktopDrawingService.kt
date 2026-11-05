package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorArtwork
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.VectorPath
import com.timachado.brothermatrizes.core.embroidery.VectorPoint
import kotlin.math.hypot

/**
 * Caminhos normalizados 0..1 desenhados por mouse. Conversão usa EXATAMENTE
 * o VectorMatrixGenerator / MachineFinishing oficial do aplicativo Android.
 * Nenhum arquivo é lido ou gravado aqui.
 */
internal object DesktopDrawingService {
    const val MAX_STROKES = 40
    const val MAX_POINTS_PER_STROKE = 300
    const val MAX_TOTAL_POINTS = 6_000
    const val MAX_STITCH_COMMANDS = 200_000

    fun generate(
        strokes: List<List<VectorPoint>>,
        widthMm: Float,
        style: TextStitchStyle,
        hoop: HoopProfile,
        rgb: Int = 0xE6BE70
    ): DesktopMatrixDocument {
        require(widthMm.isFinite() && widthMm in 5f..240f) {
            "Largura do desenho: informe de 5 a 240 mm."
        }
        require(rgb in 0..0xFFFFFF) { "Cor RGB inválida." }
        require(strokes.isNotEmpty() && strokes.size <= MAX_STROKES) {
            "Crie de 1 a $MAX_STROKES traços."
        }
        var count = 0
        for (stroke in strokes) {
            require(stroke.size in 2..MAX_POINTS_PER_STROKE) {
                "Cada traço deve ter de 2 a $MAX_POINTS_PER_STROKE pontos."
            }
            count += stroke.size
            require(count <= MAX_TOTAL_POINTS) { "Desenho possui pontos demais." }
            for (point in stroke) {
                require(point.x.isFinite() && point.y.isFinite() &&
                    point.x in 0f..1f && point.y in 0f..1f) {
                    "Coordenadas do desenho inválidas."
                }
            }
            require(stroke.zipWithNext().any { (a, b) ->
                hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) >= 0.0005
            }) { "Traço sem comprimento." }
        }
        val points = strokes.flatten()
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        require(width >= 0.005f && height >= 0.005f) {
            "Desenho precisa ter largura e altura visíveis."
        }

        val artwork = VectorArtwork(
            label = "Desenho livre",
            paths = strokes.map { VectorPath(it) }
        )
        // A mesma engine e as mesmas opções base do Android comercial.
        // Restringir tamanho e encaixe antes de aceitar projeto Windows.
        val rendered = VectorMatrixGenerator.generate(
            artwork,
            VectorMatrixOptions(
                widthMm = widthMm,
                stitchStyle = style,
                color = rgb,
                outputFormat = "DST",
                hoopProfile = hoop,
                enforceHoop = true
            )
        ).getOrThrow()
        require(rendered.points.size <= MAX_STITCH_COMMANDS) {
            "Desenho gerou pontadas demais para esta versão QA."
        }
        val design = EmbroideryIntegrity.normalize(rendered).getOrThrow().design
        require(design.endFound && design.stitchCount > 0 &&
            design.bounds.widthMm <= hoop.usableWidthMm &&
            design.bounds.heightMm <= hoop.usableHeightMm) {
            "Desenho não passou na verificação de integridade ou bastidor."
        }
        return DesktopMatrixDocument(original = design, current = design)
    }
}
