package com.timachado.brothermatrizes.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class AuthorizedMatrixMatch(
    val id: Long,
    val title: String,
    val format: String,
    val telegramUrl: String,
    val previewUrl: String
)

data class AuthorizedVisionResult(
    val catalogCount: Int,
    val matches: List<AuthorizedMatrixMatch>
)

/** Direct visual comparison API. Never falls back to fake keyword search. */
object AuthorizedVisionSearchClient {
    private const val MAX_RESPONSE_BYTES = 256 * 1024
    private val telegramPost = Regex("https://t\\.me/[A-Za-z][A-Za-z0-9_]{4,31}/[1-9][0-9]*")

    fun search(
        context: Context,
        selectedImage: Uri,
        accessToken: String,
        serviceUrl: String
    ): Result<AuthorizedVisionResult> = runCatching {
        require(accessToken.isNotBlank()) { "Entre com Google para pesquisar." }
        require(serviceUrl.startsWith("https://") && !serviceUrl.contains("@")) {
            "O serviço de pesquisa visual ainda não está conectado."
        }
        val bodyBytes = makeUploadPhoto(context, selectedImage)
        val boundary = "BrotherVision" + UUID.randomUUID().toString().replace("-", "")
        val target = URL(serviceUrl.trimEnd('/') + "/v1/search/image")
        val connection = (target.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 35_000
            instanceFollowRedirects = false
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            connection.outputStream.use { out ->
                out.write(("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"image\"; filename=\"query.jpg\"\r\n" +
                    "Content-Type: image/jpeg\r\n\r\n").toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status != 200) {
                val explanation = when (status) {
                    401, 403 -> "Sua conta precisa ser autenticada novamente."
                    413 -> "A foto é muito grande para pesquisa."
                    429 -> "Muitas consultas. Tente novamente mais tarde."
                    503 -> "O catálogo de matrizes ainda não está disponível."
                    else -> "A pesquisa falhou (HTTP $status)."
                }
                throw IOException(explanation)
            }
            val response = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) throw IOException("Resposta muito grande.")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            parseResponse(response)
        } finally {
            connection.disconnect()
            bodyBytes.fill(0)
        }
    }

    fun parseResponse(source: String): AuthorizedVisionResult {
        val json = JSONObject(source)
        val count = json.optInt("catalog_count", 0).coerceAtLeast(0)
        val matches = json.optJSONArray("matches") ?: return AuthorizedVisionResult(count, emptyList())
        val results = ArrayList<AuthorizedMatrixMatch>()
        for (i in 0 until minOf(matches.length(), 8)) {
            val item = matches.optJSONObject(i) ?: continue
            val origin = item.optString("telegram_url")
            val preview = item.optString("preview_url")
            val title = item.optString("title").take(150)
            if (!telegramPost.matches(origin) || !preview.startsWith("https://") || title.isBlank()) continue
            results.add(
                AuthorizedMatrixMatch(
                    id = item.optLong("id"),
                    title = title,
                    format = item.optString("format").take(10),
                    telegramUrl = origin,
                    previewUrl = preview
                )
            )
        }
        return AuthorizedVisionResult(count, results)
    }

    /** Normalize image on-device before upload. No images stored in app/server catalog. */
    private fun makeUploadPhoto(context: Context, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IOException("Não foi possível abrir a imagem.")
        require(bounds.outWidth in 1..30000 && bounds.outHeight in 1..30000) {
            "Imagem inválida."
        }
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Imagem inválida.")
        return try {
            ByteArrayOutputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)) {
                    throw IOException("Não foi possível preparar a foto.")
                }
                val bytes = stream.toByteArray()
                require(bytes.size <= 4 * 1024 * 1024) { "Imagem muito grande." }
                bytes
            }
        } finally {
            bitmap.recycle()
        }
    }
}
