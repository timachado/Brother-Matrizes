package com.timachado.brothermatrizes.font

import kotlin.math.abs

/**
 * Mantém os anéis de contorno obtidos do shaping OpenType do texto INTEIRO.
 *
 * Um vazado interno pertence ao mesmo glifo do contorno exterior que o
 * contém, mesmo quando seu centro fica mais próximo do avanço de outra
 * letra. Nenhum ponto da fonte é deslocado, inventado ou descartado.
 */
internal object ImportedOpenTypeContourGroups {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX: Float get() = (left + right) * 0.5f
        val area: Float get() = (right - left).coerceAtLeast(0f) *
            (bottom - top).coerceAtLeast(0f)
        fun encloses(other: Box) =
            other.left >= left && other.right <= right &&
                other.top >= top && other.bottom <= bottom &&
                area > other.area + 0.01f
    }

    fun owners(boxes: List<Box>, runCenters: List<Float>): List<Int> {
        require(runCenters.isNotEmpty() && runCenters.all { it.isFinite() })
        require(boxes.all {
            it.left.isFinite() && it.top.isFinite() &&
                it.right.isFinite() && it.bottom.isFinite() &&
                it.left <= it.right && it.top <= it.bottom
        })
        val direct = boxes.map { box ->
            runCenters.indices.minByOrNull { i ->
                abs(runCenters[i] - box.centerX)
            } ?: 0
        }.toMutableList()
        // Anéis aninhados (buracos/contraformas e seus próprios vazados)
        // herdam a mesma letra do contorno mais exterior.
        val indices = boxes.indices.sortedByDescending { boxes[it].area }
        for (outer in indices) {
            for (inner in indices) {
                if (outer != inner && boxes[outer].encloses(boxes[inner])) {
                    val isSmallestContaining = indices.none { middle ->
                        middle != outer && middle != inner &&
                            boxes[outer].encloses(boxes[middle]) &&
                            boxes[middle].encloses(boxes[inner])
                    }
                    if (isSmallestContaining) direct[inner] = direct[outer]
                }
            }
        }
        return direct
    }
}
