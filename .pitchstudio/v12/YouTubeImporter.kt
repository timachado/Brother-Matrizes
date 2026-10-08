package br.com.timachado.pitchstudio

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object YouTubeImporter {
    data class Result(
        val title: String,
        val uploader: String,
        val durationSeconds: Long,
        val url: String,
        val thumbnailUrl: String
    )

    data class Downloaded(
        val file: File,
        val displayName: String
    )

    data class Preview(
        val url: String,
        val title: String,
        val resolution: String
    )

    private data class MediaSource(
        val url: String,
        val suffix: String
    )

    private val lock = Any()
    @Volatile private var initialized = false

    private val mediaClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(lock) {
            if (!initialized) {
                NewPipe.init(ExtractorDownloader())
                initialized = true
            }
        }
    }

    fun isYouTubeUrl(value: String): Boolean {
        val v = value.lowercase()
        return v.contains("youtube.com/") ||
            v.contains("youtu.be/") ||
            v.contains("music.youtube.com/")
    }

    fun search(query: String, limit: Int = 12): List<Result> {
        ensureInitialized()
        val service = ServiceList.YouTube
        val queryHandler = service.searchQHFactory.fromQuery(query, emptyList(), "")
        val info = SearchInfo.getInfo(service, queryHandler)

        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .map {
                Result(
                    title = it.name,
                    uploader = it.uploaderName.orEmpty(),
                    durationSeconds = it.duration,
                    url = normalizeYouTubeUrl(it.url),
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url.orEmpty()
                )
            }
    }

    fun resolvePreview(videoUrl: String): Preview {
        ensureInitialized()
        val normalizedUrl = normalizeYouTubeUrl(videoUrl)
        val info = StreamInfo.getInfo(ServiceList.YouTube, normalizedUrl)
        val stream = choosePreviewStream(info.videoStreams)
            ?: error("Não foi encontrada uma prévia em vídeo compatível.")

        return Preview(
            url = stream.content,
            title = info.name,
            resolution = stream.resolution.ifBlank { "automática" }
        )
    }

    fun downloadBestAudio(
        context: Context,
        videoUrl: String,
        onProgress: (String, Int) -> Unit
    ): Downloaded {
        ensureInitialized()
        onProgress("Localizando o áudio no YouTube…", 3)

        val normalizedUrl = normalizeYouTubeUrl(videoUrl)
        val info = StreamInfo.getInfo(ServiceList.YouTube, normalizedUrl)
        val source = chooseDownloadSource(info)
            ?: error("Não foi encontrado um áudio compatível para este vídeo.")

        val safeTitle = sanitize(info.name).ifBlank { "audio-youtube" }
        val output = File.createTempFile("youtube_", "." + source.suffix, context.cacheDir)

        try {
            downloadWithRetry(source.url, output, onProgress)
            onProgress("Áudio baixado. Preparando o Pitch Studio…", 94)
            return Downloaded(output, safeTitle + "." + source.suffix)
        } catch (t: Throwable) {
            output.delete()
            throw IOException(friendlyMessage(t), t)
        }
    }

    private fun downloadWithRetry(
        url: String,
        output: File,
        onProgress: (String, Int) -> Unit
    ) {
        var lastError: Throwable? = null

        for (attempt in 1..3) {
            try {
                var offset = output.length()

                val builder = Request.Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .header("Connection", "close")

                if (offset > 0L) {
                    builder.header("Range", "bytes=" + offset + "-")
                }

                mediaClient.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("O YouTube respondeu com código " + response.code + ".")
                    }

                    val body = response.body ?: error("O YouTube retornou uma resposta vazia.")

                    if (offset > 0L && response.code == 200) {
                        output.delete()
                        output.createNewFile()
                        offset = 0L
                    }

                    val contentLength = body.contentLength()
                    val expectedTotal = if (contentLength > 0L) offset + contentLength else -1L

                    FileOutputStream(output, offset > 0L).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(128 * 1024)
                            var total = offset
                            var lastProgress = -1

                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                total += read

                                val progress = if (expectedTotal > 0L) {
                                    (8 + (total * 84L / expectedTotal).toInt()).coerceIn(8, 92)
                                } else {
                                    15
                                }

                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    val message = if (expectedTotal > 0L) {
                                        "Baixando do YouTube… " + progress + "%"
                                    } else {
                                        "Baixando do YouTube… " + (total / 1024 / 1024) + " MB"
                                    }
                                    onProgress(message, progress)
                                }
                            }
                        }
                    }

                    if (output.length() <= 0L) {
                        error("O arquivo recebido do YouTube está vazio.")
                    }

                    return
                }
            } catch (t: Throwable) {
                lastError = t
                if (attempt < 3) {
                    onProgress("A conexão caiu. Tentando novamente (" + (attempt + 1) + "/3)…", 8)
                    try {
                        Thread.sleep(700L * attempt)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }

        throw lastError ?: IOException("Falha ao baixar o áudio.")
    }

    private fun chooseDownloadSource(info: StreamInfo): MediaSource? {
        val audio = chooseAudioStream(info.audioStreams)
        if (audio != null) {
            val suffix = audio.format?.suffix?.ifBlank { null } ?: "m4a"
            return MediaSource(audio.content, suffix)
        }

        val muxed = choosePreviewStream(info.videoStreams)
        if (muxed != null) {
            val suffix = muxed.format?.suffix?.ifBlank { null } ?: "mp4"
            return MediaSource(muxed.content, suffix)
        }

        return null
    }

    private fun chooseAudioStream(streams: List<AudioStream>): AudioStream? {
        val urls = streams.filter {
            it.isUrl && it.content.startsWith("https://")
        }

        val m4a = urls.filter {
            it.format?.suffix.equals("m4a", true)
        }

        val pool = if (m4a.isNotEmpty()) m4a else urls
        return pool.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
    }

    private fun choosePreviewStream(streams: List<VideoStream>): VideoStream? {
        val muxed = streams.filter {
            it.isUrl &&
                !it.isVideoOnly &&
                it.content.startsWith("https://")
        }
        if (muxed.isEmpty()) return null

        val mp4 = muxed.filter { it.format?.suffix.equals("mp4", true) }
        val pool = if (mp4.isNotEmpty()) mp4 else muxed

        val preferred = pool.filter { it.height in 240..480 }
        return (if (preferred.isNotEmpty()) preferred else pool)
            .maxByOrNull { it.height }
    }

    fun normalizeYouTubeUrl(value: String): String {
        val raw = value.trim()
        if (raw.isBlank()) error("O link do YouTube está vazio.")

        val candidate = if (raw.contains("://")) raw else "https://" + raw
        val uri = Uri.parse(candidate)
        val host = uri.host?.lowercase()?.removePrefix("www.")
            ?: error("O link do YouTube é inválido.")

        val videoId = when {
            host == "youtu.be" -> uri.pathSegments.firstOrNull()

            host == "youtube.com" || host == "m.youtube.com" ||
                host == "music.youtube.com" -> {
                when {
                    uri.path == "/watch" -> uri.getQueryParameter("v")
                    uri.pathSegments.firstOrNull() in setOf("shorts", "live", "embed") ->
                        uri.pathSegments.getOrNull(1)
                    else -> uri.getQueryParameter("v")
                }
            }

            else -> null
        }?.substringBefore('?')
            ?.substringBefore('&')
            ?.trim()
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
            ?: error("Não reconheci esse endereço do YouTube.")

        return "https://www.youtube.com/watch?v=" + videoId
    }

    fun friendlyMessage(t: Throwable): String {
        val message = generateSequence(t) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        return when {
            t is SocketTimeoutException || "timeout" in message ->
                "A conexão com o YouTube demorou demais. Tente novamente."

            t is SocketException ||
                "connection abort" in message ||
                "connection reset" in message ||
                "broken pipe" in message ->
                "A conexão com o YouTube foi interrompida. Tente novamente."

            "url not accepted" in message ||
                "link" in message && "inválid" in message ->
                "Esse link do YouTube não foi reconhecido."

            "403" in message ->
                "O YouTube recusou temporariamente esse download. Tente novamente ou escolha outro resultado."

            "429" in message ->
                "O YouTube limitou as solicitações por alguns instantes. Tente novamente depois."

            else ->
                t.message?.takeIf { it.isNotBlank() && !it.contains("Software caused", true) }
                    ?: "Não foi possível concluir a operação com o YouTube."
        }
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\/:*?\"<>|]+"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(100)
}
