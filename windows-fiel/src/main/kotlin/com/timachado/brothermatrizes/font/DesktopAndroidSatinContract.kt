package com.timachado.brothermatrizes.font

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.desktop.platform.DesktopSkiaGlyphGeometry
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.nio.file.Path
import kotlin.math.min

/**
 * Windows adapters for the unmodified Android CleanRoomSatinEngine.
 *
 * Only the geometric extraction and canvas rendering are platform-specific.
 * The Android CleanRoomSatinEngine.kt source is copied byte-for-byte during
 * the Windows build. Invalid font shaping, rotations and silhouettes always
 * fail closed; this adapter never emits a fictional successful coverage.
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

    /**
     * Same cap height, kerning/GPOS and non-self-intersecting contour contract
     * as the Android extractor, implemented with Skia/HarfBuzz for JVM.
     * Unsupported contextual substitution and rotation are rejected instead
     * of reporting that the shaping was preserved.
     */
    fun extract(
        font: ImportedFont,
        text: String,
        targetCapHeightUnits: Float,
        spacingUnits: Float,
        rotationDegrees: Float
    ): Result {
        require(rotationDegrees.isFinite() && rotationDegrees == 0f) {
            "Rotação de fonte importada ainda não homologada no Windows."
        }
        val geometry = DesktopSkiaGlyphGeometry.sample(
            Path.of(font.absolutePath), text,
            targetHeightUnits = targetCapHeightUnits,
            spacingUnits = spacingUnits
        )
        require(geometry.glyphCount > 0 &&
            geometry.pointCount in 3..80_000) {
            "A fonte não possui geometria segura para bordado."
        }
        return Result(
            glyphPolygons = geometry.contoursByGlyph.map { glyph ->
                glyph.map { contour ->
                    contour.map { Point(it.x, it.y) }
                }
            },
            sourceWidth = geometry.right - geometry.left,
            sourceHeight = geometry.bottom - geometry.top,
            // DesktopSkiaGlyphGeometry rejects contextual substitution;
            // never claim complex shaping was faithfully reproduced.
            contextualShapingPreserved = false
        )
    }
}

/**
 * Android ImportedFontCoverageGuard port: same 320x192 alpha silhouettes,
 * nonzero winding, 2.4-unit round stitch stroke and refusal thresholds.
 *
 * JVM Java2D replaces Android Canvas only; this does not relax either
 * source-shape or needle-coverage validation. Physical stitch-out remains
 * a separate commercial release gate.
 */
internal object ImportedFontCoverageGuard {
    data class Score(val precision: Float, val coverage: Float)

    fun check(
        glyphs: List<List<List<AndroidSkiaOutlineExtractor.Point>>>,
        stitches: List<EmbroideryPoint>
    ): Score {
        val contour = Path2D.Float(Path2D.WIND_NON_ZERO)
        var validRings = 0
        glyphs.forEach { glyph ->
            glyph.forEach { ring ->
                if (ring.size >= 3) {
                    require(ring.all {
                        it.x.isFinite() && it.y.isFinite() &&
                            kotlin.math.abs(it.x) <= 1_000_000f &&
                            kotlin.math.abs(it.y) <= 1_000_000f
                    }) { "Contorno de fonte fora do intervalo seguro." }
                    contour.moveTo(ring.first().x.toDouble(), ring.first().y.toDouble())
                    ring.drop(1).forEach { point ->
                        contour.lineTo(point.x.toDouble(), point.y.toDouble())
                    }
                    contour.closePath()
                    validRings++
                }
            }
        }
        require(validRings > 0) { "A fonte não possui contorno bordável." }
        val bounds = contour.bounds2D
        require(bounds.width > 1.0 && bounds.height > 1.0 &&
            bounds.width.isFinite() && bounds.height.isFinite()) {
            "A fonte não possui área bordável suficiente."
        }

        val stitched = Path2D.Float()
        var needle: EmbroideryPoint? = null
        stitches.forEach { point ->
            if (point.command == StitchCommand.END) return@forEach
            if (point.command == StitchCommand.STITCH && needle != null) {
                val previous = checkNotNull(needle)
                stitched.moveTo(previous.xUnits.toDouble(), previous.yUnits.toDouble())
                stitched.lineTo(point.xUnits.toDouble(), point.yUnits.toDouble())
            }
            needle = point
        }

        val width = 320
        val height = 192
        val scale = min(
            (width - 18.0) / bounds.width,
            (height - 18.0) / bounds.height
        )
        require(scale.isFinite() && scale > 0.0)
        val tx = (width - bounds.width * scale) / 2.0 - bounds.minX * scale
        val ty = (height - bounds.height * scale) / 2.0 - bounds.minY * scale
        val transform = AffineTransform().apply {
            translate(tx, ty)
            scale(scale, scale)
        }

        fun render(isStitch: Boolean): IntArray {
            val bitmap = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = bitmap.createGraphics()
            try {
                graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF
                )
                graphics.setRenderingHint(
                    RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE
                )
                graphics.color = Color.WHITE
                graphics.transform = transform
                if (isStitch) {
                    graphics.stroke = BasicStroke(
                        2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND
                    )
                    graphics.draw(stitched)
                } else {
                    graphics.fill(contour)
                }
            } finally {
                graphics.dispose()
            }
            return bitmap.getRGB(0, 0, width, height, null, 0, width)
        }

        val source = render(false)
        val result = render(true)
        var expectedPixels = 0
        var producedPixels = 0
        var matchingPixels = 0
        source.indices.forEach { i ->
            val inside = (source[i] ushr 24) > 0
            val sewn = (result[i] ushr 24) > 0
            if (inside) expectedPixels++
            if (sewn) producedPixels++
            if (inside && sewn) matchingPixels++
        }
        require(expectedPixels > 25 && producedPixels > 5) {
            "Fonte sem silhueta verificável. Tente outra altura."
        }
        val score = Score(
            precision = matchingPixels.toFloat() / producedPixels,
            coverage = matchingPixels.toFloat() / expectedPixels
        )
        require(score.precision >= 0.54f && score.coverage >= 0.20f) {
            "A conversão deformou as letras da fonte. " +
                "O bordado foi bloqueado para evitar uma matriz ilegível. " +
                "Experimente altura maior ou outra fonte."
        }
        return score
    }
}
