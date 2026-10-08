package br.com.timachado.pitchstudio

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class ExtractorDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        if (request.headers().keys.none { it.equals("User-Agent", true) }) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
            )
        }

        val method = request.httpMethod().uppercase()
        val data = request.dataToSend()

        when (method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> {
                val contentType = request.headers()["Content-Type"]
                    ?.firstOrNull()
                    ?.toMediaTypeOrNull()
                builder.post((data ?: ByteArray(0)).toRequestBody(contentType))
            }
            else -> builder.method(
                method,
                if (data != null) data.toRequestBody(null) else null
            )
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody =
                if (method == "HEAD") "" else response.body?.string().orEmpty()

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString()
            )
        }
    }
}
