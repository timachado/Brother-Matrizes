package com.timachado.brothermatrizes.core.updates

import java.net.URL

/**
 * O APK é publicado pelo site da T.I. Machado ou como asset de release
 * do repositório oficial Brother-Matrizes. A origem é anunciada apenas pelo
 * manifesto assinado logicamente pelo repositório GitHub da T.I. Machado.
 * O SHA-256 e a assinatura comercial são obrigatórios em todos os casos.
 */
internal object BrotherDirectApkSource {
    private const val SITE = "timachado.ifree.page"
    private const val GITHUB = "github.com"
    private const val ASSET_CDN = "release-assets.githubusercontent.com"
    private val releasePath = Regex(
        "^/timachado/Brother-Matrizes/releases/download/v[0-9]+(?:\\.[0-9]+){1,3}/[A-Za-z0-9._-]+\\.apk$",
        RegexOption.IGNORE_CASE
    )

    fun binaryFromManifest(value: String?): URL? {
        if (value.isNullOrBlank()) return null
        val url = runCatching { URL(value) }.getOrNull() ?: return null
        if (!secure(url)) return null
        val host = url.host.lowercase()
        if (host == SITE && url.path.lowercase().endsWith(".apk")) return url
        if (host == GITHUB && releasePath.matches(url.path) && url.query.isNullOrBlank()) return url
        return null
    }

    fun allowedRedirect(previous: URL, target: URL): Boolean {
        if (!secure(target)) return false
        val current = previous.host.lowercase()
        val next = target.host.lowercase()
        return when (next) {
            SITE -> current == SITE && target.path.lowercase().endsWith(".apk")
            GITHUB -> current == GITHUB && releasePath.matches(target.path)
            ASSET_CDN -> current == GITHUB || current == ASSET_CDN
            else -> false
        }
    }

    fun secure(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.userInfo == null && (url.port == -1 || url.port == 443) &&
            url.ref == null
}
