package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertTrue
import org.junit.Test

class TextHoopAutoFitTest {

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
