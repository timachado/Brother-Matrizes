package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopTransientAuthSession
import com.timachado.brothermatrizes.desktop.platform.DesktopTransientSessionKeeper
import com.timachado.brothermatrizes.desktop.platform.DesktopSessionRefresher
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsDpapiSessionStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Comparator
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uses native DPAPI under GitHub Windows runner. No real account, network or
 * commercial license; disposable fake tokens in a temporary Windows folder.
 */
class DesktopWindowsDpapiSessionStoreTest {
    private val access = "offline_access_DPAPI_123456789012345"
    private val refresh = "offline_refresh_DPAPI_98765432109876"
    private val at = Instant.parse("2026-11-07T01:00:00Z")
    private val clock = Clock.fixed(at, ZoneOffset.UTC)

    private fun windowsOnly() = assumeTrue(
        System.getProperty("os.name").startsWith("Windows", true),
        "Native DPAPI testing requires Windows."
    )

    private fun withFolder(block: (Path) -> Unit) {
        windowsOnly()
        val folder = Files.createTempDirectory("bm-dpapi-isolated-qa-")
        try {
            block(folder)
        } finally {
            Files.walk(folder).use { entries ->
                entries.sorted(Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }

    @Test
    fun protectsTokensWithWindowsCurrentUserAndReloadsWithoutPlaintext() = withFolder { folder ->
        val vault = DesktopWindowsDpapiSessionStore(folder, clock)
        assertNull(vault.load())
        val session = DesktopTransientAuthSession(access, refresh, 3600)
        vault.save(session)
        val target = folder.resolve("session.v1.dpapi")
        assertTrue(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
        val ciphertext = Files.readAllBytes(target)
        assertTrue(ciphertext.size in 1..65_536)
        val contents = ciphertext.toString(Charsets.ISO_8859_1)
        assertFalse(contents.contains(access))
        assertFalse(contents.contains(refresh))
        assertFalse(contents.contains("access_token"))
        val snapshot = assertNotNull(
            DesktopWindowsDpapiSessionStore(folder, clock).load()
        )
        assertEquals(access, snapshot.session.accessToken)
        assertEquals(refresh, snapshot.session.refreshToken)
        assertEquals(at.plusSeconds(3600), snapshot.expiresAt)
        assertEquals(3600, snapshot.session.expiresInSeconds)
        assertFalse(snapshot.toString().contains(access))
        assertFalse(snapshot.toString().contains(refresh))
        assertFalse(vault.toString().contains(refresh))
        vault.clear()
        assertFalse(Files.exists(target))
        assertNull(vault.load())
    }

    @Test
    fun legacySupabaseRefreshTokenSurvivesEncryptedSaveAndRestore() = withFolder { folder ->
        val legacy = "ab12cd34ef56"
        val vault = DesktopWindowsDpapiSessionStore(folder, clock)
        vault.save(DesktopTransientAuthSession(access, legacy, 3600))
        val stored = Files.readAllBytes(folder.resolve("session.v1.dpapi"))
        assertFalse(stored.toString(Charsets.ISO_8859_1).contains(legacy))
        assertEquals(legacy, assertNotNull(vault.load()).session.refreshToken)
        vault.clear()
    }

    @Test
    fun rejectsTamperingAndNeverFallsBackToUnencryptedData() = withFolder { folder ->
        val vault = DesktopWindowsDpapiSessionStore(folder, clock)
        vault.save(DesktopTransientAuthSession(access, refresh, 300))
        val target = folder.resolve("session.v1.dpapi")
        val bytes = Files.readAllBytes(target)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x5a).toByte()
        Files.write(target, bytes)
        val failure = assertFailsWith<Exception> { vault.load() }
        assertFalse(failure.message.orEmpty().contains(access))
        assertFalse(failure.message.orEmpty().contains(refresh))
        vault.clear()
        assertNull(vault.load())
    }

    @Test
    fun invalidSessionNeverWritesTokenFile() = withFolder { folder ->
        val vault = DesktopWindowsDpapiSessionStore(folder, clock)
        assertFailsWith<IllegalArgumentException> {
            vault.save(DesktopTransientAuthSession(access, "short", 3600))
        }
        assertFailsWith<IllegalArgumentException> {
            vault.save(DesktopTransientAuthSession(access, refresh, 0))
        }
        assertFailsWith<IllegalArgumentException> {
            vault.save(DesktopTransientAuthSession(access, refresh, 86_401))
        }
        assertFalse(Files.exists(folder.resolve("session.v1.dpapi")))
    }

    @Test
    fun overwritesPreviousCiphertextAtomicallyWithRotatedRefreshToken() = withFolder { folder ->
        val vault = DesktopWindowsDpapiSessionStore(folder, clock)
        vault.save(DesktopTransientAuthSession(access, refresh, 3600))
        val rotated = "offline_rotated_refresh_DPAPI_3344556677"
        vault.save(DesktopTransientAuthSession(access, rotated, 1800))
        val restored = assertNotNull(vault.load())
        assertEquals(rotated, restored.session.refreshToken)
        assertEquals(at.plusSeconds(1800), restored.expiresAt)
        assertEquals(1L, Files.list(folder).use { it.count() })
    }

    @Test
    fun refusesUnexpectedDirectoryAndNonRegularSessionFile() = withFolder { folder ->
        val vault = DesktopWindowsDpapiSessionStore(folder, clock)
        Files.createDirectory(folder.resolve("session.v1.dpapi"))
        assertFailsWith<IllegalStateException> {
            vault.save(DesktopTransientAuthSession(access, refresh, 300))
        }
        assertFailsWith<IllegalStateException> { vault.load() }
        assertFailsWith<IllegalStateException> { vault.clear() }
    }

    @Test
    fun encryptedVaultRestoresKeeperAfterRestartAndLogoutClearsFile() = withFolder { folder ->
        runBlocking {
            val vault = DesktopWindowsDpapiSessionStore(folder, clock)
            val initial = DesktopTransientSessionKeeper(
                DesktopSessionRefresher { error("Unexpected network") }, clock, vault
            )
            initial.install(DesktopTransientAuthSession(access, refresh, 3600))
            val sessionFile = folder.resolve("session.v1.dpapi")
            val diskBytes = Files.readAllBytes(sessionFile)
            assertFalse(diskBytes.toString(Charsets.ISO_8859_1).contains(refresh))

            val rebooted = DesktopTransientSessionKeeper(
                DesktopSessionRefresher { error("No refresh expected") }, clock, vault
            )
            assertTrue(rebooted.restoreFromProtectedStorage())
            assertEquals(access, rebooted.tokenForIsolatedQa())
            assertFalse(rebooted.toString().contains(refresh))
            rebooted.clear()
            assertFalse(Files.exists(sessionFile))
            assertFalse(rebooted.restoreFromProtectedStorage())
            assertFailsWith<IllegalStateException> { rebooted.tokenForIsolatedQa() }
        }
    }

}
