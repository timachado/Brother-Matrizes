package com.timachado.brothermatrizes.font

import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.FlatteningPathIterator
import java.awt.geom.PathIterator
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * IMPLEMENTAÇÃO ESPECÍFICA DO JVM DESKTOP do contrato do extractor Android.
 *
 * A API mantém o mesmo nome/tipos esperados pelo motor OFICIAL
 * CleanRoomSatinEngine, mas os contornos são extraídos pelo Java2D/AWT,
 * NÃO por Skia do Android. É uma portabilidade experimental e não alega
 * equivalência comprovada em todas as fontes, ligaduras ou curvas.
 *
 * Fontes externas são dados não confiáveis: tamanho, caminho, glifos,
 * coordenadas e número de segmentos possuem limites explícitos.
 */
internal object AndroidSkiaOutlineExtractor {
    data class Point(val x: Float, val y: Float)

    data class Result(
        val glyphPolygons: List<List<List<Point>>>,
        val sourceWidth: Float,
        val sourceHeight: Float,
        val contextualShapingPreserved: Boolean = false
    )

    private const val MAX_FONT_BYTES = 12L * 1024L * 1024L
    private const val MAX_SAMPLED_POINTS = 100_000
    private const val MAX_GLYPHS = 48
    private const val MAX_CONTOURS = 2_500

    fun extract(
        font: ImportedFont,
        text: String,
        targetCapHeightUnits: Float,
        spacingUnits: Float,
        rotationDegrees: Float
    ): Result {
        require(text.isNotBlank() && text.length <= 24) { "Nome inválido para fonte externa." }
        require(targetCapHeightUnits.isFinite() && targetCapHeightUnits in 40f..600f) {
            "Altura inválida para geração."
        }
        require(spacingUnits.isFinite() && spacingUnits in 0f..120f) {
            "Espaçamento inválido."
        }
        require(rotationDegrees.isFinite() && rotationDegrees in -360f..360f) {
            "Rotação inválida."
        }
        val file = Path.of(font.absolutePath)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file) && Files.size(file) in 12..MAX_FONT_BYTES) {
            "Fonte não encontrada ou fora do limite de 12 MiB."
        }
        val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in setOf("ttf", "otf")) { "Somente TTF/OTF." }
        val bytes = Files.readAllBytes(file)
        require(bytes.size.toLong() <= MAX_FONT_BYTES) { "Fonte acima do limite." }
        val awt = ByteArrayInputStream(bytes).use {
            Font.createFont(Font.TRUETYPE_FONT, it).deriveFont(120f)
        }
        val codePoints = text.codePoints().toArray()
        for (cp in codePoints) {
            require(awt.canDisplay(cp)) {
                "A fonte não possui o caractere U+${cp.toString(16).uppercase()}."
            }
        }
        val frc = FontRenderContext(AffineTransform(), true, true)
        val capHeight = awt.createGlyphVector(frc, "H").outline.bounds2D.height
        require(capHeight.isFinite() && capHeight > 0.001) {
            "A fonte não possui altura de referência mensurável."
        }
        val scale = targetCapHeightUnits.toDouble() / capHeight
        require(scale.isFinite() && scale in 0.05..100.0) { "Escala do glifo inválida." }

        val glyphVector = awt.layoutGlyphVector(
            frc, text.toCharArray(), 0, text.length, Font.LAYOUT_LEFT_TO_RIGHT
        )
        require(glyphVector.numGlyphs in 1..MAX_GLYPHS) {
            "Número de glifos fora do limite desta QA."
        }
        val shapes = (0 until glyphVector.numGlyphs).map { glyphVector.getGlyphOutline(it) }
        val count = codePoints.size
        val overlap = shapes.zipWithNext().count { (a, b) ->
            val ar = a.bounds2D
            val br = b.bounds2D
            ar.width > 0 && br.width > 0 &&
                ar.maxX - br.minX > (capHeight * 0.05)
        }
        val contextual = count > 1 &&
            (glyphVector.numGlyphs < count ||
                overlap >= max(1, (shapes.size - 1) / 3))

        val groups: List<List<List<Point>>> = if (contextual) {
            // A forma completa mantém ligaduras/contexto aplicado pelo GlyphVector.
            val whole = glyphVector.outline
            val wholeWidth = whole.bounds2D.width * scale
            val extra = spacingUnits * (count - 1)
            val horizontal = if (wholeWidth > 0.1) {
                ((wholeWidth + extra) / wholeWidth).coerceIn(0.70, 2.0)
            } else 1.0
            listOf(sample(whole, AffineTransform.getScaleInstance(scale * horizontal, scale)))
        } else {
            shapes.mapIndexed { index, shape ->
                val transform = AffineTransform(
                    scale, 0.0, 0.0, scale,
                    index.toDouble() * spacingUnits, 0.0
                )
                sample(shape, transform)
            }.filter { it.isNotEmpty() }
        }
        val flat = groups.flatten().flatten()
        require(flat.isNotEmpty() && flat.size <= MAX_SAMPLED_POINTS) {
            "O contorno excede o limite de pontos da versão de homologação."
        }
        require(groups.sumOf { it.size } <= MAX_CONTOURS) {
            "A fonte possui contornos demais para esta QA."
        }

        val minX = flat.minOf { it.x }
        val minY = flat.minOf { it.y }
        val maxX = flat.maxOf { it.x }
        val maxY = flat.maxOf { it.y }
        require(listOf(minX, minY, maxX, maxY).all { it.isFinite() } &&
            maxX - minX >= 1f && maxY - minY >= 1f) {
            "O texto não produziu contorno com dimensões válidas."
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val angle = Math.toRadians(rotationDegrees.toDouble())
        val cs = cos(angle).toFloat()
        val sn = sin(angle).toFloat()
        return Result(
            glyphPolygons = groups.map { group ->
                group.map { contour ->
                    contour.map { p ->
                        val x = p.x - cx
                        val y = p.y - cy
                        Point(x * cs - y * sn, x * sn + y * cs)
                    }
                }
            },
            sourceWidth = maxX - minX,
            sourceHeight = maxY - minY,
            contextualShapingPreserved = contextual
        )
    }

    /** Flatten curves at physical ~1 unit tolerance, subdivide long edges. */
    private fun sample(shape: Shape, transform: AffineTransform): List<List<Point>> {
        val iter = FlatteningPathIterator(shape.getPathIterator(transform), 0.8, 12)
        val values = DoubleArray(6)
        val contours = ArrayList<List<Point>>()
        var segment = ArrayList<Point>()
        var start: Point? = null
        var total = 0
        fun emit() {
            if (segment.size >= 3) {
                if (segment.size > 1 && segment.first() == segment.last()) {
                    segment.removeAt(segment.lastIndex)
                }
                if (segment.size >= 3) contours += segment.toList()
            }
            segment = ArrayList()
            start = null
        }
        fun append(x: Float, y: Float) {
            require(x.isFinite() && y.isFinite()) { "Coordenadas inválidas em fonte." }
            val from = segment.lastOrNull()
            if (from != null) {
                val distance = hypot((x - from.x).toDouble(), (y - from.y).toDouble())
                val steps = ceil(distance / 2.0).toInt().coerceIn(1, 5_000)
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val point = Point(from.x + (x - from.x) * t, from.y + (y - from.y) * t)
                    if (segment.lastOrNull() != point) {
                        segment.add(point)
                        total++
                    }
                }
            } else {
                segment.add(Point(x, y))
                total++
            }
            require(total <= MAX_SAMPLED_POINTS && contours.size <= MAX_CONTOURS) {
                "Fonte com curva complexa demais para esta QA."
            }
        }
        while (!iter.isDone) {
            when (iter.currentSegment(values)) {
                PathIterator.SEG_MOVETO -> {
                    emit()
                    val p = Point(values[0].toFloat(), values[1].toFloat())
                    append(p.x, p.y)
                    start = p
                }
                PathIterator.SEG_LINETO ->
                    append(values[0].toFloat(), values[1].toFloat())
                PathIterator.SEG_CLOSE -> {
                    start?.let { append(it.x, it.y) }
                    emit()
                }
            }
            iter.next()
        }
        emit()
        return contours
    }
}
