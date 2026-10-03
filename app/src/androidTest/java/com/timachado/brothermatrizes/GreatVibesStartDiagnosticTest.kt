package com.timachado.brothermatrizes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryLoadResult
import com.timachado.brothermatrizes.core.embroidery.FabricProfile
import com.timachado.brothermatrizes.core.embroidery.GeneratedMatrixPipeline
import com.timachado.brothermatrizes.core.embroidery.HoopProfile
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import com.timachado.brothermatrizes.font.ImportedFont
import com.timachado.brothermatrizes.font.ImportedFontMatrixGenerator
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GreatVibesStartDiagnosticTest {

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

        val heights =
            listOf(
                44f,
                45f,
                46f,
                47f,
                48f,
                49f,
                50f
            )

        val report =
            buildString {
                appendLine(
                    "GREATVIBES_REFERENCE_PRISCILA"
                )
                appendLine(
                    "VIDEO_TARGET=155x47mm 3546pts"
                )

                heights.forEach {
                        height ->
                    val options =
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
                                0.4f,
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
                                false
                        )

                    val created =
                        ImportedFontMatrixGenerator
                            .generateText(
                                font =
                                    font,
                                text =
                                    "Priscila",
                                options =
                                    options
                            )
                            .getOrThrow()

                    val canonical =
                        when (
                            val opened =
                                GeneratedMatrixPipeline
                                    .canonicalize(
                                        design =
                                            created,
                                        outputSuffix =
                                            "greatvibes-priscila-" +
                                                height.toInt()
                                    )
                        ) {
                            is EmbroideryLoadResult.Success ->
                                opened.design

                            is EmbroideryLoadResult.Error ->
                                error(
                                    opened.userMessage
                                )
                        }

                    appendLine(
                        "height=" +
                            height +
                            " CREATED=" +
                            created.bounds.widthMm +
                            "x" +
                            created.bounds.heightMm +
                            " pts=" +
                            created.points.size +
                            " stitch=" +
                            created.stitchCount +
                            " jump=" +
                            created.jumpCount +
                            " trim=" +
                            created.points.count {
                                it.command ==
                                    StitchCommand.TRIM
                            } +
                            " firstNorm=" +
                            normalizedFirstStitch(
                                created
                            ) +
                            " CANONICAL=" +
                            canonical.bounds.widthMm +
                            "x" +
                            canonical.bounds.heightMm +
                            " pts=" +
                            canonical.points.size +
                            " stitch=" +
                            canonical.stitchCount +
                            " jump=" +
                            canonical.jumpCount +
                            " trim=" +
                            canonical.points.count {
                                it.command ==
                                    StitchCommand.TRIM
                            }
                    )

                    if (
                        height ==
                            47f
                    ) {
                        appendLine(
                            "H47_FIRST_40"
                        )

                        canonical.points
                            .take(
                                40
                            )
                            .forEachIndexed {
                                    index,
                                    point ->
                                appendLine(
                                    index.toString() +
                                        " " +
                                        point.command.name +
                                        " " +
                                        point.xUnits +
                                        "," +
                                        point.yUnits
                                )
                            }
                    }
                }
            }

        throw AssertionError(
            report
        )
    }

    private fun normalizedFirstStitch(
        design: EmbroideryDesign
    ): Pair<Float, Float> {
        val stitch =
            design.points.first {
                it.command ==
                    StitchCommand.STITCH
            }

        val width =
            (
                design.bounds.maxXUnits -
                    design.bounds.minXUnits
                ).coerceAtLeast(
                1
            )

        val height =
            (
                design.bounds.maxYUnits -
                    design.bounds.minYUnits
                ).coerceAtLeast(
                1
            )

        return (
            stitch.xUnits -
                design.bounds.minXUnits
            ).toFloat() /
            width.toFloat() to
            (
                stitch.yUnits -
                    design.bounds.minYUnits
                ).toFloat() /
            height.toFloat()
    }

    private fun StringBuilder.appendDesign(
        label: String,
        design: EmbroideryDesign
    ) {
        val width =
            (
                design.bounds
                    .maxXUnits -
                    design.bounds
                        .minXUnits
                ).coerceAtLeast(
                1
            )

        val height =
            (
                design.bounds
                    .maxYUnits -
                    design.bounds
                        .minYUnits
                ).coerceAtLeast(
                1
            )

        val firstStitchIndex =
            design.points
                .indexOfFirst {
                    it.command ==
                        StitchCommand.STITCH
                }

        val firstStitch =
            design.points
                .getOrNull(
                    firstStitchIndex
                )

        appendLine(
            "[$label]"
        )
        appendLine(
            "bounds=" +
                design.bounds
                    .minXUnits +
                "," +
                design.bounds
                    .minYUnits +
                ".." +
                design.bounds
                    .maxXUnits +
                "," +
                design.bounds
                    .maxYUnits
        )
        appendLine(
            "sizeMm=" +
                design.bounds
                    .widthMm +
                "x" +
                design.bounds
                    .heightMm
        )
        appendLine(
            "points=" +
                design.points.size +
                " stitches=" +
                design.stitchCount +
                " jumps=" +
                design.jumpCount
        )
        appendLine(
            "firstStitchIndex=" +
                firstStitchIndex
        )

        if (
            firstStitch !=
                null
        ) {
            val nx =
                (
                    firstStitch.xUnits -
                        design.bounds
                            .minXUnits
                    ).toFloat() /
                    width.toFloat()

            val ny =
                (
                    firstStitch.yUnits -
                        design.bounds
                            .minYUnits
                    ).toFloat() /
                    height.toFloat()

            appendLine(
                "firstStitch=" +
                    firstStitch.xUnits +
                    "," +
                    firstStitch.yUnits +
                    " normalized=" +
                    nx +
                    "," +
                    ny
            )
        }

        appendLine(
            "first40:"
        )

        design.points
            .take(
                40
            )
            .forEachIndexed {
                    index,
                    point ->
                appendLine(
                    index.toString() +
                        " " +
                        point.command.name +
                        " " +
                        point.xUnits +
                        "," +
                        point.yUnits
                )
            }
    }
}
