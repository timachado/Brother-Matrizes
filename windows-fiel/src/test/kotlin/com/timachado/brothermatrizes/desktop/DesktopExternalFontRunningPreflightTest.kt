package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopExternalFontRunningPreflight
import java.awt.Font
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Synthetic, in-memory QA only: no production source files, browser,
 * Supabase, fonts installation, quota access or embroidery exports.
 */
class DesktopExternalFontRunningPreflightTest {
    private val fixture = Font("Dialog", Font.PLAIN, 100)

    @Test
    fun realJava2dGlyphOutlinesGoThroughOriginalAndroidRunningStitchCore() {
        val report = DesktopExternalFontRunningPreflight.inspectLoadedFont(
            fixture, "Maria", requestedHeightMm = 18f
        )
        assertEquals("RUNNING_ONLY_QA", report.generationMode)
        assertEquals("Maria", report.testedText)
        assertTrue(report.familyName.isNotBlank())
        assertTrue(report.glyphContours > 0)
        assertTrue(report.sampledOutlinePoints >= report.glyphContours * 3)
        assertTrue(report.stitchCount > 0)
        assertTrue(report.widthMm in 5f..300f)
        assertTrue(report.heightMm > 0f)
    }

    @Test
    fun preservesCaseAndAccentsWithoutWritingAnyFontOrDesignFiles() {
        val result = DesktopExternalFontRunningPreflight.inspectLoadedFont(
            Font("Serif", Font.PLAIN, 100), "AÇÃO", 20f
        )
        assertEquals("AÇÃO", result.testedText)
        assertTrue(result.stitchCount > 0)
        assertTrue(result.glyphContours >= 1)
    }

    @Test
    fun rejectsEmptyControlTextAndInvalidSizeBeforeGeneratingStitches() {
        for (word in listOf("", "  ", "nome\nfora", "a".repeat(25))) {
            assertFailsWith<IllegalArgumentException> {
                DesktopExternalFontRunningPreflight.inspectLoadedFont(fixture, word)
            }
        }
        for (size in listOf(0f, 4f, 61f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> {
                DesktopExternalFontRunningPreflight.inspectLoadedFont(
                    fixture, "Maria", size
                )
            }
        }
    }

    @Test
    fun rejectsFalsifiedOrMissingFontFileWithoutFilesystemMutation() {
        val temp = java.nio.file.Files.createTempDirectory("bm-running-preflight-")
        try {
            val fake = temp.resolve("false.ttf")
            java.nio.file.Files.writeString(fake, "<html>invalid</html>")
            assertFailsWith<Exception> {
                DesktopExternalFontRunningPreflight.inspect(fake, "Maria")
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopExternalFontRunningPreflight.inspect(
                    temp.resolve("not-here.otf"), "Maria"
                )
            }
            assertTrue(java.nio.file.Files.exists(fake))
            assertEquals("<html>invalid</html>", java.nio.file.Files.readString(fake))
        } finally {
            java.nio.file.Files.list(temp).use { stream ->
                stream.forEach(java.nio.file.Files::deleteIfExists)
            }
            java.nio.file.Files.deleteIfExists(temp)
        }
    }
}
