package com.timachado.brothermatrizes.core.svg

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads SVG from the verified public SVG Silh file URL.
 * No scraping, credential forwarding, cookies, or unbounded allocations.
 */
internal object SvgSilhDownloader {
    private const val MAX_BYTES = 2_000_000
    const val HOME_URL = "https://svgsilh.com/pt/"

    private val hosts = setOf("svgsilh.com", "www.svgsilh.com")
    private val svgPath = Regex("^/svg/[0-9]+\\.svg$", RegexOption.IGNORE_CASE)

    fun isTrustedPage(raw: String): Boolean = parseUri(raw)?.let { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in hosts &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443)
    } ?: false

    fun isDownloadLink(raw: String): Boolean {
        val uri = parseUri(raw) ?: return false
        return isTrustedPage(raw) && svgPath.matches(uri.path.orEmpty()) &&
            uri.rawQuery.isNullOrEmpty()
    }

    fun fileName(raw: String): String {
        require(isDownloadLink(raw)) { "O endereço SVG não pertence ao catálogo permitido." }
        return "svg-silh-" + URI(raw).path.substringAfterLast('/')
    }

    private fun parseUri(raw: String): URI? =
        runCatching { URI(raw) }.getOrNull()

    suspend fun download(raw: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isDownloadLink(raw)) {
                    "Este link não é um download SVG confiável do SVG Silh."
                }
                var target = raw
                repeat(4) { hop ->
                    val connection = (URL(target).openConnection() as HttpURLConnection)
                    try {
                        connection.requestMethod = "GET"
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 12_000
                        connection.readTimeout = 15_000
                        connection.setRequestProperty("Accept", "image/svg+xml, application/xml")
                        connection.setRequestProperty(
                            "User-Agent",
                            "BrotherMatrizesSVG/1.0 (https://timachado.ifree.page)"
                        )
                        val status = connection.responseCode
                        if (status in 300..399) {
                            require(hop < 3) { "Redirecionamentos excessivos no download." }
                            val location = connection.getHeaderField("Location")
                                ?: error("Redirecionamento sem endereço.")
                            val redirected = URI(target).resolve(location).toString()
                            require(isDownloadLink(redirected)) {
                                "Download redirecionado para endereço não permitido."
                            }
                            target = redirected
                        } else {
                            require(status == 200) {
                                "Servidor SVG indisponível (HTTP $status)."
                            }
                            require(connection.contentLengthLong <= MAX_BYTES) {
                                "SVG maior que 2 MB."
                            }
                            val output = ByteArrayOutputStream()
                            connection.inputStream.use { stream ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    require(output.size() + count <= MAX_BYTES) {
                                        "SVG maior que 2 MB."
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                            val bytes = output.toByteArray()
                            require(isSvgDocument(bytes)) {
                                "O arquivo recebido não é um SVG válido para download."
                            }
                            return@runCatching bytes
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                error("Redirecionamentos excessivos no download.")
            }
        }

    /**
     * Conservative check for downloads, not a full SVG parser.
     * Import uses the stricter SimpleSvgParser in the embroidery core.
     */
    fun isSvgDocument(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return false
        val xml = bytes.toString(Charsets.UTF_8)
        if (xml.contains("<!ENTITY", ignoreCase = true) ||
            xml.contains("<script", ignoreCase = true) ||
            xml.contains("<foreignObject", ignoreCase = true)) return false
        return Regex("<(?:[a-zA-Z][\\w-]*:)?svg\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(xml.take(8192))
    }
}
