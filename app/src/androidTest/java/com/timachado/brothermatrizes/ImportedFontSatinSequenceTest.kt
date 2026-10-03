package com.timachado.brothermatrizes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timachado.brothermatrizes.core.embroidery.GeneratedMatrixPipeline
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextGlyphProvider
import com.timachado.brothermatrizes.core.embroidery.TextLayoutGenerator
import com.timachado.brothermatrizes.core.embroidery.TextLayoutOptions
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import java.io.File
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportedFontSatinSequenceTest {

    private fun systemFont(): ImportedFont {
        val fontFile =
            listOf(
                File("/system/fonts/Roboto-Regular.ttf"),
                File("/system/fonts/NotoSans-Regular.ttf")
            ).firstOrNull {
                it.isFile
            } ?: error(
                "Fonte de sistema não encontrada no emulador."
            )

        return ImportedFont(
            id = fontFile.name,
            displayName = "Sistema",
            fileName = fontFile.name,
            extension = "ttf",
            absolutePath = fontFile.absolutePath
        )
    }

    @Test
    fun importedSatinNeverReturnsToPreviousGlyphAfterAdvancing() {
        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "AB",
                    options =
                        TextMatrixOptions(
                            text = "AB",
                            heightMm = 25f,
                            spacingMm = 15f,
                            style = TextStitchStyle.SATIN,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val sewn =
            design.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A matriz precisa conter pontos Satin.",
            sewn.isNotEmpty()
        )

        val sortedX =
            sewn
                .map {
                    it.xUnits
                }
                .distinct()
                .sorted()

        val largestGap =
            sortedX
                .zipWithNext()
                .maxByOrNull {
                    it.second -
                        it.first
                }
                ?: error(
                    "Não foi possível separar os dois glifos."
                )

        assertTrue(
            "O teste precisa manter separação clara entre os glifos.",
            largestGap.second -
                largestGap.first >=
                50
        )

        val boundary =
            (
                largestGap.first +
                    largestGap.second
                ) /
                2

        var enteredSecondGlyph =
            false

        sewn.forEach {
                point ->
            if (
                point.xUnits >
                    boundary
            ) {
                enteredSecondGlyph =
                    true
            } else if (
                enteredSecondGlyph
            ) {
                error(
                    "A sequência voltou ao primeiro glifo depois de iniciar o segundo."
                )
            }
        }

        assertTrue(
            "A sequência deve alcançar o segundo glifo.",
            enteredSecondGlyph
        )
    }

    @Test
    fun importedSatinUsesReferenceStartUnderlayAndFinalLock() {
        val withUnderlay =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "I",
                    options =
                        TextMatrixOptions(
                            text = "I",
                            heightMm = 25f,
                            style = TextStitchStyle.SATIN,
                            satinDensityMm = 0.4f,
                            satinPullCompensationMm = 0.2f,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val withoutUnderlay =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "I",
                    options =
                        TextMatrixOptions(
                            text = "I",
                            heightMm = 25f,
                            style = TextStitchStyle.SATIN,
                            satinDensityMm = 0.4f,
                            satinPullCompensationMm = 0.2f,
                            satinUnderlayMode =
                                SatinUnderlayMode.NONE,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val commands =
            withUnderlay.points.filter {
                it.command !=
                    StitchCommand.END
            }

        assertTrue(
            "O primeiro comando do bloco Satin deve ser um salto até a entrada.",
            commands.firstOrNull()?.command ==
                StitchCommand.JUMP
        )

        assertTrue(
            "O underlay central de referência deve adicionar pontos antes da cobertura.",
            withUnderlay.stitchCount >
                withoutUnderlay.stitchCount
        )

        val sewn =
            withUnderlay.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A matriz precisa terminar com a trava de três pontos.",
            sewn.size >=
                3
        )

        val lockA =
            sewn[
                sewn.size -
                    3
            ]

        val lockInside =
            sewn[
                sewn.size -
                    2
            ]

        val lockBack =
            sewn.last()

        assertEquals(
            "A trava final deve voltar exatamente ao ponto A.",
            lockA.xUnits,
            lockBack.xUnits
        )

        assertEquals(
            "A trava final deve voltar exatamente ao ponto A.",
            lockA.yUnits,
            lockBack.yUnits
        )

        val lockLength =
            hypot(
                (
                    lockInside.xUnits -
                        lockA.xUnits
                    ).toDouble(),
                (
                    lockInside.yUnits -
                        lockA.yUnits
                    ).toDouble()
            )

        assertTrue(
            "A trava interna deve medir aproximadamente 0,6 mm.",
            lockLength in
                4.0..8.0
        )

        assertTrue(
            "Depois da trava final não deve existir uma passada de contorno.",
            withUnderlay.points
                .dropWhile {
                    it !==
                        lockBack
                }
                .drop(1)
                .all {
                    it.command ==
                        StitchCommand.END
                }
        )
    }

    @Test
    fun importedSatinDoesNotForceTrimBetweenNearbyLetters() {
        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "II",
                    options =
                        TextMatrixOptions(
                            text = "II",
                            heightMm = 4f,
                            spacingMm = 0f,
                            style = TextStitchStyle.SATIN,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val stitchedX =
            design.points
                .filter {
                    it.command ==
                        StitchCommand.STITCH
                }
                .map {
                    it.xUnits
                }
                .distinct()
                .sorted()

        val largestGap =
            stitchedX
                .zipWithNext()
                .maxByOrNull {
                    it.second -
                        it.first
                }
                ?: error(
                    "Não foi possível localizar a separação entre os glifos."
                )

        val boundary =
            (
                largestGap.first +
                    largestGap.second
                ) /
                2

        val firstRightIndex =
            design.points
                .indexOfFirst {
                    it.command ==
                        StitchCommand.STITCH &&
                        it.xUnits >
                            boundary
                }

        require(
            firstRightIndex >
                0
        )

        val lastLeftIndex =
            design.points
                .subList(
                    0,
                    firstRightIndex
                )
                .indexOfLast {
                    it.command ==
                        StitchCommand.STITCH &&
                        it.xUnits <
                            boundary
                }

        require(
            lastLeftIndex >=
                0
        )

        val transition =
            design.points
                .subList(
                    lastLeftIndex +
                        1,
                    firstRightIndex
                )

        assertFalse(
            "Letras próximas não devem receber TRIM obrigatório na transição entre glifos.",
            transition.any {
                it.command ==
                    StitchCommand.TRIM
            }
        )
    }

    @Test
    fun textLayoutPreservesReferenceSatinSequenceWithoutSecondFinishingPass() {
        val font =
            systemFont()

        val options =
            TextMatrixOptions(
                text = "Maria",
                heightMm = 18f,
                spacingMm = 0f,
                style = TextStitchStyle.SATIN,
                satinDensityMm = 0.4f,
                satinPullCompensationMm = 0.2f,
                satinUnderlayMode =
                    SatinUnderlayMode.CENTER,
                enforceHoop = false
            )

        val direct =
            ImportedFontMatrixGenerator
                .generateText(
                    font = font,
                    text = "Maria",
                    options = options
                )
                .getOrThrow()

        val provider =
            TextGlyphProvider(
                preserveCase = true,
                generate = {
                        char,
                        glyphOptions ->
                    ImportedFontMatrixGenerator
                        .generateGlyph(
                            font = font,
                            char = char,
                            options = glyphOptions
                        )
                },
                generateText = {
                        sourceText,
                        textOptions ->
                    ImportedFontMatrixGenerator
                        .generateText(
                            font = font,
                            text = sourceText,
                            options = textOptions
                        )
                },
                preserveWholeTextSequenceForSatin =
                    true
            )

        val laidOut =
            TextLayoutGenerator
                .generate(
                    TextLayoutOptions(
                        textOptions = options,
                        glyphProvider = provider
                    )
                )
                .getOrThrow()

        val directSequence =
            direct.points.map {
                listOf(
                    it.xUnits,
                    it.yUnits,
                    it.command.ordinal,
                    it.colorIndex
                )
            }

        val layoutSequence =
            laidOut.points.map {
                listOf(
                    it.xUnits,
                    it.yUnits,
                    it.command.ordinal,
                    it.colorIndex
                )
            }

        assertEquals(
            "O layout não pode adicionar tie-in, tie-off, TRIM ou reordenar o Satin importado.",
            directSequence,
            layoutSequence
        )

        assertEquals(
            "O layout precisa preservar a contagem exata de pontos do gerador de referência.",
            direct.stitchCount,
            laidOut.stitchCount
        )

        assertEquals(
            "O layout precisa preservar a contagem exata de saltos do gerador de referência.",
            direct.jumpCount,
            laidOut.jumpCount
        )
    }


    @Test
    fun importedSatinGuideAndStitchesShareTheSameGlyphGeometry() {
        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "Maria",
                    options =
                        TextMatrixOptions(
                            text = "Maria",
                            heightMm = 18f,
                            spacingMm = 0f,
                            style = TextStitchStyle.SATIN,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val sewn =
            design.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        val guide =
            design.guidePoints.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A costura Satin precisa conter pontos.",
            sewn.isNotEmpty()
        )

        assertTrue(
            "O guia do curso precisa existir.",
            guide.isNotEmpty()
        )

        fun centerX(
            points:
                List<
                    com.timachado.brothermatrizes
                        .core
                        .embroidery
                        .EmbroideryPoint
                >
        ): Float =
            (
                points.minOf {
                    it.xUnits
                } +
                    points.maxOf {
                        it.xUnits
                    }
                ) /
                2f

        fun centerY(
            points:
                List<
                    com.timachado.brothermatrizes
                        .core
                        .embroidery
                        .EmbroideryPoint
                >
        ): Float =
            (
                points.minOf {
                    it.yUnits
                } +
                    points.maxOf {
                        it.yUnits
                    }
                ) /
                2f

        assertTrue(
            "O guia rosa e a costura precisam compartilhar o mesmo centro horizontal.",
            kotlin.math.abs(
                centerX(
                    sewn
                ) -
                    centerX(
                        guide
                    )
            ) <=
                5f
        )

        assertTrue(
            "O guia rosa e a costura precisam compartilhar o mesmo centro vertical.",
            kotlin.math.abs(
                centerY(
                    sewn
                ) -
                    centerY(
                        guide
                    )
            ) <=
                5f
        )
    }

    @Test
    fun importedTtfOtfCreationUsesOpenMatrixCanonicalPipeline() {
        val source =
            ImportedFontMatrixGenerator
                .generateText(
                    font =
                        systemFont(),
                    text =
                        "Maria",
                    options =
                        TextMatrixOptions(
                            text =
                                "Maria",
                            heightMm =
                                18f,
                            style =
                                TextStitchStyle.SATIN,
                            outputFormat =
                                "PES",
                            color =
                                0xE63946,
                            enforceHoop =
                                false
                        )
                )
                .getOrThrow()

        val result =
            GeneratedMatrixPipeline
                .canonicalize(
                    design =
                        source,
                    outputSuffix =
                        "android-test"
                )

        assertTrue(
            result is
                EmbroideryLoadResult.Success
        )

        val opened =
            (
                result as
                    EmbroideryLoadResult.Success
                ).design

        assertTrue(
            "A fonte importada precisa virar uma matriz real antes de entrar no Viewer/Simulator.",
            opened.sourceBytes
                .isNotEmpty()
        )

        assertTrue(
            "Depois de reabrir como Abrir Matriz, a sequência deve vir sem guia vetorial do editor.",
            opened.guidePoints
                .isEmpty()
        )

        assertTrue(
            "A matriz canonizada precisa manter pontadas válidas.",
            opened.stitchCount >
                0
        )

        assertEquals(
            source.threadColors,
            opened.threadColors
        )
    }



    @Test
    fun importedSatinCenterUnderlayEntersAtColumnCenterAndCoverageReturnsFromFarEnd() {
        val design =
            ImportedFontMatrixGenerator
                .generateText(
                    font = systemFont(),
                    text = "I",
                    options =
                        TextMatrixOptions(
                            text = "I",
                            heightMm = 25f,
                            style = TextStitchStyle.SATIN,
                            satinDensityMm = 0.4f,
                            satinPullCompensationMm = 0.2f,
                            satinUnderlayMode =
                                SatinUnderlayMode.CENTER,
                            enforceHoop = false
                        )
                )
                .getOrThrow()

        val firstJump =
            design.points.first {
                it.command ==
                    StitchCommand.JUMP
            }

        val centerX =
            (
                design.bounds.minXUnits +
                    design.bounds.maxXUnits
                ) /
                2f

        val distanceToCenter =
            kotlin.math.abs(
                firstJump.xUnits -
                    centerX
            )

        val distanceToNearestEdge =
            minOf(
                kotlin.math.abs(
                    firstJump.xUnits -
                        design.bounds.minXUnits
                ),
                kotlin.math.abs(
                    firstJump.xUnits -
                        design.bounds.maxXUnits
                )
            )

        assertTrue(
            "Com underlay CENTER, a agulha deve entrar pelo centro da coluna Satin, não pela borda.",
            distanceToCenter <
                distanceToNearestEdge
        )

        val stitches =
            design.points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        assertTrue(
            "A sequência Satin precisa conter underlay e cobertura.",
            stitches.size >
                12
        )

        val startY =
            firstJump.yUnits

        val farIndex =
            stitches.indices.maxByOrNull {
                kotlin.math.abs(
                    stitches[it].yUnits -
                        startY
                )
            } ?: error(
                "Não foi possível localizar o extremo da coluna Satin."
            )

        assertTrue(
            "O underlay deve alcançar o extremo antes de a cobertura retornar.",
            farIndex in
                1 until
                    stitches.lastIndex
        )

        val farDistance =
            kotlin.math.abs(
                stitches[farIndex].yUnits -
                    startY
            )

        assertTrue(
            "Depois do extremo, a cobertura Satin deve voltar em direção à entrada da coluna.",
            stitches
                .drop(
                    farIndex +
                        1
                )
                .any {
                    kotlin.math.abs(
                        it.yUnits -
                            startY
                    ) <
                        farDistance *
                            0.75f
                }
        )
    }

}
