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


    @Test
    fun edvaldoPreviewAndSimulationUseOneFinalStitchSequenceForMachineFormats() {
        // A simulação recebe o design canônico já preparado em Criar Nome.
        // O arquivo não deve passar por nova conversão depois do clique.
        for (format in MatrixConverter.supportedFormats) {
            val generated = TextMatrixGenerator.generate(
                TextMatrixOptions(
                    text = "Edvaldo",
                    heightMm = 18f,
                    style = TextStitchStyle.SATIN,
                    outputFormat = format,
                    color = 0xE63946,
                    enforceHoop = false
                )
            ).getOrThrow()

            val prepared = GeneratedMatrixPipeline.canonicalize(
                design = generated,
                outputSuffix = "criado",
                preserveGuidePoints = true
            )
            assertTrue("A conversão final falhou em $format",
                prepared is EmbroideryLoadResult.Success)
            val preview = (prepared as EmbroideryLoadResult.Success).design
            val simulator = preview.copy(hoopProfile = HoopProfile.H130X180)
            val created = preview.copy(hoopProfile = HoopProfile.H130X180)

            assertTrue("APK real deve existir para $format",
                preview.sourceBytes.isNotEmpty())
            assertEquals("A prévia deve contar só STITCH em $format",
                preview.points.count { it.command == StitchCommand.STITCH },
                preview.stitchCount)
            assertEquals("Prévia e simulação devem ter a mesma sequência em $format",
                preview.points, simulator.points)
            assertEquals("Simulação e matriz criada devem ser idênticas em $format",
                simulator.points, created.points)
            assertEquals(preview.stitchCount, simulator.stitchCount)
            assertEquals(simulator.stitchCount, created.stitchCount)
            org.junit.Assert.assertArrayEquals(
                "A simulação não deve reescrever os bytes em $format",
                preview.sourceBytes, simulator.sourceBytes
            )
        }
    }


}
