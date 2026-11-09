package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopRecoveredAndroidFontPreflight
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** No Google session, account, quotas, or Windows catalog is accessed. */
class DesktopRecoveredAndroidFontPreflightTest {
    private fun systemFont(): Path {
        val root = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            root.resolve("arial.ttf"),
            root.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Fonte de sistema não encontrada no ambiente de QA.")
    }

    @Test
    fun reviewsValidAndCorruptedRecoveredFontsWithoutImportingOrChangingBytes() {
        val parent = Files.createTempDirectory("bm-android-recovered-fonts-")
        try {
            val folder = Files.createDirectory(parent.resolve("fonts"))
            val real = folder.resolve("Original.ttf")
            val broken = folder.resolve("Invalid.ttf")
            val original = Files.readAllBytes(systemFont())
            val corrupted = byteArrayOf(0, 1, 0, 0) + ByteArray(128)
            Files.write(real, original)
            Files.write(broken, corrupted)
            val report = DesktopRecoveredAndroidFontPreflight.inspect(folder)
            assertEquals(2, report.items.size)
            assertEquals(1, report.readableCount)
            assertEquals(1, report.blockedCount)
            val displayed = report.displayText()
            assertTrue(displayed.contains("Compatíveis para inspeção: 1"))
            assertTrue(displayed.contains("Exigem revisão: 1"))
            assertTrue(displayed.contains("[Legível] "))
            assertTrue(displayed.contains("[Revisar] "))
            assertTrue(displayed.contains("Nenhuma fonte foi ativada"))
            assertEquals("Original.ttf", report.items.single { it.readableOutlines }.fileName)
            assertTrue(report.items.single { it.readableOutlines }.displayName.isNotBlank())
            assertContentEquals(original, Files.readAllBytes(real))
            assertContentEquals(corrupted, Files.readAllBytes(broken))
            assertFalse(Files.exists(parent.resolve("font-library")))
            assertEquals(2L, Files.list(folder).use { it.count() })
        } finally {
            Files.walk(parent).use { it.sorted(Comparator.reverseOrder()).forEach { entry -> Files.deleteIfExists(entry) } }
        }
    }

    @Test
    fun showsEmptyRecoveryFolderWithoutCreatingCommercialCatalog() {
        val parent = Files.createTempDirectory("bm-android-empty-recovery-")
        try {
            val folder = Files.createDirectory(parent.resolve("fonts"))
            val report = DesktopRecoveredAndroidFontPreflight.inspect(folder)
            assertEquals(0, report.readableCount)
            assertEquals(0, report.blockedCount)
            assertTrue(report.displayText().contains("Nenhuma fonte TTF/OTF recuperada"))
            assertEquals(0L, Files.list(folder).use { it.count() })
            assertFalse(Files.exists(parent.resolve("font-library")))
        } finally {
            Files.walk(parent).use {
                it.sorted(Comparator.reverseOrder()).forEach { entry ->
                    Files.deleteIfExists(entry)
                }
            }
        }
    }

    @Test
    fun rejectsUnexpectedFilesAndMissingRecoveryFolderWithoutCreatingAnything() {
        val parent = Files.createTempDirectory("bm-android-recovered-invalid-")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopRecoveredAndroidFontPreflight.inspect(parent.resolve("fonts"))
            }
            val folder = Files.createDirectory(parent.resolve("fonts"))
            Files.writeString(folder.resolve("unexpected.exe"), "not a font")
            assertFailsWith<IllegalArgumentException> {
                DesktopRecoveredAndroidFontPreflight.inspect(folder)
            }
            assertFalse(Files.exists(parent.resolve("font-library")))
        } finally {
            Files.walk(parent).use { it.sorted(Comparator.reverseOrder()).forEach { entry -> Files.deleteIfExists(entry) } }
        }
    }
}
