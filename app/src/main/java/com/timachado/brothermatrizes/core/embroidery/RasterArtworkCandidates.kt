package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.max
import kotlin.math.min

/** Region suggested by local image processing; NOT semantic object detection. */
data class RasterArtworkCandidate(
    val id: Int,
    val bounds: RasterCropBounds,
    val activePixels: Int,
    val confidence: Float
)

/**
 * Offline region proposal for individual contrasted logo/text fragments.
 *
 * Extracts alpha/edge-contrast ink, closes short gaps in a coarse image grid,
 * then finds connected regions and returns at most eight padded proposals.
 * It cannot know that a photographic feature is a "logo" without a trained
 * recognizer. Users explicitly confirm the selection before digitizing.
 */
object RasterArtworkCandidates {
    private const val CELL = 4
    private const val MAX_CANDIDATES = 8

    fun detect(source: RasterImagePixels): List<RasterArtworkCandidate> {
        val w = source.width
        val h = source.height
        if (w !in 16..512 || h !in 16..512 || source.argb.size != w * h) {
            return emptyList()
        }
        val transparent = source.argb.count { (it ushr 24) < 128 }
        val usesAlpha = transparent > w * h / 60
        val borderR = ArrayList<Int>()
        val borderG = ArrayList<Int>()
        val borderB = ArrayList<Int>()
        if (!usesAlpha) {
            for (y in 0 until h) for (x in 0 until w) {
                if (x > 1 && y > 1 && x < w - 2 && y < h - 2) continue
                val p = source.argb[y * w + x]
                borderR += (p ushr 16) and 255
                borderG += (p ushr 8) and 255
                borderB += p and 255
            }
        }
        fun median(list: MutableList<Int>): Int {
            list.sort()
            return list[list.size / 2]
        }
        val bgR = if (usesAlpha) 0 else median(borderR)
        val bgG = if (usesAlpha) 0 else median(borderG)
        val bgB = if (usesAlpha) 0 else median(borderB)
        val cw = (w + CELL - 1) / CELL
        val ch = (h + CELL - 1) / CELL
        val density = IntArray(cw * ch)
        for (y in 0 until h) for (x in 0 until w) {
            val p = source.argb[y * w + x]
            val ink = if (usesAlpha) (p ushr 24) >= 180 else {
                val dr = ((p ushr 16) and 255) - bgR
                val dg = ((p ushr 8) and 255) - bgG
                val db = (p and 255) - bgB
                dr * dr + dg * dg + db * db >= 75 * 75
            }
            if (ink) density[(y / CELL) * cw + (x / CELL)]++
        }
        val active = BooleanArray(cw * ch) { density[it] >= 2 }
        // Bridge gaps up to 8px between neighboring strokes/letters.
        // Use original activity as the source to avoid unlimited dilation.
        val bridged = active.copyOf()
        for (cy in 0 until ch) for (cx in 0 until cw) {
            val i = cy * cw + cx
            if (!active[i]) continue
            for (dy in -1..1) for (dx in -1..1) {
                val yy = cy + dy
                val xx = cx + dx
                if (xx in 0 until cw && yy in 0 until ch) {
                    bridged[yy * cw + xx] = true
                }
            }
        }

        val visited = BooleanArray(cw * ch)
        val queue = IntArray(cw * ch)
        data class Region(
            val minX: Int, val minY: Int, val maxX: Int, val maxY: Int,
            val cells: Int, val pixels: Int
        )
        val components = ArrayList<Region>()
        for (root in bridged.indices) {
            if (!bridged[root] || visited[root]) continue
            var head = 0
            var tail = 0
            queue[tail++] = root
            visited[root] = true
            var minX = cw
            var minY = ch
            var maxX = 0
            var maxY = 0
            var cells = 0
            var ink = 0
            while (head < tail) {
                val node = queue[head++]
                val x = node % cw
                val y = node / cw
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                cells++
                ink += density[node]
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until cw || yy !in 0 until ch) continue
                    val next = yy * cw + xx
                    if (!visited[next] && bridged[next]) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
            val pxW = (maxX - minX + 1) * CELL
            val pxH = (maxY - minY + 1) * CELL
            val coverage = ink.toFloat() / (w * h)
            // Reject tiny noise, almost-full-photo/background blobs and stripes.
            if (ink < 20 || cells < 4 || pxW < 8 || pxH < 8 ||
                coverage > .78f || (pxW >= w * .95f && pxH >= h * .95f)
            ) continue
            components += Region(minX, minY, maxX, maxY, cells, ink)
        }
        return components.sortedByDescending { it.pixels }
            .take(MAX_CANDIDATES)
            .mapIndexed { index, r ->
                val left = (r.minX * CELL - max(3, w / 45)).coerceAtLeast(0)
                val top = (r.minY * CELL - max(3, h / 45)).coerceAtLeast(0)
                val right = ((r.maxX + 1) * CELL + max(3, w / 45)).coerceAtMost(w)
                val bottom = ((r.maxY + 1) * CELL + max(3, h / 45)).coerceAtMost(h)
                RasterArtworkCandidate(
                    id = index + 1,
                    bounds = RasterCropBounds(
                        left.toFloat() / w, top.toFloat() / h,
                        right.toFloat() / w, bottom.toFloat() / h
                    ),
                    activePixels = r.pixels,
                    confidence = (r.pixels.toFloat() /
                        max(1, (right - left) * (bottom - top))).coerceIn(0f, 1f)
                )
            }
    }
}
