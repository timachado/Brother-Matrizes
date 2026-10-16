package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.max
import kotlin.math.min

/** Simple offline initial framing of visible artwork; no semantic AI claim. */
object RasterArtworkAutoCrop {
    fun detect(photo: RasterImagePixels): RasterCropBounds {
        val w = photo.width; val h = photo.height
        if (w < 16 || h < 16 || photo.argb.size != w * h) return RasterCropBounds()
        val alpha = photo.argb.count { (it ushr 24) < 128 } > w * h / 60
        val sample = ArrayList<Int>()
        if (!alpha) {
            for (x in 0 until w) {
                sample.add(photo.argb[x])
                sample.add(photo.argb[(h - 1) * w + x])
            }
            for (y in 0 until h) {
                sample.add(photo.argb[y * w])
                sample.add(photo.argb[y * w + w - 1])
            }
        }
        fun median(values: List<Int>, shift: Int): Int =
            values.map { (it ushr shift) and 255 }.sorted().let { it[it.size / 2] }
        val br = if (alpha) 0 else median(sample, 16)
        val bg = if (alpha) 0 else median(sample, 8)
        val bb = if (alpha) 0 else median(sample, 0)

        var left = w; var right = -1; var top = h; var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            val px = photo.argb[y * w + x]
            val visible = if (alpha) (px ushr 24) >= 150 else {
                val dr = ((px ushr 16) and 255) - br
                val dg = ((px ushr 8) and 255) - bg
                val db = (px and 255) - bb
                dr * dr + dg * dg + db * db > 55 * 55
            }
            if (visible) {
                left = min(left, x); right = max(right, x)
                top = min(top, y); bottom = max(bottom, y)
            }
        }
        if (right < left || bottom < top ||
            (right - left + 1) * (bottom - top + 1) > w * h * .92f) {
            return RasterCropBounds()
        }
        val marginX = max(3, w / 35)
        val marginY = max(3, h / 35)
        return RasterCropBounds(
            left = (left - marginX).coerceAtLeast(0).toFloat() / w,
            top = (top - marginY).coerceAtLeast(0).toFloat() / h,
            right = (right + marginX + 1).coerceAtMost(w).toFloat() / w,
            bottom = (bottom + marginY + 1).coerceAtMost(h).toFloat() / h
        )
    }
}
