package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryIntegrityTest {
    @Test
    fun normalizesDerivedCountersAndBounds() {
        val design =
            EmbroideryDesign(
                fileName =
                    "teste.pes",
                format =
                    "pes",
                label =
                    "Teste",
                points =
                    listOf(
                        EmbroideryPoint(
                            -20,
                            -10,
                            StitchCommand.JUMP,
                            0
                        ),
                        EmbroideryPoint(
                            30,
                            40,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            30,
                            40,
                            StitchCommand.COLOR_CHANGE,
                            1
                        ),
                        EmbroideryPoint(
                            50,
                            70,
                            StitchCommand.STITCH,
                            1
                        ),
                        EmbroideryPoint(
                            50,
                            70,
                            StitchCommand.END,
                            1
                        )
                    ),
                bounds =
                    EmbroideryBounds(
                        0,
                        0,
                        0,
                        0
                    ),
                stitchCount =
                    99,
                jumpCount =
                    99,
                colorChanges =
                    99,
                endFound =
                    false,
                sourceBytes =
                    byteArrayOf(
                        1
                    )
            )

        val report =
            EmbroideryIntegrity
                .normalize(
                    design
                )
                .getOrThrow()

        assertEquals(
            "PES",
            report.design
                .format
        )

        assertEquals(
            2,
            report.design
                .stitchCount
        )

        assertEquals(
            1,
            report.design
                .jumpCount
        )

        assertEquals(
            1,
            report.design
                .colorChanges
        )

        assertTrue(
            report.design
                .endFound
        )

        assertEquals(
            EmbroideryBounds(
                -20,
                50,
                -10,
                70
            ),
            report.design
                .bounds
        )

        assertTrue(
            report.warnings
                .isNotEmpty()
        )
    }

    @Test
    fun rejectsDesignWithoutStitches() {
        val design =
            EmbroideryDesign(
                fileName =
                    "quebrado.dst",
                format =
                    "DST",
                label =
                    null,
                points =
                    listOf(
                        EmbroideryPoint(
                            0,
                            0,
                            StitchCommand.JUMP,
                            0
                        ),
                        EmbroideryPoint(
                            0,
                            0,
                            StitchCommand.END,
                            0
                        )
                    ),
                bounds =
                    EmbroideryBounds(
                        0,
                        0,
                        0,
                        0
                    ),
                stitchCount =
                    0,
                jumpCount =
                    1,
                colorChanges =
                    0,
                endFound =
                    true,
                sourceBytes =
                    byteArrayOf()
            )

        assertFalse(
            EmbroideryIntegrity
                .normalize(
                    design
                )
                .isSuccess
        )
    }

    @Test
    fun missingEndIsWarningNotBlocking() {
        val design =
            EmbroideryDesign(
                fileName =
                    "sem-end.jef",
                format =
                    "JEF",
                label =
                    null,
                points =
                    listOf(
                        EmbroideryPoint(
                            0,
                            0,
                            StitchCommand.STITCH,
                            0
                        ),
                        EmbroideryPoint(
                            10,
                            10,
                            StitchCommand.STITCH,
                            0
                        )
                    ),
                bounds =
                    EmbroideryBounds(
                        0,
                        10,
                        0,
                        10
                    ),
                stitchCount =
                    2,
                jumpCount =
                    0,
                colorChanges =
                    0,
                endFound =
                    false,
                sourceBytes =
                    byteArrayOf()
            )

        val report =
            EmbroideryIntegrity
                .normalize(
                    design
                )
                .getOrThrow()

        assertFalse(
            report.design
                .endFound
        )

        assertTrue(
            report.warnings
                .any {
                    it.contains(
                        "END"
                    )
                }
        )
    }
}
