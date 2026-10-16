package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shape-aware starter rails from a single vectorized, non-branching stroke.
 *
 * Estimates the major tangent by principal components, samples the actual
 * ink cross-sections perpendicular to it, and follows varying left/right
 * contours. Rejects holes, forked strokes, jumps, excessive turns and wide
 * blocks. Unlike raster-row "Satin", this NEVER stitches image scanlines.
 *
 * Output is a suggestion for the user to inspect and edit, NOT an automatic
 * machine-ready embroidery pattern or a substitute for a path skeleton.
 */
object RasterAutoSatinRails {
    fun propose(
        image: RasterImagePixels,
        part: RasterSatinObject,
        fullWidthMm: Float
    ): Result<ManualSatinSection> = runCatching {
        require(image.width in 8..512 && image.height in 8..512 &&
            part.mask.pixels.size == image.argb.size &&
            fullWidthMm.isFinite() && fullWidthMm in 10f..200f) {
            "Imagem inválida para sugestão automática Satin."
        }
        val mask = part.mask
        val w = mask.maxX - mask.minX + 1
        val h = mask.maxY - mask.minY + 1
        require(w >= 4 && h >= 3 && part.pixelCount >= 12) {
            "Elemento pequeno: ajuste os trilhos manualmente."
        }
        // Pixel-edge contours distinguish solid strokes from loops and holes.
        val contours = RasterVectorContours.trace(mask, image.width).getOrThrow()
        require(contours.size == 1 && !contours[0].isHole) {
            "Letra com furo ou múltiplos contornos: divida em seções manuais."
        }
        fun ink(x: Double, y: Double): Boolean {
            val xx = x.toInt()
            val yy = y.toInt()
            return x >= 0.0 && y >= 0.0 && xx in 0 until w && yy in 0 until h &&
                mask.pixels[(mask.minY + yy) * image.width + mask.minX + xx] == 0
        }
        var count = 0
        var mx = 0.0
        var my = 0.0
        for (y in 0 until h) for (x in 0 until w) if (ink(x + .5, y + .5)) {
            count++
            mx += x + .5
            my += y + .5
        }
        require(count == part.pixelCount && count >= 12) { "Máscara inconsistente." }
        mx /= count
        my /= count

        var xx = 0.0
        var xy = 0.0
        var yy = 0.0
        for (y in 0 until h) for (x in 0 until w) if (ink(x + .5, y + .5)) {
            val dx = x + .5 - mx
            val dy = y + .5 - my
            xx += dx * dx
            xy += dx * dy
            yy += dy * dy
        }
        // Major eigenvector: direction of a straight or gently curved stroke.
        val angle = 0.5 * atan2(2.0 * xy, xx - yy)
        val tx = cos(angle)
        val ty = sin(angle)
        val nx = -ty
        val ny = tx
        var minT = Double.POSITIVE_INFINITY
        var maxT = Double.NEGATIVE_INFINITY
        var minN = Double.POSITIVE_INFINITY
        var maxN = Double.NEGATIVE_INFINITY
        for (y in 0 until h) for (x in 0 until w) if (ink(x + .5, y + .5)) {
            val dx = x + .5 - mx
            val dy = y + .5 - my
            val t = dx * tx + dy * ty
            val n = dx * nx + dy * ny
            minT = min(minT, t); maxT = max(maxT, t)
            minN = min(minN, n); maxN = max(maxN, n)
        }
        val lengthPx = maxT - minT
        require(lengthPx >= 9.0) { "Faixa muito curta para inferência Satin." }
        val stepCount = ceil(lengthPx / 1.6).toInt().coerceIn(8, 68)
        val mmPerPx = fullWidthMm / image.width
        val rails = ArrayList<SatinRailPair>()
        val widths = ArrayList<Double>()
        val centers = ArrayList<Double>()
        val times = ArrayList<Double>()
        var missed = 0

        for (index in 0..stepCount) {
            val t = minT + .9 + (lengthPx - 1.8) * index / stepCount
            var low = Double.POSITIVE_INFINITY
            var high = Double.NEGATIVE_INFINITY
            var last = Double.NaN
            var split = false
            // Subpixel sampling detects holes/parallel branches across each
            // slice. Sample density is independent of image scaling.
            val steps = ceil((maxN - minN + 2.0) / .4).toInt()
            for (i in 0..steps) {
                val n = minN - 1.0 + i * .4
                val x = mx + tx * t + nx * n
                val y = my + ty * t + ny * n
                if (!ink(x, y)) continue
                if (last.isFinite() && n - last > 1.0) split = true
                last = n
                low = min(low, n)
                high = max(high, n)
            }
            require(!split) {
                "A forma possui dois traços ou ramificação no mesmo corte: " +
                    "separe em seções manuais."
            }
            if (!low.isFinite() || high - low < .4) {
                missed++
                continue
            }
            val widthMm = (high - low) * mmPerPx
            require(widthMm in .8..6.0) {
                "Largura Satin inadequada nesta parte: ajuste manual necessário."
            }
            val center = (low + high) / 2
            fun px(n: Double): Pair<Float, Float> {
                val x = mx + tx * t + nx * n
                val y = my + ty * t + ny * n
                return ((x / w).coerceIn(.001, .999)).toFloat() to
                    ((y / h).coerceIn(.001, .999)).toFloat()
            }
            val (ax, ay) = px(low)
            val (bx, by) = px(high)
            rails += SatinRailPair(ax, ay, bx, by)
            widths += high - low
            centers += center
            times += t
        }
        require(missed <= 1 && rails.size >= 7) {
            "Contorno irregular: não foi possível seguir uma coluna Satin única."
        }
        val narrow = widths.minOrNull() ?: 0.0
        val wide = widths.maxOrNull() ?: 0.0
        require(narrow > .0 && wide / narrow <= 2.35) {
            "Espessura muda demais: esta forma exige seções Satin separadas."
        }
        for (i in 1 until rails.size) {
            val dCenter = abs(centers[i] - centers[i - 1])
            val dt = times[i] - times[i - 1]
            require(dt > 0 && dCenter / dt <= .55) {
                "Curva muda de direção abruptamente: edite os trilhos manualmente."
            }
        }
        // Unequal global spread indicates a block instead of a stroke.
        require(lengthPx / (maxN - minN).coerceAtLeast(.1) >= 2.15) {
            "Região larga demais para um Satin automático de uma coluna."
        }
        ManualSatinSection(rails = rails)
    }
}
