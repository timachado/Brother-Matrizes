package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialWindowsReleaseNotice
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsNewsPreferences
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsOfficialReleaseAutoCheck
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Synthetic release metadata and a fake notification transport only.
 * No real HTTP, FCM, Windows tray, Google account, quota or installation.
 */
class DesktopWindowsOfficialReleaseAutoCheckTest {
    private fun fixture(block: suspend (Path, Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("bm-windows-release-notification-qa-")
        try {
            block(root.resolve("android-news.txt"), root.resolve("windows-releases.txt"))
        } finally {
            Files.walk(root).use { entries ->
                entries.sorted(java.util.Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }

    private fun release(code: Int) = DesktopOfficialWindowsReleaseNotice.Notice(
        versionName = "1.0.$code",
        versionCode = code,
        releaseTag = "windows-v1.0.$code",
        highlights = listOf("Melhorias comerciais de teste.")
    )

    @Test
    fun disabledByDefaultDoesNotCallNetworkOrCreateFiles() = fixture { android, windows ->
        val store = DesktopWindowsNewsPreferences(windows)
        var network = 0
        var sends = 0
        val checker = DesktopWindowsOfficialReleaseAutoCheck(
            store,
            fetch = { network++; release(100) },
            notify = { _, _ -> sends++; true }
        )
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.DISABLED, checker.checkOnce())
        assertEquals(0, network)
        assertEquals(0, sends)
        assertFalse(Files.exists(windows))
        assertFalse(Files.exists(android))
    }

    @Test
    fun missingManifestAfterConsentNeverAnnouncesWindowsRelease() = fixture { _, windows ->
        val store = DesktopWindowsNewsPreferences(windows)
        var network = 0
        var sends = 0
        val checker = DesktopWindowsOfficialReleaseAutoCheck(
            store,
            fetch = { network++; null },
            notify = { _, _ -> sends++; true }
        )
        assertTrue(checker.setEnabled(true))
        assertTrue(store.load().enabled)
        assertEquals(0, store.load().lastNotifiedCode)
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.NOT_PUBLISHED,
            checker.checkOnce())
        assertEquals(2, network)
        assertEquals(0, sends)
    }

    @Test
    fun onlyNewWindowsReleaseNotifiesAndIsDedupedAfterRestart() = fixture { _, windows ->
        val store = DesktopWindowsNewsPreferences(windows)
        var published = 100
        var sends = 0
        val messages = mutableListOf<String>()
        val fetch: suspend () -> DesktopOfficialWindowsReleaseNotice.Notice? = {
            release(published)
        }
        val notifier: (String, String) -> Boolean = { title, message ->
            sends++
            messages += "$title: $message"
            true
        }
        val checker = DesktopWindowsOfficialReleaseAutoCheck(store, fetch, notifier)
        assertTrue(checker.setEnabled(true))
        assertEquals(100, store.load().lastNotifiedCode)
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.UNCHANGED, checker.checkOnce())
        assertEquals(0, sends)

        published = 101
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.NOTIFIED, checker.checkOnce())
        assertEquals(1, sends)
        assertTrue(messages.single().contains("Windows"))
        assertFalse(messages.single().contains("Android"))
        assertEquals(101, store.load().lastNotifiedCode)

        val restarted = DesktopWindowsOfficialReleaseAutoCheck(
            DesktopWindowsNewsPreferences(windows), fetch, notifier
        )
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.UNCHANGED,
            restarted.checkOnce())
        assertEquals(1, sends)

        published = 99
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.UNCHANGED,
            restarted.checkOnce())
        assertEquals(101, store.load().lastNotifiedCode)
    }

    @Test
    fun androidNewsPreferenceDoesNotEnableWindowsAlerts() = fixture { android, windows ->
        val androidStore = DesktopWindowsNewsPreferences(android)
        val windowsStore = DesktopWindowsNewsPreferences(windows)
        androidStore.save(DesktopWindowsNewsPreferences.State(enabled = true, lastNotifiedCode = 195))
        var network = 0
        val checker = DesktopWindowsOfficialReleaseAutoCheck(
            windowsStore,
            fetch = { network++; release(100) },
            notify = { _, _ -> error("No Windows consent") }
        )
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.DISABLED, checker.checkOnce())
        assertEquals(0, network)
        assertFalse(Files.exists(windows))
        assertEquals(195, androidStore.load().lastNotifiedCode)
        assertTrue(checker.setEnabled(true))
        assertEquals(100, windowsStore.load().lastNotifiedCode)
        assertEquals(195, androidStore.load().lastNotifiedCode)
        checker.setEnabled(false)
        assertFalse(windowsStore.load().enabled)
        assertTrue(androidStore.load().enabled)
        assertEquals(1, network)
    }

    @Test
    fun offlineActivationMustFailWithoutChangingConsent() = fixture { _, windows ->
        val store = DesktopWindowsNewsPreferences(windows)
        val checker = DesktopWindowsOfficialReleaseAutoCheck(
            store, fetch = { error("Simulated offline") },
            notify = { _, _ -> error("No displayed alert") }
        )
        assertFailsWith<IllegalStateException> { checker.setEnabled(true) }
        assertFalse(store.load().enabled)
        assertFalse(Files.exists(windows))
    }

    @Test
    fun disablingDuringNetworkWaitBlocksDisplayAndLeavesMarker() = fixture { _, windows ->
        val store = DesktopWindowsNewsPreferences(windows)
        store.save(DesktopWindowsNewsPreferences.State(enabled = true, lastNotifiedCode = 100))
        var sends = 0
        val checker = DesktopWindowsOfficialReleaseAutoCheck(
            store,
            fetch = {
                store.save(DesktopWindowsNewsPreferences.State(false, 100))
                release(101)
            },
            notify = { _, _ -> sends++; true }
        )
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.DISABLED, checker.checkOnce())
        assertEquals(0, sends)
        assertEquals(100, store.load().lastNotifiedCode)
    }

    @Test
    fun trayUnavailableMarksNewVersionAndDoesNotSpamAgain() = fixture { _, windows ->
        val store = DesktopWindowsNewsPreferences(windows)
        store.save(DesktopWindowsNewsPreferences.State(true, 100))
        var sends = 0
        val checker = DesktopWindowsOfficialReleaseAutoCheck(
            store,
            fetch = { release(101) },
            notify = { _, _ -> sends++; false }
        )
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.SYSTEM_UNAVAILABLE,
            checker.checkOnce())
        assertEquals(101, store.load().lastNotifiedCode)
        assertEquals(DesktopWindowsOfficialReleaseAutoCheck.Outcome.UNCHANGED,
            checker.checkOnce())
        assertEquals(1, sends)
    }
}
