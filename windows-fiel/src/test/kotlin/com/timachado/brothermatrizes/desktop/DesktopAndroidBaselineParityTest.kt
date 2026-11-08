package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopAndroidBaseline
import com.timachado.brothermatrizes.font.OpenTypeFontNames
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prevent future version-code or OpenType parser drift from Android source.
 * No network calls, no login, no writes and no customer files.
 */
class DesktopAndroidBaselineParityTest {
    private val root: Path = Path.of("").toAbsolutePath().normalize()

    @Test
    fun advertisedVersionMustEqualSourceAndroidGradleMetadata() {
        val source = Files.readString(root.resolve("../app/build.gradle.kts"))
        val code = Regex("""\bversionCode\s*=\s*(\d+)""").find(source)
            ?.groupValues?.get(1)?.toInt()
            ?: error("Android versionCode não encontrado.")
        val name = Regex("""versionName\s*=\s*"([^"]+)""").find(source)
            ?.groupValues?.get(1)
            ?: error("Android versionName não encontrado.")
        assertEquals(code, DesktopAndroidBaseline.VERSION_CODE)
        assertEquals(name, DesktopAndroidBaseline.VERSION_NAME)
        assertTrue(DesktopAndroidBaseline.WINDOWS_QA_LABEL.contains(name))
        assertTrue(DesktopAndroidBaseline.WINDOWS_QA_LABEL.contains("QA"))
    }

    @Test
    fun windowsUsesExactlyTheOpenTypeParserFromOriginalAndroidSource() {
        val sourceFile = root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/font/" +
                "OpenTypeFontNames.kt"
        )
        val generatedFile = root.resolve(
            "build/generated/android-opentype/kotlin/" +
                "com/timachado/brothermatrizes/font/OpenTypeFontNames.kt"
        )
        assertContentEquals(Files.readAllBytes(sourceFile),
            Files.readAllBytes(generatedFile))
    }

    @Test
    fun existingSourceReadersArePreservedAndFontIdsRemainStable() {
        val android = Files.readString(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/font/" +
                "ImportedFontStore.kt"
        ))
        assertTrue(android.contains("OpenTypeFontNames.readDisplayName(file)"))
        val desktop = Files.readString(root.resolve(
            "src/main/kotlin/com/timachado/brothermatrizes/desktop/platform/" +
                "DesktopImportedFontLibrary.kt"
        ))
        assertTrue(desktop.contains("OpenTypeFontNames.readDisplayName(file.toFile())"))
        assertTrue(desktop.contains("id = name"))
        assertTrue(desktop.contains("absolutePath = file.toAbsolutePath().normalize().toString()"))
    }
}
