package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameOpenMatrixPipelineTest {

    @Test
    fun generatedNameSequenceIsExactlyTheOpenMatrixSequence() {
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
                        "referencia"
                )
                .getOrThrow()

        val openedDirectly =
            EmbroideryLoader
                .loadBytes(
                    displayName =
                        converted.fileName,
                    bytes =
                        converted.bytes
                )

        val canonical =
            GeneratedMatrixPipeline
                .canonicalize(
                    design =
                        source,
                    outputSuffix =
                        "referencia"
                )

        assertTrue(
            openedDirectly is
                EmbroideryLoadResult.Success
        )

        assertTrue(
            canonical is
                EmbroideryLoadResult.Success
        )

        val directDesign =
            (
                openedDirectly as
                    EmbroideryLoadResult.Success
                ).design

        val canonicalDesign =
            (
                canonical as
                    EmbroideryLoadResult.Success
                ).design

        val directSequence =
            directDesign.points.map {
                listOf(
                    it.xUnits,
                    it.yUnits,
                    it.command.ordinal,
                    it.colorIndex
                )
            }

        val canonicalSequence =
            canonicalDesign.points.map {
                listOf(
                    it.xUnits,
                    it.yUnits,
                    it.command.ordinal,
                    it.colorIndex
                )
            }

        assertEquals(
            "Criar Nome precisa usar exatamente os mesmos pontos/comandos de Abrir Matriz.",
            directSequence,
            canonicalSequence
        )

        assertEquals(
            directDesign.stitchCount,
            canonicalDesign.stitchCount
        )

        assertEquals(
            directDesign.jumpCount,
            canonicalDesign.jumpCount
        )

        assertEquals(
            directDesign.colorChanges,
            canonicalDesign.colorChanges
        )

        assertTrue(
            canonicalDesign.sourceBytes
                .isNotEmpty()
        )

        assertTrue(
            canonicalDesign.guidePoints
                .isEmpty()
        )

        assertEquals(
            source.threadColors,
            canonicalDesign.threadColors
        )
    }
    @Test
    fun simulationCanPreserveVectorGuideWithoutChangingCanonicalStitches() {
        val source =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "MARIA",
                        heightMm = 18f,
                        style = TextStitchStyle.SATIN,
                        outputFormat = "PES",
                        color = 0xE63946
                    )
                )
                .getOrThrow()

        val guided =
            source.copy(
                guidePoints =
                    listOf(
                        EmbroideryPoint(0, 0, StitchCommand.JUMP, 0),
                        EmbroideryPoint(20, 0, StitchCommand.STITCH, 0),
                        EmbroideryPoint(20, 20, StitchCommand.STITCH, 0),
                        EmbroideryPoint(0, 20, StitchCommand.STITCH, 0)
                    )
            )

        val canonical =
            GeneratedMatrixPipeline
                .canonicalize(
                    design = guided,
                    outputSuffix = "sim-guide",
                    preserveGuidePoints = true
                )

        assertTrue(
            canonical is EmbroideryLoadResult.Success
        )

        val result =
            (canonical as EmbroideryLoadResult.Success)
                .design

        assertEquals(
            guided.guidePoints,
            result.guidePoints
        )

        assertTrue(
            result.stitchCount > 0
        )
    }


}
