package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameOpenMatrixPipelineTest {

    @Test
    fun generatedNameUsesTheSameParserAsOpenedMatrix() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 18f,
                        style =
                            TextStitchStyle.SATIN,
                        outputFormat =
                            "PES",
                        color =
                            0xE63946
                    )
                )
                .getOrThrow()

        val converted =
            MatrixConverter
                .convert(
                    design =
                        source,
                    targetFormat =
                        source.format,
                    outputSuffix =
                        "simulacao"
                )
                .getOrThrow()

        val result =
            EmbroideryLoader
                .loadBytes(
                    displayName =
                        converted.fileName,
                    bytes =
                        converted.bytes
                )

        assertTrue(
            result is
                EmbroideryLoadResult.Success
        )

        val opened =
            (
                result as
                    EmbroideryLoadResult.Success
                ).design

        assertEquals(
            "PES",
            opened.format
        )

        assertTrue(
            opened.sourceBytes
                .isNotEmpty()
        )

        assertTrue(
            opened.guidePoints
                .isEmpty()
        )

        assertTrue(
            opened.stitchCount >
                0
        )
    }
}
