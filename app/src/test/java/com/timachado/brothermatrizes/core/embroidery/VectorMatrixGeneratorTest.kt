package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMatrixGeneratorTest {

    @Test
    fun parsesCommonSvgShapes() {
        val svg =
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <path d="M10 80 C 20 10, 80 10, 90 80"/>
              <rect x="20" y="20" width="20" height="30"/>
              <circle cx="70" cy="40" r="12"/>
              <polyline points="10,90 50,60 90,90"/>
            </svg>
            """.trimIndent()
                .toByteArray()

        val artwork =
            SimpleSvgParser
                .parse(
                    "logo.svg",
                    svg
                )
                .getOrThrow()

        assertTrue(
            artwork.paths.size >=
                4
        )

        assertEquals(
            "logo",
            artwork.label
        )
    }

    @Test
    fun runningVectorGeneratesRealStitches() {
        val artwork =
            VectorArtwork(
                label = "Triangulo",
                paths =
                    listOf(
                        VectorPath(
                            points =
                                listOf(
                                    VectorPoint(
                                        0f,
                                        0f
                                    ),
                                    VectorPoint(
                                        50f,
                                        100f
                                    ),
                                    VectorPoint(
                                        100f,
                                        0f
                                    )
                                ),
                            closed = true
                        )
                    )
            )

        val design =
            VectorMatrixGenerator
                .generate(
                    artwork,
                    VectorMatrixOptions(
                        widthMm = 50f,
                        stitchStyle =
                            TextStitchStyle
                                .RUNNING
                    )
                )
                .getOrThrow()

        assertTrue(
            design.stitchCount >
                20
        )

        assertTrue(
            design.bounds.widthMm >=
                49f
        )
    }

    @Test
    fun satinVectorGeneratesMoreStitchesThanRunning() {
        val artwork =
            VectorArtwork(
                paths =
                    listOf(
                        VectorPath(
                            points =
                                listOf(
                                    VectorPoint(
                                        0f,
                                        0f
                                    ),
                                    VectorPoint(
                                        100f,
                                        0f
                                    )
                                )
                        )
                    )
            )

        val running =
            VectorMatrixGenerator
                .generate(
                    artwork,
                    VectorMatrixOptions(
                        widthMm = 60f,
                        stitchStyle =
                            TextStitchStyle
                                .RUNNING
                    )
                )
                .getOrThrow()

        val satin =
            VectorMatrixGenerator
                .generate(
                    artwork,
                    VectorMatrixOptions(
                        widthMm = 60f,
                        stitchStyle =
                            TextStitchStyle
                                .SATIN
                    )
                )
                .getOrThrow()

        assertTrue(
            satin.stitchCount >
                running.stitchCount
        )
    }

    @Test
    fun vectorExportsToAllReleasedFormats() {
        val artwork =
            VectorArtwork(
                label = "Logo",
                paths =
                    listOf(
                        VectorPath(
                            points =
                                listOf(
                                    VectorPoint(
                                        0f,
                                        0f
                                    ),
                                    VectorPoint(
                                        50f,
                                        80f
                                    ),
                                    VectorPoint(
                                        100f,
                                        0f
                                    )
                                )
                        )
                    )
            )

        val design =
            VectorMatrixGenerator
                .generate(
                    artwork,
                    VectorMatrixOptions(
                        widthMm = 40f,
                        stitchStyle =
                            TextStitchStyle
                                .SATIN
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach {
                    format ->
                val converted =
                    MatrixConverter
                        .convert(
                            design,
                            format,
                            "logo"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )
            }
    }

    @Test
    fun svgGroupTransformPreservesGeometryAndYFlip() {
        // Mesmo tipo de transformação do SVG fornecido para diagnóstico.
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 1049">
              <g transform="translate(0.000000,1049.000000) scale(0.100000,-0.100000)">
                <path d="M4859 10485 c-3 -2 -45 -16 -95 -30 z"/>
              </g>
            </svg>
        """.trimIndent().toByteArray()
        val artwork = SimpleSvgParser.parse("potrace.svg", svg).getOrThrow()
        val start = artwork.paths.first().points.first()
        assertEquals(485.9f, start.x, 0.001f)
        assertEquals(0.5f, start.y, 0.001f)
        assertTrue(artwork.paths.first().closed)
    }

    @Test
    fun nestedTransformsComposeInSvgOrder() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
              <g transform="translate(10,20)">
                <g transform="scale(2,-3)">
                  <line x1="1" y1="2" x2="3" y2="4"/>
                </g>
              </g>
            </svg>
        """.trimIndent().toByteArray()
        val path = SimpleSvgParser.parse("nested.svg", svg).getOrThrow().paths.single()
        assertEquals(12f, path.points[0].x, 0.001f)
        assertEquals(14f, path.points[0].y, 0.001f)
        assertEquals(16f, path.points[1].x, 0.001f)
        assertEquals(8f, path.points[1].y, 0.001f)
    }

    @Test
    fun legacyPotraceDoctypeIsAcceptedWithoutExternalDtd() {
        val svg = """
            <?xml version="1.0" standalone="no"?>
            <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 20010904//EN"
             "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
            <svg xmlns="http://www.w3.org/2000/svg">
              <g transform="translate(0,1049) scale(0.1,-0.1)" fill="#000000" stroke="none">
                <path d="M 0 100 l 10 0 l 0 -10 z"/>
              </g>
            </svg>
        """.trimIndent().toByteArray()
        val paths = SimpleSvgParser.parse("legacy.svg", svg).getOrThrow().paths
        assertEquals(1, paths.size)
        assertTrue(paths[0].closed)
        assertEquals(1049f, paths[0].points.first().y, 0.001f)
    }

    @Test
    fun maliciousDoctypeIsNotAccepted() {
        val malicious = """
            <!DOCTYPE svg [<!ENTITY data SYSTEM "file:///etc/passwd">]>
            <svg xmlns="http://www.w3.org/2000/svg">
              <path d="M0 0 L10 10"/><text>&data;</text>
            </svg>
        """.trimIndent().toByteArray()
        assertTrue(SimpleSvgParser.parse("evil.svg", malicious).isFailure)
    }

    @Test
    fun malformedOrUnsupportedTransformFailsClosed() {
        val wrong = """
            <svg xmlns="http://www.w3.org/2000/svg">
              <g transform="translate(10) nonsense(1)">
                <line x1="0" y1="0" x2="10" y2="10"/>
              </g>
            </svg>
        """.trimIndent().toByteArray()
        assertTrue(SimpleSvgParser.parse("invalid.svg", wrong).isFailure)
    }

    @Test
    fun svgUseElementIsNotSilentlyIgnored() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
              <defs><path id="a" d="M0 0 L10 10"/></defs>
              <use href="#a"/>
            </svg>
        """.trimIndent().toByteArray()
        assertTrue(SimpleSvgParser.parse("use.svg", svg).isFailure)
    }

    @Test
    fun unsupportedSvgCommandReturnsFailure() {
        val svg =
            """
            <svg xmlns="http://www.w3.org/2000/svg">
              <path d="M0 0 A20 20 0 0 1 40 40"/>
            </svg>
            """.trimIndent()
                .toByteArray()

        val result =
            SimpleSvgParser
                .parse(
                    "arco.svg",
                    svg
                )

        assertTrue(
            result.isFailure
        )
    }
}
