package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryFontPreset
import com.timachado.brothermatrizes.desktop.platform.DesktopReadOnlyFontCatalog
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopReadOnlyFontCatalogTest {
    @Test
    fun catalogsOfficialAndroidPresetsWithActualNamesNotCodes() {
        val root = Files.createTempDirectory("bm-fonts-read-only-")
        val location = root.resolve("fonts-uncreated")
        try {
            val catalog = DesktopReadOnlyFontCatalog(location)
            val entries = catalog.snapshot()
            assertEquals(EmbroideryFontPreset.entries.size, entries.builtIn.size)
            assertEquals(
                EmbroideryFontPreset.entries.map { it.displayName },
                entries.builtIn.map { it.displayName }
            )
            assertEquals(
                EmbroideryFontPreset.entries.map { it.description },
                entries.builtIn.map { it.description }
            )
            assertTrue(entries.builtIn.all { it.displayName.startsWith("BM ") })
            assertTrue(entries.localFontMetadata.isEmpty())
            assertFalse(Files.exists(location))
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun ignoresUntrustedFilesAndDoesNotImportOrModifyData() {
        val root = Files.createTempDirectory("bm-fonts-list-existing-")
        val unused = root.resolve("not-a-font.exe")
        val unrelated = root.resolve("nota.txt")
        try {
            val original = byteArrayOf(1, 2, 3, 4)
            Files.write(unused, original)
            Files.write(unrelated, original)
            val result = DesktopReadOnlyFontCatalog(root).snapshot()
            assertTrue(result.localFontMetadata.isEmpty())
            assertContentEquals(original, Files.readAllBytes(unused))
            assertContentEquals(original, Files.readAllBytes(unrelated))
        } finally {
            Files.deleteIfExists(unused)
            Files.deleteIfExists(unrelated)
            Files.deleteIfExists(root)
        }
    }
}
