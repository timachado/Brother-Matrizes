package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.font.AndroidSkiaOutlineExtractor
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontCoverageGuard
import com.timachado.brothermatrizes.font.CleanRoomSatinEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android active Satin bridge on actual Windows rendering APIs.
 * Uses only local system fonts; never logs into Google or consumes quotas.
 */
class DesktopAndroidActiveSatinBridgeTest {
    private fun font(): Path {
        val windir = Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        return listOf(
            windir.resolve("arial.ttf"),
            windir.resolve("segoeui.ttf"),
            Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        ).firstOrNull { Files.isRegularFile(it) }
            ?: error("Fonte TTF de teste indisponível.")
    }

    private fun importedFont(): ImportedFont {
        val p = font()
        return ImportedFont(
            id = "system-fixture",
            displayName = p.fileName.toString(),
            fileName = p.fileName.toString(),
            extension = "ttf",
            absolutePath = p.toString()
        )
    }

    @Test
    fun androidSkiaAdapterProvidesRealOutlinesWithCountersAndHoles() {
        val result = AndroidSkiaOutlineExtractor.extract(
            font = importedFont(), text = "OO",
            targetCapHeightUnits = 120f,
            spacingUnits = 4f,
            rotationDegrees = 0f
        )
        assertEquals(2, result.glyphPolygons.size)
        assertTrue(result.glyphPolygons.all { glyph -> glyph.size >= 2 })
        assertTrue(result.sourceWidth > 0f && result.sourceHeight > 0f)
        assertFalse(result.contextualShapingPreserved)
    }

    @Test
    fun unsupportedRotationOrMissingFontMustFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            AndroidSkiaOutlineExtractor.extract(importedFont(), "Maria", 120f, 3f, 15f)
        }
        val badFont = importedFont().copy(absolutePath = font().parent.resolve("missing.ttf").toString())
        assertFailsWith<IllegalArgumentException> {
            AndroidSkiaOutlineExtractor.extract(badFont, "Maria", 120f, 3f, 0f)
        }
    }

    @Test
    fun silhouetteMustRejectNoStitchesAndStitchesEntirelyOutsideLetters() {
        val ring = listOf(
            AndroidSkiaOutlineExtractor.Point(0f, 0f),
            AndroidSkiaOutlineExtractor.Point(100f, 0f),
            AndroidSkiaOutlineExtractor.Point(100f, 100f),
            AndroidSkiaOutlineExtractor.Point(0f, 100f)
        )
        val glyphs = listOf(listOf(ring))
        assertFailsWith<IllegalArgumentException> {
            ImportedFontCoverageGuard.check(glyphs, emptyList())
        }
        val outside = listOf(
            EmbroideryPoint(1000, 1000, StitchCommand.JUMP, 0),
            EmbroideryPoint(1050, 1000, StitchCommand.STITCH, 0),
            EmbroideryPoint(1050, 1050, StitchCommand.STITCH, 0)
        )
        assertFailsWith<IllegalArgumentException> {
            ImportedFontCoverageGuard.check(glyphs, outside)
        }
    }

    @Test
    fun cleanRoomCoreIsCopiedByteForByteFromAndroidCurrentSource() {
        val root = Path.of("").toAbsolutePath().normalize()
        val original = Files.readAllBytes(root.resolve(
            "../app/src/main/java/com/timachado/brothermatrizes/" +
                "font/CleanRoomSatinEngine.kt"
        ))
        val windows = Files.readAllBytes(root.resolve(
            "build/generated/active-satin-android/kotlin/" +
                "com/timachado/brothermatrizes/font/CleanRoomSatinEngine.kt"
        ))
        kotlin.test.assertContentEquals(original, windows)
        // Static source-exactness is distinct from actual geometric parity.
        assertTrue(original.isNotEmpty())
    }
}
