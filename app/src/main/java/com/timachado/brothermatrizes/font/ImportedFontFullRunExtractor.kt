package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Contornos de TODO o texto com shaping do Android Skia, sem repartição artificial
 * por centro de caractere. Somente para o caminho QA do Tatami, atualmente desligado.
 * Nunca afeta fontes nativas, simulador, exportação oficial ou previews publicados.
 */
internal object ImportedFontFullRunExtractor {
    private const val MAX_CONTOUR_POINTS = 250_000
    private const val MAX_CONTOURS = 8_000

    fun extract(
        font: ImportedFont,
        text: String,
        targetCapHeightUnits: Float,
        rotationDegrees: Float
    ): AndroidSkiaOutlineExtractor.Result {
        require(text.isNotBlank() && targetCapHeightUnits.isFinite() &&
            targetCapHeightUnits > 0f && rotationDegrees.isFinite())
        val fontFile = File(font.absolutePath)
        require(fontFile.isFile) { "Fonte importada indisponível." }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.createFromFile(fontFile)
            isSubpixelText = true
            hinting = Paint.HINTING_OFF
            textSize = 100f
        }
        val capPath = Path()
        paint.getTextPath("H", 0, 1, 0f, 0f, capPath)
        val capBounds = RectF()
        capPath.computeBounds(capBounds, true)
        val cap = if (!capPath.isEmpty && capBounds.height() > 0.001f)
            capBounds.height() else abs(paint.fontMetrics.ascent) * 0.72f
        require(cap.isFinite() && cap > 0.001f) { "Altura da fonte inválida." }
        paint.textSize = 100f * targetCapHeightUnits / cap

        // Fonte do contorno é exatamente o texto completo. A liberação de
        // espaçamento manual deve ter uma implementação específica futura.
        val path = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        require(!path.isEmpty) { "Fonte sem contornos de bordado." }
        val bounds = RectF()
        path.computeBounds(bounds, true)
        require(bounds.width() > 0f && bounds.height() > 0f)

        val contours = ArrayList<List<AndroidSkiaOutlineExtractor.Point>>()
        val measure = PathMeasure(path, true)
        val position = FloatArray(2)
        var sampledPoints = 0
        do {
            val len = measure.length
            if (len.isFinite() && len > 0f) {
                val count = max(8, ceil(len / 1.5f).toInt())
                require(count <= MAX_CONTOUR_POINTS &&
                    sampledPoints + count <= MAX_CONTOUR_POINTS &&
                    contours.size < MAX_CONTOURS) {
                    "Fonte detalhada demais para o planejador experimental."
                }
                val ring = ArrayList<AndroidSkiaOutlineExtractor.Point>(count)
                for (i in 0 until count) {
                    if (measure.getPosTan(len * i / count, position, null)) {
                        ring += AndroidSkiaOutlineExtractor.Point(position[0], position[1])
                    }
                }
                if (ring.size >= 3) {
                    contours += ring
                    sampledPoints += ring.size
                }
            }
        } while (measure.nextContour())
        require(contours.isNotEmpty()) { "Fonte sem anéis de contorno utilizáveis." }

        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val transformed = contours.map { ring ->
            ring.map { v ->
                val x = v.x - cx
                val y = v.y - cy
                AndroidSkiaOutlineExtractor.Point(x * c - y * s, x * s + y * c)
            }
        }
        // Um ÚNICO glifo topológico: união dos anéis da palavra completa.
        // Mantém vazados e sobreposições pela regra non-zero do planejador.
        return AndroidSkiaOutlineExtractor.Result(
            glyphPolygons = listOf(transformed),
            sourceWidth = bounds.width(),
            sourceHeight = bounds.height()
        )
    }
}
