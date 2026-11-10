package com.timachado.brothermatrizes.desktop.platform

import java.io.Closeable
import java.nio.file.Path
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/** Login source is injectable so QA never requires a real Google account. */
internal fun interface DesktopQaSignIn {
    suspend fun signIn(): DesktopTransientAuthSession
}

/**
 * Isolated Windows account lifecycle. This controller is NOT a
 * DesktopVerifiedFontSession and must not be wired to commercial features
 * before the Google redirect and server entitlements are homologated.
 *
 * Holds the OS lease for its lifetime, uses DPAPI for the session and
 * delegates refresh rotation to DesktopTransientSessionKeeper.
 */
internal class DesktopWindowsAuthQaController private constructor(
    private val runtime: DesktopWindowsLockedSessionRuntime,
    private val signIn: DesktopQaSignIn,
    private val keeper: DesktopTransientSessionKeeper
) : Closeable {
    private val lock = Mutex()
    @Volatile private var closed = false

    suspend fun recover(): Boolean = exclusive {
        keeper.restoreFromProtectedStorage()
    }

    suspend fun login(): Unit = exclusive {
        // Generic offline QA only; NOT an authorization for commercial UI.
        keeper.clear()
        val session = signIn.signIn()
        keeper.install(session)
    }

    /**
     * Authenticated QA login: Google provider must be confirmed by the
     * Supabase server BEFORE any token is written to protected storage.
     */
    suspend fun loginAndVerifyGoogleForQa(
        verifier: DesktopSupabaseGoogleIdentityVerifier
    ): DesktopVerifiedGoogleIdentity = exclusive {
        keeper.clear()
        val session = signIn.signIn()
        val identity = try {
            verifier.verifyGoogleIdentity(session.accessToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw DesktopGoogleLoginFailure(DesktopGoogleLoginStage.IDENTITY)
        }
        try {
            keeper.install(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw DesktopGoogleLoginFailure(DesktopGoogleLoginStage.STORAGE)
        }
        identity
    }

    suspend fun accessTokenForQa(): String = exclusive {
        keeper.tokenForIsolatedQa()
    }

    /**
     * Read-only identity check against Supabase Auth.
     * Does not mean that the account has Pro access or import quotas.
     * No desktop UI or commercial gate uses this QA-only method.
     */
    suspend fun verifyGoogleAccountForQa(
        verifier: DesktopSupabaseGoogleIdentityVerifier
    ): DesktopVerifiedGoogleIdentity = exclusive {
        try {
            verifier.verifyGoogleIdentity(keeper.tokenForIsolatedQa())
        } catch (cancelled: CancellationException) {
            keeper.clear()
            throw cancelled
        } catch (_: Exception) {
            // A failed server verification invalidates even DPAPI recovery.
            keeper.clear()
            error("Não foi possível validar a conta Google. Entre novamente.")
        }
    }

    /**
     * Explicit QA-only bearer for desktop catalog actions. The same DPAPI
     * session backing Minha Conta is checked against Supabase /auth/v1/user
     * and required to report the Google provider. Token and verification
     * run under ONE controller mutex to avoid refresh/login/logout races.
     *
     * The caller must never log or persist the returned token separately.
     * This does NOT grant Pro access, import rights or bypass server quotas.
     */
    suspend fun verifiedAccessTokenForQa(
        verifier: DesktopSupabaseGoogleIdentityVerifier
    ): String = exclusive {
        try {
            val token = keeper.tokenForIsolatedQa()
            verifier.verifyGoogleIdentity(token)
            token
        } catch (cancelled: CancellationException) {
            keeper.clear()
            throw cancelled
        } catch (_: Exception) {
            keeper.clear()
            error("Sessão Google não validada pelo Supabase. Entre novamente.")
        }
    }

    /**
     * Read-only commercial preview: verify Google identity with Supabase Auth,
     * then match that user to server-issued WordPress/WooCommerce entitlement.
     * Does NOT implement DesktopVerifiedFontSession or release a commercial gate.
     */
    suspend fun accountPreviewForQa(
        identityVerifier: DesktopSupabaseGoogleIdentityVerifier,
        entitlementReader: DesktopBrotherEntitlementReader
    ): DesktopServerAccountSnapshot = exclusive {
        try {
            val token = keeper.tokenForIsolatedQa()
            val identity = identityVerifier.verifyGoogleIdentity(token)
            entitlementReader.readOnlyAccount(identity, token)
        } catch (cancelled: CancellationException) {
            keeper.clear()
            throw cancelled
        } catch (temporary: DesktopEntitlementUnavailableException) {
            // /auth/v1/user already confirmed the identity. If ONLY the
            // entitlement service is temporarily unavailable, preserve the
            // DPAPI Google session, but grant NO plan or import rights.
            throw temporary
        } catch (_: Exception) {
            // Mismatched subject, invalid Google identity or malformed data:
            // fail closed and revoke local tokens rather than show Pro.
            keeper.clear()
            error("Não foi possível verificar sua assinatura. Entre novamente.")
        }
    }

    suspend fun logout(): Unit = exclusive {
        keeper.clear()
    }

    private suspend fun <T> exclusive(block: suspend () -> T): T {
        lock.lock()
        try {
            check(!closed) { "Sessão de QA encerrada." }
            return block()
        } finally {
            lock.unlock()
        }
    }

    override fun close() {
        // This class is not intended for simultaneous close and suspending
        // operations. The underlying runtime checks lease validity on access.
        closed = true
        runtime.close()
    }

    override fun toString(): String = "DesktopWindowsAuthQaController(redacted)"

    companion object {
        fun open(
            signIn: DesktopQaSignIn,
            refresher: DesktopSessionRefresher,
            clock: Clock = Clock.systemUTC(),
            folder: Path = DesktopWindowsDpapiSessionStore.defaultUserDataFolder()
        ): DesktopWindowsAuthQaController {
            val runtime = DesktopWindowsLockedSessionRuntime.open(folder = folder, clock = clock)
            return try {
                DesktopWindowsAuthQaController(
                    runtime, signIn, runtime.createKeeper(refresher, clock)
                )
            } catch (failure: Exception) {
                runtime.close()
                throw failure
            }
        }

        /** Only explicit opt-in QA; does not initiate browser login itself. */
        fun googleSignInSource(
            coordinator: DesktopGooglePkceLoginCoordinator
        ): DesktopQaSignIn = DesktopQaSignIn {
            coordinator.signInForIsolatedQa()
        }
    }
}
