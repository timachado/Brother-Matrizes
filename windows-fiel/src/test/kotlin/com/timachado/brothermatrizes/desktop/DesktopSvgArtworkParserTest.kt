package com.timachado.brothermatrizes.desktop

import com.timachado.brothermatrizes.desktop.platform.DesktopSvgArtworkParser
import com.timachado.brothermatrizes.core.embroidery.SimpleSvgParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSvgArtworkParserTest {
    private fun parse(xml: String) = DesktopSvgArtworkParser.parse(
        "orientacao.svg", xml.toByteArray(Charsets.UTF_8)
    )

    @Test
    fun matrixNegativeYIsAppliedBeforeGeneratingStitches() {
        // Common Inkscape/machine SVG export: content relies on matrix(1,0,0,-1,0,H).
        val result = parse(
            """<svg><g transform="matrix(1 0 0 -1 0 100)">""" +
                """<line x1="0" y1="0" x2="10" y2="20"/></g></svg>"""
        ).getOrThrow()
        val path = result.paths.single()
        assertEquals(100f, path.points[0].y)
        assertEquals(80f, path.points[1].y)
        assertEquals(10f, path.points[1].x)
    }

    @Test
    fun nestedTranslateRotateAndMirrorUseSvgDocumentOrder() {
        val result = parse(
            """<svg><g transform="translate(10 5)">""" +
                """<g transform="rotate(90)"><line x1="0" y1="0" x2="10" y2="0"/></g>""" +
                """</g></svg>"""
        ).getOrThrow()
        val path = result.paths.single()
        assertEquals(10f, path.points[0].x)
        assertEquals(5f, path.points[0].y)
        assertTrue(kotlin.math.abs(path.points[1].x - 10f) < 0.01f)
        assertTrue(kotlin.math.abs(path.points[1].y - 15f) < 0.01f)
    }

    @Test
    fun plainSvgKeepsOriginalAndroidGeometry() {
        val bytes = """<svg><path d="M0 0 L10 20 L30 40"/></svg>"""
            .toByteArray(Charsets.UTF_8)
        val original = SimpleSvgParser.parse("plain.svg", bytes).getOrThrow()
        val desktop = DesktopSvgArtworkParser.parse("plain.svg", bytes).getOrThrow()
        assertEquals(original.paths, desktop.paths)
    }

    @Test
    fun doesNotRenderDefinitionsAndFailsClosedOnUnsupportedTransforms() {
        val result = parse(
            """<svg><defs><path d="M 99 99 L 88 88"/></defs>""" +
                """<line x1="0" y1="0" x2="1" y2="1"/></svg>"""
        ).getOrThrow()
        assertEquals(1, result.paths.size)
        assertTrue(parse(
            """<svg><g transform="rotateZ(90)"><rect width="12" height="12"/></g></svg>"""
        ).isFailure)
        assertTrue(parse(
            """<!DOCTYPE svg [<!ENTITY x SYSTEM "file:///etc/passwd">]><svg><line x1="0" y1="0" x2="1" y2="1"/></svg>"""
        ).isFailure)
        assertFalse(result.label.isBlank())
    }
}