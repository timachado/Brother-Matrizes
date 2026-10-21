package com.timachado.brothermatrizes

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Imported source artwork on the simulation fabric, not embroidery data.
 * Always scaled to the SAME center and physical dimensions as QA stitches.
 * Image data never becomes a PES color map or a machine command.
 */
internal data class PremiumImageGhost(
    val bitmap: ImageBitmap,
    val widthMm: Float,
    val heightMm: Float
)
