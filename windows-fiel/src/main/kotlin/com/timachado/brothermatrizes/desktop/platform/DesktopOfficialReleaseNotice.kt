package com.timachado.brothermatrizes.desktop.platform

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Read-only manual check of the OFFICIAL Android stable.json manifest.
 * Announcements are explicitly labeled ANDROID, never Windows availability.
 * No APK download, auto-install, Google account, telemetry or quota operation.
 */
internal class DesktopOfficialReleaseNotice(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    companion object {
        const val ANDROID_MANIFEST =
            "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/stable.json"
        private const val MAX_JSON_CHARS = 12_288
    }

    data class Notice(
        val versionName: String,
        val versionCode: Int,
        val highlights: List<String>
    ) {
        fun displayText(): String =
            "Novidades oficiais do aplicativo Android: versão $versionName.\n" +
                (if (highlights.isEmpty()) "Consulte a página oficial para conhecer as novidades."
                 else highlights.joinToString("\n") { "• $it" }) +
                "\nO Brother Matrizes Windows continua em homologação; " +
                "esta publicação NÃO é um instalador ou atualização para Windows."
    }

    suspend fun checkAndroidAnnouncements(): Notice = withContext(Dispatchers.IO) {
        fetch(URL(ANDROID_MANIFEST))
    }

    private fun fetch(url: URL): Notice {
        check(url.protocol == "https" &&
            url.host == "raw.githubusercontent.com" &&
            url.path == "/timachado/Brother-Matrizes/main/updates/stable.json" &&
            url.port == -1 && url.query == null && url.ref == null &&
            url.userInfo == null) { "Origem da atualização não autorizada." }
        val conn = connectionFactory(url)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            check(conn.responseCode == HttpURLConnection.HTTP_OK) {
                "Avisos oficiais temporariamente indisponíveis."
            }
            check(conn.contentLengthLong <= MAX_JSON_CHARS ||
                conn.contentLengthLong < 0) { "Manifesto grande demais." }
            val mime = conn.contentType.orEmpty()
                .substringBefore(';').trim().lowercase()
            check(mime == "application/json" || mime.endsWith("+json")) {
                "Resposta oficial incompatível."
            }
            val root = Json.parseToJsonElement(readBounded(conn.inputStream)) as? JsonObject
                ?: error("Manifesto de versão inválido.")
            check(root.text("applicationId") == "com.timachado.brothermatrizes" &&
                root.text("channel") == "stable") {
                "Manifesto não pertence ao Brother Matrizes Android estável."
            }
            val version = root.text("versionName")
            check(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$").matches(version)) {
                "Versão publicada inválida."
            }
            val code = (root["versionCode"] as? JsonPrimitive)
                ?.takeIf { !it.isString }?.intOrNull
                ?: error("Código da versão inválido.")
            check(code > 0) { "Código da versão inválido." }
            return Notice(
                versionName = version,
                versionCode = code,
                highlights = root.text("releaseNotes")
                    .split(';', '\n')
                    .map { it.trim().replace(Regex("[\\r\\t]+"), " ") }
                    .filter { it.length in 12..160 && !it.contains("://") }
                    .distinct()
                    .take(3)
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.text(name: String): String =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun readBounded(stream: InputStream): String =
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(1024)
            val body = StringBuilder()
            while (true) {
                val read = reader.read(chars)
                if (read < 0) break
                check(body.length + read <= MAX_JSON_CHARS) {
                    "Manifesto de atualização excedeu limite."
                }
                body.append(chars, 0, read)
            }
            body.toString()
        }
}
