package com.timachado.brothermatrizes.core.embroidery

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Rectangle in normalized image coordinates, independent of phone screen size. */
data class RasterCropBounds(
    val left: Float = 0.04f,
    val top: Float = 0.04f,
    val right: Float = 0.96f,
    val bottom: Float = 0.96f
)

data class PreparedRasterPhoto(
    val image: RasterImagePixels,
    val transparentPixels: Int,
    val removedBackgroundPixels: Int
)

/**
 * Conservative photo preprocessing; does not change the original image in memory.
 *
 * Only removes regions CONNECTED TO THE EDGE of the selected crop. Internal
 * closed holes and isolated details are never deleted just because they share
 * a color. Complex backgrounds can still need manual retouching.
 */
object RasterPhotoPreparation {
    fun prepare(
        original: RasterImagePixels,
        bounds: RasterCropBounds,
        removeEdgeBackground: Boolean,
        tolerance: Int = 55
    ): Result<PreparedRasterPhoto> = runCatching {
        require(original.width in 8..512 && original.height in 8..512 &&
            original.argb.size == original.width * original.height) {
            "Imagem original inválida para recorte."
        }
        require(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            .all { it.isFinite() && it in 0f..1f } &&
            bounds.left < bounds.right && bounds.top < bounds.bottom) {
            "A área do recorte está fora dos limites."
        }
        require(tolerance in 12..110) { "Tolerância do fundo fora do limite seguro." }
        val x0 = floor(bounds.left * original.width).toInt()
            .coerceIn(0, original.width - 1)
        val y0 = floor(bounds.top * original.height).toInt()
            .coerceIn(0, original.height - 1)
        val x1 = ceil(bounds.right * original.width).toInt()
            .coerceIn(x0 + 1, original.width)
        val y1 = ceil(bounds.bottom * original.height).toInt()
            .coerceIn(y0 + 1, original.height)
        val w = x1 - x0
        val h = y1 - y0
        require(w >= 8 && h >= 8) {
            "Recorte pequeno demais: selecione ao menos 8 × 8 pixels."
        }
        val pixels = IntArray(w * h) { i ->
            original.argb[(y0 + i / w) * original.width + (x0 + i % w)]
        }
        if (!removeEdgeBackground) {
            return@runCatching PreparedRasterPhoto(
                RasterImagePixels(w, h, pixels, original.label),
                transparentPixels = pixels.count { it ushr 24 < 128 },
                removedBackgroundPixels = 0
            )
        }

        // The matte is estimated from genuinely opaque border samples.
        // Reject mostly-transparent art: PNG alpha should already be respected.
        val border = ArrayList<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            if (x != 0 && y != 0 && x != w - 1 && y != h - 1) continue
            val p = pixels[y * w + x]
            if ((p ushr 24) >= 200) border += p
        }
        require(border.size >= 12) {
            "Esta imagem já pode ter transparência. Desative Remover fundo."
        }
        fun median(channel: Int): Int =
            border.map { (it ushr channel) and 255 }.sorted()[border.size / 2]
        val r = median(16)
        val g = median(8)
        val b = median(0)
        val radiusSq = tolerance * tolerance
        fun similar(p: Int): Boolean {
            if ((p ushr 24) < 128) return true
            val dr = ((p ushr 16) and 255) - r
            val dg = ((p ushr 8) and 255) - g
            val db = (p and 255) - b
            return dr * dr + dg * dg + db * db <= radiusSq
        }

        val visited = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0
        fun add(index: Int) {
            if (visited[index] || !similar(pixels[index])) return
            visited[index] = true
            queue[tail++] = index
        }
        for (x in 0 until w) {
            add(x)
            add((h - 1) * w + x)
        }
        for (y in 0 until h) {
            add(y * w)
            add(y * w + w - 1)
        }
        var removed = 0
        while (head < tail) {
            val i = queue[head++]
            if (pixels[i] ushr 24 >= 128) removed++
            pixels[i] = 0
            val x = i % w
            val y = i / w
            if (x > 0) add(i - 1)
            if (x < w - 1) add(i + 1)
            if (y > 0) add(i - w)
            if (y < h - 1) add(i + w)
        }
        val fraction = removed.toFloat() / pixels.size
        require(fraction >= 0.03f) {
            "Fundo não identificado na borda. Ajuste o recorte ou a tolerância."
        }
        require(fraction < 0.96f) {
            "O fundo selecionado cobre quase a imagem toda. Evite apagar o desenho."
        }
        val solid = pixels.count { it ushr 24 >= 128 }
        require(solid >= 15 && solid.toFloat() / pixels.size < 0.73f) {
            "Não foi possível isolar uma logo. Recorte mais próximo da arte ou ajuste o fundo."
        }
        PreparedRasterPhoto(
            image = RasterImagePixels(w, h, pixels, original.label),
            transparentPixels = pixels.size - solid,
            removedBackgroundPixels = removed
        )
    }
}
