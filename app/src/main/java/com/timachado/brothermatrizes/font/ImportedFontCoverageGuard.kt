package com.timachado.brothermatrizes.font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import kotlin.math.min

/**
 * A integridade da sequência PES não comprova que as letras são legíveis.
 * Comparar a SILHUETA vetorial gerada pela fonte com a área efetivamente
 * visitada pela agulha evita que uma fonte que virou emaranhado receba
 * status "Prévia válida" no editor.
 *
 * Não altera pontos de produção e não depende de IA/OCR ou de uma fonte
 * instalada no sistema. Memória fixa: dois bitmaps de 320 × 192 px.
 */
internal object ImportedFontCoverageGuard {
    data class Score(val precision: Float, val coverage: Float)

    fun check(
        glyphs: List<List<List<AndroidSkiaOutlineExtractor.Point>>>,
        stitches: List<EmbroideryPoint>
    ): Score {
        val contour = Path().apply { fillType = Path.FillType.WINDING }
        glyphs.forEach { glyph ->
            glyph.forEach { ring ->
                if (ring.size >= 3) {
                    contour.moveTo(ring.first().x, ring.first().y)
                    ring.drop(1).forEach { contour.lineTo(it.x, it.y) }
                    contour.close()
                }
            }
        }
        require(!contour.isEmpty) { "A fonte não possui contorno bordável." }
        val bounds = RectF().also { contour.computeBounds(it, true) }
        require(bounds.width() > 1f && bounds.height() > 1f) {
            "A fonte não possui área bordável suficiente."
        }

        val stitchedPath = Path()
        var needle: EmbroideryPoint? = null
        stitches.forEach { point ->
            if (point.command == StitchCommand.END) return@forEach
            if (point.command == StitchCommand.STITCH && needle != null) {
                stitchedPath.moveTo(needle!!.xUnits.toFloat(),
                    needle!!.yUnits.toFloat())
                stitchedPath.lineTo(point.xUnits.toFloat(), point.yUnits.toFloat())
            }
            needle = point
        }

        val bitmapW = 320
        val bitmapH = 192
        val scale = min(
            (bitmapW - 18f) / bounds.width(),
            (bitmapH - 18f) / bounds.height()
        )
        val original = Bitmap.createBitmap(
            bitmapW, bitmapH, Bitmap.Config.ARGB_8888
        )
        val stitched = Bitmap.createBitmap(
            bitmapW, bitmapH, Bitmap.Config.ARGB_8888
        )
        try {
            fun render(bitmap: Bitmap, path: Path, isStitch: Boolean) {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.TRANSPARENT)
                canvas.save()
                canvas.translate(
                    (bitmapW - bounds.width() * scale) / 2f -
                        bounds.left * scale,
                    (bitmapH - bounds.height() * scale) / 2f -
                        bounds.top * scale
                )
                canvas.scale(scale, scale)
                canvas.drawPath(path, Paint().apply {
                    isAntiAlias = false
                    color = Color.WHITE
                    style = if (isStitch) Paint.Style.STROKE else Paint.Style.FILL
                    // Simulação conservadora de volume de linha: 0,24mm.
                    strokeWidth = 2.4f
                    strokeCap = Paint.Cap.ROUND
                })
                canvas.restore()
            }
            render(original, contour, false)
            render(stitched, stitchedPath, true)
            val src = IntArray(bitmapW * bitmapH)
            val dst = IntArray(bitmapW * bitmapH)
            original.getPixels(src, 0, bitmapW, 0, 0, bitmapW, bitmapH)
            stitched.getPixels(dst, 0, bitmapW, 0, 0, bitmapW, bitmapH)
            var expectedPixels = 0
            var producedPixels = 0
            var matchingPixels = 0
            for (i in src.indices) {
                val inside = (src[i] ushr 24) > 0
                val sewn = (dst[i] ushr 24) > 0
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
            require(score.precision >= .54f && score.coverage >= .20f) {
                "A conversão deformou as letras da fonte. " +
                    "O bordado foi bloqueado para evitar uma matriz ilegível. " +
                    "Experimente uma altura maior ou outra fonte."
            }
            return score
        } finally {
            original.recycle()
            stitched.recycle()
        }
    }
}
