package com.timachado.brothermatrizes.core.embroidery

/**
 * Conservative post-quantization cleanup for the colors of an imported logo.
 * A wrong-color dot inside a nearly uniform 3x3 neighborhood is corrected
 * WITHOUT changing alpha/background (-1), outer edges or disconnected shapes.
 */
object RasterPaletteFidelity {
    fun cleanIsolatedColorBleed(
        labels: IntArray,
        width: Int,
        height: Int,
        colorCount: Int
    ): IntArray {
        require(width in 1..512 && height in 1..512 &&
            labels.size == width * height && colorCount in 1..4) {
            "Dimensões ou paleta inválidas."
        }
        require(labels.all { it in -1 until colorCount }) {
            "Pixels fora da paleta."
        }
        if (width < 3 || height < 3 || colorCount < 2) return labels.copyOf()
        val out = labels.copyOf()
        val neighbors = IntArray(colorCount)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val index = y * width + x
            val current = labels[index]
            if (current < 0) continue
            neighbors.fill(0)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val candidate = labels[(y + dy) * width + x + dx]
                if (candidate >= 0) neighbors[candidate]++
            }
            val dominant = neighbors.indices.maxByOrNull { neighbors[it] } ?: continue
            // A definite embedded speck, not an edge/legitimate narrow line.
            if (dominant != current && neighbors[dominant] >= 6 &&
                neighbors[current] <= 1) {
                out[index] = dominant
            }
        }
        return out
    }
}
