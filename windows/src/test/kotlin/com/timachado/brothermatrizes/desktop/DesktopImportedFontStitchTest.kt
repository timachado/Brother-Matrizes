package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.font.AndroidSkiaOutlineExtractor
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontCoverageGuard
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopImportedFontStitchTest {
    private fun windowsFont(): Path? {
        val dir = System.getenv("WINDIR") ?: return null
        return listOf("arial.ttf", "segoeui.ttf", "calibri.ttf")
            .map { Path.of(dir, "Fonts", it) }
            .firstOrNull { Files.isRegularFile(it) }
    }

    @Test
    fun `extrai contorno vetorial de TrueType sem instalar fonte`() {
        val path = windowsFont() ?: return
        val preview = DesktopFontPreviewService.open(path, "H")
        val imported = ImportedFont(
            id = "test", displayName = preview.family,
            fileName = path.fileName.toString(),
            extension = "ttf", absolutePath = path.toString()
        )
        val outline = AndroidSkiaOutlineExtractor.extract(
            imported, "H", 180f, 0f, 0f
        )
        assertTrue(outline.glyphPolygons.isNotEmpty())
        assertTrue(outline.glyphPolygons.flatten().flatten().size > 12)
        assertTrue(outline.sourceWidth > 0f)
        assertTrue(outline.sourceHeight > 0f)
    }

    @Test
    fun `motor Satin oficial gera pontadas de TTF e relê saída DST`() {
        val path = windowsFont() ?: return
        val result = DesktopMatrixService.createFromImportedFont(path, "H", 18f)
        assertTrue(result.current.endFound)
        assertTrue(result.current.stitchCount > 20)
        assertTrue(result.current.points.any { it.command == StitchCommand.STITCH })
        assertEquals(result.current.points, result.original.points)
        val converted = DesktopMatrixService.convert(result, "DST")
        assertTrue(converted.bytes.size > 512)
        val reopened = DesktopMatrixService.open(converted.fileName, converted.bytes)
        assertTrue(reopened.current.stitchCount > 0)
        assertEquals(result.current.label, "H")
    }

    @Test
    fun `contorno sem costura nao pode passar no controle de legibilidade`() {
        val ring = listOf(
            AndroidSkiaOutlineExtractor.Point(0f, 0f),
            AndroidSkiaOutlineExtractor.Point(80f, 0f),
            AndroidSkiaOutlineExtractor.Point(80f, 90f),
            AndroidSkiaOutlineExtractor.Point(0f, 90f)
        )
        assertFailsWith<IllegalArgumentException> {
            ImportedFontCoverageGuard.check(listOf(listOf(ring)), emptyList())
        }
    }

    @Test
    fun `rejeita fonte externa ausente ou altura insegura`() {
        val fake = Path.of("arquivo-que-nao-existe.ttf")
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createFromImportedFont(fake, "ANA", 12f)
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMatrixService.createFromImportedFont(fake, "ANA", Float.NaN)
        }
    }
}
