package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopReadOnlyFontAnalyzer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Somente fontes públicas da máquina QA, sem contas, rede ou projetos pessoais. */
class DesktopReadOnlyFontAnalyzerTest {
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
    fun readsRealFontAndKeepsBytesUnchangedWithoutImportedLibrary() {
        val source = systemFont()
        val before = Files.readAllBytes(source)
        val folder = Files.createTempDirectory("bm-readonly-font-")
        try {
            val report = DesktopReadOnlyFontAnalyzer.inspect(source)
            assertEquals("TTF", report.format)
            assertTrue(report.familyName.isNotBlank())
            assertTrue(report.testedGlyphs > 1)
            assertTrue(report.visibleOutlines > 1)
            assertEquals(0, report.missingGlyphs)
            assertEquals(before.size.toLong(), report.byteSize)
            assertEquals(source.fileName.toString(), report.sourceName)
            assertContentEquals(before, Files.readAllBytes(source))
            Files.list(folder).use { assertEquals(0L, it.count()) }
            assertFalse(Files.exists(folder.resolve("fonts")))
        } finally {
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun rejectsFakeSignaturesWrongExtensionsAndCorruptFontsWithoutWritingAnything() {
        val folder = Files.createTempDirectory("bm-readonly-invalid-")
        val notFont = folder.resolve("arquivo.ttf")
        val wrongExtension = folder.resolve("identidade.otf")
        val forged = folder.resolve("forged.ttf")
        val executable = folder.resolve("disfarce.exe")
        try {
            Files.write(notFont, byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 1))
            Files.write(wrongExtension, Files.readAllBytes(systemFont()))
            Files.write(forged, byteArrayOf(0, 1, 0, 0) + ByteArray(256))
            Files.write(executable, Files.readAllBytes(systemFont()))
            for (source in listOf(notFont, wrongExtension, forged, executable)) {
                val original = Files.readAllBytes(source)
                assertFailsWith<Exception> {
                    DesktopReadOnlyFontAnalyzer.inspect(source)
                }
                assertContentEquals(original, Files.readAllBytes(source))
            }
            assertFalse(Files.exists(folder.resolve("fonts")))
            assertEquals(4, Files.list(folder).use { it.count().toInt() })
        } finally {
            listOf(notFont, wrongExtension, forged, executable)
                .forEach { Files.deleteIfExists(it) }
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun refusesMissingOrOversizeFilesWithoutCreatingAnyCache() {
        val folder = Files.createTempDirectory("bm-readonly-limit-")
        val missing = folder.resolve("missing.ttf")
        val big = folder.resolve("big.ttf")
        try {
            assertFailsWith<IllegalArgumentException> {
                DesktopReadOnlyFontAnalyzer.inspect(missing)
            }
            Files.newOutputStream(big).use {
                it.write(ByteArray(12 * 1024 * 1024 + 1))
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopReadOnlyFontAnalyzer.inspect(big)
            }
            assertFalse(Files.exists(folder.resolve("fonts")))
        } finally {
            Files.deleteIfExists(big)
            Files.deleteIfExists(folder)
        }
    }
}
