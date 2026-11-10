package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopOfficialReleaseNotice
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsNewsAutoCheck
import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsNewsPreferences
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No real network, tray, credentials, startup service or customer files. */
class DesktopWindowsNewsAutoCheckTest {
    private fun withFile(block: suspend (Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("bm-news-consent-qa-")
        try {
            block(root.resolve("preferences").resolve("news.txt"))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder()).forEach {
                    Files.deleteIfExists(it)
                }
            }
        }
    }

    private fun notice(code: Int) = DesktopOfficialReleaseNotice.Notice(
        versionName = "1.11.$code",
        versionCode = code,
        highlights = listOf("Notícia Android de homologação.")
    )

    @Test
    fun offByDefaultAndNeverReadsNetworkOrCreatesSettings() = withFile { file ->
        val prefs = DesktopWindowsNewsPreferences(file)
        var reads = 0
        var displays = 0
        val checker = DesktopWindowsNewsAutoCheck(prefs,
            fetchNews = { reads++; notice(196) },
            display = { _, _ -> displays++; true }
        )
        assertEquals(DesktopWindowsNewsAutoCheck.Outcome.DISABLED, checker.checkOnce())
        assertEquals(0, reads)
        assertEquals(0, displays)
        assertFalse(Files.exists(file))
        assertFalse(Files.exists(file.parent))
    }

    @Test
    fun explicitEnableBaselinesCurrentAndroidVersionAndDeduplicatesAfterRestart() =
        withFile { file ->
            val prefs = DesktopWindowsNewsPreferences(file)
            var published = 195
            var reads = 0
            val shown = mutableListOf<String>()
            val fetch: suspend () -> DesktopOfficialReleaseNotice.Notice = {
                reads++
                notice(published)
            }
            val display: (String, String) -> Boolean = { title, message ->
                shown += "$title | $message"
                true
            }
            val first = DesktopWindowsNewsAutoCheck(prefs, fetch, display)
            assertTrue(first.setEnabled(true))
            assertTrue(Files.isRegularFile(file))
            assertEquals(195, prefs.load().lastNotifiedCode)
            assertEquals(DesktopWindowsNewsAutoCheck.Outcome.UNCHANGED, first.checkOnce())
            assertTrue(shown.isEmpty())

            published = 196
            assertEquals(DesktopWindowsNewsAutoCheck.Outcome.NOTIFIED, first.checkOnce())
            assertEquals(1, shown.size)
            assertTrue(shown.single().contains("Android"))
            assertTrue(shown.single().contains("Windows continua em homologação"))
            val restarted = DesktopWindowsNewsAutoCheck(
                DesktopWindowsNewsPreferences(file), fetch, display
            )
            assertEquals(DesktopWindowsNewsAutoCheck.Outcome.UNCHANGED,
                restarted.checkOnce())
            assertEquals(1, shown.size)
            assertEquals(196, prefs.load().lastNotifiedCode)
            assertTrue(reads >= 4)
        }

    @Test
    fun disablingRemainsPersistentAndPreventsNetworkOrNotifications() = withFile { file ->
        val prefs = DesktopWindowsNewsPreferences(file)
        var requests = 0
        var displayed = 0
        val runner = DesktopWindowsNewsAutoCheck(prefs,
            fetchNews = { requests++; notice(195) },
            display = { _, _ -> displayed++; true }
        )
        runner.setEnabled(true)
        assertEquals(1, requests)
        assertFalse(runner.setEnabled(false))
        assertFalse(prefs.load().enabled)
        assertEquals(195, prefs.load().lastNotifiedCode)
        assertEquals(DesktopWindowsNewsAutoCheck.Outcome.DISABLED,
            DesktopWindowsNewsAutoCheck(
                DesktopWindowsNewsPreferences(file),
                fetchNews = { requests++; notice(196) },
                display = { _, _ -> displayed++; true }
            ).checkOnce()
        )
        assertEquals(1, requests)
        assertEquals(0, displayed)
    }

    @Test
    fun failedActivationDoesNotSaveConsentAndTrayFailureIsNotSpamLoop() = withFile { file ->
        val prefs = DesktopWindowsNewsPreferences(file)
        val offline = DesktopWindowsNewsAutoCheck(prefs,
            fetchNews = { error("No Internet in QA") },
            display = { _, _ -> error("Unexpected display") }
        )
        assertFailsWith<IllegalStateException> { offline.setEnabled(true) }
        assertFalse(Files.exists(file))

        prefs.save(DesktopWindowsNewsPreferences.State(true, 195))
        var displays = 0
        val noTray = DesktopWindowsNewsAutoCheck(prefs,
            fetchNews = { notice(196) },
            display = { _, _ -> displays++; false }
        )
        assertEquals(DesktopWindowsNewsAutoCheck.Outcome.SYSTEM_UNAVAILABLE,
            noTray.checkOnce())
        assertEquals(196, prefs.load().lastNotifiedCode)
        assertEquals(DesktopWindowsNewsAutoCheck.Outcome.UNCHANGED,
            noTray.checkOnce())
        assertEquals(1, displays)
    }

    @Test
    fun concurrentDisableDuringFetchAbortsWithoutMarkingOrDisplaying() =
        withFile { file ->
            val prefs = DesktopWindowsNewsPreferences(file)
            prefs.save(DesktopWindowsNewsPreferences.State(true, 195))
            var displayed = 0
            val poller = DesktopWindowsNewsAutoCheck(
                prefs,
                fetchNews = {
                    prefs.save(DesktopWindowsNewsPreferences.State(false, 195))
                    notice(196)
                },
                display = { _, _ -> displayed++; true }
            )
            assertEquals(DesktopWindowsNewsAutoCheck.Outcome.DISABLED,
                poller.checkOnce())
            assertEquals(0, displayed)
            assertEquals(195, prefs.load().lastNotifiedCode)
        }

    @Test
    fun malformedOrSymlinkedSettingsCannotSilentlyEnableNotifications() =
        withFile { file ->
            Files.createDirectories(file.parent)
            Files.writeString(file, "invalid")
            assertFailsWith<IllegalStateException> {
                DesktopWindowsNewsPreferences(file).load()
            }
            Files.writeString(
                file,
                "BROTHER_MATRIZES_WINDOWS_NEWS_V1\nenabled=1\n" +
                    "lastNotifiedCode=-1\n"
            )
            assertFailsWith<IllegalStateException> {
                DesktopWindowsNewsPreferences(file).load()
            }
            Files.delete(file)
            val other = Files.createTempFile(file.parent, "target-", ".txt")
            try {
                val linked = runCatching { Files.createSymbolicLink(file, other) }.isSuccess
                if (linked) {
                    assertFailsWith<IllegalStateException> {
                        DesktopWindowsNewsPreferences(file).save(
                            DesktopWindowsNewsPreferences.State(true, 195)
                        )
                    }
                    assertTrue(Files.isSymbolicLink(file))
                }
            } finally {
                Files.deleteIfExists(file)
                Files.deleteIfExists(other)
            }
        }
}
