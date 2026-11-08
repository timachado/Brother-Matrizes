package com.timachado.brothermatrizes.desktop.platform

/**
 * Opt-in developer QA for the SAME official Supabase Brother Matrizes uses
 * on Android. Never create a separate project, a user directory, a Pro grant
 * or an independent quota ledger for Windows.
 *
 * The feature is disabled in release QA packages by default. Even when opted
 * in, no network request, browser login or paid import starts automatically.
 * No service-role/secret keys are accepted; all paid rights remain server-side.
 */
internal object DesktopWindowsAccountQaOptIn {
    internal fun publicKeyFromEnvironment(environment: Map<String, String>): String? {
        if (environment["BROTHER_WINDOWS_QA_GOOGLE_AUTH"] != "enabled") return null
        val key = environment["BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY"]
            ?: return null
        return key.takeIf {
            it.startsWith("sb_publishable_") &&
                it.length in 20..4096 &&
                it.all { c -> c.code in 0x21..0x7e && c != '"' && c != '\\' }
        }
    }

    // Read-only configuration identity. No second Supabase project required.
    internal fun targetForQa(environment: Map<String, String>): DesktopBrotherBackendTarget? {
        if (environment["BROTHER_WINDOWS_QA_GOOGLE_AUTH"] != "enabled") return null
        return DesktopBrotherBackendTarget.official()
    }

    /**
     * One shared QA session is owned by the desktop host, not each screen.
     * Missing explicit opt-in or missing Google backend always fails closed.
     * Real reserve/finalize is only reachable by deliberate catalog clicks
     * after the user signs in and Supabase verifies the Google identity.
     */
    fun fontCatalogAccessForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopFontCatalogAccess {
        val key = publicKeyFromEnvironment(environment)
            ?: return DesktopFontCatalogAccessUnavailable
        if (qaEnvironment == null) return DesktopFontCatalogAccessUnavailable
        return DesktopBrotherDaFontUsageGateway(
            publishableKey = key,
            // This Windows branch still compiles the 1.11.8 (195) Android
            // core. NEVER advertise the newer 1.11.9 code to the quota server
            // until the actual Windows source baseline has been synchronized.
            versionCode = 195,
            verifiedGoogle = DesktopVerifiedGoogleTokenSource {
                qaEnvironment.verifiedAccessTokenForQa()
            }
        )
    }

    fun openWhenOptedIn(): DesktopWindowsGoogleQaEnvironment? {
        val environment = System.getenv()
        val key = publicKeyFromEnvironment(environment) ?: return null
        val target = targetForQa(environment) ?: return null
        return DesktopWindowsGoogleQaEnvironment.openForQa(key, target)
    }
}
