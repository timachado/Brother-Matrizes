package com.timachado.brothermatrizes.desktop.platform

import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/**
 * Explicit Google sign-in for the normal Windows account.
 * OAuth PKCE S256, loopback state verification, user-bound Windows DPAPI.
 * Server is still the sole authority for subscriptions and import quotas.
 */
internal class DesktopWindowsGoogleAccount private constructor(
    private val account: DesktopWindowsAuthQaController,
    private val identity: DesktopSupabaseGoogleIdentityVerifier,
    private val entitlement: DesktopBrotherEntitlementReader,
    private val provider: DesktopGoogleProviderReadiness
) : Closeable {
    /** Stage 1: Google verified by Supabase. No Pro/quotas inferred. */
    suspend fun signInAndVerify(): DesktopVerifiedGoogleIdentity {
        check(provider.checkPublicGoogleProvider().googleProviderEnabled) {
            "Google não está habilitado no Supabase oficial."
        }
        // The browser callback only returns an authorization code. PKCE token
        // exchange, Google identity check and DPAPI storage happen afterwards.
        return account.loginAndVerifyGoogleForQa(identity)
    }

    /** Stage 1 on restart: revalidate the protected current-user session. */
    suspend fun restoreAndVerify(): DesktopVerifiedGoogleIdentity? {
        if (!account.recover()) return null
        return account.verifyGoogleAccountForQa(identity)
    }

    suspend fun verifiedGoogleIdentity(): DesktopVerifiedGoogleIdentity =
        account.verifyGoogleAccountForQa(identity)

    suspend fun signInAndPreview(): DesktopServerAccountSnapshot {
        signInAndVerify()
        return account.accountPreviewForQa(identity, entitlement)
    }

    suspend fun restoreAndPreview(): DesktopServerAccountSnapshot? {
        restoreAndVerify() ?: return null
        return account.accountPreviewForQa(identity, entitlement)
    }

    suspend fun accountPreview(): DesktopServerAccountSnapshot =
        account.accountPreviewForQa(identity, entitlement)

    suspend fun verifiedToken(): String =
        account.verifiedAccessTokenForQa(identity)

    suspend fun logout() = account.logout()

    override fun close() = account.close()
    override fun toString() = "DesktopWindowsGoogleAccount(redacted)"

    companion object {
        fun open(
            publishableKey: String,
            target: DesktopBrotherBackendTarget = DesktopBrotherBackendTarget.official(),
            folder: Path = DesktopWindowsDpapiSessionStore.defaultUserDataFolder(),
            clock: Clock = Clock.systemUTC(),
            browser: DesktopExternalBrowser = WindowsExternalBrowser,
            connectionFactory: (URL) -> HttpURLConnection = {
                it.openConnection() as HttpURLConnection
            }
        ): DesktopWindowsGoogleAccount {
            require(target.ref == DesktopBrotherBackendTarget.official().ref) {
                "A conta deve usar o mesmo Supabase oficial do Android."
            }
            require(publishableKey.startsWith("sb_publishable_") &&
                publishableKey.length in 20..4096 &&
                publishableKey.all {
                    it.code in 0x21..0x7e && it != '"' && it != '\\'
                }) {
                "O cliente Google aceita somente chave publicável do Supabase."
            }
            val auth = DesktopSupabaseGooglePkceClient(
                publishableKey, connectionFactory, projectRef = target.ref
            )
            val verifier = DesktopSupabaseGoogleIdentityVerifier(
                publishableKey, connectionFactory, projectRef = target.ref
            )
            val coordinator = DesktopGooglePkceLoginCoordinator(
                auth = auth, browser = browser,
                callbackTimeout = Duration.ofMinutes(5)
            )
            // Never silently copy a prior QA token or an Android credential.
            val scoped = folder.resolve("google-account-" + target.ref)
            val controller = DesktopWindowsAuthQaController.open(
                signIn = DesktopWindowsAuthQaController.googleSignInSource(coordinator),
                refresher = DesktopSessionRefresher { prior ->
                    auth.refreshSession(prior)
                },
                clock = clock, folder = scoped
            )
            return try {
                DesktopWindowsGoogleAccount(
                    account = controller,
                    identity = verifier,
                    entitlement = DesktopBrotherEntitlementReader(
                        publishableKey, connectionFactory, projectRef = target.ref
                    ),
                    provider = DesktopGoogleProviderReadiness(
                        publishableKey, connectionFactory, projectRef = target.ref
                    )
                )
            } catch (failure: Exception) {
                controller.close()
                throw failure
            }
        }
    }
}

/** A public client key is safe to bundle; never embed service_role/secret. */
internal object DesktopWindowsGooglePublicClient {
    private const val OFFICIAL_PUBLISHABLE = "sb_publishable_K7JQ8O5fNgZjhEGSQsoZdQ_W4TLRHY4"
    fun key(): String = OFFICIAL_PUBLISHABLE
}
