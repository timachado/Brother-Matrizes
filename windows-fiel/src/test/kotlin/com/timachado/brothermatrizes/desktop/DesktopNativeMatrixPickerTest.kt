package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopNativeMatrixPicker
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import javax.swing.JFileChooser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNativeMatrixPickerTest {
    @Test
    fun windowsChooserOffersEveryAndroidEmbroideryFormat() {
        val picker = DesktopNativeMatrixPicker.configuredChooser()
        assertEquals(DesktopNativeMatrixPicker.TITLE, picker.dialogTitle)
        assertEquals(JFileChooser.FILES_ONLY, picker.fileSelectionMode)
        assertFalse(picker.isAcceptAllFileFilterUsed)
        for (ext in listOf("pes", "PES", "dst", "jef", "flp")) {
            assertTrue(picker.fileFilter.accept(File("design.$ext")), ext)
        }
        for (ext in listOf("exe", "bat", "zip", "pdf")) {
            assertFalse(picker.fileFilter.accept(File("design.$ext")), ext)
        }
    }

    @Test
    fun invalidSelectionNeverChangesLocalFilesOrBypassesFilter() {
        val root = Files.createTempDirectory("bm-open-matrix-")
        try {
            val valid = root.resolve("example.pes")
            val unsafe = root.resolve("program.exe")
            val empty = root.resolve("empty.dst")
            Files.write(valid, byteArrayOf(1, 2, 3))
            Files.write(unsafe, byteArrayOf(5, 6, 7))
            Files.createFile(empty)
            assertEquals(valid, DesktopNativeMatrixPicker.validateSelectedFile(valid))
            for (file in listOf(unsafe, empty, root.resolve("missing.jef"))) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopNativeMatrixPicker.validateSelectedFile(file)
                }
            }
            assertEquals(3L, Files.size(valid))
            assertEquals(3L, Files.size(unsafe))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
    }
}
