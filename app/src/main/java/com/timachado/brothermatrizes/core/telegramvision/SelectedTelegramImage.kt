package com.timachado.brothermatrizes.core.telegramvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri

/** Normalizes only an explicitly selected photo; does not store a searchable index. */
object SelectedTelegramImage {
    fun grayscale9x8(context: Context, uri: Uri): IntArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth !in 1..20000 || bounds.outHeight !in 1..20000) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
            sample *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it, null,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } ?: return null
        return try {
            val thumbnail = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            try {
                val pixels = IntArray(72)
                thumbnail.getPixels(pixels, 0, 9, 0, 0, 9, 8)
                IntArray(72) { pos ->
                    val color = pixels[pos]
                    val alpha = Color.alpha(color)
                    val red = (Color.red(color) * alpha + 255 * (255 - alpha)) / 255
                    val green = (Color.green(color) * alpha + 255 * (255 - alpha)) / 255
                    val blue = (Color.blue(color) * alpha + 255 * (255 - alpha)) / 255
                    (red * 299 + green * 587 + blue * 114) / 1000
                }
            } finally {
                if (thumbnail !== bitmap) thumbnail.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
