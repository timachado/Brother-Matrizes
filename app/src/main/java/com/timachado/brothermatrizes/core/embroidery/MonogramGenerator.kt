package com.timachado.brothermatrizes.core.embroidery

import java.text.Normalizer
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class MonogramStyle(
    val displayName: String
) {
    LINEAR("Linear"),
    CLASSIC("Clássico"),
    STACKED("Empilhado")
}

data class MonogramOptions(
    val initials: String,
    val style: MonogramStyle =
        MonogramStyle.CLASSIC,
    val heightMm: Float = 22f,
    val spacingMm: Float = 2f,
    val satinWidthMm: Float = 2.6f,
    val satinDensityMm: Float = 0.45f,
    val satinPullCompensationMm: Float = 0.2f,
    val satinShortStitches: Boolean = true,
    val satinUnderlayMode: SatinUnderlayMode =
        SatinUnderlayMode.BOTH,
    val color: Int = 0xE6BE70,
    val initialColors: List<Int> =
        emptyList(),
    val font: EmbroideryFontPreset =
        EmbroideryFontPreset.LINE,
    val outputFormat: String = "DST",
    val hoopProfile: HoopProfile? =
        HoopProfile.H100X100,
    val fabricProfile: FabricProfile? =
        FabricProfile.COTTON,
    val enforceHoop: Boolean = false,
    val machineFinishingSettings:
        MachineFinishingSettings =
        MachineFinishingSettings()
)

object MonogramGenerator {

    private data class LetterPlacement(
        val design: EmbroideryDesign,
        val centerXUnits: Float,
        val centerYUnits: Float,
        val color: Int
    )

    fun generate(
        options: MonogramOptions
    ): Result<EmbroideryDesign> =
        runCatching {
            val initials =
                sanitizeInitials(
                    options.initials
                )

            require(
                initials.length in 1..3
            ) {
                "Use de 1 a 3 iniciais."
            }

            require(
                options.heightMm in
                    8f..50f
            ) {
                "A altura deve ficar entre 8 e 50 mm."
            }

            require(
                options.spacingMm in
                    0f..12f
            ) {
                "O espaçamento deve ficar entre 0 e 12 mm."
            }

            val outputFormat =
                options.outputFormat
                    .uppercase(
                        Locale.ROOT
                    )

            require(
                outputFormat in
                    MatrixConverter
                        .supportedFormats
            ) {
                "Formato de saída inválido."
            }

            val letters =
                initials.mapIndexed {
                        index,
                        char ->
                    val factor =
                        sizeFactor(
                            style =
                                options.style,
                            count =
                                initials.length,
                            index =
                                index
                        )

                    TextMatrixGenerator
                        .generate(
                            TextMatrixOptions(
                                text =
                                    char.toString(),
                                heightMm =
                                    options.heightMm *
                                        factor,
                                spacingMm = 0f,
                                style =
                                    TextStitchStyle
                                        .SATIN,
                                satinWidthMm =
                                    options
                                        .satinWidthMm,
                                satinDensityMm =
                                    options
                                        .satinDensityMm,
                                satinPullCompensationMm =
                                    options
                                        .satinPullCompensationMm,
                                satinShortStitches =
                                    options
                                        .satinShortStitches,
                                satinUnderlayMode =
                                    options
                                        .satinUnderlayMode,
                                color =
                                    options
                                        .initialColors
                                        .getOrNull(
                                            index
                                        )
                                        ?: options
                                            .color,
                                font =
                                    options.font,
                                outputFormat =
                                    outputFormat,
                                hoopProfile =
                                    null,
                                fabricProfile =
                                    options
                                        .fabricProfile,
                                enforceHoop =
                                    false
                            )
                        )
                        .getOrThrow()
                }

            val placements =
                when (
                    options.style
                ) {
                    MonogramStyle.LINEAR,
                    MonogramStyle.CLASSIC ->
                        horizontalPlacements(
                            letters =
                                letters,
                            spacingUnits =
                                options.spacingMm *
                                    10f
                        )

                    MonogramStyle.STACKED ->
                        stackedPlacements(
                            letters =
                                letters,
                            spacingUnits =
                                options.spacingMm *
                                    10f
                        )
                }

            val mergedPoints =
                mutableListOf<
                    EmbroideryPoint
                >()

            var stitchCount = 0
            var jumpCount = 0
            var lastX = 0
            var lastY = 0
            var hasLast = false

            val threadSequence =
                mutableListOf<Int>()

            var currentBlockColor:
                Int? = null

            var currentColorIndex =
                -1

            var colorChanges =
                0

            placements.forEach {
                    placement ->
                val letterColor =
                    placement.color

                if (
                    currentBlockColor ==
                        null
                ) {
                    threadSequence +=
                        letterColor

                    currentBlockColor =
                        letterColor

                    currentColorIndex =
                        0
                } else if (
                    currentBlockColor !=
                        letterColor
                ) {
                    currentColorIndex++

                    threadSequence +=
                        letterColor

                    if (hasLast) {
                        mergedPoints +=
                            EmbroideryPoint(
                                lastX,
                                lastY,
                                StitchCommand
                                    .COLOR_CHANGE,
                                currentColorIndex
                            )
                    }

                    currentBlockColor =
                        letterColor

                    colorChanges++
                }
                val source =
                    placement.design

                val sourceCenterX =
                    (
                        source.bounds
                            .minXUnits +
                            source.bounds
                                .maxXUnits
                        ) /
                        2f

                val sourceCenterY =
                    (
                        source.bounds
                            .minYUnits +
                            source.bounds
                                .maxYUnits
                        ) /
                        2f

                val translated =
                    source.points
                        .filter {
                            it.command !=
                                StitchCommand.END
                        }
                        .map {
                                point ->
                            point.copy(
                                colorIndex =
                                    currentColorIndex,
                                xUnits =
                                    (
                                        point.xUnits -
                                            sourceCenterX +
                                            placement
                                                .centerXUnits
                                        ).roundToInt(),
                                yUnits =
                                    (
                                        point.yUnits -
                                            sourceCenterY +
                                            placement
                                                .centerYUnits
                                        ).roundToInt()
                            )
                        }

                val first =
                    translated.firstOrNull()

                if (
                    first != null &&
                    hasLast
                ) {
                    val distance =
                        hypot(
                            (
                                first.xUnits -
                                    lastX
                                ).toDouble(),
                            (
                                first.yUnits -
                                    lastY
                                ).toDouble()
                        )

                    if (
                        distance >
                            40.0
                    ) {
                        mergedPoints +=
                            EmbroideryPoint(
                                lastX,
                                lastY,
                                StitchCommand.TRIM,
                                currentColorIndex
                            )
                    }
                }

                translated.forEach {
                        point ->
                    mergedPoints +=
                        point

                    when (
                        point.command
                    ) {
                        StitchCommand.STITCH ->
                            stitchCount++

                        StitchCommand.JUMP ->
                            jumpCount++

                        else ->
                            Unit
                    }

                    lastX =
                        point.xUnits

                    lastY =
                        point.yUnits

                    hasLast = true
                }
            }

            require(
                stitchCount >
                    0
            ) {
                "O monograma não gerou pontadas."
            }

            mergedPoints +=
                EmbroideryPoint(
                    lastX,
                    lastY,
                    StitchCommand.END,
                    0
                )

            val coordinatePoints =
                mergedPoints
                    .filter {
                        it.command !=
                            StitchCommand.END
                    }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        coordinatePoints
                            .minOf {
                                it.xUnits
                            },
                    maxXUnits =
                        coordinatePoints
                            .maxOf {
                                it.xUnits
                            },
                    minYUnits =
                        coordinatePoints
                            .minOf {
                                it.yUnits
                            },
                    maxYUnits =
                        coordinatePoints
                            .maxOf {
                                it.yUnits
                            }
                )

            val safeName =
                Normalizer
                    .normalize(
                        initials,
                        Normalizer
                            .Form
                            .NFD
                    )
                    .replace(
                        Regex(
                            "\\p{M}+"
                        ),
                        ""
                    )
                    .replace(
                        Regex(
                            "[^A-Za-z0-9]+"
                        ),
                        ""
                    )
                    .ifBlank {
                        "MONO"
                    }

            val design =
                EmbroideryDesign(
                    fileName =
                        "monograma-" +
                            safeName +
                            "." +
                            outputFormat
                                .lowercase(
                                    Locale.ROOT
                                ),
                    format =
                        outputFormat,
                    label =
                        "Monograma " +
                            initials,
                    points =
                        mergedPoints,
                    bounds =
                        bounds,
                    stitchCount =
                        stitchCount,
                    jumpCount =
                        jumpCount,
                    colorChanges =
                        colorChanges,
                    endFound = true,
                    sourceBytes =
                        ByteArray(0),
                    threadColors =
                        threadSequence,
                    isModified = true,
                    hoopProfile =
                        options
                            .hoopProfile,
                    fabricProfile =
                        options
                            .fabricProfile
                )

            val finished =
                MachineFinishing
                    .apply(
                        design,
                        options
                            .machineFinishingSettings
                    )
                    .design

            if (
                options.enforceHoop &&
                options.hoopProfile !=
                    null
            ) {
                val fit =
                    HoopValidator
                        .validate(
                            finished,
                            options
                                .hoopProfile
                        )

                require(
                    fit.fits
                ) {
                    "O monograma ultrapassa a área segura do bastidor " +
                        options
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            finished
        }

    private fun sanitizeInitials(
        value: String
    ): String =
        value
            .trim()
            .uppercase(
                Locale.forLanguageTag(
                    "pt-BR"
                )
            )
            .filter {
                it.isLetter()
            }

    private fun sizeFactor(
        style: MonogramStyle,
        count: Int,
        index: Int
    ): Float =
        when (style) {
            MonogramStyle.LINEAR ->
                1f

            MonogramStyle.CLASSIC ->
                if (
                    count ==
                        3 &&
                    index ==
                        1
                ) {
                    1.28f
                } else if (
                    count ==
                        3
                ) {
                    0.82f
                } else {
                    1f
                }

            MonogramStyle.STACKED ->
                if (
                    count ==
                        3 &&
                    index ==
                        0
                ) {
                    1.08f
                } else {
                    0.88f
                }
        }

    private fun horizontalPlacements(
        letters: List<EmbroideryDesign>,
        spacingUnits: Float
    ): List<LetterPlacement> {
        val widths =
            letters.map {
                it.bounds
                    .maxXUnits -
                    it.bounds
                        .minXUnits
            }

        val totalWidth =
            widths.sum()
                .toFloat() +
                spacingUnits *
                    (
                        letters.size -
                            1
                        ).coerceAtLeast(
                            0
                        )

        var cursor =
            -totalWidth /
                2f

        return letters.mapIndexed {
                index,
                design ->
            val width =
                widths[index]
                    .toFloat()

            val center =
                cursor +
                    width /
                        2f

            cursor +=
                width +
                    spacingUnits

            LetterPlacement(
                design =
                    design,
                centerXUnits =
                    center,
                centerYUnits =
                    0f,
                color =
                    design.threadColors
                        .firstOrNull()
                        ?: 0xE6BE70
            )
        }
    }

    private fun stackedPlacements(
        letters: List<EmbroideryDesign>,
        spacingUnits: Float
    ): List<LetterPlacement> {
        val heights =
            letters.map {
                it.bounds
                    .maxYUnits -
                    it.bounds
                        .minYUnits
            }

        val totalHeight =
            heights.sum()
                .toFloat() +
                spacingUnits *
                    (
                        letters.size -
                            1
                        ).coerceAtLeast(
                            0
                        )

        var cursor =
            totalHeight /
                2f

        return letters.mapIndexed {
                index,
                design ->
            val height =
                heights[index]
                    .toFloat()

            val center =
                cursor -
                    height /
                        2f

            cursor -=
                height +
                    spacingUnits

            LetterPlacement(
                design =
                    design,
                centerXUnits =
                    0f,
                centerYUnits =
                    center,
                color =
                    design.threadColors
                        .firstOrNull()
                        ?: 0xE6BE70
            )
        }
    }
}
