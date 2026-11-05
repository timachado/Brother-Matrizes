package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset

/**
 * Teste de toque sobre a geometria real de uma letra gerada pelo motor.
 * Seleção puramente visual: não altera o documento nem o arquivo original.
 */
internal object DesktopLetterPicker {
    private const val TOLERANCE_PX = 5f

    fun pick(
        designBounds: PreviewBounds,
        regions: List<DesktopLetterRegion>,
        width: Float,
        height: Float,
        zoom: Float,
        pan: Offset,
        cursor: Offset
    ): Int? {
        require(cursor.x.isFinite() && cursor.y.isFinite()) {
            "Ponto de seleção inválido."
        }
        val viewport = DesktopPreviewNavigation.viewport(
            bounds = designBounds, canvasWidth = width,
            canvasHeight = height, zoom = zoom, pan = pan
        )
        var closest: Int? = null
        var bestScore = Float.POSITIVE_INFINITY
        for (item in regions) {
            val b = item.bounds
            val p0 = viewport.screenPoint(b.minX, b.minY)
            val p1 = viewport.screenPoint(b.maxX, b.maxY)
            val left = minOf(p0.x, p1.x) - TOLERANCE_PX
            val right = maxOf(p0.x, p1.x) + TOLERANCE_PX
            val top = minOf(p0.y, p1.y) - TOLERANCE_PX
            val bottom = maxOf(p0.y, p1.y) + TOLERANCE_PX
            if (cursor.x !in left..right || cursor.y !in top..bottom) continue
            // Sobreposição: preferir a região de menor distância normalizada
            // ao centro. Em empate, primeira letra da ordem de renderização.
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            val extentX = (right - left).coerceAtLeast(1f)
            val extentY = (bottom - top).coerceAtLeast(1f)
            val dx = (cursor.x - centerX) / extentX
            val dy = (cursor.y - centerY) / extentY
            val score = dx * dx + dy * dy
            if (score < bestScore) {
                closest = item.index
                bestScore = score
            }
        }
        return closest
    }
}
