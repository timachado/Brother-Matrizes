package com.timachado.brothermatrizes.core.project

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.MachineFinishingInfo
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
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

}
