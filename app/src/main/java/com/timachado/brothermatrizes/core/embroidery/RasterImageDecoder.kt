package com.timachado.brothermatrizes.core.embroidery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns

/** Importação local de imagens por SAF, com controle de dimensões e memória. */
object RasterImageDecoder {
    private const val MAX_FILE_BYTES = 12L * 1024L * 1024L
    private const val MAX_SOURCE_SIDE = 12000
    private const val MAX_PREVIEW_SIDE = 256

    fun decode(resolver: ContentResolver, uri: Uri): Result<RasterImagePixels> =
        runCatching {
            val mime = resolver.getType(uri)?.lowercase()
            require(mime == null || mime.startsWith("image/") ||
                mime == "application/octet-stream") {
                "Selecione um arquivo PNG, JPG ou WebP."
            }
            var title = "Imagem importada"
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        require(cursor.getLong(sizeColumn) <= MAX_FILE_BYTES) {
                            "Imagem acima do limite de 12 MB."
                        }
                    }
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                        title = cursor.getString(nameColumn).take(80)
                    }
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri)
                ?: error("Não foi possível abrir a imagem.")
            boundsStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            require(bounds.outWidth in 2..MAX_SOURCE_SIDE &&
                bounds.outHeight in 2..MAX_SOURCE_SIDE) {
                "Dimensões da imagem inválidas ou muito grandes."
            }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_PREVIEW_SIDE ||
                bounds.outHeight / sampleSize > MAX_PREVIEW_SIDE
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: error("Formato de imagem não reconhecido.")
            try {
                val width = bitmap.width
                val height = bitmap.height
                require(width in 2..512 && height in 2..512) {
                    "Imagem acima do limite de processamento."
                }
                val argb = IntArray(width * height)
                bitmap.getPixels(argb, 0, width, 0, 0, width, height)
                RasterImagePixels(
                    width = width,
                    height = height,
                    argb = argb,
                    label = title
                )
            } finally {
                bitmap.recycle()
            }
        }
}
