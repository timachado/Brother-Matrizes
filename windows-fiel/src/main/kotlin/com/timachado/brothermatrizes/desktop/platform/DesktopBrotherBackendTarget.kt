package com.timachado.brothermatrizes.desktop.platform

import java.net.URL

/**
 * The Windows application uses the EXACT SAME Supabase project as the
 * official Android Brother Matrizes. No separate accounts, subscription
 * database, quota ledger or parallel Supabase installation.
 *
 * This value is a public routing identifier, never a secret or credential.
 * QA uses an injected fake HttpURLConnection and never calls the backend
 * unless the developer explicitly opts into an authorized account test.
 */
internal class DesktopBrotherBackendTarget private constructor(val ref: String) {
    val origin: String = "https://$ref.supabase.co"

    fun endpoint(path: String): URL {
        require(path in setOf(
            "/auth/v1/user",
            "/auth/v1/settings",
            "/auth/v1/token?grant_type=pkce",
            "/auth/v1/token?grant_type=refresh_token",
            "/functions/v1/brother-entitlement",
            "/functions/v1/brother-usage"
        )) { "Endpoint Brother Matrizes não autorizado." }

        return URL(origin + path).also { url ->
            check(url.protocol == "https" &&
                url.host == "$ref.supabase.co" &&
                url.port == -1 && url.userInfo == null &&
                url.ref == null) {
                "Host Brother Matrizes inesperado."
            }
        }
    }

    companion object {
        const val OFFICIAL_REF = "dwpcddiramxlhavdmmyn"

        fun official(): DesktopBrotherBackendTarget =
            DesktopBrotherBackendTarget(OFFICIAL_REF)
    }

    override fun toString(): String = "DesktopBrotherBackendTarget(redacted)"
}
