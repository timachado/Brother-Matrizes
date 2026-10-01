package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationMatrixRoundTripTest {

    @Test
    fun createNameSimulationUsesFinalParsedMatrix() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "PRISCILA",
                        heightMm = 18f,
                        style =
                            TextStitchStyle
                                .SATIN,
                        outputFormat =
                            "PES",
                        color =
                            0xE63946
                    )
                )
                .getOrThrow()

        val simulated =
            SimulationMatrixRoundTrip
                .prepare(
                    design =
                        source,
                    targetFormat =
                        "PES"
                )
                .getOrThrow()

        assertEquals(
            "PES",
            simulated.format
        )

        assertTrue(
            simulated.sourceBytes
                .isNotEmpty()
        )

        assertTrue(
            simulated.sourceYAxisDown
        )

        assertEquals(
            source.threadColors,
            simulated.threadColors
        )

        assertTrue(
            simulated.guidePoints
                .isEmpty()
        )

        assertTrue(
            simulated.stitchCount >
                0
        )

        assertTrue(
            kotlin.math.abs(
                simulated.bounds.widthMm -
                    source.bounds.widthMm
            ) <
                0.2f
        )

        assertTrue(
            kotlin.math.abs(
                simulated.bounds.heightMm -
                    source.bounds.heightMm
            ) <
                0.2f
        )
    }

    @Test
    fun preservesSelectedThreadColorInEverySimulationFormat() {
        val selectedColor =
            0xE63946

        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 18f,
                        style =
                            TextStitchStyle
                                .SATIN,
                        color =
                            selectedColor
                    )
                )
                .getOrThrow()

        listOf(
            "DST",
            "PES",
            "JEF"
        ).forEach {
                format ->
            val simulated =
                SimulationMatrixRoundTrip
                    .prepare(
                        design =
                            source.copy(
                                format =
                                    format
                            ),
                        targetFormat =
                            format
                    )
                    .getOrThrow()

            assertEquals(
                "A simulação de $format deve manter a cor escolhida no Criar Nome.",
                listOf(
                    selectedColor
                ),
                simulated.threadColors
            )
        }
    }
}
