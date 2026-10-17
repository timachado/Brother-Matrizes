package com.timachado.brothermatrizes.core.embroidery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.security.MessageDigest

/** Importação local de imagens por SAF, com controle de dimensões e memória. */
/**
 * Import evidence is kept only in the current session. No photo, raw bytes,
 * thumbnail, or SHA is uploaded to a server by this diagnostic.
 */
internal data class RasterImageQaImport(
    val image: RasterImagePixels,
    val evidence: RasterImageSamplingEvidence
)

object RasterImageDecoder {
    private const val MAX_FILE_BYTES = 12L * 1024L * 1024L
    private const val MAX_SOURCE_SIDE = 12000
    // 512px keeps thin lettering legible longer; max 512x512 is validated below.
    private const val MAX_PREVIEW_SIDE = 512

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
    /**
     * Add source dimensions and a memory-bounded reference (<=1024px) to
     * the EXISTING 512px reader. Cannot claim to retain full-res pixels.
     * Never used by the commercial PES/JEF/DST file loaders.
     */
    internal fun decodeWithSamplingEvidence(
        resolver: ContentResolver,
        uri: Uri
    ): Result<RasterImageQaImport> = runCatching {
        val working = decode(resolver, uri).getOrThrow()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val dimensionsStream = resolver.openInputStream(uri)
            ?: error("Não foi possível abrir a foto original para conferir dimensões.")
        // Android returns a NULL Bitmap intentionally with inJustDecodeBounds.
        // Success is determined by outWidth/outHeight, not decodeStream's return.
        dimensionsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth in 2..MAX_SOURCE_SIDE &&
            bounds.outHeight in 2..MAX_SOURCE_SIDE) {
            "Dimensões originais inválidas."
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        resolver.openInputStream(uri)?.use { stream ->
            while (true) {
                val length = stream.read(buffer)
                if (length < 0) break
                total += length
                require(total <= MAX_FILE_BYTES) {
                    "Arquivo original maior que 12 MB."
                }
                digest.update(buffer, 0, length)
            }
        } ?: error("Não foi possível identificar a foto de origem.")
        val alphabet = "0123456789abcdef"
        val hash = buildString(64) {
            digest.digest().forEach { byte ->
                val i = byte.toInt() and 255
                append(alphabet[i ushr 4])
                append(alphabet[i and 15])
            }
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1024 ||
            bounds.outHeight / sampleSize > 1024) sampleSize *= 2

        val reference = if (
            bounds.outWidth == working.width &&
            bounds.outHeight == working.height
        ) working else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: error("Não foi possível abrir a referência de maior resolução.")
            try {
                require(bitmap.width in working.width..1024 &&
                    bitmap.height in working.height..1024
                ) { "Referência de alta resolução fora do limite de memória." }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0,
                    bitmap.width, bitmap.height)
                RasterImagePixels(bitmap.width, bitmap.height, pixels, working.label)
            } finally {
                bitmap.recycle()
            }
        }
        val evidence = RasterImageSamplingAudit.inspect(
            reference = reference, working = working,
            sourceWidth = bounds.outWidth, sourceHeight = bounds.outHeight,
            sha256 = hash
        ).getOrThrow()
        RasterImageQaImport(working, evidence)
    }

}
