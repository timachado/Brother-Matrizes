package com.timachado.brothermatrizes.core.project

import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectBackupCodecTest {

    private fun project(
        name: String,
        offset: Int
    ): ByteArray =
        ProjectCodec.encode(
            EmbroideryDesign(
                fileName =
                    name +
                        ".dst",
                format =
                    "DST",
                label =
                    name,
                points =
                    listOf(
                        EmbroideryPoint(
                            offset,
                            0,
                            StitchCommand.JUMP,
                            0
                        ),
                        EmbroideryPoint(
                            offset +
                                20,
                            20,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            offset +
                                20,
                            20,
                            StitchCommand.END,
                            0
                        )
                    ),
                bounds =
                    EmbroideryBounds(
                        offset,
                        offset +
                            20,
                        0,
                        20
                    ),
                stitchCount = 1,
                jumpCount = 1,
                colorChanges = 0,
                endFound = true,
                sourceBytes =
                    ByteArray(0),
                threadColors =
                    listOf(
                        0xE6BE70
                    ),
                isModified = true
            )
        )

    @Test
    fun backupRoundTripPreservesEveryProject() {
        val first =
            project(
                "Maria",
                0
            )

        val second =
            project(
                "Logo",
                100
            )

        val restored =
            ProjectBackupCodec
                .decode(
                    ProjectBackupCodec
                        .encode(
                            listOf(
                                first,
                                second
                            )
                        )
                )

        assertEquals(
            2,
            restored.size
        )

        assertEquals(
            "Maria",
            ProjectCodec
                .decode(
                    restored[0]
                )
                .label
        )

        assertEquals(
            "Logo",
            ProjectCodec
                .decode(
                    restored[1]
                )
                .label
        )
    }

    @Test
    fun emptyBackupIsValid() {
        val restored =
            ProjectBackupCodec
                .decode(
                    ProjectBackupCodec
                        .encode(
                            emptyList()
                        )
                )

        assertTrue(
            restored.isEmpty()
        )
    }

    @Test
    fun arbitraryZipOrGarbageIsRejected() {
        val result =
            runCatching {
                ProjectBackupCodec
                    .decode(
                        "not-a-fioLab-backup"
                            .toByteArray()
                    )
            }

        assertTrue(
            result.isFailure
        )
    }

    @Test
    fun projectInsideBackupIsValidated() {
        val result =
            runCatching {
                ProjectBackupCodec
                    .encode(
                        listOf(
                            "broken-project"
                                .toByteArray()
                        )
                    )
            }

        assertTrue(
            result.isFailure
        )
    }
}
