package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameOpenMatrixPipelineTest {

    @Test
    fun generatedNameUsesTheSameCanonicalPipelineAsOpenedMatrix() {
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

        val result =
            GeneratedMatrixPipeline
                .canonicalize(
                    design =
                        source,
                    outputSuffix =
                        "teste"
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

        assertEquals(
            source.threadColors,
            opened.threadColors
        )

        assertEquals(
            source.hoopProfile,
            opened.hoopProfile
        )

        assertEquals(
            source.fabricProfile,
            opened.fabricProfile
        )
    }
}
