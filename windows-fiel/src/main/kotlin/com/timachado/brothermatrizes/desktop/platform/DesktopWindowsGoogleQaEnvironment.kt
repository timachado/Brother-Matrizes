package com.timachado.brothermatrizes.desktop.platform

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Clock
import java.time.Duration

/**
 * Explicit, opt-in Windows QA composition for native Google OAuth.
 *
 * All network I/O is deferred until signIn(), restoreAndVerify() or
 * accountPreview() is called. No UI, checkout, import gate, auto-login, Pro
 * rights, quota bypass or release packaging is enabled by this class.
 */
internal class DesktopWindowsGoogleQaEnvironment private constructor(
    private val account: DesktopWindowsAuthQaController,
    private val identityVerifier: DesktopSupabaseGoogleIdentityVerifier,
    private val entitlementReader: DesktopBrotherEntitlementReader,
    private val googleProviderReadiness: DesktopGoogleProviderReadiness
) : Closeable {

    suspend fun signIn(): DesktopVerifiedGoogleIdentity =
        account.loginAndVerifyGoogleForQa(identityVerifier)

    /** Restored DPAPI credentials must be rechecked with Supabase Auth. */
    suspend fun restoreAndVerify(): Boolean {
        if (!account.recover()) return false
        account.verifyGoogleAccountForQa(identityVerifier)
        return true
    }

    /** Server verified but informational; never grants importing privileges. */
    suspend fun accountPreview(): DesktopServerAccountSnapshot =
        account.accountPreviewForQa(identityVerifier, entitlementReader)

    /** Public /auth/v1/settings: does not log in or verify redirect URLs. */
    suspend fun googleProviderReadiness(): DesktopGoogleProviderReadiness.Snapshot =
        googleProviderReadiness.checkPublicGoogleProvider()

    suspend fun logout() {
        account.logout()
    }

    override fun close() {
        account.close()
    }

    override fun toString(): String = "DesktopWindowsGoogleQaEnvironment(redacted)"

    companion object {
        fun openForQa(
            publishableKey: String,
            target: DesktopBrotherBackendTarget,
            folder: Path = DesktopWindowsDpapiSessionStore.defaultUserDataFolder(),
            clock: Clock = Clock.systemUTC(),
            browser: DesktopExternalBrowser = WindowsExternalBrowser,
            connectionFactory: (URL) -> HttpURLConnection = {
                it.openConnection() as HttpURLConnection
            }
        ): DesktopWindowsGoogleQaEnvironment {
            // A public key is supplied at runtime; never embed a service key.
            val client = DesktopSupabaseGooglePkceClient(
                publishableKey, connectionFactory, projectRef = target.ref
            )
            val identity = DesktopSupabaseGoogleIdentityVerifier(
                publishableKey, connectionFactory, projectRef = target.ref
            )
            val entitlement = DesktopBrotherEntitlementReader(
                publishableKey, connectionFactory, projectRef = target.ref
            )
            val coordinator = DesktopGooglePkceLoginCoordinator(
                auth = client,
                browser = browser,
                callbackTimeout = Duration.ofMinutes(5)
            )
            // Keep Windows QA DPAPI tokens apart from normal application
            // state and unrelated apps, but use the SAME Supabase identity.
            // No Android device secrets or client files are accessed.
            val scoped = folder.resolve("qa-brother-account-" + target.ref)
            check(!Files.isSymbolicLink(folder) &&
                !Files.isSymbolicLink(scoped) &&
                (!Files.exists(scoped, LinkOption.NOFOLLOW_LINKS) ||
                    Files.isDirectory(scoped, LinkOption.NOFOLLOW_LINKS))) {
                "Diretório de sessão Windows inválido."
            }
            val account = DesktopWindowsAuthQaController.open(
                signIn = DesktopWindowsAuthQaController.googleSignInSource(coordinator),
                refresher = DesktopSessionRefresher { prior ->
                    client.refreshSession(prior)
                },
                clock = clock,
                folder = scoped
            )
            val readiness = DesktopGoogleProviderReadiness(
                publishableKey, connectionFactory, projectRef = target.ref
            )
            return DesktopWindowsGoogleQaEnvironment(
                account, identity, entitlement, readiness
            )
        }
    }
}
