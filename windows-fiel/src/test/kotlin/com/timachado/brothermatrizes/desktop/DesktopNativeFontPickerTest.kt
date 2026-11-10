package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopNativeFontPicker
import java.io.File
import javax.swing.JFileChooser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No browser, downloaded font, OAuth, server quota or modal UI is invoked. */
class DesktopNativeFontPickerTest {
    @Test
    fun dafontAndNetfontesTTFOTFChooserRejectsExecutableAndArchives() {
        val picker = DesktopNativeFontPicker.configuredFontChooser(
            "Analisar TTF/OTF sem importar fonte"
        )
        assertEquals(JFileChooser.FILES_ONLY, picker.fileSelectionMode)
        assertFalse(picker.isAcceptAllFileFilterUsed)
        for (ext in listOf("ttf", "TTF", "otf", "OTF")) {
            assertTrue(picker.fileFilter.accept(File("fonte.$ext")), ext)
        }
        for (ext in listOf("exe", "dll", "bat", "zip", "jar", "pdf")) {
            assertFalse(picker.fileFilter.accept(File("fonte.$ext")), ext)
        }
    }

    @Test
    fun zipChooserOnlyAcceptsFontZipAndNeverAutomaticallyExtracts() {
        val picker = DesktopNativeFontPicker.configuredZipChooser()
        assertEquals(JFileChooser.FILES_ONLY, picker.fileSelectionMode)
        assertFalse(picker.isAcceptAllFileFilterUsed)
        assertTrue(picker.fileFilter.accept(File("netfontes-fonte.zip")))
        assertTrue(picker.fileFilter.accept(File("dafont-fonte.ZIP")))
        for (ext in listOf("exe", "rar", "7z", "jar", "otf", "ttf")) {
            assertFalse(picker.fileFilter.accept(File("fonte.$ext")), ext)
        }
    }
}
