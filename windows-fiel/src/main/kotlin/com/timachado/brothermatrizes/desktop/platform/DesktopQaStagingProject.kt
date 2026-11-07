package com.timachado.brothermatrizes.desktop.platform

import java.net.URL

/**
 * Explicit QA target. A public key alone never chooses a Supabase project.
 * Prevents the Brother Matrizes production project and the unrelated Matrix
 * Engine project from being used as Windows OAuth/quota test environments.
 *
 * Ownership, actual Google provider configuration, redirect allowlist and
 * isolation of test accounts are external homologation requirements; syntax
 * checks here are NOT evidence that a project belongs to the current user.
 */
internal class DesktopQaStagingProject private constructor(val ref: String) {
    val origin: String = "https://$ref.supabase.co"

    fun endpoint(path: String): URL {
        require(path in setOf(
            "/auth/v1/user",
            "/auth/v1/settings",
            "/functions/v1/brother-entitlement",
            "/functions/v1/brother-usage"
        ) || path in setOf(
            "/auth/v1/token?grant_type=pkce",
            "/auth/v1/token?grant_type=refresh_token"
        )) {
            "Endpoint de QA não permitido."
        }
        return URL(origin + path).also {
            check(it.protocol == "https" &&
                it.host == "$ref.supabase.co" && it.port == -1 &&
                it.userInfo == null && it.ref == null) {
                "Destino de QA inválido."
            }
        }
    }

    companion object {
        private const val production = "dwpcddiramxlhavdmmyn"
        private const val matrixEngine = "likurzkruaznsvvidhun"

        fun isolated(ref: String): DesktopQaStagingProject {
            require(ref.matches(Regex("[a-z0-9]{20}")) &&
                ref != production && ref != matrixEngine
            ) {
                "Homologação Windows exige um projeto Supabase próprio, separado da produção."
            }
            return DesktopQaStagingProject(ref)
        }
    }

    override fun toString(): String = "DesktopQaStagingProject(redacted)"
}
