package com.timachado.brothermatrizes.desktop.platform

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/** Injectable refresh adapter; all commercial authorization stays on the server. */
internal fun interface DesktopSessionRefresher {
    suspend fun refresh(previous: DesktopTransientAuthSession): DesktopTransientAuthSession
}

/**
 * QA session coordinator with optional encrypted DPAPI persistence.
 *
 * With a vault, the old refresh token is removed from disk BEFORE any refresh
 * request, so a process crash cannot accidentally recover a token the server
 * may already have rotated. A replacement is saved before exposing its
 * access token to consumers. Failures leave this instance unauthenticated.
 *
 * Mutex protects concurrency only within this keeper instance, NOT multiple
 * application processes. Do not connect to commercial UI until cross-process
 * session ownership, Google QA and server entitlement checks are homologated.
 */
internal class DesktopTransientSessionKeeper(
    private val refresh: DesktopSessionRefresher,
    private val clock: Clock = Clock.systemUTC(),
    private val vault: DesktopProtectedSessionVault? = null,
    private val sessionGuard: () -> Unit = {}
) {
    private val lock = Mutex()
    private var current: DesktopTransientAuthSession? = null
    private var expiresAt: Instant? = null

    suspend fun install(session: DesktopTransientAuthSession) {
        lock.lock()
        try {
            sessionGuard()
            current = null
            expiresAt = null
            require(session.expiresInSeconds in 1..86_400) {
                "Validade de sessão inválida."
            }
            // Clear a previous account before installing a new one.
            vault?.clear()
            vault?.save(session)
            current = session
            expiresAt = clock.instant().plusSeconds(session.expiresInSeconds.toLong())
        } finally {
            lock.unlock()
        }
    }

    /** Explicit startup recovery; does not grant license or Pro features. */
    suspend fun restoreFromProtectedStorage(): Boolean {
        lock.lock()
        try {
            sessionGuard()
            current = null
            expiresAt = null
            val snapshot = vault?.load() ?: return false
            check(snapshot.session.expiresInSeconds in 1..86_400 &&
                !snapshot.expiresAt.isAfter(clock.instant().plusSeconds(86_400))) {
                "Sessão protegida fora da validade."
            }
            current = snapshot.session
            // Preserve original expiry, not an artificially renewed lifetime.
            expiresAt = snapshot.expiresAt
            return true
        } finally {
            lock.unlock()
        }
    }

    suspend fun tokenForIsolatedQa(): String {
        lock.lock()
        try {
            sessionGuard()
            val previous = checkNotNull(current) {
                "Entre novamente na sua conta."
            }
            val expiry = checkNotNull(expiresAt)
            if (!clock.instant().plusSeconds(30).isBefore(expiry)) {
                // Invalidate memory first; do not hand out an old token on failure.
                current = null
                expiresAt = null
                try {
                    // CRITICAL: invalidate persisted old refresh BEFORE remote rotate.
                    // If the response is lost, a restarted process cannot replay it.
                    vault?.clear()
                    val rotated = refresh.refresh(previous)
                    check(rotated.expiresInSeconds in 1..86_400) {
                        "Servidor retornou uma sessão inválida."
                    }
                    // Publish only after the encrypted replacement is durable.
                    vault?.save(rotated)
                    current = rotated
                    expiresAt = clock.instant().plusSeconds(rotated.expiresInSeconds.toLong())
                } catch (cancelled: CancellationException) {
                    current = null
                    expiresAt = null
                    throw cancelled
                } catch (_: Exception) {
                    current = null
                    expiresAt = null
                    error("Não foi possível renovar a sessão. Entre novamente.")
                }
            }
            // A revoked exclusive OS lease must never expose a cached token.
            sessionGuard()
            return checkNotNull(current).accessToken
        } finally {
            lock.unlock()
        }
    }

    suspend fun clear() {
        lock.lock()
        try {
            sessionGuard()
            current = null
            expiresAt = null
            vault?.clear()
        } finally {
            lock.unlock()
        }
    }

    override fun toString(): String = "DesktopTransientSessionKeeper(redacted)"
}
