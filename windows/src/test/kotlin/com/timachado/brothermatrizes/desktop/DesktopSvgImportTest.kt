package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopSvgImportTest {
    private val simple = """
        <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
            <polyline points="0,0 100,0 100,100 0,100 0,0"/>
        </svg>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Test
    fun `svg vetorial usa parser e gerador oficiais android`() {
        val imported = DesktopSvgImport.parse("quadrado.svg", simple)
        assertEquals(1, imported.artwork.paths.size)
        val doc = DesktopDrawingService.generateSvg(
            imported, 40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
        )
        assertEquals("DST", doc.current.format)
        assertTrue(doc.current.endFound && doc.current.stitchCount > 0)
        assertEquals(1, doc.current.points.count { it.command == StitchCommand.END })
        assertEquals(doc.original.points, doc.current.points)
        assertTrue(doc.current.bounds.widthMm < 90f)
    }

    @Test
    fun `svg importado nao altera arquivo original`() {
        val directory = Files.createTempDirectory("brother-svg-qa")
        val file = directory.resolve("desenho.svg")
        Files.write(file, simple)
        try {
            val parsed = DesktopSvgImport.open(file)
            assertEquals("desenho.svg", parsed.sourceName)
            DesktopDrawingService.generateSvg(
                parsed, 40f, TextStitchStyle.RUNNING, HoopProfile.H100X100
            )
            assertTrue(simple.contentEquals(Files.readAllBytes(file)))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun `svg bloqueia entidades scripts referencias remotas e eventos`() {
        val invalid = listOf(
            """<!DOCTYPE svg [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]><svg/>""",
            """<svg><script>alert(1)</script><path d="M0 0 L9 9"/></svg>""",
            """<svg><image href="https://example.com/image.png"/></svg>""",
            """<svg onload="alert(1)"><path d="M0 0 L9 9"/></svg>""",
            """<svg><use xlink:href="#obj"/></svg>""",
            """<svg><style>@import url(https://example.com)</style></svg>"""
        )
        invalid.forEach { xml ->
            assertFailsWith<IllegalArgumentException> {
                DesktopSvgImport.parse("invalid.svg", xml.toByteArray(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun `svg rejeita entrada acima de 2mb ou geometria sem tamanho`() {
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgImport.parse("big.svg", ByteArray(DesktopSvgImport.MAX_BYTES + 1))
        }
        assertFailsWith<Exception> {
            DesktopSvgImport.parse(
                "line.svg",
                """<svg><polyline points="0,0 10,0"/></svg>"""
                    .toByteArray(Charsets.UTF_8)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopSvgImport.parse("image.png", simple)
        }
    }
}
