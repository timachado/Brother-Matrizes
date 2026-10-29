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

    /**
     * Modo automático: tocar na área das pontadas do nome seleciona somente
     * o nome. Fora dela, um dedo desloca a matriz real. Nunca tratar esse
     * gesto como pan temporário da câmera.
     */
    fun targetAtTouch(
        design: EmbroideryDesign,
        hoop: HoopProfile,
        touchX: Float,
        touchY: Float,
        canvasWidth: Int,
        canvasHeight: Int,
        paddingPixels: Float,
        nameTouchPaddingPx: Float = 12f
    ): com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning.Target {
        val matrixTarget =
            com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning.Target.MATRIX
        val layer = design.editableName ?: return matrixTarget
        val firstNameStitch = layer.original.points.size + 2
        val stitches = design.points.drop(firstNameStitch).filter {
            it.command == com.timachado.brothermatrizes.core.embroidery.StitchCommand.STITCH
        }
        if (stitches.isEmpty()) return matrixTarget
        val minX = stitches.minOf { it.xUnits }
        val maxX = stitches.maxOf { it.xUnits }
        val minY = stitches.minOf { it.yUnits }
        val maxY = stitches.maxOf { it.yUnits }
        // O nome é desenhado sobre o enquadramento da matriz-base. Usar
        // bounds da composição inteira aqui deslocaria a área tocável.
        val first = screenOffset(layer.original, hoop, minX, minY,
            canvasWidth, canvasHeight, paddingPixels)
        val last = screenOffset(layer.original, hoop, maxX, maxY,
            canvasWidth, canvasHeight, paddingPixels)
        val left = minOf(first.x, last.x) + canvasWidth / 2f - nameTouchPaddingPx
        val right = maxOf(first.x, last.x) + canvasWidth / 2f + nameTouchPaddingPx
        val top = minOf(first.y, last.y) + canvasHeight / 2f - nameTouchPaddingPx
        val bottom = maxOf(first.y, last.y) + canvasHeight / 2f + nameTouchPaddingPx
        return if (touchX in left..right && touchY in top..bottom) {
            com.timachado.brothermatrizes.core.embroidery.HoopLayerPositioning.Target.NAME
        } else matrixTarget
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
