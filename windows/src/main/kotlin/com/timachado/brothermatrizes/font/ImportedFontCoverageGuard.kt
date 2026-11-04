package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.min

/**
 * Porta Java2D da verificação de silhueta usada pelo CleanRoomSatinEngine.
 * Mantém grade 320×192, limiares do Android, e falha fechada se não houver
 * cobertura/precisão suficientes. Não altera comandos nem gera pontos extras.
 */
internal object ImportedFontCoverageGuard {
    data class Score(val precision: Float, val coverage: Float)

    fun check(
        glyphs: List<List<List<AndroidSkiaOutlineExtractor.Point>>>,
        stitches: List<EmbroideryPoint>
    ): Score {
        val contour = Path2D.Float(Path2D.WIND_NON_ZERO)
        for (glyph in glyphs) for (ring in glyph) {
            if (ring.size < 3) continue
            contour.moveTo(ring.first().x.toDouble(), ring.first().y.toDouble())
            for (point in ring.drop(1)) {
                contour.lineTo(point.x.toDouble(), point.y.toDouble())
            }
            contour.closePath()
        }
        val bounds = contour.bounds2D
        require(bounds.width > 1.0 && bounds.height > 1.0 &&
            bounds.width.isFinite() && bounds.height.isFinite()) {
            "A fonte não possui área bordável suficiente."
        }
        val stitched = Path2D.Float()
        var previous: EmbroideryPoint? = null
        for (point in stitches) {
            if (point.command == StitchCommand.END) continue
            if (point.command == StitchCommand.STITCH && previous != null) {
                stitched.moveTo(previous.xUnits.toDouble(), previous.yUnits.toDouble())
                stitched.lineTo(point.xUnits.toDouble(), point.yUnits.toDouble())
            }
            previous = point
        }
        val width = 320
        val height = 192
        val scale = min(
            (width - 18.0) / bounds.width,
            (height - 18.0) / bounds.height
        )
        require(scale.isFinite() && scale > 0.0) { "Escala de verificação inválida." }
        val translateX = (width - bounds.width * scale) / 2.0 - bounds.minX * scale
        val translateY = (height - bounds.height * scale) / 2.0 - bounds.minY * scale
        val transform = AffineTransform(scale, 0.0, 0.0, scale, translateX, translateY)

        fun render(path: Path2D.Float, stitch: Boolean): BufferedImage {
            val bitmap = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
            val g = bitmap.createGraphics()
            try {
                g.color = Color.BLACK
                g.fillRect(0, 0, width, height)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
                g.transform = transform
                g.color = Color.WHITE
                if (stitch) {
                    g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.draw(path)
                } else {
                    g.fill(path)
                }
            } finally {
                g.dispose()
            }
            return bitmap
        }
        val original = render(contour, false).raster
        val produced = render(stitched, true).raster
        var expectedPixels = 0
        var sewnPixels = 0
        var bothPixels = 0
        for (y in 0 until height) for (x in 0 until width) {
            val within = original.getSample(x, y, 0) > 0
            val sewn = produced.getSample(x, y, 0) > 0
            if (within) expectedPixels++
            if (sewn) sewnPixels++
            if (within && sewn) bothPixels++
        }
        require(expectedPixels > 25 && sewnPixels > 5) {
            "Fonte sem silhueta verificável. Experimente outra altura."
        }
        val result = Score(
            precision = bothPixels.toFloat() / sewnPixels,
            coverage = bothPixels.toFloat() / expectedPixels
        )
        require(result.precision >= .54f && result.coverage >= .20f) {
            "Contorno não atingiu cobertura Satin segura; exportação bloqueada."
        }
        return result
    }
}
