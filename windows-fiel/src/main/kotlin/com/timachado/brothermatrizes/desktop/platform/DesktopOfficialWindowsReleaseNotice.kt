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
 * Future official Windows release channel, completely separate from Android's
 * updates/stable.json. Read-only metadata, never a binary downloader/updater.
 *
 * The manifest is NOT published as part of this Windows QA branch. HTTP 404
 * is an expected "not yet published" result, NOT evidence of a new release.
 * No notifications, downloads, installation or background service are started.
 */
internal class DesktopOfficialWindowsReleaseNotice(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    companion object {
        const val WINDOWS_MANIFEST =
            "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/windows/stable.json"
        private const val MAX_CHARS = 12_288
        private val VERSION = Regex("^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$")
    }

    data class Notice(
        val versionName: String,
        val versionCode: Int,
        val highlights: List<String>,
        val releaseTag: String
    ) {
        fun displayText(): String = buildString {
            append("Versão oficial Windows ").append(versionName).append(".\\n")
            if (highlights.isEmpty()) append("Consulte a página oficial para detalhes.")
            else highlights.forEach { append("• ").append(it).append('\\n') }
            append("\\nAviso informativo: nenhum executável será baixado, ")
            append("instalado ou executado automaticamente.")
        }
    }

    /**
     * null means the publisher has not made an accessible official Windows
     * manifest at the designated URL. Never replace it with Android metadata.
     */
    suspend fun checkPublishedWindowsRelease(): Notice? = withContext(Dispatchers.IO) {
        fetch(URL(WINDOWS_MANIFEST))
    }

    private fun fetch(url: URL): Notice? {
        check(url.protocol == "https" &&
            url.host == "raw.githubusercontent.com" &&
            url.port == -1 && url.userInfo == null &&
            url.ref == null && url.query == null &&
            url.path == "/timachado/Brother-Matrizes/main/updates/windows/stable.json") {
            "Fonte de atualização Windows inesperada."
        }
        val conn = connectionFactory(url)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> return null
                HttpURLConnection.HTTP_OK -> Unit
                else -> error("Canal oficial Windows indisponível.")
            }
            check(conn.contentLengthLong in -1L..MAX_CHARS.toLong()) {
                "Manifesto Windows acima do limite."
            }
            val mime = conn.contentType.orEmpty()
                .substringBefore(';').trim().lowercase()
            // GitHub Raw sometimes serves JSON files as text/plain. Content
            // still must parse as JSON, and origin/path cannot be overridden.
            check(mime == "application/json" || mime == "text/plain" ||
                mime.endsWith("+json")) { "Tipo de manifesto Windows inesperado." }
            val root = Json.parseToJsonElement(readLimited(conn.inputStream)) as? JsonObject
                ?: error("Manifesto Windows inválido.")
            check(root.string("applicationId") == "com.timachado.brothermatrizes" &&
                root.string("platform") == "windows" &&
                root.string("channel") == "stable" &&
                root.string("status") == "published") {
                "Manifesto não representa uma versão Windows oficial publicada."
            }
            val versionName = root.string("versionName")
            check(VERSION.matches(versionName)) { "Versão Windows inválida." }
            val versionCode = (root["versionCode"] as? JsonPrimitive)
                ?.takeUnless { it.isString }?.intOrNull
                ?: error("Número da versão Windows inválido.")
            check(versionCode in 1..999_999_999) {
                "Número da versão Windows inválido."
            }
            val releaseTag = root.string("releaseTag")
            check(releaseTag == "windows-v$versionName") {
                "Identificação de release Windows não corresponde à versão."
            }
            return Notice(
                versionName = versionName,
                versionCode = versionCode,
                releaseTag = releaseTag,
                highlights = root.string("releaseNotes")
                    .split(';', '\\n')
                    .map { it.trim().replace(Regex("[\\r\\t]+"), " ") }
                    .filter { it.length in 12..160 && !it.contains("://") }
                    .distinct().take(3)
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.string(field: String): String =
        (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun readLimited(stream: InputStream): String =
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val chars = CharArray(1024)
            val text = StringBuilder()
            while (true) {
                val amount = reader.read(chars)
                if (amount < 0) break
                check(text.length + amount <= MAX_CHARS) {
                    "Manifesto Windows excedeu o limite seguro."
                }
                text.append(chars, 0, amount)
            }
            text.toString()
        }

    override fun toString() = "DesktopOfficialWindowsReleaseNotice(redacted)"
}
