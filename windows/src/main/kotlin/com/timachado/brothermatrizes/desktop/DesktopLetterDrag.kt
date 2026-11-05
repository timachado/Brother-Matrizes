package com.timachado.brothermatrizes.desktop

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/**
 * Transforma gesto da prévia em edição de UMA letra de uma composição nativa
 * com camadas verificadas. Conserva os limites de segurança do motor Android.
 *
 * Não tenta identificar objetos em matrizes DST/PES/JEF importadas.
 */
internal object DesktopLetterDrag {
    fun move(
        layout: DesktopLetterLayout,
        index: Int,
        bounds: PreviewBounds,
        canvasWidth: Float,
        canvasHeight: Float,
        zoom: Float,
        deltaPx: Offset
    ): DesktopLetterLayout {
        require(deltaPx.x.isFinite() && deltaPx.y.isFinite()) {
            "Movimento do mouse inválido."
        }
        require(index in layout.editableIndices) {
            "A letra selecionada não é editável."
        }
        val viewport = DesktopPreviewNavigation.viewport(
            bounds = bounds, canvasWidth = canvasWidth,
            canvasHeight = canvasHeight, zoom = zoom, pan = Offset.Zero
        )
        val stepX = deltaPx.x / viewport.scale
        val stepY = deltaPx.y / viewport.scale
        require(stepX.isFinite() && stepY.isFinite() &&
            stepX in -1000.49f..1000.49f &&
            stepY in -1000.49f..1000.49f) {
            "Movimento de letra acima do limite de 100 mm."
        }
        // Coordenadas do motor têm resolução de 0,1 mm.
        val moveX = stepX.roundToInt() / 10f
        val moveY = stepY.roundToInt() / 10f
        val current = layout.objects[index]
        return DesktopLetterComposer.update(
            layout, index,
            moveXmm = current.moveXmm + moveX,
            moveYmm = current.moveYmm + moveY,
            scale = current.scale,
            rotationDegrees = current.rotationDegrees
        )
    }
}
