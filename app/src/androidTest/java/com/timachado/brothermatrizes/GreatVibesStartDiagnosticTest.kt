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
    fun dumpMariaGreatVibesRealStartSequence() {
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

        val options =
            TextMatrixOptions(
                text =
                    "Maria",
                heightMm =
                    18f,
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
                    HoopProfile.H100X100,
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
                        "Maria",
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
                                "greatvibes-diagnostic"
                        )
            ) {
                is EmbroideryLoadResult.Success ->
                    opened.design

                is EmbroideryLoadResult.Error ->
                    error(
                        opened.userMessage
                    )
            }

        val report =
            buildString {
                appendLine(
                    "FONT=GreatVibes-Regular.ttf"
                )
                appendLine(
                    "TEXT=Maria"
                )
                appendLine(
                    "OPTIONS=height18 spacing0 satin density0.4 pull0.2 underlayCENTER DST"
                )
                appendLine(
                    "[START_GEOMETRY]"
                )
                appendLine(
                    com.timachado.brothermatrizes.font.ReferenceImportedFontEngine
                        .debugRealStartGeometry(
                            font =
                                font,
                            sourceText =
                                "Maria",
                            options =
                                options
                        )
                )
                appendDesign(
                    label =
                        "CREATED",
                    design =
                        created
                )
                appendDesign(
                    label =
                        "CANONICAL",
                    design =
                        canonical
                )
            }

        assertTrue(
            created.points.any {
                it.command ==
                    StitchCommand.STITCH
            }
        )

        assertTrue(
            canonical.points.any {
                it.command ==
                    StitchCommand.STITCH
            }
        )

        val createdStart =
            normalizedFirstStitch(
                created
            )

        val canonicalStart =
            normalizedFirstStitch(
                canonical
            )

        assertTrue(
            "Great Vibes / Maria deve iniciar no ponto marcado: x entre 18% e 21%.\n" +
                report,
            createdStart.first in
                0.18f..0.21f
        )

        assertTrue(
            "Great Vibes / Maria deve iniciar cerca de 29% acima da base antes do round-trip.\n" +
                report,
            createdStart.second in
                0.24f..0.34f
        )

        assertTrue(
            "O round-trip DST deve preservar o mesmo X inicial.\n" +
                report,
            canonicalStart.first in
                0.18f..0.21f
        )

        assertTrue(
            "Após o DST, o Y é espelhado mas deve continuar representando o mesmo ponto físico.\n" +
                report,
            canonicalStart.second in
                0.66f..0.76f
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
