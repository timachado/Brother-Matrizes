package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopSessionRefresher
import com.timachado.brothermatrizes.desktop.platform.DesktopTransientAuthSession
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsLockedSessionRuntime
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Native Windows OS file locks + DPAPI. Fake tokens only; no network, Google
 * consent or production Supabase access.
 */
class DesktopWindowsLockedSessionRuntimeTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"), ZoneOffset.UTC)
    private val access = "offline_access_LOCKED_123456789012345"
    private val refresh = "offline_refresh_LOCKED_98765432109876"

    private fun onWindows(block: (Path) -> Unit) {
        assumeTrue(
            System.getProperty("os.name").startsWith("Windows", true),
            "Native Windows FileLock / DPAPI test only."
        )
        val folder = Files.createTempDirectory("bm-exclusive-qa-")
        try {
            block(folder)
        } finally {
            Files.walk(folder).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test
    fun preventsTwoWindowsInstancesAndAllowsControlledRestart() = onWindows { folder ->
        val first = DesktopWindowsLockedSessionRuntime.open(folder, clock)
        val firstKeeper = first.createKeeper(
            DesktopSessionRefresher { error("Unexpected network request") }, clock
        )
        try {
            runBlocking {
                firstKeeper.install(DesktopTransientAuthSession(access, refresh, 3600))
                assertEquals(access, firstKeeper.tokenForIsolatedQa())
            }
            val error = assertFailsWith<IllegalStateException> {
                DesktopWindowsLockedSessionRuntime.open(folder, clock)
            }
            assertTrue(error.message.orEmpty().contains("Outra instância"))
            assertFalse(error.message.orEmpty().contains(refresh))
            assertTrue(Files.isRegularFile(
                folder.resolve("session.v1.dpapi.lock"), LinkOption.NOFOLLOW_LINKS
            ))
        } finally {
            first.close()
            first.close() // idempotent
        }

        runBlocking {
            // Even a previously cached token cannot be used after releasing OS lock.
            assertFailsWith<IllegalStateException> { firstKeeper.tokenForIsolatedQa() }
        }

        DesktopWindowsLockedSessionRuntime.open(folder, clock).use { second ->
            val keeper = second.createKeeper(
                DesktopSessionRefresher { error("Unexpected network request") }, clock
            )
            runBlocking {
                assertTrue(keeper.restoreFromProtectedStorage())
                assertEquals(access, keeper.tokenForIsolatedQa())
                keeper.clear()
                assertFalse(keeper.restoreFromProtectedStorage())
            }
        }
        // Keeping the lock file on disk avoids deleting a locked inode.
        assertTrue(Files.isRegularFile(folder.resolve("session.v1.dpapi.lock")))
        assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
    }

    @Test
    fun rejectsUnexpectedLockFileAndRefusesTokenAccessAfterShutdown() = onWindows { folder ->
        Files.createDirectory(folder.resolve("session.v1.dpapi.lock"))
        assertFailsWith<IllegalStateException> {
            DesktopWindowsLockedSessionRuntime.open(folder, clock)
        }
        Files.delete(folder.resolve("session.v1.dpapi.lock"))
        val runtime = DesktopWindowsLockedSessionRuntime.open(folder, clock)
        val keeper = runtime.createKeeper(
            DesktopSessionRefresher { error("Never refresh") }, clock
        )
        runtime.close()
        runBlocking {
            assertFailsWith<IllegalStateException> {
                keeper.install(DesktopTransientAuthSession(access, refresh, 3600))
            }
            assertFailsWith<IllegalStateException> {
                keeper.restoreFromProtectedStorage()
            }
            assertFailsWith<IllegalStateException> { keeper.clear() }
        }
        assertFailsWith<IllegalStateException> {
            runtime.createKeeper(DesktopSessionRefresher { error("Never refresh") }, clock)
        }
    }

    @Test
    fun refusesInvalidFoldersAndNonWindowsOperatingSystems() {
        if (!System.getProperty("os.name").startsWith("Windows", true)) {
            assertFailsWith<IllegalStateException> {
                DesktopWindowsLockedSessionRuntime.open(Path.of("relative"))
            }
            return
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopWindowsLockedSessionRuntime.open(Path.of("relative"), clock)
        }
    }
}
