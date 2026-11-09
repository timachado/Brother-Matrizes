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
    fun saveChooserShowsNativeFiltersAndRejectsWrongNamesWithoutWriting() {
        for (format in listOf("PES", "DST", "JEF", "FLP")) {
            val suggested = "novo-nome.${format.lowercase()}"
            val picker = DesktopNativeMatrixPicker.configuredSaveChooser(
                suggested, format, "Salvar matriz de teste"
            )
            assertEquals(JFileChooser.FILES_ONLY, picker.fileSelectionMode)
            assertFalse(picker.isAcceptAllFileFilterUsed)
            assertEquals(suggested, picker.selectedFile.name)
            assertTrue(picker.fileFilter.accept(File(suggested)))
            assertFalse(picker.fileFilter.accept(File("program.exe")))
        }
        for (wrong in listOf("novo-nome.exe", "../novo-nome.pes", "nome.dst")) {
            assertFailsWith<IllegalArgumentException> {
                DesktopNativeMatrixPicker.configuredSaveChooser(
                    wrong, "PES", "Salvar matriz"
                )
            }
        }
    }

    @Test
    fun projectSaveChooserKeepsQaDestinationAndLibraryPickerUsesFolderOnly() {
        val root = Files.createTempDirectory("bm-flp-native-dialog-")
        try {
            val qaTarget = root.resolve("qa-project-original.flp")
            val save = DesktopNativeMatrixPicker.configuredSaveChooser(
                "brother-matrizes-projeto.flp", "FLP",
                "Salvar projeto original Brother Matrizes FLP", qaTarget
            )
            assertEquals(qaTarget.toFile().absolutePath, save.selectedFile.absolutePath)
            assertEquals(JFileChooser.FILES_ONLY, save.fileSelectionMode)
            assertFalse(save.isAcceptAllFileFilterUsed)
            assertTrue(save.fileFilter.accept(File("matriz.FLP")))
            assertFalse(save.fileFilter.accept(File("program.exe")))
            val folder = DesktopNativeMatrixPicker.configuredDirectoryChooser(
                "Selecionar pasta FLP", root
            )
            assertEquals(JFileChooser.DIRECTORIES_ONLY, folder.fileSelectionMode)
            assertFalse(folder.isAcceptAllFileFilterUsed)
            // Windows may expand the user's 8.3 short directory alias:
            // compare filesystem identity, not path string spelling.
            assertTrue(Files.isSameFile(root, folder.currentDirectory.toPath()))
            assertFalse(Files.exists(qaTarget))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun archiveChooserOnlyOffersZipRarAnd7zWithoutImportingFiles() {
        val archive = DesktopNativeMatrixPicker.configuredArchiveChooser()
        assertEquals(JFileChooser.FILES_ONLY, archive.fileSelectionMode)
        assertFalse(archive.isAcceptAllFileFilterUsed)
        for (extension in listOf("zip", "ZIP", "rar", "7z")) {
            assertTrue(archive.fileFilter.accept(File("backup.$extension")))
        }
        for (extension in listOf("exe", "bat", "flp", "pes", "pdf")) {
            assertFalse(archive.fileFilter.accept(File("backup.$extension")))
        }
        // Merely constructing a chooser does not create or extract any file.
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
