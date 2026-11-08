package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsShareHandoff
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No Explorer, Windows UI, network or external program runs in these tests. */
class DesktopWindowsShareHandoffTest {

    @Test
    fun selectsAlreadySavedDesignInExplorerWithoutSendingOrModifyingBytes() {
        val folder = Files.createTempDirectory("brother-windows-share-")
        try {
            for (format in listOf("pes", "dst", "jef", "flp")) {
                val file = folder.resolve("Maria bordado.$format")
                val original = byteArrayOf(7, 11, 19, 23)
                Files.write(file, original)
                val launched = mutableListOf<List<String>>()
                val selected = DesktopWindowsShareHandoff.revealSavedMatrix(
                    file, windowsName = "Windows 11",
                    launchExplorer = { cmd -> launched += cmd }
                )
                assertEquals(file.toAbsolutePath().normalize(), selected)
                assertEquals(1, launched.size)
                assertEquals("explorer.exe", launched.single()[0])
                assertEquals("/select,${selected}", launched.single()[1])
                assertEquals(2, launched.single().size)
                assertTrue(original.contentEquals(Files.readAllBytes(file)))
            }
        } finally {
            Files.list(folder).use { stream -> stream.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun refusesMissingWrongFormatAndNonWindowsWithoutLaunchingExplorer() {
        val folder = Files.createTempDirectory("brother-windows-share-invalid-")
        try {
            val file = folder.resolve("Maria.pes")
            Files.write(file, byteArrayOf(1, 2, 3))
            var launched = 0
            val launch: (List<String>) -> Unit = { launched++ }
            assertFailsWith<IllegalStateException> {
                DesktopWindowsShareHandoff.revealSavedMatrix(
                    file, windowsName = "Linux", launchExplorer = launch
                )
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopWindowsShareHandoff.revealSavedMatrix(
                    folder.resolve("missing.pes"), windowsName = "Windows 10",
                    launchExplorer = launch
                )
            }
            val bad = folder.resolve("malicious.cmd")
            Files.write(bad, byteArrayOf(10))
            assertFailsWith<IllegalArgumentException> {
                DesktopWindowsShareHandoff.revealSavedMatrix(
                    bad, windowsName = "Windows 10", launchExplorer = launch
                )
            }
            assertEquals(0, launched)
            assertTrue(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            assertTrue(byteArrayOf(1, 2, 3).contentEquals(Files.readAllBytes(file)))
        } finally {
            Files.list(folder).use { stream -> stream.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun rejectsSymlinksWithoutLaunchingExternalProgramsWhenSupported() {
        val folder = Files.createTempDirectory("brother-windows-share-link-")
        try {
            val real = folder.resolve("real.pes")
            val link = folder.resolve("shortcut.pes")
            Files.write(real, byteArrayOf(3))
            val created = runCatching { Files.createSymbolicLink(link, real.fileName) }.isSuccess
            var launched = false
            if (created) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopWindowsShareHandoff.revealSavedMatrix(
                        link, windowsName = "Windows 11",
                        launchExplorer = { launched = true }
                    )
                }
                assertFalse(launched)
            }
        } finally {
            Files.list(folder).use { stream -> stream.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(folder)
        }
    }
}
