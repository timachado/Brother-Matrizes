package com.timachado.brothermatrizes.desktop.platform

import java.io.Closeable
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
        // Existing credentials are invalidated before a new OAuth attempt.
        keeper.clear()
        val session = signIn.signIn()
        keeper.install(session)
    }

    suspend fun accessTokenForQa(): String = exclusive {
        keeper.tokenForIsolatedQa()
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
            clock: Clock = Clock.systemUTC()
        ): DesktopWindowsAuthQaController {
            val runtime = DesktopWindowsLockedSessionRuntime.open(clock = clock)
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
