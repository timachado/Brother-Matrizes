package com.timachado.brothermatrizes.core.embroidery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonogramGeneratorTest {

    @Test
    fun generatesOneTwoAndThreeInitials() {
        listOf(
            "A",
            "TM",
            "ABC"
        ).forEach {
                initials ->
            val design =
                MonogramGenerator
                    .generate(
                        MonogramOptions(
                            initials =
                                initials,
                            heightMm =
                                18f
                        )
                    )
                    .getOrThrow()

            assertTrue(
                design.stitchCount >
                    0
            )

            assertEquals(
                initials,
                design.label
                    ?.removePrefix(
                        "Monograma "
                    )
            )
        }
    }

    @Test
    fun classicThreeLetterLayoutDiffersFromLinear() {
        val classic =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "ABC",
                        style =
                            MonogramStyle
                                .CLASSIC
                    )
                )
                .getOrThrow()

        val linear =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "ABC",
                        style =
                            MonogramStyle
                                .LINEAR
                    )
                )
                .getOrThrow()

        assertNotEquals(
            classic.points,
            linear.points
        )

        assertTrue(
            classic.bounds.heightMm >
                0f
        )
    }

    @Test
    fun stackedLayoutIsTallerThanLinearForThreeLetters() {
        val stacked =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "ABC",
                        style =
                            MonogramStyle
                                .STACKED,
                        heightMm =
                            14f
                    )
                )
                .getOrThrow()

        val linear =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "ABC",
                        style =
                            MonogramStyle
                                .LINEAR,
                        heightMm =
                            14f
                    )
                )
                .getOrThrow()

        assertTrue(
            stacked.bounds.heightMm >
                linear.bounds.heightMm
        )
    }

    @Test
    fun selectedHoopCanRejectOversizedMonogram() {
        val result =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "ABC",
                        heightMm =
                            48f,
                        style =
                            MonogramStyle
                                .STACKED,
                        hoopProfile =
                            HoopProfile
                                .H100X100,
                        enforceHoop =
                            true
                    )
                )

        assertTrue(
            result.isFailure
        )
    }

    @Test
    fun monogramExportsToAllReleasedFormats() {
        val source =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "TM",
                        heightMm =
                            18f,
                        hoopProfile =
                            HoopProfile
                                .H100X100
                    )
                )
                .getOrThrow()

        MatrixConverter
            .supportedFormats
            .forEach {
                    format ->
                val converted =
                    MatrixConverter
                        .convert(
                            source,
                            format,
                            "monograma-teste"
                        )
                        .getOrThrow()

                assertTrue(
                    converted.bytes
                        .isNotEmpty()
                )
            }
    }
    @Test
    fun monogramInitialColorsCreateColorChanges() {
        val design =
            MonogramGenerator
                .generate(
                    MonogramOptions(
                        initials =
                            "ABC",
                        initialColors =
                            listOf(
                                0xE6BE70,
                                0x457B9D,
                                0xE63946
                            )
                    )
                )
                .getOrThrow()

        assertTrue(
            design.colorChanges ==
                2
        )

        assertTrue(
            design.threadColors.size ==
                3
        )

        assertTrue(
            design.points
                .count {
                    it.command ==
                        StitchCommand
                            .COLOR_CHANGE
                } ==
                2
        )
    }

}
