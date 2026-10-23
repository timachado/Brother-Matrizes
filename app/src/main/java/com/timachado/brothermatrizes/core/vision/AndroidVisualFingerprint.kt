package com.timachado.brothermatrizes.core.vision

import android.graphics.Bitmap
import android.graphics.Color

/** Extrai uma impressão digital compacta em memória a partir de uma foto local. */
object AndroidVisualFingerprint {
    fun fromBitmap(bitmap: Bitmap): Long? {
        val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        try {
            val argb = IntArray(72)
            small.getPixels(argb, 0, 9, 0, 0, 9, 8)
            val gray = IntArray(72) { index ->
                val color = argb[index]
                // A transparência é tratada sobre fundo branco, como a maioria
                // das visualizações de matrizes e fotos de bordado.
                val alpha = Color.alpha(color)
                val red = (Color.red(color) * alpha + 255 * (255 - alpha)) / 255
                val green = (Color.green(color) * alpha + 255 * (255 - alpha)) / 255
                val blue = (Color.blue(color) * alpha + 255 * (255 - alpha)) / 255
                (red * 299 + green * 587 + blue * 114) / 1000
            }
            return if (VisualFingerprint.hasEnoughDetail(gray)) {
                VisualFingerprint.fromGray9x8(gray)
            } else {
                null
            }
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }
}
