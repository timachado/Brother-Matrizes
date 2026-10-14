package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNameOpenMatrixPipelineTest {

    @Test
    fun selectedHoopSurvivesGeneratedMatrixCanonicalization() {
        val selectedHoop = HoopProfile.H130X180
        val nameDesign = TextMatrixGenerator
            .generate(
                TextMatrixOptions(
                    text = "Maria",
                    heightMm = 18f,
                    style = TextStitchStyle.SATIN,
                    outputFormat = "PES",
                    hoopProfile = selectedHoop
                )
            )
            .getOrThrow()
            .copy(hoopProfile = selectedHoop)

        val canonical = GeneratedMatrixPipeline
            .canonicalize(
                design = nameDesign,
                outputSuffix = "selected-hoop",
                preserveGuidePoints = true
            )

        assertTrue(canonical is EmbroideryLoadResult.Success)
        val result = (canonical as EmbroideryLoadResult.Success).design
        // O parser pode normalizar a quantidade de comandos/pontos;
        // este teste verifica exclusivamente a persistencia do bastidor.
        assertEquals(selectedHoop, result.hoopProfile)
    }


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

        assertTrue(
            "A matriz criada e reaberta deve continuar marcada como modificada para o simulador usar o enquadramento pelos STITCHs.",
            canonicalDesign.isModified
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



    @Test
    fun generatedPipelineCollapsesOnlyLeadingPositioningJumps() {
        val points =
            listOf(
                EmbroideryPoint(
                    20,
                    10,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    40,
                    20,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    60,
                    30,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    62,
                    32,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    70,
                    35,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    72,
                    36,
                    StitchCommand.STITCH,
                    0
                )
            )

        val normalized =
            GeneratedMatrixPipeline
                .collapseLeadingPositioningJumps(
                    points
                )

        assertEquals(
            4,
            normalized.size
        )

        assertEquals(
            StitchCommand.JUMP,
            normalized.first()
                .command
        )

        assertEquals(
            60,
            normalized.first()
                .xUnits
        )

        assertEquals(
            30,
            normalized.first()
                .yUnits
        )

        assertEquals(
            "JUMPs reais após o início da costura devem permanecer.",
            2,
            normalized.count {
                it.command ==
                    StitchCommand.JUMP
            }
        )
    }

    @Test
    fun generatedPipelineDoesNotCollapseMixedLeadingCommands() {
        val points =
            listOf(
                EmbroideryPoint(
                    20,
                    10,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    20,
                    10,
                    StitchCommand.TRIM,
                    0
                ),
                EmbroideryPoint(
                    22,
                    12,
                    StitchCommand.STITCH,
                    0
                )
            )

        assertEquals(
            points,
            GeneratedMatrixPipeline
                .collapseLeadingPositioningJumps(
                    points
                )
        )
    }

}
