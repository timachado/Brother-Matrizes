package com.timachado.brothermatrizes.desktop.platform

import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathVerb
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path as FilePath
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/**
 * Primeira porta geométrica do ImportedFontMatrixGenerator Android.
 *
 * Implementa o contrato de buildReferenceGlyphGeometry() e
 * polygonizeReferencePath(): altura baseada no contorno de 'H',
 * avanço por glifo, espaçamento, centro geométrico da união,
 * amostragem fechada a cada ~2 unidades e preservação de contornos
 * interiores (ex.: buraco da letra O).
 *
 * O Skia do Compose fornece o contorno vetorial do TTF/OTF; curvas
 * são discretizadas antes de reamostrar por comprimento de arco.
 * NÃO faz satin, underlay, lock, guidePoints nem gera EmbroideryDesign.
 * Portanto NÃO substituir o motor comercial Android pelo resultado.
 */
internal data class DesktopContourPoint(val x: Float, val y: Float)

internal data class DesktopGlyphGeometry(
    val contoursByGlyph: List<List<List<DesktopContourPoint>>>,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val glyphCount: Int get() = contoursByGlyph.size
    val contourCount: Int get() = contoursByGlyph.sumOf { it.size }
    val pointCount: Int get() = contoursByGlyph.sumOf { glyph ->
        glyph.sumOf { contour -> contour.size }
    }
}

internal object DesktopSkiaGlyphGeometry {
    private const val MAX_TEXT_LENGTH = 64
    private const val MAX_SIZE = 12L * 1024 * 1024
    private const val MAX_CONTOUR_POINTS = 20_000
    private const val MAX_ALL_POINTS = 80_000
    private const val MAX_RAW_POINTS = 100_000
    private const val MAX_SEGMENT_SUBDIVISIONS = 2048

    fun sample(
        fontFile: FilePath,
        text: String,
        targetHeightUnits: Float,
        spacingUnits: Float
    ): DesktopGlyphGeometry {
        require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH &&
            text.none { Character.isISOControl(it) || Character.isSurrogate(it) }) {
            "Texto da fonte deve ter 1 a 64 caracteres Unicode BMP válidos."
        }
        require(targetHeightUnits.isFinite() &&
            targetHeightUnits in 1f..2000f) {
            "Altura vetorial fora do intervalo de segurança."
        }
        require(spacingUnits.isFinite() && abs(spacingUnits) <= 500f) {
            "Espaçamento vetorial fora do intervalo de segurança."
        }
        val file = fontFile.toAbsolutePath().normalize()
        require(!Files.isSymbolicLink(file) &&
            Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(file) in 4L..MAX_SIZE) {
            "O arquivo TTF/OTF deve ser local, regular e ter até 12 MB."
        }
        val typeface = FontMgr.default.makeFromFile(file.toString())
            ?: throw IllegalArgumentException("Fonte vetorial não reconhecida pelo Skia.")
        try {
            val font = Font(typeface, 100f)
            try {
                // Android mede getTextPath("H") antes de calcular font size.
                val capSample = if (font.getUTF32Glyph('H'.code).toInt() != 0)
                    'H' else text.first { !it.isWhitespace() }
                val capPath = font.getPath(font.getUTF32Glyph(capSample.code))
                val capHeight = try {
                    capPath?.computeTightBounds()?.height ?: 0f
                } finally {
                    capPath?.close()
                }
                val fallbackHeight = abs(font.metrics.ascent) * 0.72f
                val normalizedHeight = if (capHeight > 0.01f)
                    capHeight else fallbackHeight
                require(normalizedHeight.isFinite() && normalizedHeight > 0.01f) {
                    "Fonte sem altura de maiúscula utilizável."
                }
                font.size = 100f * targetHeightUnits / normalizedHeight
                require(font.size.isFinite() && font.size in 0.01f..50_000f) {
                    "Escala da fonte inválida."
                }

                data class GlyphPath(val source: Path, val penX: Float)
                val paths = mutableListOf<GlyphPath>()
                var penX = 0f
                try {
                    text.forEach { char ->
                        val glyph = font.getUTF32Glyph(char.code)
                        require(glyph.toInt() != 0 || char.isWhitespace()) {
                            "A fonte não possui o glifo '$char'."
                        }
                        if (!char.isWhitespace()) {
                            val outline = font.getPath(glyph)
                            if (outline != null) {
                                if (!outline.isEmpty) paths += GlyphPath(outline, penX)
                                else outline.close()
                            }
                        }
                        penX += font.measureTextWidth(char.toString()) + spacingUnits
                        require(penX.isFinite() && abs(penX) < 1_000_000f) {
                            "Dimensões de texto excederam o limite vetorial."
                        }
                    }
                    require(paths.isNotEmpty()) {
                        "Nenhum glifo resultou em contorno vetorial válido."
                    }
                    var left = Float.POSITIVE_INFINITY
                    var right = Float.NEGATIVE_INFINITY
                    var top = Float.POSITIVE_INFINITY
                    var bottom = Float.NEGATIVE_INFINITY
                    paths.forEach { (path, offset) ->
                        val bounds = path.computeTightBounds()
                        left = minOf(left, bounds.left + offset)
                        right = maxOf(right, bounds.right + offset)
                        top = minOf(top, bounds.top)
                        bottom = maxOf(bottom, bounds.bottom)
                    }
                    require(left.isFinite() && right.isFinite() &&
                        top.isFinite() && bottom.isFinite() &&
                        right - left > 0.01f && bottom - top > 0.01f) {
                        "A fonte não produziu área vetorial válida."
                    }
                    val centerX = (left + right) / 2f
                    val centerY = (top + bottom) / 2f
                    val glyphs = mutableListOf<List<List<DesktopContourPoint>>>()
                    var allPoints = 0
                    paths.forEach { (path, offset) ->
                        val contours = sampleContours(path).map { contour ->
                            contour.map { p ->
                                DesktopContourPoint(
                                    p.x + offset - centerX,
                                    p.y - centerY
                                )
                            }
                        }
                        if (contours.isNotEmpty()) {
                            allPoints += contours.sumOf { it.size }
                            require(allPoints <= MAX_ALL_POINTS) {
                                "Amostragem excedeu o limite de pontos."
                            }
                            glyphs += contours
                        }
                    }
                    require(glyphs.isNotEmpty()) {
                        "A fonte não gerou polígonos utilizáveis."
                    }
                    return DesktopGlyphGeometry(
                        contoursByGlyph = glyphs,
                        left = left - centerX,
                        top = top - centerY,
                        right = right - centerX,
                        bottom = bottom - centerY
                    )
                } finally {
                    paths.forEach { it.source.close() }
                }
            } finally {
                font.close()
            }
        } finally {
            typeface.close()
        }
    }

    /**
     * Contornos são independentes: NÃO juntar o vazado da letra O ao
     * perímetro externo. O método Android força caminhos fechados e
     * escolhe max(8, ceil(length / 2)) amostras para cada contorno.
     */
    internal fun sampleContours(path: Path): List<List<DesktopContourPoint>> {
        require(path.isFinite && path.isValid) {
            "Path Skia inválido."
        }
        val contours = mutableListOf<List<DesktopContourPoint>>()
        var current = mutableListOf<DesktopContourPoint>()
        var rawPoints = 0

        fun point(x: Float, y: Float): DesktopContourPoint {
            require(x.isFinite() && y.isFinite() &&
                abs(x) <= 1_000_000f && abs(y) <= 1_000_000f) {
                "Coordenadas do contorno TTF/OTF fora do limite."
            }
            return DesktopContourPoint(x, y)
        }

        fun add(p: DesktopContourPoint) {
            if (current.lastOrNull() != p) {
                rawPoints++
                require(rawPoints <= MAX_RAW_POINTS) {
                    "Curva da fonte possui detalhes excessivos."
                }
                current.add(p)
            }
        }

        fun flush() {
            if (current.size >= 3) {
                if (current.last() != current.first()) add(current.first())
                if (current.size >= 4) {
                    samplePolyline(current)?.let { contours += it }
                }
            }
            current = mutableListOf()
        }

        fun curve(
            start: DesktopContourPoint,
            controls: List<DesktopContourPoint>,
            end: DesktopContourPoint,
            weight: Float? = null
        ) {
            val chain = listOf(start) + controls + end
            var estimate = 0.0
            chain.zipWithNext().forEach { (a, b) ->
                estimate += hypot(
                    (a.x - b.x).toDouble(),
                    (a.y - b.y).toDouble()
                )
            }
            val steps = max(4, ceil(estimate / 0.5).toInt())
                .coerceAtMost(MAX_SEGMENT_SUBDIVISIONS)
            if (current.isEmpty()) add(start)
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val u = 1f - t
                val p = when (controls.size) {
                    1 -> {
                        val ctrl = controls[0]
                        if (weight == null) {
                            point(
                                u * u * start.x + 2 * u * t * ctrl.x + t * t * end.x,
                                u * u * start.y + 2 * u * t * ctrl.y + t * t * end.y
                            )
                        } else {
                            require(weight.isFinite() && weight > 0f) {
                                "Peso da curva cônica inválido."
                            }
                            val mid = 2 * u * t * weight
                            val denom = u * u + mid + t * t
                            point(
                                (u * u * start.x + mid * ctrl.x + t * t * end.x) / denom,
                                (u * u * start.y + mid * ctrl.y + t * t * end.y) / denom
                            )
                        }
                    }
                    2 -> {
                        val c1 = controls[0]
                        val c2 = controls[1]
                        point(
                            u*u*u * start.x + 3*u*u*t * c1.x +
                                3*u*t*t * c2.x + t*t*t * end.x,
                            u*u*u * start.y + 3*u*u*t * c1.y +
                                3*u*t*t * c2.y + t*t*t * end.y
                        )
                    }
                    else -> error("Curva não suportada.")
                }
                add(p)
            }
        }

        for (s in path.iterator(true)) {
            if (s == null) continue
            when (s.verb) {
                PathVerb.MOVE -> {
                    flush()
                    val p = requireNotNull(s.p0)
                    add(point(p.x, p.y))
                }
                PathVerb.LINE -> {
                    val a = requireNotNull(s.p0)
                    val b = requireNotNull(s.p1)
                    if (current.isEmpty()) add(point(a.x, a.y))
                    add(point(b.x, b.y))
                }
                PathVerb.QUAD, PathVerb.CONIC -> {
                    val a = requireNotNull(s.p0)
                    val b = requireNotNull(s.p1)
                    val z = requireNotNull(s.p2)
                    curve(
                        point(a.x, a.y), listOf(point(b.x, b.y)),
                        point(z.x, z.y),
                        if (s.verb == PathVerb.CONIC) s.conicWeight else null
                    )
                }
                PathVerb.CUBIC -> {
                    val a = requireNotNull(s.p0)
                    val b = requireNotNull(s.p1)
                    val d = requireNotNull(s.p2)
                    val z = requireNotNull(s.p3)
                    curve(
                        point(a.x, a.y),
                        listOf(point(b.x, b.y), point(d.x, d.y)),
                        point(z.x, z.y)
                    )
                }
                PathVerb.CLOSE -> flush()
                PathVerb.DONE -> break
            }
        }
        flush()
        return contours
    }

    /** Reamostra segmentos discretizados em passos equidistantes de arco. */
    private fun samplePolyline(
        closed: List<DesktopContourPoint>
    ): List<DesktopContourPoint>? {
        val lengths = DoubleArray(closed.size)
        for (index in 1 until closed.size) {
            val a = closed[index-1]
            val b = closed[index]
            lengths[index] = lengths[index-1] + hypot(
                (b.x - a.x).toDouble(), (b.y - a.y).toDouble()
            )
        }
        val perimeter = lengths.last()
        if (!perimeter.isFinite() || perimeter <= 0.01) return null
        val count = max(8, ceil(perimeter / 2.0).toInt())
        require(count <= MAX_CONTOUR_POINTS) {
            "Contorno TTF/OTF excede o limite de amostras."
        }
        val out = ArrayList<DesktopContourPoint>(count)
        var segment = 1
        for (i in 0 until count) {
            val distance = perimeter * i / count.toDouble()
            while (segment < lengths.lastIndex && lengths[segment] < distance) {
                segment++
            }
            val a = closed[segment - 1]
            val b = closed[segment]
            val span = lengths[segment] - lengths[segment - 1]
            val t = if (span <= 0.000001) 0.0
                else (distance - lengths[segment - 1]) / span
            out += DesktopContourPoint(
                (a.x + (b.x - a.x) * t).toFloat(),
                (a.y + (b.y - a.y) * t).toFloat()
            )
        }
        return out
    }
}
