package com.timachado.brothermatrizes.desktop.platform

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Surface
import kotlin.math.min

/**
 * Diagnóstico QA do ImportedFontCoverageGuard Android 1.11.8.
 *
 * A fonte Android renderiza silhueta WINDING e todos os movimentos
 * STITCH com antiAlias=false em dois bitmaps 320×192. Os pixels com
 * alpha>0 dão precisão e cobertura. Este módulo ainda NÃO substitui
 * o ImportedFontCoverageGuard da versão comercial Windows:
 * diferenças Skia/Android precisam passar em golden numérico.
 */
internal object DesktopCoverageGuardProbe {
    data class Score(
        val precision: Float,
        val coverage: Float,
        val expectedPixels: Int,
        val producedPixels: Int,
        val matchingPixels: Int
    )

    fun check(
        glyphs: List<List<List<DesktopContourPoint>>>,
        stitches: List<EmbroideryPoint>
    ): Score {
        require(glyphs.size in 1..64 && stitches.size in 1..100_000) {
            "Complexidade de matriz excede os limites de QA."
        }
        val rings = glyphs.flatten().filter { it.size >= 3 }
        require(rings.isNotEmpty() && rings.size <= 1024 &&
            rings.sumOf { it.size } <= 80_000 &&
            rings.flatten().all { it.x.isFinite() && it.y.isFinite() &&
                kotlin.math.abs(it.x) <= 1_000_000f &&
                kotlin.math.abs(it.y) <= 1_000_000f }) {
            "Fonte sem contornos bordáveis seguros."
        }
        val left = rings.flatten().minOf { it.x }
        val right = rings.flatten().maxOf { it.x }
        val top = rings.flatten().minOf { it.y }
        val bottom = rings.flatten().maxOf { it.y }
        require(right - left > 1f && bottom - top > 1f) {
            "A fonte não possui área bordável suficiente."
        }
        val bitmapW = 320
        val bitmapH = 192
        val scale = min(
            (bitmapW - 18f) / (right - left),
            (bitmapH - 18f) / (bottom - top)
        )
        require(scale.isFinite() && scale > 0f) {
            "Escala da silhueta não é válida."
        }
        val tx = (bitmapW - (right - left) * scale) / 2f - left * scale
        val ty = (bitmapH - (bottom - top) * scale) / 2f - top * scale
        fun mapX(x: Float): Float = tx + x * scale
        fun mapY(y: Float): Float = ty + y * scale

        val contour = Path()
        val stitched = Path()
        try {
            contour.fillMode = PathFillMode.WINDING
            rings.forEach { ring ->
                contour.moveTo(mapX(ring.first().x), mapY(ring.first().y))
                ring.drop(1).forEach { p ->
                    contour.lineTo(mapX(p.x), mapY(p.y))
                }
                contour.closePath()
            }
            var needle: EmbroideryPoint? = null
            stitches.forEach { point ->
                require(point.xUnits in -1_000_000..1_000_000 &&
                    point.yUnits in -1_000_000..1_000_000) {
                    "Ponto de bordado fora da faixa de segurança."
                }
                if (point.command != StitchCommand.END) {
                    if (point.command == StitchCommand.STITCH && needle != null) {
                        stitched.moveTo(
                            mapX(needle!!.xUnits.toFloat()),
                            mapY(needle!!.yUnits.toFloat())
                        )
                        stitched.lineTo(
                            mapX(point.xUnits.toFloat()),
                            mapY(point.yUnits.toFloat())
                        )
                    }
                    needle = point
                }
            }
            val originalMask = renderMask(contour, bitmapW, bitmapH, null)
            val stitchedMask = renderMask(stitched, bitmapW, bitmapH, 2.4f * scale)
            var expected = 0
            var produced = 0
            var matching = 0
            for (i in originalMask.indices) {
                val inside = originalMask[i]
                val sewn = stitchedMask[i]
                if (inside) expected++
                if (sewn) produced++
                if (inside && sewn) matching++
            }
            require(expected > 25 && produced > 5) {
                "Fonte sem silhueta verificável. Tente outra altura."
            }
            val score = Score(
                matching.toFloat() / produced,
                matching.toFloat() / expected,
                expected, produced, matching
            )
            require(score.precision >= .54f && score.coverage >= .20f) {
                "A conversão deformou as letras da fonte. " +
                    "O bordado foi bloqueado para evitar uma matriz ilegível."
            }
            return score
        } finally {
            contour.close()
            stitched.close()
        }
    }

    private fun renderMask(
        path: Path, width: Int, height: Int, strokeWidth: Float?
    ): BooleanArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            val paint = Paint()
            try {
                paint.isAntiAlias = false
                paint.color = 0xFFFFFFFF.toInt()
                paint.mode = if (strokeWidth == null) PaintMode.FILL else PaintMode.STROKE
                if (strokeWidth != null) {
                    paint.strokeWidth = strokeWidth
                    paint.strokeCap = PaintStrokeCap.ROUND
                }
                surface.canvas.clear(0x00000000)
                surface.canvas.drawPath(path, paint)
                val bitmap = Bitmap()
                try {
                    require(bitmap.allocN32Pixels(width, height)) {
                        "Máscara QA não pôde ser alocada."
                    }
                    require(surface.readPixels(bitmap, 0, 0)) {
                        "Máscara QA não pôde ser lida do Skia."
                    }
                    return BooleanArray(width * height) { i ->
                        bitmap.getAlphaf(i % width, i / width) > 0f
                    }
                } finally {
                    bitmap.close()
                }
            } finally {
                paint.close()
            }
        } finally {
            surface.close()
        }
    }
}
