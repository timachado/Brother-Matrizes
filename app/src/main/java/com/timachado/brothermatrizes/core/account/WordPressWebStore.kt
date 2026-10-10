package com.timachado.brothermatrizes.core.account

import java.net.URI

/**
 * Browser-only access to the existing WooCommerce account page.
 * The InfinityFree free tier does not support Android HTTP API clients.
 *
 * A browser session is NOT an app license, an OAuth session or evidence of
 * a completed Efí payment. Never attach Supabase or WooCommerce credentials.
 */
internal object WordPressWebStore {
    fun accountUrl(siteUrl: String): String {
        val base = URI(siteUrl.trim())
        require(base.scheme == "https" &&
            base.host.equals("timachado.ifree.page", ignoreCase = true) &&
            base.port == -1 &&
            base.rawUserInfo == null &&
            base.rawQuery == null &&
            base.rawFragment == null &&
            base.rawPath.trim('/').isEmpty()
        ) { "Site WordPress não autorizado." }
        return "https://timachado.ifree.page/minha-conta/meus-aplicativos/"
    }
}
