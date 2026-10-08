package br.com.timachado.pitchstudio

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
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

    private val lock = Any()
    @Volatile private var initialized = false

    private val mediaClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
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

    fun downloadBestAudio(
        context: Context,
        videoUrl: String,
        onProgress: (String, Int) -> Unit
    ): Downloaded {
        ensureInitialized()
        onProgress("Resolvendo áudio do YouTube…", 3)

        val normalizedUrl = normalizeYouTubeUrl(videoUrl)
        val info = StreamInfo.getInfo(ServiceList.YouTube, normalizedUrl)
        val stream = chooseAudioStream(info.audioStreams)
            ?: error("Nenhum stream de áudio utilizável foi encontrado para este vídeo.")

        if (!stream.isUrl) error("O stream retornado pelo YouTube não possui URL direta.")

        val suffix = stream.format?.suffix?.ifBlank { null } ?: "m4a"
        val safeTitle = sanitize(info.name).ifBlank { "youtube-audio" }
        val output = File.createTempFile("youtube_", "." + suffix, context.cacheDir)

        try {
            val request = Request.Builder()
                .url(stream.content)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                        "Chrome/140.0 Mobile Safari/537.36"
                )
                .header("Accept", "audio/*,*/*;q=0.8")
                .build()

            mediaClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download do áudio retornou HTTP " + response.code + ".")
                }

                val body = response.body ?: error("Resposta de áudio vazia.")
                val expected = body.contentLength()

                FileOutputStream(output).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        var total = 0L
                        var lastProgress = -1

                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            total += read

                            val progress = if (expected > 0) {
                                (8 + (total * 82 / expected).toInt()).coerceIn(8, 90)
                            } else 15

                            if (progress != lastProgress) {
                                lastProgress = progress
                                val message = if (expected > 0) {
                                    "Baixando do YouTube… " + progress + "%"
                                } else {
                                    "Baixando do YouTube… " + (total / 1024 / 1024) + " MB"
                                }
                                onProgress(message, progress)
                            }
                        }
                    }
                }
            }

            onProgress("Áudio baixado. Preparando para o Pitch Studio…", 92)
            return Downloaded(output, safeTitle + "." + suffix)
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    fun normalizeYouTubeUrl(value: String): String {
        val raw = value.trim()
        if (raw.isBlank()) error("Link do YouTube vazio.")

        val candidate = if (raw.contains("://")) raw else "https://" + raw
        val uri = Uri.parse(candidate)
        val host = uri.host?.lowercase()?.removePrefix("www.")
            ?: error("Link do YouTube inválido.")

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
            ?: error("URL do YouTube não reconhecida.")

        return "https://www.youtube.com/watch?v=" + videoId
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

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(100)
}
