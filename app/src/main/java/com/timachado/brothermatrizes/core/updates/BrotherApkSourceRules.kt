package com.timachado.brothermatrizes.core.updates

import java.net.URI
import java.net.URL

/**
 * Aceita somente o link de download dinâmico disponibilizado pelo site oficial.
 * Nenhum token/nonce é armazenado no app; lê o link válido a cada atualização.
 */
internal object BrotherApkSourceRules {
    private const val HOST = "timachado.ifree.page"
    private val anchor = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val noncePattern = Regex("[A-Za-z0-9_-]{6,64}")

    fun findDownloadUrl(html: String): URL? {
        val base = URL("https://timachado.ifree.page/aplicativos/brother-matrizes/")
        for (match in anchor.findAll(html)) {
            val link = match.groupValues[1]
                .replace("&amp;", "&")
                .replace("&#038;", "&")
                .replace("&#38;", "&")
            val url = runCatching { URL(base, link) }.getOrNull() ?: continue
            if (!isOfficial(url)) continue
            if (url.path != "/") continue
            val params = url.query.orEmpty().split('&')
                .mapNotNull {
                    val split = it.indexOf('=')
                    if (split <= 0) null else it.substring(0, split) to it.substring(split + 1)
                }.toMap()
            val id = params["ti_app_download"]?.toLongOrNull()
            val nonce = params["_wpnonce"].orEmpty()
            if (id != null && id > 0 && noncePattern.matches(nonce)) return url
        }
        return null
    }

    fun isOfficial(url: URL): Boolean =
        url.protocol == "https" && url.host.equals(HOST, ignoreCase = true) &&
            url.userInfo == null && (url.port == -1 || url.port == 443)
}
