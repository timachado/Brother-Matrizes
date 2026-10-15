package com.timachado.brothermatrizes.font

import java.net.URI
import java.util.Locale

/**
 * Uses NetFontes' own pages, search form and download action.
 * No undocumented API, website scraping or implied commercial-use license.
 */
internal object NetFontesNavigationRules {
    const val HOME = "https://www.netfontes.com/"

    fun isOfficialPage(url: String): Boolean {
        val parsed = parseHttps(url) ?: return false
        return parsed.host.lowercase(Locale.ROOT) in setOf("netfontes.com", "www.netfontes.com")
    }

    fun isOfficialDownload(url: String): Boolean {
        val parsed = parseHttps(url) ?: return false
        if (parsed.host.lowercase(Locale.ROOT) !in setOf("netfontes.com", "www.netfontes.com")) return false
        // Verified public download route, without accepting arbitrary remote links.
        return parsed.path == "/dow.php" &&
            parsed.rawQuery?.matches(Regex("cod=[A-Za-z0-9_-]{1,150}")) == true
    }

    private fun parseHttps(url: String): URI? = runCatching {
        URI(url).takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                it.host != null && it.userInfo == null &&
                (it.port == -1 || it.port == 443) &&
                url.length <= 4096
        }
    }.getOrNull()
}

/** One browser and one ZIP-import workflow for both official catalogs. */
enum class FontCatalogSource(
    val displayName: String,
    val homeUrl: String,
    val filePrefix: String
) {
    DAFONT("DaFont", DaFontNavigationRules.HOME, "dafont"),
    NETFONTES("NetFontes", NetFontesNavigationRules.HOME, "netfontes");

    fun isOfficialPage(url: String): Boolean = when (this) {
        DAFONT -> DaFontNavigationRules.isOfficialPage(url)
        NETFONTES -> NetFontesNavigationRules.isOfficialPage(url)
    }

    fun isOfficialDownload(url: String): Boolean = when (this) {
        DAFONT -> DaFontNavigationRules.isOfficialDownload(url)
        NETFONTES -> NetFontesNavigationRules.isOfficialDownload(url)
    }
}
