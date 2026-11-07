package com.timachado.brothermatrizes.desktop.platform

/**
 * Temporary, explicit developer-only bridge for verifying the real Google
 * redirect on Windows after the Supabase redirect allowlist is confirmed.
 *
 * Production desktop builds must not silently activate login using a bundled
 * Supabase key. This switch is QA-only and cannot authorize any paid feature.
 * A publishable key is public; secret/service_role keys are never accepted.
 */
internal object DesktopWindowsAccountQaOptIn {
    internal fun publicKeyFromEnvironment(environment: Map<String, String>): String? {
        if (environment["BROTHER_WINDOWS_QA_GOOGLE_AUTH"] != "enabled") return null
        val key = environment["BROTHER_WINDOWS_QA_SUPABASE_PUBLISHABLE_KEY"]
            ?: return null
        return key.takeIf {
            it.startsWith("sb_publishable_") &&
                it.length in 20..4096 &&
                it.all { c ->
                    c.code in 0x21..0x7e && c != '"' && c != '\\'
                }
        }
    }

    internal fun stagingTargetFromEnvironment(
        environment: Map<String, String>
    ): DesktopQaStagingProject? {
        if (environment["BROTHER_WINDOWS_QA_GOOGLE_AUTH"] != "enabled") return null
        val ref = environment["BROTHER_WINDOWS_QA_STAGING_PROJECT_REF"]
            ?: return null
        return runCatching { DesktopQaStagingProject.isolated(ref) }.getOrNull()
    }

    fun openWhenOptedIn(): DesktopWindowsGoogleQaEnvironment? {
        val environment = System.getenv()
        val key = publicKeyFromEnvironment(environment) ?: return null
        // An opt-in key alone is never enough to target the production Auth.
        val staging = stagingTargetFromEnvironment(environment) ?: return null
        return DesktopWindowsGoogleQaEnvironment.openForQa(key, staging)
    }
}
