package com.timachado.brothermatrizes.desktop.platform

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Windows counterpart of the ORIGINAL Android BrotherCommunityScreen.getOfficialGroup.
 *
 * Read-only public metadata: official GitHub raw JSON first, official WordPress
 * endpoint second, shipped Android invitation as safe offline fallback.
 * No account, API key, token, analytics, quota or user data is transmitted.
 * No redirects, arbitrary hosts, downloaded code or automatic group joining.
 */
internal class DesktopOfficialCommunityGroupResolver(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    companion object {
        const val GITHUB_SOURCE =
            "https://raw.githubusercontent.com/timachado/Brother-Matrizes/main/updates/community.json"
        const val WORDPRESS_SOURCE =
            "https://timachado.ifree.page/wp-json/tim-brother-sync/v1/community"
    }

    suspend fun resolve(): URI = withContext(Dispatchers.IO) {
        for (source in listOf(GITHUB_SOURCE, WORDPRESS_SOURCE)) {
            val valid = runCatching { fetch(URL(source)) }.getOrNull()
            if (valid != null) return@withContext valid
        }
        DesktopBrotherCommunityLinks.group()
    }

    private fun fetch(source: URL): URI? {
        check(source.protocol == "https" && source.port == -1 &&
            source.userInfo == null && source.ref == null &&
            source.query == null && (
                (source.host == "raw.githubusercontent.com" &&
                    source.path == "/timachado/Brother-Matrizes/main/updates/community.json") ||
                (source.host == "timachado.ifree.page" &&
                    source.path == "/wp-json/tim-brother-sync/v1/community")
            )) { "Fonte de comunicado inesperada." }
        val conn = connectionFactory(source)
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 4_500
            conn.readTimeout = 4_500
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val mime = conn.contentType.orEmpty().substringBefore(';').trim().lowercase()
            if (mime != "application/json" && !mime.endsWith("+json")) return null
            val body = Json.parseToJsonElement(readLimited(conn.inputStream)) as? JsonObject
                ?: return null
            if (source.host == "raw.githubusercontent.com" &&
                body.text("product") != "brother-matrizes"
            ) return null
            return DesktopBrotherCommunityLinks.verifiedGroup(body.text("groupUrl"))
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.text(name: String): String =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

    private fun readLimited(stream: InputStream): String =
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val body = StringBuilder()
            val chars = CharArray(1024)
            while (true) {
                val n = reader.read(chars)
                if (n < 0) break
                check(body.length + n <= 4_096) {
                    "Dados públicos da comunidade excederam limite seguro."
                }
                body.append(chars, 0, n)
            }
            body.toString()
        }
}
