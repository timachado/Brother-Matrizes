package com.timachado.brothermatrizes.core.project

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryIntegrity
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineFinishingInfo
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.NamePersonalization
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCodecTest {

    private fun sample():
        EmbroideryDesign =
        EmbroideryDesign(
            fileName =
                "nome-maria.pes",
            format =
                "PES",
            label =
                "Maria",
            points =
                listOf(
                    EmbroideryPoint(
                        0,
                        0,
                        StitchCommand.JUMP,
                        0
                    ),
                    EmbroideryPoint(
                        10,
                        20,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        20,
                        40,
                        StitchCommand.COLOR_CHANGE,
                        1
                    ),
                    EmbroideryPoint(
                        30,
                        50,
                        StitchCommand.STITCH,
                        1
                    ),
                    EmbroideryPoint(
                        30,
                        50,
                        StitchCommand.END,
                        1
                    )
                ),
            bounds =
                EmbroideryBounds(
                    0,
                    30,
                    0,
                    50
                ),
            stitchCount = 2,
            jumpCount = 1,
            colorChanges = 1,
            endFound = true,
            sourceBytes =
                byteArrayOf(
                    1,
                    2,
                    3
                ),
            threadColors =
                listOf(
                    0xE6BE70,
                    0x457B9D
                ),
            isModified = true,
            hoopProfile =
                HoopProfile.H100X100,
            fabricProfile =
                FabricProfile.COTTON,
            machineFinishing =
                MachineFinishingInfo(
                    tieInEnabled = true,
                    tieOffEnabled = true,
                    autoTrimLongJumps = true,
                    trimJumpThresholdMm = 8f,
                    optimizeTravel = true
                )
        )

    @Test
    fun projectRoundTripPreservesMatrix() {
        val original =
            sample()

        val restored =
            ProjectCodec
                .decode(
                    ProjectCodec
                        .encode(
                            original
                        )
                )

        assertEquals(
            original.fileName,
            restored.fileName
        )

        assertEquals(
            original.format,
            restored.format
        )

        assertEquals(
            original.label,
            restored.label
        )

        assertEquals(
            original.points,
            restored.points
        )

        assertEquals(
            original.threadColors,
            restored.threadColors
        )

        assertEquals(
            original.hoopProfile,
            restored.hoopProfile
        )

        assertEquals(
            original.fabricProfile,
            restored.fabricProfile
        )

        assertEquals(
            original
                .machineFinishing,
            restored
                .machineFinishing
        )

        assertTrue(
            restored.sourceBytes
                .isEmpty()
        )

        assertTrue(
            restored.isModified
        )
    }

    @Test
    fun editableNameSurvivesSavingReopeningAndReplacement() {
        // A versão anterior do teste tentava bordar Maria sobre um desenho
        // minúsculo de 3x5 mm. A proteção geométrica corretamente bloqueia.
        val frame = EmbroideryIntegrity.normalize(
            sample().copy(
                points = listOf(
                    EmbroideryPoint(-380, -380, StitchCommand.JUMP, 0),
                    EmbroideryPoint(-380, 380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, 380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, -380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-380, -380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-380, -380, StitchCommand.END, 0)
                ),
                threadColors = listOf(0xE6BE70),
                hoopProfile = HoopProfile.H100X100
            )
        ).getOrThrow().design
        val maria = NamePersonalization.add(
            frame,
            TextMatrixOptions(
                text = "Maria",
                style = TextStitchStyle.SATIN,
                heightMm = 10f
            ),
            0, 0
        ).getOrThrow()
        val reopened = ProjectCodec.decode(ProjectCodec.encode(maria))
        assertEquals("Maria", reopened.editableName?.options?.text)
        assertEquals(frame.points, reopened.editableName?.original?.points)
        val sophia = NamePersonalization.add(
            reopened,
            TextMatrixOptions(
                text = "Sophia",
                style = TextStitchStyle.SATIN,
                heightMm = 10f
            ),
            0, 0
        ).getOrThrow()
        assertEquals(frame.points.dropLast(1),
            sophia.points.take(frame.points.size - 1))
        assertEquals(1, sophia.points.count { it.command == StitchCommand.COLOR_CHANGE } -
            frame.points.count { it.command == StitchCommand.COLOR_CHANGE })
        assertEquals("Sophia", sophia.editableName?.options?.text)
    }

    @Test
    fun rotatedNameSurvivesProjectV5Roundtrip() {
        val frame = EmbroideryIntegrity.normalize(
            sample().copy(
                points = listOf(
                    EmbroideryPoint(-380, -380, StitchCommand.JUMP, 0),
                    EmbroideryPoint(-380, 380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, 380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, -380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-380, -380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-380, -380, StitchCommand.END, 0)
                ),
                hoopProfile = HoopProfile.H100X100
            )
        ).getOrThrow().design
        val name = NamePersonalization.add(
            frame,
            TextMatrixOptions(
                text = "Sophia", heightMm = 8f,
                rotationDegrees = 25f, style = TextStitchStyle.SATIN
            ),
            0, 0
        ).getOrThrow()
        val restored = ProjectCodec.decode(ProjectCodec.encode(name))
        assertEquals(25f, restored.editableName!!.options.rotationDegrees)
        assertEquals(frame.points, restored.editableName!!.original.points)
    }

    @Test
    fun importedFontIdSurvivesProjectV6Roundtrip() {
        val frame = EmbroideryIntegrity.normalize(
            sample().copy(
                points = listOf(
                    EmbroideryPoint(-380, -380, StitchCommand.JUMP, 0),
                    EmbroideryPoint(-380, 380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, 380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(380, -380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-380, -380, StitchCommand.STITCH, 0),
                    EmbroideryPoint(-380, -380, StitchCommand.END, 0)
                ),
                hoopProfile = HoopProfile.H100X100
            )
        ).getOrThrow().design
        val id = "great-vibes-f96de8fe10.ttf"
        val matrix = NamePersonalization.add(
            frame,
            TextMatrixOptions(
                text = "Maria", heightMm = 10f,
                importedFontId = id, style = TextStitchStyle.SATIN
            ), 0, 0,
            importedGenerator = { options ->
                com.timachado.brothermatrizes.core.embroidery.TextMatrixGenerator
                    .generate(options.copy(importedFontId = null))
            }
        ).getOrThrow()
        val saved = ProjectCodec.decode(ProjectCodec.encode(matrix))
        assertEquals(id, saved.editableName?.options?.importedFontId)
        assertEquals(frame.points, saved.editableName?.original?.points)
    }

    @Test
    fun decodedProjectUsesIndependentByteArray() {
        val original =
            sample()

        val restored =
            ProjectCodec
                .decode(
                    ProjectCodec
                        .encode(
                            original
                        )
                )

        assertNotSame(
            original.sourceBytes,
            restored.sourceBytes
        )
    }

    @Test
    fun corruptProjectIsRejected() {
        val result =
            runCatching {
                ProjectCodec
                    .decode(
                        "not-a-project"
                            .toByteArray()
                    )
            }

        assertTrue(
            result.isFailure
        )
    }

    @Test
    fun streamingRoundTripPreservesMatrix() {
        val original =
            sample()

        val output =
            ByteArrayOutputStream()

        ProjectCodec
            .encodeTo(
                design =
                    original,
                sink =
                    output
            )

        val restored =
            ByteArrayInputStream(
                output.toByteArray()
            ).use {
                ProjectCodec.decode(
                    it
                )
            }

        assertEquals(
            original.points,
            restored.points
        )

        assertEquals(
            original.threadColors,
            restored.threadColors
        )

        assertEquals(
            original.hoopProfile,
            restored.hoopProfile
        )
    }


    @Test
    fun horizontalExpansionAndPinchSurviveV8SaveAndReopen() {
        val sample = sample()
        val base = EmbroideryIntegrity.normalize(sample.copy(
            fileName = "quadro-nome.pes",
            points = listOf(
                EmbroideryPoint(-400, -400, StitchCommand.JUMP, 0),
                EmbroideryPoint(-400, 400, StitchCommand.STITCH, 0),
                EmbroideryPoint(400, 400, StitchCommand.STITCH, 0),
                EmbroideryPoint(400, -400, StitchCommand.STITCH, 0),
                EmbroideryPoint(-400, -400, StitchCommand.STITCH, 0),
                EmbroideryPoint(-400, -400, StitchCommand.END, 0)
            ),
            bounds = EmbroideryBounds(0, 0, 0, 0),
            hoopProfile = HoopProfile.H100X100,
            threadColors = listOf(0x123456)
        )).getOrThrow().design
        val options = TextMatrixOptions(
            text = "Maria", heightMm = 8f,
            widthScale = 1.3f, heightScale = 1.15f,
            horizontalGlyphScale = 1.2f,
            style = TextStitchStyle.SATIN
        )
        val named = NamePersonalization.add(base, options, 0, 0).getOrThrow()
        val restored = ProjectCodec.decode(ProjectCodec.encode(named))
        assertEquals(1.3f, restored.editableName!!.options.widthScale, 0.001f)
        assertEquals(1.15f, restored.editableName!!.options.heightScale, 0.001f)
        assertEquals(1.2f,
            restored.editableName!!.options.horizontalGlyphScale, 0.001f)
        assertEquals(base.points, restored.editableName!!.original.points)
        assertEquals(named.points, restored.points)
    }
}
