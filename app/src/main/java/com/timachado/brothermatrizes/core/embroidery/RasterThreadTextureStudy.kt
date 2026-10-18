package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A nondestructive color-family study of textured transparent logo pictures.
 * Produces a VIEW ONLY: no needle coordinates, PES, or approval are generated.
 * The original photo is always retained separately for source-fidelity QA.
 */
internal data class RasterThreadTextureStudyReport(
    val flatPreview: RasterImagePixels,
    val foregroundPixels: Int,
    val originalToneBuckets: Int,
    val suggestedColorFamilies: Int,
    val ambiguousPixels: Int,
    val originalAlphaHolesPreserved: Boolean,
    // Detect stale study results if an imported raster changed in memory.
    val sourceArgbFingerprint: Long = 0L,
    val reviewRequired: Boolean = true,
    val exportPermitted: Boolean = false
) {
    fun message(): String =
        "Prévia em " + suggestedColorFamilies + " famílias de cor (de " +
            originalToneBuckets + " grupos de tons). " + ambiguousPixels +
            " pixels precisam de revisão. Vazados e formas devem ser " +
            "reconstruídos em pontos reais. Isto NÃO é uma matriz PES."
}

internal object RasterThreadTextureStudy {
    private const val MAX_SIDE = 512
    private const val MIN_ALPHA = 180
    private const val HUE_BINS = 24
    private const val MAX_CHROMATIC_FAMILIES = 3
    private const val HUE_GAP_DEGREES = 48

    fun inspect(source: RasterImagePixels): Result<RasterThreadTextureStudyReport> =
        runCatching {
            val w = source.width
            val h = source.height
            require(w in 8..MAX_SIDE && h in 8..MAX_SIDE &&
                source.argb.size == w * h
            ) { "Imagem inválida para análise das cores planas." }
            require(source.argb.any { (it ushr 24) < 128 }) {
                "Esta revisão exige fundo transparente; remover fundo " +
                    "de fotografia comum requer revisão separada."
            }
            var sourceFingerprint = 1469598103934665603L
            for (pixel in source.argb) {
                sourceFingerprint = (sourceFingerprint xor
                    (pixel.toLong() and 0xffffffffL)) * 1099511628211L
            }
            val active = BooleanArray(source.argb.size)
            val tones = HashSet<Int>()
            val hueHistogram = IntArray(HUE_BINS)
            val pixelHue = IntArray(source.argb.size) { -1 }
            val neutralHistogram = IntArray(256)
            var opaque = 0
            var colored = 0
            var neutral = 0
            for (i in source.argb.indices) {
                val c = source.argb[i]
                if ((c ushr 24) < MIN_ALPHA) continue
                active[i] = true
                opaque++
                val r = (c ushr 16) and 255
                val g = (c ushr 8) and 255
                val b = c and 255
                val hi = max(r, max(g, b))
                val lo = min(r, min(g, b))
                val d = hi - lo
                tones += ((r / 24) shl 8) or ((g / 24) shl 4) or (b / 24)
                if (d < 45 || hi < 80) {
                    neutral++
                    neutralHistogram[hi]++
                } else {
                    val hue = hueDegrees(r, g, b, hi, d)
                    pixelHue[i] = hue
                    hueHistogram[((hue + 7) / 15) % HUE_BINS]++
                    colored++
                }
            }
            require(opaque >= 24 && opaque.toDouble() / active.size < .80) {
                "Logotipo sem silhueta isolada suficiente para revisão."
            }
            val hues = ArrayList<Int>()
            for (bin in (0 until HUE_BINS).sortedByDescending { hueHistogram[it] }) {
                if (hueHistogram[bin] < max(4, colored / 120)) continue
                val candidate = bin * 15
                if (hues.none { hueDistance(it, candidate) < HUE_GAP_DEGREES }) {
                    hues += candidate
                    if (hues.size == MAX_CHROMATIC_FAMILIES) break
                }
            }
            if (colored > 0 && hues.isEmpty()) {
                hues += (0 until HUE_BINS).maxBy { hueHistogram[it] } * 15
            }

            val hasNeutralFamily = neutral > 0
            val palette = ArrayList<Int>()
            if (hasNeutralFamily) {
                val target = (neutral * .70).toInt()
                var cumulative = 0
                var shade = 230
                for (v in neutralHistogram.indices) {
                    cumulative += neutralHistogram[v]
                    if (cumulative >= target) {
                        shade = (v + 12).coerceIn(48, 248)
                        break
                    }
                }
                palette += argb(shade, shade, shade)
            }
            hues.forEach { palette += swatch(it) }
            require(palette.size in 1..4) {
                "Muitas famílias de cor para uma revisão segura."
            }

            val result = IntArray(active.size)
            var ambiguous = 0
            for (i in active.indices) {
                if (!active[i]) continue
                val hue = pixelHue[i]
                val family = if (hue < 0 && hasNeutralFamily) 0
                else if (hues.isEmpty()) {
                    ambiguous++
                    0
                } else {
                    val nearest = hues.indices.minBy {
                        hueDistance(hue.coerceAtLeast(0), hues[it])
                    }
                    if (hue < 0 ||
                        hueDistance(hue, hues[nearest]) >= HUE_GAP_DEGREES) {
                        ambiguous++
                    }
                    nearest + if (hasNeutralFamily) 1 else 0
                }
                result[i] = palette[family]
            }
            // Alpha silhouette is exact for all >=180 source pixels;
            // internal transparent letter counters cannot be painted over.
            val preserved = active.indices.all { i ->
                active[i] == (result[i] ushr 24 >= MIN_ALPHA)
            }
            require(preserved) { "A revisão alterou os vazados ou a silhueta." }
            RasterThreadTextureStudyReport(
                flatPreview = RasterImagePixels(w, h, result,
                    source.label + " — cores planas QA"),
                foregroundPixels = opaque,
                originalToneBuckets = tones.size,
                suggestedColorFamilies = palette.size,
                ambiguousPixels = ambiguous,
                originalAlphaHolesPreserved = preserved,
                sourceArgbFingerprint = sourceFingerprint
            )
        }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (255 shl 24) or (r shl 16) or (g shl 8) or b

    private fun hueDegrees(r: Int, g: Int, b: Int, hi: Int, delta: Int): Int {
        val hue = when (hi) {
            r -> 60 * (g - b) / delta
            g -> 120 + 60 * (b - r) / delta
            else -> 240 + 60 * (r - g) / delta
        }
        return ((hue % 360) + 360) % 360
    }

    private fun hueDistance(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return min(d, 360 - d)
    }

    private fun swatch(degrees: Int): Int {
        // Display swatch, NOT an actual manufacturer thread designation.
        val h = (degrees % 360) / 60.0
        val chroma = .92 * .76
        val second = chroma * (1 - abs(h % 2 - 1))
        val minimum = .92 - chroma
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(chroma, second, 0.0)
            1 -> Triple(second, chroma, 0.0)
            2 -> Triple(0.0, chroma, second)
            3 -> Triple(0.0, second, chroma)
            4 -> Triple(second, 0.0, chroma)
            else -> Triple(chroma, 0.0, second)
        }
        return argb(((r + minimum) * 255).toInt().coerceIn(0, 255),
            ((g + minimum) * 255).toInt().coerceIn(0, 255),
            ((b + minimum) * 255).toInt().coerceIn(0, 255))
    }
}
