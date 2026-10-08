package com.timachado.brothermatrizes

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GreatVibesStartDiagnosticTest {

    private data class Candidate(
        val heightMm: Float,
        val densityMm: Float,
        val widthMm: Float,
        val designHeightMm: Float,
        val points: Int,
        val stitches: Int,
        val jumps: Int,
        val trims: Int,
        val score: Float,
        val firstPoints: List<String>,
        val sequenceCsv: String
    )

    @Test
    fun comparePriscilaAgainstReferenceVideo() {
        val instrumentation =
            InstrumentationRegistry
                .getInstrumentation()

        val testContext =
            instrumentation.context

        val targetContext =
            instrumentation.targetContext

        val fontFile =
            File(
                targetContext.cacheDir,
                "GreatVibes-Regular.ttf"
            )

        testContext.assets
            .open(
                "GreatVibes-Regular.ttf"
            )
            .use {
                    input ->
                fontFile.outputStream()
                    .use {
                        output ->
                    input.copyTo(
                        output
                    )
                }
            }

        val font =
            ImportedFont(
                id =
                    fontFile.name,
                displayName =
                    "Great Vibes",
                fileName =
                    fontFile.name,
                extension =
                    "ttf",
                absolutePath =
                    fontFile.absolutePath
            )

        /*
         * O selo 47 x 155 mm do vídeo descreve o bounds FINAL da matriz,
         * não LetterHeightMm. O diagnóstico antigo usava height=47 e portanto
         * comparava a referência contra uma entrada diferente.
         *
         * Testamos um pequeno envelope plausível de altura/densidade e usamos
         * a combinação mais próxima apenas para validar a equivalência
         * geométrica/operacional do motor.
         */
        val heights =
            listOf(
                38f,
                39f,
                40f,
                41f,
                42f
            )

        val densities =
            listOf(
                0.4f,
                0.5f,
                0.6f
            )

        val targetWidth =
            47f

        val targetHeight =
            155f

        val targetPoints =
            3546

        val candidates =
            mutableListOf<
                Candidate
            >()

        heights.forEach {
                height ->
            densities.forEach {
                    density ->
                val design =
                    ImportedFontMatrixGenerator
                        .generateText(
                            font =
                                font,
                            text =
                                "Priscila",
                            options =
                                TextMatrixOptions(
                                    text =
                                        "Priscila",
                                    heightMm =
                                        height,
                                    spacingMm =
                                        0f,
                                    stitchLengthMm =
                                        2.5f,
                                    style =
                                        TextStitchStyle.SATIN,
                                    satinWidthMm =
                                        2.4f,
                                    satinDensityMm =
                                        density,
                                    satinPullCompensationMm =
                                        0.2f,
                                    satinShortStitches =
                                        true,
                                    satinUnderlayMode =
                                        SatinUnderlayMode.CENTER,
                                    specialStitchMode =
                                        null,
                                    color =
                                        0xE63946,
                                    outputFormat =
                                        "DST",
                                    hoopProfile =
                                        HoopProfile.H200X300,
                                    fabricProfile =
                                        FabricProfile.COTTON,
                                    enforceHoop =
                                        false,
                                    rotationDegrees =
                                        90f
                                )
                        )
                        .getOrThrow()

                val widthError =
                    abs(
                        design.bounds.widthMm -
                            targetWidth
                    ) /
                        targetWidth

                val heightError =
                    abs(
                        design.bounds.heightMm -
                            targetHeight
                    ) /
                        targetHeight

                val pointError =
                    abs(
                        design.points.size -
                            targetPoints
                    )
                        .toFloat() /
                        targetPoints
                            .toFloat()

                val candidate =
                    Candidate(
                        heightMm =
                            height,
                        densityMm =
                            density,
                        widthMm =
                            design.bounds.widthMm,
                        designHeightMm =
                            design.bounds.heightMm,
                        points =
                            design.points.size,
                        stitches =
                            design.stitchCount,
                        jumps =
                            design.jumpCount,
                        trims =
                            design.points.count {
                                it.command ==
                                    StitchCommand.TRIM
                            },
                        score =
                            widthError +
                                heightError +
                                pointError,
                        firstPoints =
                            design.points
                                .take(
                                    40
                                )
                                .map {
                                        point ->
                                    point.command.name +
                                        " " +
                                        point.xUnits +
                                        "," +
                                        point.yUnits
                                },
                        sequenceCsv =
                            buildString {
                                appendLine(
                                    "index,command,xUnits,yUnits,colorIndex"
                                )

                                design.points
                                    .forEachIndexed {
                                            index,
                                            point ->
                                        append(
                                            index
                                        )
                                        append(
                                            ','
                                        )
                                        append(
                                            point.command.name
                                        )
                                        append(
                                            ','
                                        )
                                        append(
                                            point.xUnits
                                        )
                                        append(
                                            ','
                                        )
                                        append(
                                            point.yUnits
                                        )
                                        append(
                                            ','
                                        )
                                        appendLine(
                                            point.colorIndex
                                        )
                                    }
                            }
                    )

                candidates +=
                    candidate

                Log.i(
                    "GreatVibesDiagnostic",
                    "CANDIDATE height=" +
                        height +
                        " density=" +
                        density +
                        " size=" +
                        candidate.widthMm +
                        "x" +
                        candidate.designHeightMm +
                        " points=" +
                        candidate.points +
                        " stitches=" +
                        candidate.stitches +
                        " jumps=" +
                        candidate.jumps +
                        " trims=" +
                        candidate.trims +
                        " score=" +
                        candidate.score
                )
            }
        }

        val best =
            candidates.minBy {
                it.score
            }

        Log.i(
            "GreatVibesDiagnostic",
            "BEST height=" +
                best.heightMm +
                " density=" +
                best.densityMm +
                " size=" +
                best.widthMm +
                "x" +
                best.designHeightMm +
                " points=" +
                best.points +
                " stitches=" +
                best.stitches +
                " jumps=" +
                best.jumps +
                " trims=" +
                best.trims +
                " score=" +
                best.score
        )

        File(
            targetContext.filesDir,
            "greatvibes-best-sequence.csv"
        )
            .writeText(
                best.sequenceCsv
            )

        best.firstPoints
            .forEachIndexed {
                    index,
                    point ->
                Log.i(
                    "GreatVibesDiagnostic",
                    "BEST_POINT " +
                        index +
                        " " +
                        point
                )
            }

        assertTrue(
            "Nenhuma configuração do motor chegou perto da largura de 47 mm. Melhor: " +
                best,
            abs(
                best.widthMm -
                    targetWidth
            ) <=
                7f
        )

        assertTrue(
            "Nenhuma configuração do motor chegou perto da altura de 155 mm. Melhor: " +
                best,
            abs(
                best.designHeightMm -
                    targetHeight
            ) <=
                15f
        )

        assertTrue(
            "A densidade de pontos ainda está longe da referência de 3546 pontos. Melhor: " +
                best,
            abs(
                best.points -
                    targetPoints
            ) <=
                1200
        )
    }

    @Test
    fun diagnoseMariaVisualStartOnGreatVibes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val fixture = File(targetContext.cacheDir, "GreatVibes-Regular.ttf")
        instrumentation.context.assets.open("GreatVibes-Regular.ttf").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        val font = ImportedFont(
            id = fixture.name,
            displayName = "Great Vibes",
            fileName = fixture.name,
            extension = "ttf",
            absolutePath = fixture.absolutePath
        )
        val options = TextMatrixOptions(
            text = "Maria",
            heightMm = 60f,
            spacingMm = 0f,
            stitchLengthMm = 2.5f,
            style = TextStitchStyle.SATIN,
            satinWidthMm = 2.4f,
            satinDensityMm = 0.4f,
            satinPullCompensationMm = 0.2f,
            satinShortStitches = true,
            satinUnderlayMode = SatinUnderlayMode.CENTER,
            specialStitchMode = null,
            color = 0xE63946,
            outputFormat = "PES",
            hoopProfile = HoopProfile.H200X300,
            fabricProfile = FabricProfile.COTTON,
            enforceHoop = false,
            rotationDegrees = 0f
        )
        val design = ImportedFontMatrixGenerator.generateText(
            font = font, text = "Maria", options = options
        ).getOrThrow()
        val first = design.points.first { it.command != StitchCommand.TRIM }
        val b = design.bounds
        Log.i("GreatVibesDiagnostic", "MARIA size=" + b.widthMm + "x" + b.heightMm + " pts=" + design.points.size)
        Log.i("GreatVibesDiagnostic", "MARIA first=" + first.command + " " + first.xUnits + "," + first.yUnits
             + " bounds=" + b.minXUnits + "," + b.minYUnits + ".." + b.maxXUnits + "," + b.maxYUnits)
        Log.i("GreatVibesDiagnostic", "MARIA structural:\n" +
            com.timachado.brothermatrizes.font.PeDesignImportedFontEngine.debugRealStartGeometry(
                font = font, sourceText = "Maria", options = options
            )
        )
        design.points.take(35).forEachIndexed { index, p ->
            Log.i("GreatVibesDiagnostic", "MARIA_POINT " + index + " " + p.command + " " + p.xUnits + "," + p.yUnits)
        }
        assertTrue("Generated stitches must be nonempty.", design.stitchCount > 100)
    }

}
