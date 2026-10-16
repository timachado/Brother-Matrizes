package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import kotlin.math.min

/**
 * Same orientation rule as the original EmbroideryCanvas: rotate the
 * REFERENCE HOOP (not the raster) for landscape art in a portrait hoop.
 * This class controls preview placement, not machine safety.
 */
internal object ImageHoopSizing {
    private fun frame(imageWidth: Int, imageHeight: Int, hoop: HoopProfile): Pair<Float, Float> {
        require(imageWidth > 0 && imageHeight > 0) { "Imagem inválida." }
        val rotated = hoop.widthMm != hoop.heightMm &&
            (imageWidth > imageHeight) != (hoop.widthMm > hoop.heightMm)
        return if (rotated) {
            hoop.usableHeightMm to hoop.usableWidthMm
        } else {
            hoop.usableWidthMm to hoop.usableHeightMm
        }
    }

    fun maximumWidthMm(imageWidth: Int, imageHeight: Int, hoop: HoopProfile): Float {
        val (safeW, safeH) = frame(imageWidth, imageHeight, hoop)
        return min(200f, min(safeW, safeH * imageWidth.toFloat() / imageHeight))
    }

    /** Change hoop -> fit artwork again, including growing it in larger hoops. */
    fun recommendedWidthMm(imageWidth: Int, imageHeight: Int, hoop: HoopProfile): Float {
        return (maximumWidthMm(imageWidth, imageHeight, hoop) * .92f)
            .coerceAtLeast(1f)
    }

    fun heightMm(imageWidth: Int, imageHeight: Int, widthMm: Float): Float {
        require(imageWidth > 0 && imageHeight > 0 && widthMm.isFinite() && widthMm > 0f)
        return widthMm * imageHeight.toFloat() / imageWidth
    }

    fun fits(imageWidth: Int, imageHeight: Int, widthMm: Float, hoop: HoopProfile): Boolean {
        if (!widthMm.isFinite() || widthMm <= 0f) return false
        val (safeW, safeH) = frame(imageWidth, imageHeight, hoop)
        return widthMm <= safeW &&
            heightMm(imageWidth, imageHeight, widthMm) <= safeH
    }
}
