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
            // Verified against the source app/build.gradle.kts by a QA test.
            // Version metadata is NOT authorization or proof of parity.
            versionCode = DesktopAndroidBaseline.VERSION_CODE,
            verifiedGoogle = DesktopVerifiedGoogleTokenSource {
                qaEnvironment.verifiedAccessTokenForQa()
            }
        )
    }

    /**
     * The only opt-in entry point for a REAL import_font reservation.
     *
     * TWO independent opt-ins are needed, plus an existing verified Google
     * QA controller. Default Windows packages expose no import gate.
     * Never persist the publishable key, bearer or plan as local entitlement.
     */
    internal fun fontImportEnabled(environment: Map<String, String>): Boolean =
        environment["BROTHER_WINDOWS_QA_FONT_IMPORT"] == "enabled" &&
            publicKeyFromEnvironment(environment) != null

    fun fontImportGateForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopCommercialFontImportGate? {
        if (qaEnvironment == null || !fontImportEnabled(environment)) return null
        val key = checkNotNull(publicKeyFromEnvironment(environment))
        val fonts = DesktopWindowsDpapiSessionStore.defaultUserDataFolder()
            .resolve("fonts")
        return DesktopCommercialFontImportGate(
            library = DesktopImportedFontLibrary(fonts),
            verifiedSession = DesktopVerifiedFontSession {
                qaEnvironment.verifiedAccessTokenForQa()
            },
            usage = DesktopBrotherFontUsageGateway(
                publishableKey = key,
                appVersionCode = DesktopAndroidBaseline.VERSION_CODE
            ),
            journal = DesktopFontImportSafety.journalFor(fonts)
        )
    }

    /** Same official Google/PKCE controller as Minha Conta and DaFont. */
    fun telegramSearchAccessForQa(
        qaEnvironment: DesktopWindowsGoogleQaEnvironment?,
        environment: Map<String, String> = System.getenv()
    ): DesktopTelegramSearchAccess {
        val key = publicKeyFromEnvironment(environment)
            ?: return DesktopTelegramSearchUnavailable
        if (qaEnvironment == null) return DesktopTelegramSearchUnavailable
        return DesktopBrotherTelegramUsageGateway(
            publishableKey = key,
            versionCode = DesktopAndroidBaseline.VERSION_CODE,
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
