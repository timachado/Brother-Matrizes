package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixEditorTest {

    @Test
    fun scalesAndKeepsCenter() {
        val source = sampleDesign()

        val result =
            MatrixEditor.apply(
                source,
                EditTransform(
                    scale = 2f
                )
            )

        assertEquals(
            20f,
            result.bounds.widthMm,
            0.01f
        )

        assertEquals(
            10f,
            result.bounds.heightMm,
            0.01f
        )

        assertEquals(
            centerX(source),
            centerX(result)
        )

        assertEquals(
            centerY(source),
            centerY(result)
        )
    }

    @Test
    fun rotatesNinetyDegrees() {
        val result =
            MatrixEditor.apply(
                sampleDesign(),
                EditTransform(
                    rotationDegrees = 90f
                )
            )

        assertEquals(
            5f,
            result.bounds.widthMm,
            0.11f
        )

        assertEquals(
            10f,
            result.bounds.heightMm,
            0.11f
        )
    }

    @Test
    fun mirrorsHorizontally() {
        val source = sampleDesign()

        val result =
            MatrixEditor.apply(
                source,
                EditTransform(
                    mirrorHorizontal =
                        true
                )
            )

        assertEquals(
            source.bounds.widthMm,
            result.bounds.widthMm,
            0.01f
        )

        assertTrue(
            result.points[0].xUnits >
                result.points[1].xUnits
        )
    }

    @Test
    fun centersAtOriginAndMoves() {
        val result =
            MatrixEditor.apply(
                sampleDesign(),
                EditTransform(
                    centerAtOrigin = true,
                    offsetXUnits = 50,
                    offsetYUnits = -20
                )
            )

        assertEquals(
            50,
            centerX(result)
        )

        assertEquals(
            -20,
            centerY(result)
        )
    }

    @Test
    fun replacesThreadColors() {
        val colors =
            listOf(
                0x112233,
                0x445566
            )

        val result =
            MatrixEditor.apply(
                sampleDesign(),
                EditTransform(
                    threadColors = colors
                )
            )

        assertEquals(
            colors,
            result.threadColors
        )

        assertTrue(
            result.isModified
        )
    }

    private fun centerX(
        design: EmbroideryDesign
    ): Int =
        (
            design.bounds.minXUnits +
                design.bounds.maxXUnits
            ) / 2

    private fun centerY(
        design: EmbroideryDesign
    ): Int =
        (
            design.bounds.minYUnits +
                design.bounds.maxYUnits
            ) / 2

    private fun sampleDesign(): EmbroideryDesign =
        EmbroideryDesign(
            fileName = "editor.dst",
            format = "DST",
            label = "FIOLAB",
            points =
                listOf(
                    EmbroideryPoint(
                        100,
                        100,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        200,
                        100,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        200,
                        150,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        100,
                        150,
                        StitchCommand.STITCH,
                        0
                    ),
                    EmbroideryPoint(
                        100,
                        100,
                        StitchCommand.END,
                        0
                    )
                ),
            bounds =
                EmbroideryBounds(
                    minXUnits = 100,
                    maxXUnits = 200,
                    minYUnits = 100,
                    maxYUnits = 150
                ),
            stitchCount = 4,
            jumpCount = 0,
            colorChanges = 0,
            endFound = true,
            sourceBytes =
                ByteArray(0),
            threadColors =
                listOf(0xE6BE70)
        )
}
