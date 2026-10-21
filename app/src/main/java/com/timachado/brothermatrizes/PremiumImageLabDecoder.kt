package com.timachado.brothermatrizes

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.roundToInt

/**
 * Android content-URI decoder for the Premium QA PNG importer.
 *
 * BitmapFactory.Options(inJustDecodeBounds=true) INTENTIONALLY returns null;
 * only outWidth/outHeight indicate if the header was read correctly.
 * Keeps alpha intact and never uploads or stores the imported photo.
 */
internal object PremiumImageLabDecoder {
    fun decode(
        resolver: ContentResolver,
        uri: Uri
    ): Result<PremiumImageLabPhoto> = runCatching {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val headerStream = resolver.openInputStream(uri)
            ?: error("Não foi possível ler o arquivo escolhido.")
        headerStream.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        require(bounds.outWidth in 8..12000 &&
            bounds.outHeight in 8..12000
        ) { "PNG inválido ou com dimensões fora do limite." }

        var sample = 1
        while (bounds.outWidth / sample > 160 ||
            bounds.outHeight / sample > 160) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Não foi possível decodificar o PNG selecionado.")

        try {
            val ratio = minOf(
                1f, 160f / bitmap.width, 160f / bitmap.height
            )
            val width = (bitmap.width * ratio).roundToInt()
                .coerceAtLeast(2)
            val height = (bitmap.height * ratio).roundToInt()
                .coerceAtLeast(2)
            require(width in 8..160 && height in 8..160) {
                "Reduza a imagem: pelo menos 8 px por lado após a amostragem."
            }

            // Nearest-neighbor preserves intentional transparent holes.
            val scaled = if (width == bitmap.width &&
                height == bitmap.height) bitmap
                else Bitmap.createScaledBitmap(bitmap, width, height, false)
            try {
                val pixels = IntArray(width * height)
                scaled.getPixels(pixels, 0, width, 0, 0, width, height)
                require(pixels.any { (it ushr 24) < 128 }) {
                    "A imagem possui fundo opaco. Importe PNG transparente para o teste de contorno."
                }
                require(pixels.any { (it ushr 24) >= 208 }) {
                    "A imagem não possui tinta visível para gerar pontos."
                }
                PremiumImageLabPhoto(
                    width, height, pixels,
                    sample > 1 ||
                        width != bounds.outWidth ||
                        height != bounds.outHeight
                )
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
