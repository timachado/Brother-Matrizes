package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.abs
import kotlin.math.max

/**
 * QA-only comparison of a higher-resolution sampled reference and the
 * 512-pixel working raster. The reference may itself be downsampled.
 * It is NOT evidence of complete/full-resolution fidelity or sewability.
 */
internal data class RasterImageSamplingEvidence(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val referenceWidth: Int,
    val referenceHeight: Int,
    val workingWidth: Int,
    val workingHeight: Int,
    val sha256: String,
    val foregroundReferencePixels: Int,
    val potentiallyLostPixels: Int,
    val largestPotentiallyLostRegion: Int
) {
    val referenceIsFullResolution: Boolean get() =
        sourceWidth == referenceWidth && sourceHeight == referenceHeight
    val workingIsFullResolution: Boolean get() =
        sourceWidth == workingWidth && sourceHeight == workingHeight
    val potentiallyLostFraction: Double get() =
        if (foregroundReferencePixels == 0) 0.0
        else potentiallyLostPixels.toDouble() / foregroundReferencePixels
    val needsDetailReview: Boolean get() =
        largestPotentiallyLostRegion >= 4 || potentiallyLostFraction > .015
    val isFullSourceProven: Boolean get() = false // Visual + stitch-out validation absent.
}

internal object RasterImageSamplingAudit {
    private const val MAX_REFERENCE_SIDE = 1024
    private const val MAX_WORKING_SIDE = 512

    fun inspect(
        reference: RasterImagePixels,
        working: RasterImagePixels,
        sourceWidth: Int,
        sourceHeight: Int,
        sha256: String
    ): Result<RasterImageSamplingEvidence> = runCatching {
        require(
            sourceWidth in 2..12_000 && sourceHeight in 2..12_000 &&
                reference.width in 2..MAX_REFERENCE_SIDE &&
                reference.height in 2..MAX_REFERENCE_SIDE &&
                working.width in 2..MAX_WORKING_SIDE &&
                working.height in 2..MAX_WORKING_SIDE &&
                reference.width >= working.width &&
                reference.height >= working.height &&
                reference.width <= sourceWidth &&
                reference.height <= sourceHeight &&
                reference.argb.size == reference.width * reference.height &&
                working.argb.size == working.width * working.height &&
                sha256.length == 64 && sha256.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        ) { "Dimensões, origem ou assinatura da imagem inconsistentes." }

        val sourceAspect = sourceWidth.toDouble() / sourceHeight
        val referenceAspect = reference.width.toDouble() / reference.height
        val workAspect = working.width.toDouble() / working.height
        require(
            abs(sourceAspect - referenceAspect) / sourceAspect < .025 &&
                abs(sourceAspect - workAspect) / sourceAspect < .025
        ) { "Prévia e referência apresentam proporções incompatíveis." }

        val high = reference.argb
        val low = working.argb
        val hasAlpha = high.any { (it ushr 24) < 128 }

        fun background(pixels: IntArray, width: Int, height: Int): Int {
            val red = ArrayList<Int>()
            val green = ArrayList<Int>()
            val blue = ArrayList<Int>()
            for (y in 0 until height) for (x in 0 until width) {
                if (x > 1 && x < width - 2 &&
                    y > 1 && y < height - 2) continue
                val p = pixels[y * width + x]
                if ((p ushr 24) < 128) continue
                red += (p ushr 16) and 255
                green += (p ushr 8) and 255
                blue += p and 255
            }
            if (red.isEmpty()) return 0
            red.sort(); green.sort(); blue.sort()
            val mid = red.size / 2
            return (red[mid] shl 16) or (green[mid] shl 8) or blue[mid]
        }
        val referenceBackground = if (hasAlpha) 0 else
            background(high, reference.width, reference.height)
        val workingBackground = if (hasAlpha) 0 else
            background(low, working.width, working.height)

        fun differenceSquared(a: Int, b: Int): Int {
            val dr = ((a ushr 16) and 255) - ((b ushr 16) and 255)
            val dg = ((a ushr 8) and 255) - ((b ushr 8) and 255)
            val db = (a and 255) - (b and 255)
            return dr * dr + dg * dg + db * db
        }
        fun foreground(p: Int, bg: Int): Boolean =
            if (hasAlpha) ((p ushr 24) >= 180)
            else differenceSquared(p, bg) >= 72 * 72

        val lost = BooleanArray(high.size)
        var refInk = 0
        var atRisk = 0
        val directlyComparable = reference.width == working.width &&
            reference.height == working.height
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val idx = y * reference.width + x
                val p = high[idx]
                if (!foreground(p, referenceBackground)) continue
                refInk++
                if (directlyComparable && p == low[idx]) continue
                // Compare source color with nearby WORKING image colors.
                // A one-cell tolerance avoids counting anti-aliasing on
                // ordinary edges as lost embroidery details.
                val cx = x * working.width / reference.width
                val cy = y * working.height / reference.height
                var represented = false
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = cx + dx
                    val yy = cy + dy
                    if (xx !in 0 until working.width ||
                        yy !in 0 until working.height) continue
                    val candidate = low[yy * working.width + xx]
                    if (foreground(candidate, workingBackground) &&
                        differenceSquared(p, candidate) <= 110 * 110
                    ) represented = true
                }
                if (!represented) {
                    lost[idx] = true
                    atRisk++
                }
            }
        }

        // Distinguish coherent missing accents from isolated JPEG noise.
        val visited = BooleanArray(lost.size)
        val queue = IntArray(lost.size)
        var largest = 0
        for (root in lost.indices) {
            if (!lost[root] || visited[root]) continue
            visited[root] = true
            queue[0] = root
            var front = 0
            var tail = 1
            while (front < tail) {
                val index = queue[front++]
                val x = index % reference.width
                val y = index / reference.width
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until reference.width ||
                        yy !in 0 until reference.height) continue
                    val next = yy * reference.width + xx
                    if (lost[next] && !visited[next]) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
            largest = max(largest, tail)
        }
        RasterImageSamplingEvidence(
            sourceWidth = sourceWidth, sourceHeight = sourceHeight,
            referenceWidth = reference.width, referenceHeight = reference.height,
            workingWidth = working.width, workingHeight = working.height,
            sha256 = sha256,
            foregroundReferencePixels = refInk,
            potentiallyLostPixels = atRisk,
            largestPotentiallyLostRegion = largest
        )
    }
}
