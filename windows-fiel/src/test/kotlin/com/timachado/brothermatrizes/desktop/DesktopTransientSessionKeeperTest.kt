package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopSessionRefresher
import com.timachado.brothermatrizes.desktop.platform.DesktopTransientAuthSession
import com.timachado.brothermatrizes.desktop.platform.DesktopTransientSessionKeeper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real coroutines, fake tokens: no production network and no Windows secrets. */
class DesktopTransientSessionKeeperTest {
    private val initialAccess = "offline_old_access_1234567890"
    private val initialRefresh = "offline_old_refresh_1234567890"
    private val newAccess = "offline_new_access_1234567890"
    private val newRefresh = "offline_new_refresh_1234567890"
    private val clock = Clock.fixed(Instant.parse("2026-11-07T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun serializesParallelRefreshCallsIntoOneTokenRotation() = runBlocking {
        val count = AtomicInteger(0)
        val keeper = DesktopTransientSessionKeeper(
            DesktopSessionRefresher { previous ->
                assertEquals(initialRefresh, previous.refreshToken)
                count.incrementAndGet()
                delay(70)
                DesktopTransientAuthSession(newAccess, newRefresh, 3600)
            },
            clock
        )
        keeper.install(DesktopTransientAuthSession(initialAccess, initialRefresh, 15))
        val result = withTimeout(20_000) {
            (1..12).map {
                async(Dispatchers.Default) { keeper.tokenForIsolatedQa() }
            }.awaitAll()
        }
        assertEquals(List(12) { newAccess }, result)
        assertEquals(1, count.get())
        assertFalse(keeper.toString().contains(initialRefresh))
        assertFalse(keeper.toString().contains(newAccess))
        keeper.clear()
        assertFailsWith<IllegalStateException> { keeper.tokenForIsolatedQa() }
    }

    @Test
    fun doesNotRefreshValidSessionAndRejectsAbsentLogin() = runBlocking {
        val keeper = DesktopTransientSessionKeeper(
            DesktopSessionRefresher { error("Refresh must not be called") },
            clock
        )
        assertFailsWith<IllegalStateException> { keeper.tokenForIsolatedQa() }
        keeper.install(DesktopTransientAuthSession(initialAccess, initialRefresh, 3600))
        assertEquals(initialAccess, keeper.tokenForIsolatedQa())
    }

    @Test
    fun refreshFailuresInvalidateSessionInsteadOfGrantingStaleAccess() = runBlocking {
        val keeper = DesktopTransientSessionKeeper(
            DesktopSessionRefresher { error("Server rejected token") },
            clock
        )
        keeper.install(DesktopTransientAuthSession(initialAccess, initialRefresh, 5))
        val first = assertFailsWith<IllegalStateException> {
            keeper.tokenForIsolatedQa()
        }
        assertTrue(first.message.orEmpty().contains("Entre novamente"))
        assertFalse(first.message.orEmpty().contains(initialRefresh))
        assertFailsWith<IllegalStateException> { keeper.tokenForIsolatedQa() }
    }

    @Test
    fun refusesInvalidSessionLifetimeBeforeSavingInMemory() = runBlocking {
        val keeper = DesktopTransientSessionKeeper(
            DesktopSessionRefresher { error("Do not refresh") },
            clock
        )
        assertFailsWith<IllegalArgumentException> {
            keeper.install(DesktopTransientAuthSession(initialAccess, initialRefresh, 0))
        }
        assertFailsWith<IllegalArgumentException> {
            keeper.install(DesktopTransientAuthSession(initialAccess, initialRefresh, 86_401))
        }
        assertFailsWith<IllegalStateException> { keeper.tokenForIsolatedQa() }
    }
}
