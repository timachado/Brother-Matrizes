package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopWindowsPortableShortcuts
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopWindowsPortableShortcutsTest {
    @Test
    fun requiresCompleteAppImageBeforeAnyShortcutIsCreated() {
        val root = Files.createTempDirectory("bm-shortcut-validation-")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopWindowsPortableShortcuts.scriptForRoot(root)
            }
            Files.createFile(root.resolve("BrotherMatrizes.exe"))
            Files.createFile(root.resolve(DesktopWindowsPortableShortcuts.SCRIPT))
            Files.createFile(root.resolve("LEIA-ME-QA.txt"))
            Files.createDirectory(root.resolve("app"))
            assertFailsWith<IllegalArgumentException> {
                DesktopWindowsPortableShortcuts.scriptForRoot(root)
            }
            Files.createDirectory(root.resolve("runtime"))
            assertEquals(
                root.resolve(DesktopWindowsPortableShortcuts.SCRIPT),
                DesktopWindowsPortableShortcuts.scriptForRoot(root)
            )
            // No Windows shortcut or process is created by this offline test.
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
    }
}
