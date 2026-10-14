package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class TextHoopAutoFitTest {

    private fun importedText(height: Float): Result<EmbroideryDesign> {
        val widthUnits = (height * 30f).toInt()
        val heightUnits = (height * 10f).toInt()
        return Result.success(
            EmbroideryDesign(
                fileName = "Maria.pes",
                format = "PES",
                label = "Maria",
                points = emptyList(),
                bounds = EmbroideryBounds(0, widthUnits, 0, heightUnits),
                stitchCount = 0,
                jumpCount = 0,
                colorChanges = 0,
                endFound = true,
                sourceBytes = byteArrayOf()
            )
        )
    }

    @Test
    fun importedFontFollowsSelectedHoopWithoutExcessiveGenerations() {
        var generations = 0
        fun generated(height: Float): Result<EmbroideryDesign> {
            generations++
            return importedText(height)
        }

        val small = TextHoopAutoFit
            .fitImported(HoopProfile.H100X100, 18f, ::generated)
            .getOrThrow()
        val smallGenerations = generations
        generations = 0
        val larger = TextHoopAutoFit
            .fitImported(HoopProfile.H130X180, small.heightMm, ::generated)
            .getOrThrow()

        assertTrue(smallGenerations <= 3)
        assertTrue(generations <= 3)
        assertTrue(larger.heightMm > small.heightMm)
        assertTrue(larger.design.bounds.widthMm > small.design.bounds.widthMm)
        assertTrue(larger.design.bounds.widthMm <= HoopProfile.H130X180.usableWidthMm)
        assertTrue(larger.design.bounds.heightMm <= HoopProfile.H130X180.usableHeightMm)
    }

    @Test
    fun importedFontShrinksToFitSmallerHoop() {
        val resized = TextHoopAutoFit
            .fitImported(HoopProfile.H100X100, 60f, ::importedText)
            .getOrThrow()

        assertTrue(resized.heightMm < 60f)
        assertTrue(resized.design.bounds.widthMm <= HoopProfile.H100X100.usableWidthMm)
        assertTrue(resized.design.bounds.heightMm <= HoopProfile.H100X100.usableHeightMm)
    }


    @Test
    fun fillsSafeAreaWithoutOverflowing100Hoop() {
        val hoop =
            HoopProfile.H100X100

        val fitted =
            TextHoopAutoFit
                .fit(
                    hoop
                ) {
                        height ->
                    TextLayoutGenerator
                        .generate(
                            TextLayoutOptions(
                                textOptions =
                                    TextMatrixOptions(
                                        text =
                                            "MARIA",
                                        heightMm =
                                            height,
                                        spacingMm =
                                            0f,
                                        style =
                                            TextStitchStyle.RUNNING,
                                        hoopProfile =
                                            hoop
                                    )
                            )
                        )
                }
                .getOrThrow()

        assertTrue(
            fitted.heightMm >
                18f
        )

        assertTrue(
            HoopValidator
                .validate(
                    fitted.design,
                    hoop
                )
                .fits
        )

        assertTrue(
            TextHoopAutoFit
                .dominantFillRatio(
                    fitted
                ) >=
                0.94f
        )
    }

    @Test
    fun largerHoopProducesLargerAutomaticText() {
        fun fitted(
            hoop: HoopProfile
        ) =
            TextHoopAutoFit
                .fit(
                    hoop
                ) {
                        height ->
                    TextLayoutGenerator
                        .generate(
                            TextLayoutOptions(
                                textOptions =
                                    TextMatrixOptions(
                                        text =
                                            "MARIA",
                                        heightMm =
                                            height,
                                        spacingMm =
                                            0f,
                                        style =
                                            TextStitchStyle.RUNNING,
                                        hoopProfile =
                                            hoop
                                    )
                            )
                        )
                }
                .getOrThrow()

        val small =
            fitted(
                HoopProfile.H100X100
            )

        val large =
            fitted(
                HoopProfile.H130X180
            )

        assertTrue(
            large.heightMm >
                small.heightMm
        )

        assertTrue(
            HoopValidator
                .validate(
                    large.design,
                    HoopProfile.H130X180
                )
                .fits
        )
    }
}
