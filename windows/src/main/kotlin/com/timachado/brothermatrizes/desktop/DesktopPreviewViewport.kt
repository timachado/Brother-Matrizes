package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset

/**
 * Navegação exclusivamente visual da prévia Windows. Não altera coordenadas
 * do projeto, comandos de bordado, transformações do editor nem arquivos.
 */
internal data class DesktopPreviewViewport(
    private val minX: Int,
    private val minY: Int,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    fun screenPoint(x: Int, y: Int): Offset = Offset(
        offsetX + (x.toLong() - minX.toLong()).toFloat() * scale,
        offsetY + (y.toLong() - minY.toLong()).toFloat() * scale
    )
}

internal object DesktopPreviewNavigation {
    const val MAX_ZOOM = 8f

    /** Um evento de rolagem altera só a ampliação visual, sem tocar nos pontos. */
    fun zoomForWheel(currentZoom: Float, wheelY: Float): Float {
        require(currentZoom.isFinite() && currentZoom in 1f..MAX_ZOOM) {
            "Ampliação atual inválida."
        }
        require(wheelY.isFinite()) { "Rolagem de mouse inválida." }
        if (wheelY == 0f) return currentZoom
        val multiplier = if (wheelY < 0f) 1.25f else 0.8f
        return (currentZoom * multiplier).coerceIn(1f, MAX_ZOOM)
    }

    /**
     * Mantém o mesmo ponto da matriz sob o cursor durante o zoom.
     * O centro do canvas é a origem visual; nenhum ponto do bordado muda.
     */
    fun panForZoomAt(
        currentZoom: Float,
        nextZoom: Float,
        pan: Offset,
        cursor: Offset,
        canvasWidth: Float,
        canvasHeight: Float
    ): Offset {
        require(currentZoom.isFinite() && currentZoom in 1f..MAX_ZOOM &&
            nextZoom.isFinite() && nextZoom in 1f..MAX_ZOOM) {
            "Níveis de zoom inválidos."
        }
        require(canvasWidth.isFinite() && canvasHeight.isFinite() &&
            canvasWidth > 0f && canvasHeight > 0f) {
            "Área de prévia inválida."
        }
        require(pan.x.isFinite() && pan.y.isFinite() &&
            cursor.x.isFinite() && cursor.y.isFinite()) {
            "Posições de zoom inválidas."
        }
        val factor = nextZoom / currentZoom
        val nextX = (cursor.x - canvasWidth / 2f) * (1f - factor) + pan.x * factor
        val nextY = (cursor.y - canvasHeight / 2f) * (1f - factor) + pan.y * factor
        require(nextX.isFinite() && nextY.isFinite()) {
            "Deslocamento de zoom fora do intervalo permitido."
        }
        return Offset(nextX, nextY)
    }

    fun viewport(
        bounds: PreviewBounds,
        canvasWidth: Float,
        canvasHeight: Float,
        zoom: Float,
        pan: Offset
    ): DesktopPreviewViewport {
        require(canvasWidth.isFinite() && canvasHeight.isFinite() &&
            canvasWidth > 0f && canvasHeight > 0f) {
            "Área de prévia inválida."
        }
        require(zoom.isFinite() && zoom in 1f..MAX_ZOOM) {
            "Zoom visual deve estar entre 1x e 8x."
        }
        require(pan.x.isFinite() && pan.y.isFinite()) {
            "Deslocamento de prévia inválido."
        }
        val width = (bounds.maxX.toLong() - bounds.minX.toLong())
            .coerceAtLeast(1L).toFloat()
        val height = (bounds.maxY.toLong() - bounds.minY.toLong())
            .coerceAtLeast(1L).toFloat()
        val usableWidth = (canvasWidth - 40f).coerceAtLeast(1f)
        val usableHeight = (canvasHeight - 40f).coerceAtLeast(1f)
        val scale = minOf(usableWidth / width, usableHeight / height) * zoom
        require(scale.isFinite() && scale > 0f) { "Escala da prévia inválida." }
        val offsetX = (canvasWidth - width * scale) / 2f + pan.x
        val offsetY = (canvasHeight - height * scale) / 2f + pan.y
        require(offsetX.isFinite() && offsetY.isFinite()) {
            "Posição da prévia inválida."
        }
        return DesktopPreviewViewport(
            minX = bounds.minX, minY = bounds.minY,
            scale = scale, offsetX = offsetX, offsetY = offsetY
        )
    }
}
