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
        val identity = verifier.verifyGoogleIdentity(session.accessToken)
        keeper.install(session)
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
