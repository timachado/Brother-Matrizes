package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbroideryProfilesTest {

    @Test
    fun smallNameFits100Hoop() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "ANA",
                        heightMm = 12f,
                        style =
                            TextStitchStyle.SATIN,
                        hoopProfile =
                            HoopProfile.H100X100,
                        enforceHoop = true
                    )
                )
                .getOrThrow()

        val fit =
            HoopValidator.validate(
                design,
                HoopProfile.H100X100
            )

        assertTrue(fit.fits)

        assertEquals(
            HoopProfile.H100X100,
            design.hoopProfile
        )
    }

    @Test
    fun oversizedNameIsRejectedBySelectedHoop() {
        val result =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text =
                            "NOME MUITO COMPRIDO",
                        heightMm = 30f,
                        spacingMm = 5f,
                        style =
                            TextStitchStyle.SATIN,
                        hoopProfile =
                            HoopProfile.H100X100,
                        enforceHoop = true
                    )
                )

        assertTrue(result.isFailure)

        assertTrue(
            result.exceptionOrNull()
                ?.message
                ?.contains(
                    "bastidor",
                    ignoreCase = true
                ) ==
                true
        )
    }

    @Test
    fun largerHoopAcceptsLongerName() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "BROTHER",
                        heightMm = 20f,
                        style =
                            TextStitchStyle.SATIN,
                        hoopProfile =
                            HoopProfile.H140X200,
                        enforceHoop = true
                    )
                )
                .getOrThrow()

        assertTrue(
            HoopValidator
                .validate(
                    design,
                    HoopProfile.H140X200
                )
                .fits
        )
    }

    @Test
    fun fabricPresetsExposeEditableStartingValues() {
        assertEquals(
            0.20f,
            FabricProfile
                .COTTON
                .pullCompensationMm,
            0.001f
        )

        assertEquals(
            0.35f,
            FabricProfile
                .KNIT
                .pullCompensationMm,
            0.001f
        )

        assertEquals(
            SatinUnderlayMode.BOTH,
            FabricProfile
                .TOWEL
                .underlayMode
        )

        assertFalse(
            HoopProfile
                .H100X100
                .usableWidthMm >=
                HoopProfile
                    .H100X100
                    .widthMm
        )
    }

    @Test
    fun designKeepsSelectedFabricProfile() {
        val design =
            TextMatrixGenerator
                .generate(
                    TextMatrixOptions(
                        text = "FIO",
                        style =
                            TextStitchStyle.SATIN,
                        fabricProfile =
                            FabricProfile.KNIT,
                        hoopProfile =
                            HoopProfile.H100X100
                    )
                )
                .getOrThrow()

        assertEquals(
            FabricProfile.KNIT,
            design.fabricProfile
        )
    }
}
