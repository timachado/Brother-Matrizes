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
    /**
     * Opens the existing WordPress/WooCommerce purchase flow in an external browser.
     * No Efí or WooCommerce credential is sent from the APK. Store is authoritative
     * for amount, availability, stock and confirmation of payment.
     */
    fun planUrl(siteUrl: String, code: String): String {
        accountUrl(siteUrl) // Strict origin check before constructing any purchase link.
        return when (code) {
            "pro_monthly" ->
                "https://timachado.ifree.page/minha-conta/?tiac_auth=confirm&tiac_resume_app=307&tiac_resume_plan=monthly"
            "pro_yearly" ->
                "https://timachado.ifree.page/minha-conta/?tiac_auth=confirm&tiac_resume_app=307&tiac_resume_plan=yearly"
            "pro_lifetime" ->
                "https://timachado.ifree.page/minha-conta/?tiac_auth=confirm&tiac_resume_app=307&tiac_resume_plan=lifetime"
            // Promotional product #308 is independent from the standard lifetime offer.
            // View its WooCommerce product page rather than silently buying the wrong plan.
            "pro_lifetime_launch" ->
                "https://timachado.ifree.page/?post_type=product&p=308"
            else -> throw IllegalArgumentException("Plano de compra não autorizado.")
        }
    }
}
