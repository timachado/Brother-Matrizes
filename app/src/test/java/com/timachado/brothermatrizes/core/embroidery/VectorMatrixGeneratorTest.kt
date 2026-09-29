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
