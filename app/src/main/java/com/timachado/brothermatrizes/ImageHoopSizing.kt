package com.timachado.brothermatrizes

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import kotlin.math.min

/**
 * Preview-only dimensions, using the SAME usable hoop margins defined by
 * HoopProfile in the commercial app. Does not certify stitch safety.
 */
internal object ImageHoopSizing {
    fun maximumWidthMm(imageWidth: Int, imageHeight: Int, hoop: HoopProfile): Float {
        require(imageWidth > 0 && imageHeight > 0) { "Imagem inválida." }
        return min(
            200f,
            min(
                hoop.usableWidthMm,
                hoop.usableHeightMm * imageWidth.toFloat() / imageHeight
            )
        )
    }

    fun heightMm(imageWidth: Int, imageHeight: Int, widthMm: Float): Float {
        require(imageWidth > 0 && imageHeight > 0 && widthMm.isFinite() && widthMm > 0f)
        return widthMm * imageHeight.toFloat() / imageWidth
    }

    fun fits(imageWidth: Int, imageHeight: Int, widthMm: Float, hoop: HoopProfile): Boolean =
        widthMm.isFinite() &&
            widthMm > 0f &&
            widthMm <= hoop.usableWidthMm &&
            heightMm(imageWidth, imageHeight, widthMm) <= hoop.usableHeightMm
}
