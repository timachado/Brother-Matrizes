package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixGenerator
import com.timachado.brothermatrizes.core.embroidery.VectorMatrixOptions
import com.timachado.brothermatrizes.desktop.platform.DesktopSvgFilePicker
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic SVG in private temp folder, no UI picker, client data or network. */
class DesktopSvgFilePickerTest {
    private val validSvg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 40 30">
          <polyline points="0,0 40,0 40,30 0,30 0,0"/>
        </svg>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun importsSvgAndUsesOriginalAndroidEmbroideryVectorEngine() {
        val folder = Files.createTempDirectory("bm-drawing-svg-")
        val svg = folder.resolve("meu desenho.svg")
        try {
            Files.write(svg, validSvg)
            val original = Files.readAllBytes(svg)
            val selected = DesktopSvgFilePicker.readVerified(svg)
            assertEquals(svg.fileName.toString(), selected.name)
            assertContentEquals(original, selected.bytes)
            val artwork = SimpleSvgParser.parse(selected.name, selected.bytes).getOrThrow()
            assertTrue(artwork.paths.isNotEmpty())
            val design = VectorMatrixGenerator.generate(
                artwork,
                VectorMatrixOptions(
                    widthMm = 60f,
                    stitchStyle = TextStitchStyle.RUNNING,
                    outputFormat = "DST",
                    enforceHoop = false
                )
            ).getOrThrow()
            assertTrue(design.stitchCount > 0)
            assertTrue(design.points.isNotEmpty())
            assertContentEquals(original, Files.readAllBytes(svg))
        } finally {
            Files.deleteIfExists(svg)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun rejectsMalformedInputsExtensionEmptyOversizedAndMissingFiles() {
        val folder = Files.createTempDirectory("bm-drawing-rejected-")
        val bad = folder.resolve("logo.exe")
        val empty = folder.resolve("blank.svg")
        val large = folder.resolve("huge.svg")
        try {
            Files.write(bad, validSvg)
            Files.write(empty, byteArrayOf())
            Files.write(large, ByteArray(2_000_001) { 65 })
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgFilePicker.readVerified(bad)
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgFilePicker.readVerified(empty)
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgFilePicker.readVerified(large)
            }
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgFilePicker.readVerified(folder.resolve("missing.svg"))
            }
            assertFalse(Files.exists(folder.resolve("generated.dst")))
        } finally {
            Files.deleteIfExists(bad)
            Files.deleteIfExists(empty)
            Files.deleteIfExists(large)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun rejectsMalformedXmlDoctypeAndExternalEntityWithoutAccessingAnyResource() {
        val folder = Files.createTempDirectory("bm-drawing-xxe-")
        val svg = folder.resolve("malicious.svg")
        try {
            val attacks = listOf(
                "<svg><path d='M 0 0'></svg",
                "<!DOCTYPE svg [<!ENTITY external SYSTEM 'file:///does-not-exist'>]>" +
                    "<svg xmlns='http://www.w3.org/2000/svg'>&external;</svg>",
                "<!DOCTYPE svg SYSTEM 'https://example.invalid/evil.dtd'>" +
                    "<svg xmlns='http://www.w3.org/2000/svg'/>"
            )
            for (attempt in attacks) {
                Files.writeString(svg, attempt)
                kotlin.test.assertFailsWith<Exception> {
                    DesktopSvgFilePicker.readVerified(svg)
                }
                assertEquals(attempt, Files.readString(svg))
            }
        } finally {
            Files.deleteIfExists(svg)
            Files.deleteIfExists(folder)
        }
    }

    @Test
    fun doesNotReadSymbolicLinkAsSvg() {
        val folder = Files.createTempDirectory("bm-drawing-symlink-")
        val target = folder.resolve("real.svg")
        val link = folder.resolve("linked.svg")
        try {
            Files.write(target, validSvg)
            if (runCatching { Files.createSymbolicLink(link, target) }.isSuccess) {
                assertFailsWith<IllegalArgumentException> {
                    DesktopSvgFilePicker.readVerified(link)
                }
            }
            assertContentEquals(validSvg, Files.readAllBytes(target))
        } finally {
            Files.deleteIfExists(link)
            Files.deleteIfExists(target)
            Files.deleteIfExists(folder)
        }
    }
}
