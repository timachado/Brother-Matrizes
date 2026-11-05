package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/** Deslocamento de design em décimos de milímetro; nunca modifica arquivos. */
internal data class DesktopEmbroideryOffset(val xUnits: Int, val yUnits: Int)

/**
 * Converte arrasto do cursor em deslocamento de pontadas, mantendo
 * a escala exata da prévia (inclusive zoom).
 *
 * É uma operação de matriz inteira, não seleção de glifos importados.
 * Os limites são os mesmos de DesktopMatrixService.edit.
 */
internal object DesktopEditorDrag {
    fun fromPixels(
        bounds: PreviewBounds,
        canvasWidth: Float,
        canvasHeight: Float,
        zoom: Float,
        delta: Offset
    ): DesktopEmbroideryOffset {
        require(delta.x.isFinite() && delta.y.isFinite()) {
            "Deslocamento de mouse não finito."
        }
        val viewport = DesktopPreviewNavigation.viewport(
            bounds = bounds,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            zoom = zoom,
            pan = Offset.Zero
        )
        val rawX = delta.x / viewport.scale
        val rawY = delta.y / viewport.scale
        require(rawX.isFinite() && rawY.isFinite() &&
            rawX in -2000.49f..2000.49f &&
            rawY in -2000.49f..2000.49f) {
            "Movimento máximo por operação: 200 mm em cada eixo."
        }
        val xUnits = rawX.roundToInt()
        val yUnits = rawY.roundToInt()
        require(xUnits in -2000..2000 && yUnits in -2000..2000) {
            "Movimento excedeu o limite do editor."
        }
        return DesktopEmbroideryOffset(xUnits, yUnits)
    }
}
