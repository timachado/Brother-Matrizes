package com.timachado.brothermatrizes.desktop.platform

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/** Callback adapter: keep HTTP refresh separate from in-memory session state. */
internal fun interface DesktopSessionRefresher {
    suspend fun refresh(previous: DesktopTransientAuthSession): DesktopTransientAuthSession
}

/**
 * QA session coordinator. Holds tokens only in process memory, never on disk.
 *
 * Refresh is serialized so parallel consumers do not try to rotate the same
 * Supabase refresh token. On a failure the session is immediately forgotten:
 * no stale access token, Pro entitlement or local quota fallback is granted.
 *
 * This is intentionally NOT a DesktopVerifiedFontSession or a licensing gate.
 * A verified account and secure Windows token storage are still missing.
 */
internal class DesktopTransientSessionKeeper(
    private val refresh: DesktopSessionRefresher,
    private val clock: Clock = Clock.systemUTC()
) {
    private val lock = Mutex()
    private var current: DesktopTransientAuthSession? = null
    private var expiresAt: Instant? = null

    suspend fun install(session: DesktopTransientAuthSession) {
        lock.lock()
        try {
            require(session.expiresInSeconds in 1..86_400) {
                "Validade de sessão inválida."
            }
            current = session
            expiresAt = clock.instant().plusSeconds(session.expiresInSeconds.toLong())
        } finally {
            lock.unlock()
        }
    }

    suspend fun tokenForIsolatedQa(): String {
        lock.lock()
        try {
            val previous = checkNotNull(current) {
                "Entre novamente na sua conta."
            }
            val expiry = checkNotNull(expiresAt)
            if (!clock.instant().plusSeconds(30).isBefore(expiry)) {
                try {
                    val rotated = refresh.refresh(previous)
                    check(rotated.expiresInSeconds in 1..86_400) {
                        "Servidor retornou uma sessão inválida."
                    }
                    current = rotated
                    expiresAt = clock.instant().plusSeconds(rotated.expiresInSeconds.toLong())
                } catch (cancelled: CancellationException) {
                    current = null
                    expiresAt = null
                    throw cancelled
                } catch (_: Exception) {
                    // Reuse is unsafe if refresh succeeded remotely but reply was lost.
                    current = null
                    expiresAt = null
                    error("Não foi possível renovar a sessão. Entre novamente.")
                }
            }
            return checkNotNull(current).accessToken
        } finally {
            lock.unlock()
        }
    }

    suspend fun clear() {
        lock.lock()
        try {
            current = null
            expiresAt = null
        } finally {
            lock.unlock()
        }
    }

    override fun toString(): String = "DesktopTransientSessionKeeper(redacted)"
}
