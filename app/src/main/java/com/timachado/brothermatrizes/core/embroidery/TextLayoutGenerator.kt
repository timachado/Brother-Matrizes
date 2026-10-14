package com.timachado.brothermatrizes.core.embroidery

import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TextLayoutMode(
    val displayName: String
) {
    STRAIGHT("Reto"),
    ARC_UP("Arco para cima"),
    ARC_DOWN("Arco para baixo")
}

data class LetterAdjustment(
    val sourceIndex: Int,
    val offsetXmm: Float = 0f,
    val offsetYmm: Float = 0f,
    val rotationDegrees: Float = 0f,
    val spacingAfterMm: Float = 0f,
    val color: Int? = null
)

data class TextGlyphProvider(
    val preserveCase: Boolean = false,
    val spacingScale: Float = 1f,
    val generate:
        (Char, TextMatrixOptions) ->
            Result<EmbroideryDesign>,
    val generateText:
        ((
            String,
            TextMatrixOptions
        ) ->
            Result<EmbroideryDesign>)? =
        null,
    val preserveWholeTextSequenceForSatin:
        Boolean =
        false
)

data class TextLayoutOptions(
    val textOptions: TextMatrixOptions,
    val layoutMode: TextLayoutMode =
        TextLayoutMode.STRAIGHT,
    val arcHeightMm: Float = 8f,
    val letterAdjustments:
        List<LetterAdjustment> =
        emptyList(),
    val glyphProvider:
        TextGlyphProvider? =
        null,
    val machineFinishingSettings:
        MachineFinishingSettings =
        MachineFinishingSettings()
)

object TextLayoutGenerator {

    private data class LetterUnit(
        val sourceIndex: Int,
        val char: Char,
        val design: EmbroideryDesign,
        val widthUnits: Float
    )

    fun generate(
        options: TextLayoutOptions
    ): Result<EmbroideryDesign> =
        runCatching {
            val sourceText =
                options.textOptions
                    .text
                    .trim()
                    .take(24)

            require(
                sourceText.isNotBlank()
            ) {
                "Digite um nome."
            }

            require(
                options.arcHeightMm in
                    0f..40f
            ) {
                "O arco deve ficar entre 0 e 40 mm."
            }

            val normalized =
                if (
                    options
                        .glyphProvider
                        ?.preserveCase ==
                        true
                ) {
                    sourceText
                } else {
                    sourceText.uppercase(
                        Locale.forLanguageTag(
                            "pt-BR"
                        )
                    )
                }

            val adjustmentMap =
                options
                    .letterAdjustments
                    .associateBy {
                        it.sourceIndex
                    }

            val wholeTextGenerator =
                options
                    .glyphProvider
                    ?.generateText

            val hasPerLetterChanges =
                options
                    .letterAdjustments
                    .any {
                        it.offsetXmm != 0f ||
                            it.offsetYmm != 0f ||
                            it.rotationDegrees != 0f ||
                            it.spacingAfterMm != 0f ||
                            it.color != null
                    }

            if (
                wholeTextGenerator != null &&
                options.layoutMode ==
                    TextLayoutMode.STRAIGHT &&
                !hasPerLetterChanges
            ) {
                val direct =
                    wholeTextGenerator(
                        normalized,
                        options
                            .textOptions
                            .copy(
                                text =
                                    normalized,
                                enforceHoop =
                                    false
                            )
                    )
                    .getOrThrow()
                    .copy(
                        label =
                            sourceText
                    )

                val preserveDirectSatinSequence =
                    options
                        .glyphProvider
                        ?.preserveWholeTextSequenceForSatin ==
                        true &&
                        options.textOptions
                            .style ==
                        TextStitchStyle.SATIN &&
                        options.textOptions
                            .specialStitchMode ==
                        null

                val finished =
                    if (
                        preserveDirectSatinSequence
                    ) {
                        /*
                         * O gerador Satin importado já emite o bloco
                         * completo no padrão de referência: travel,
                         * TRIM > 5 mm, underlay central e locks de
                         * entrada/saída. Repassar pelo MachineFinishing
                         * duplicaria arremates e mudaria a sequência.
                         */
                        direct
                    } else {
                        MachineFinishing
                            .apply(
                                direct,
                                options
                                    .machineFinishingSettings
                            )
                            .design
                    }

                EmbroideryStressPolicy
                    .requireGeneratedSafe(
                        finished
                    )

                if (
                    options.textOptions
                        .enforceHoop &&
                    options.textOptions
                        .hoopProfile !=
                        null
                ) {
                    val fit =
                        HoopValidator
                            .validate(
                                finished,
                                options
                                    .textOptions
                                    .hoopProfile
                            )

                    require(
                        fit.fits
                    ) {
                        "O texto ultrapassa a área segura do bastidor " +
                            options
                                .textOptions
                                .hoopProfile
                                .displayName +
                            "."
                    }
                }

                return@runCatching finished
            }

            val spacingScale =
                options
                    .glyphProvider
                    ?.spacingScale
                    ?: options
                        .textOptions
                        .font
                        .spacingScale

            val spacingUnits =
                options.textOptions
                    .spacingMm *
                    10f *
                    spacingScale

            val units =
                normalized
                    .mapIndexedNotNull {
                            sourceIndex,
                            char ->
                        if (
                            char ==
                                ' '
                        ) {
                            null
                        } else {
                            val glyphOptions =
                                options
                                    .textOptions
                                    .copy(
                                        text =
                                            char
                                                .toString(),
                                        hoopProfile =
                                            null,
                                        enforceHoop =
                                            false
                                    )

                            val design =
                                (
                                    options
                                        .glyphProvider
                                        ?.generate
                                        ?.invoke(
                                            char,
                                            glyphOptions
                                        )
                                        ?: TextMatrixGenerator
                                            .generate(
                                                glyphOptions
                                            )
                                    )
                                    .getOrThrow()

                            LetterUnit(
                                sourceIndex =
                                    sourceIndex,
                                char =
                                    char,
                                design =
                                    design,
                                widthUnits =
                                    design.bounds
                                        .widthMm *
                                        10f
                            )
                        }
                    }

            require(
                units.isNotEmpty()
            ) {
                "O texto não gerou letras."
            }

            val spaceCountBefore =
                IntArray(
                    normalized.length +
                        1
                )

            for (
                index in
                    normalized.indices
            ) {
                spaceCountBefore[
                    index +
                        1
                ] =
                    spaceCountBefore[
                        index
                    ] +
                    if (
                        normalized[
                            index
                        ] ==
                            ' '
                    ) {
                        1
                    } else {
                        0
                    }
            }

            val defaultSpaceUnits =
                options.textOptions
                    .heightMm *
                    10f *
                    .38f

            val totalWidth =
                units.sumOf {
                    it.widthUnits
                        .toDouble()
                }.toFloat() +
                    spacingUnits *
                        (
                            units.size -
                                1
                            ).coerceAtLeast(
                                0
                            ) +
                    defaultSpaceUnits *
                        normalized.count {
                            it ==
                                ' '
                        } +
                    units.sumOf {
                        unit ->
                        (
                            adjustmentMap[
                                unit.sourceIndex
                            ]?.spacingAfterMm
                                ?: 0f
                            ).toDouble()
                    }.toFloat() *
                    10f

            var cursor =
                -totalWidth /
                    2f

            val merged =
                mutableListOf<
                    EmbroideryPoint
                >()

            var stitches = 0
            var jumps = 0
            var trims = 0
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

            units.forEachIndexed {
                    visibleIndex,
                    unit ->
                val adjustment =
                    adjustmentMap[
                        unit.sourceIndex
                    ] ?: LetterAdjustment(
                        sourceIndex =
                            unit.sourceIndex
                    )

                val letterColor =
                    adjustment.color
                        ?: options
                            .textOptions
                            .color

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
                        merged +=
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

                val previousSourceIndex =
                    if (
                        visibleIndex ==
                            0
                    ) {
                        -1
                    } else {
                        units[
                            visibleIndex -
                                1
                        ].sourceIndex
                    }

                val spacesBetween =
                    if (
                        previousSourceIndex <
                            0
                    ) {
                        spaceCountBefore[
                            unit.sourceIndex
                        ]
                    } else {
                        spaceCountBefore[
                            unit.sourceIndex
                        ] -
                            spaceCountBefore[
                                previousSourceIndex +
                                    1
                            ]
                    }

                cursor +=
                    defaultSpaceUnits *
                        spacesBetween

                val baseCenterX =
                    cursor +
                        unit.widthUnits /
                            2f

                val normalizedX =
                    if (
                        totalWidth <=
                            0.001f
                    ) {
                        0f
                    } else {
                        (
                            baseCenterX /
                                (
                                    totalWidth /
                                        2f
                                    )
                            ).coerceIn(
                                -1f,
                                1f
                            )
                    }

                val arcHeightUnits =
                    options.arcHeightMm *
                        10f

                val arcY =
                    when (
                        options.layoutMode
                    ) {
                        TextLayoutMode.STRAIGHT ->
                            0f

                        TextLayoutMode.ARC_UP ->
                            arcHeightUnits *
                                (
                                    1f -
                                        normalizedX *
                                            normalizedX
                                    )

                        TextLayoutMode.ARC_DOWN ->
                            -arcHeightUnits *
                                (
                                    1f -
                                        normalizedX *
                                            normalizedX
                                    )
                    }

                val tangentRotation =
                    when (
                        options.layoutMode
                    ) {
                        TextLayoutMode.STRAIGHT ->
                            0f

                        TextLayoutMode.ARC_UP ->
                            Math.toDegrees(
                                kotlin.math.atan(
                                    (
                                        -2f *
                                            arcHeightUnits *
                                            normalizedX /
                                            (
                                                totalWidth /
                                                    2f
                                                ).coerceAtLeast(
                                                    1f
                                                )
                                        ).toDouble()
                                )
                            ).toFloat()

                        TextLayoutMode.ARC_DOWN ->
                            Math.toDegrees(
                                kotlin.math.atan(
                                    (
                                        2f *
                                            arcHeightUnits *
                                            normalizedX /
                                            (
                                                totalWidth /
                                                    2f
                                                ).coerceAtLeast(
                                                    1f
                                                )
                                        ).toDouble()
                                )
                            ).toFloat()
                    }

                val centerX =
                    baseCenterX +
                        adjustment
                            .offsetXmm *
                            10f

                val centerY =
                    arcY +
                        adjustment
                            .offsetYmm *
                            10f

                val rotation =
                    tangentRotation +
                        adjustment
                            .rotationDegrees

                val sourceCenterX =
                    (
                        unit.design
                            .bounds
                            .minXUnits +
                            unit.design
                                .bounds
                                .maxXUnits
                        ) /
                        2f

                val sourceCenterY =
                    (
                        unit.design
                            .bounds
                            .minYUnits +
                            unit.design
                                .bounds
                                .maxYUnits
                        ) /
                        2f

                val radians =
                    rotation /
                        180f *
                        PI.toFloat()

                val cosine =
                    cos(radians)

                val sine =
                    sin(radians)

                val translated =
                    unit.design
                        .points
                        .filter {
                            it.command !=
                                StitchCommand.END
                        }
                        .map {
                                point ->
                            val x =
                                point.xUnits -
                                    sourceCenterX

                            val y =
                                point.yUnits -
                                    sourceCenterY

                            val rotatedX =
                                x *
                                    cosine -
                                    y *
                                        sine

                            val rotatedY =
                                x *
                                    sine +
                                    y *
                                        cosine

                            point.copy(
                                colorIndex =
                                    currentColorIndex,
                                xUnits =
                                    (
                                        rotatedX +
                                            centerX
                                        ).roundToInt(),
                                yUnits =
                                    (
                                        rotatedY +
                                            centerY
                                        ).roundToInt()
                            )
                        }

                val first =
                    translated.firstOrNull()

                if (
                    first != null &&
                    hasLast
                ) {
                    val dx =
                        first.xUnits -
                            lastX

                    val dy =
                        first.yUnits -
                            lastY

                    val distance =
                        kotlin.math.hypot(
                            dx.toDouble(),
                            dy.toDouble()
                        )

                    if (
                        distance >
                            40.0
                    ) {
                        merged +=
                            EmbroideryPoint(
                                lastX,
                                lastY,
                                StitchCommand.TRIM,
                                currentColorIndex
                            )

                        trims++
                    }
                }

                translated.forEach {
                        point ->
                    require(
                        merged.size <
                            EmbroideryStressPolicy
                                .MAX_GENERATED_COMMANDS
                    ) {
                        "O texto ficou complexo demais para montar com segurança no celular."
                    }

                    merged +=
                        point

                    when (
                        point.command
                    ) {
                        StitchCommand.STITCH ->
                            stitches++

                        StitchCommand.JUMP ->
                            jumps++

                        else ->
                            Unit
                    }

                    lastX =
                        point.xUnits

                    lastY =
                        point.yUnits

                    hasLast = true
                }

                cursor +=
                    unit.widthUnits +
                        spacingUnits +
                        adjustment
                            .spacingAfterMm *
                            10f
            }

            require(
                stitches >
                    0
            ) {
                "O texto não gerou pontadas."
            }

            val coordinates =
                merged.ifEmpty {
                    error(
                        "Nenhuma pontada foi gerada."
                    )
                }

            val minX =
                coordinates.minOf {
                    it.xUnits
                }

            val maxX =
                coordinates.maxOf {
                    it.xUnits
                }

            val minY =
                coordinates.minOf {
                    it.yUnits
                }

            val maxY =
                coordinates.maxOf {
                    it.yUnits
                }

            val centerX =
                (
                    minX +
                        maxX
                    ) /
                    2f

            val centerY =
                (
                    minY +
                        maxY
                    ) /
                    2f

            val centered =
                merged.map {
                        point ->
                    point.copy(
                        xUnits =
                            (
                                point.xUnits -
                                    centerX
                                ).roundToInt(),
                        yUnits =
                            (
                                point.yUnits -
                                    centerY
                                ).roundToInt()
                    )
                }.toMutableList()

            val endX =
                centered
                    .last()
                    .xUnits

            val endY =
                centered
                    .last()
                    .yUnits

            centered +=
                EmbroideryPoint(
                    endX,
                    endY,
                    StitchCommand.END,
                    0
                )

            val centeredCoordinates =
                centered.filter {
                    it.command !=
                        StitchCommand.END
                }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        centeredCoordinates
                            .minOf {
                                it.xUnits
                            },
                    maxXUnits =
                        centeredCoordinates
                            .maxOf {
                                it.xUnits
                            },
                    minYUnits =
                        centeredCoordinates
                            .minOf {
                                it.yUnits
                            },
                    maxYUnits =
                        centeredCoordinates
                            .maxOf {
                                it.yUnits
                            }
                )

            val safeName =
                Normalizer
                    .normalize(
                        sourceText,
                        Normalizer.Form.NFD
                    )
                    .replace(
                        Regex(
                            "\\p{M}+"
                        ),
                        ""
                    )
                    .replace(
                        Regex(
                            "[^A-Za-z0-9_-]+"
                        ),
                        "_"
                    )
                    .trim('_')
                    .ifBlank {
                        "texto"
                    }

            val design =
                EmbroideryDesign(
                    fileName =
                        "nome-" +
                            safeName +
                            "." +
                            options
                                .textOptions
                                .outputFormat
                                .lowercase(
                                    Locale.ROOT
                                ),
                    format =
                        options
                            .textOptions
                            .outputFormat
                            .uppercase(
                                Locale.ROOT
                            ),
                    label =
                        sourceText,
                    points =
                        centered,
                    bounds =
                        bounds,
                    stitchCount =
                        stitches,
                    jumpCount =
                        jumps,
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
                            .textOptions
                            .hoopProfile,
                    fabricProfile =
                        options
                            .textOptions
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

            EmbroideryStressPolicy
                .requireGeneratedSafe(
                    finished
                )

            if (
                options.textOptions
                    .enforceHoop &&
                options.textOptions
                    .hoopProfile !=
                    null
            ) {
                val fit =
                    HoopValidator
                        .validate(
                            finished,
                            options
                                .textOptions
                                .hoopProfile
                        )

                require(
                    fit.fits
                ) {
                    "O texto ultrapassa a área segura do bastidor " +
                        options
                            .textOptions
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            finished
        }
}
