package com.timachado.brothermatrizes.font

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Rotas oficiais do DaFont. Não inventa resultados e não faz scraping.
 * Permite navegar livremente pelo site original sem confundir resultados com novidades.
 */
internal object DaFontNavigationRules {
    const val HOME = "https://www.dafont.com/pt/"

    fun searchUrl(query: String): String {
        val term = query.trim().take(90)
        return if (term.isBlank()) HOME
        else "https://www.dafont.com/search.php?q=" +
            URLEncoder.encode(term, "UTF-8")
    }

    fun isOfficialPage(url: String): Boolean {
        val parsed = parseHttps(url) ?: return false
        return parsed.host.lowercase(Locale.ROOT) in setOf("dafont.com", "www.dafont.com")
    }

    fun isOfficialDownload(url: String): Boolean {
        val parsed = parseHttps(url) ?: return false
        if (parsed.host.lowercase(Locale.ROOT) != "dl.dafont.com") return false
        return parsed.path == "/dl/" && !parsed.rawQuery.isNullOrBlank()
    }

    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4b.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun parseHttps(value: String): URI? = runCatching {
        URI(value).takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                it.host != null &&
                it.userInfo == null &&
                (it.port == -1 || it.port == 443) &&
                value.length <= 4096
        }
    }.getOrNull()
}
