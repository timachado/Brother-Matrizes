package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import kotlin.math.roundToInt

/**
 * Compartilha o enquadramento exato de EmbroideryCanvas com a alça de mover nome.
 * A imagem do nome e a região tocável precisam usar a mesma coordenada.
 */
internal object NameEditorPlacement {
    data class ScreenOffset(val x: Float, val y: Float, val unitsPerPixel: Float)

    fun dragUnits(dxPixels: Float, dyPixels: Float, scale: Float,
                  yDirection: Float): Pair<Int, Int> {
        val safeScale = scale.coerceAtLeast(0.01f)
        return (dxPixels / safeScale).roundToInt() to
            (dyPixels / (safeScale * yDirection)).roundToInt()
    }

    fun screenOffset(
        design: EmbroideryDesign,
        hoop: HoopProfile,
        xUnits: Int,
        yUnits: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        paddingPixels: Float
    ): ScreenOffset {
        val bounds = design.bounds
        val width = (bounds.maxXUnits - bounds.minXUnits).coerceAtLeast(1)
        val height = (bounds.maxYUnits - bounds.minYUnits).coerceAtLeast(1)
        val rotate = hoop.widthMm != hoop.heightMm &&
            (width > height) != (hoop.widthMm > hoop.heightMm)
        val frameW = (if (rotate) hoop.heightMm else hoop.widthMm) * 10f
        val frameH = (if (rotate) hoop.widthMm else hoop.heightMm) * 10f
        val scale = minOf(
            (canvasWidth.toFloat() - 2f * paddingPixels) / frameW,
            (canvasHeight.toFloat() - 2f * paddingPixels) / frameH
        ).coerceAtLeast(0.01f)
        val preserve = shouldPreserveHoopPosition(design, hoop, rotate)
        val centerX = if (preserve) 0f else
            (bounds.minXUnits.toLong() + bounds.maxXUnits.toLong()) / 2f
        val centerY = if (preserve) 0f else
            (bounds.minYUnits.toLong() + bounds.maxYUnits.toLong()) / 2f
        val direction = if (design.sourceYAxisDown) 1f else -1f
        return ScreenOffset(
            x = (xUnits - centerX) * scale,
            y = (yUnits - centerY) * scale * direction,
            unitsPerPixel = scale
        )
    }
}
