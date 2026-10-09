package com.timachado.brothermatrizes.core.embroidery

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class TextStitchStyle(
    val displayName: String
) {
    RUNNING("Ponto corrido"),
    SATIN("Satin")
}

enum class FontCategory(
    val displayName: String
) {
    ESSENTIAL("Essencial"),
    MODERN("Moderna"),
    ELEGANT("Elegante"),
    DECORATIVE("Decorativa")
}

enum class EmbroideryFontPreset(
    val displayName: String,
    val category: FontCategory,
    val description: String,
    val xScale: Float,
    val spacingScale: Float,
    val slant: Float = 0f,
    val serifAmount: Float = 0f,
    val swashAmount: Float = 0f,
    val centerShift: Float = 0f
) {
    LINE(
        "Fio Linha",
        FontCategory.ESSENTIAL,
        "Equilibrada e versátil",
        1f,
        1f
    ),
    COMPACT(
        "Fio Compacta",
        FontCategory.ESSENTIAL,
        "Mais estreita para espaços pequenos",
        0.82f,
        0.86f
    ),
    WIDE(
        "Fio Larga",
        FontCategory.ESSENTIAL,
        "Presença maior e leitura aberta",
        1.18f,
        1.08f
    ),
    MODERN(
        "Fio Moderna",
        FontCategory.MODERN,
        "Geométrica com proporção limpa",
        0.96f,
        0.94f,
        centerShift = -0.035f
    ),
    ITALIC(
        "Fio Itálica",
        FontCategory.MODERN,
        "Inclinação suave e dinâmica",
        1f,
        0.96f,
        slant = 0.20f
    ),
    ELEGANT(
        "Fio Elegante",
        FontCategory.ELEGANT,
        "Inclinação discreta com serifas",
        0.94f,
        1.02f,
        slant = 0.08f,
        serifAmount = 0.12f
    ),
    ORNAMENTAL(
        "Fio Ornamental",
        FontCategory.DECORATIVE,
        "Serifas e prolongamentos para monogramas",
        1.03f,
        1.06f,
        slant = 0.05f,
        serifAmount = 0.16f,
        swashAmount = 0.18f
    )
}

data class TextMatrixOptions(
    val text: String,
    val heightMm: Float = 10f,
    val spacingMm: Float = 1.2f,
    val stitchLengthMm: Float = 2.5f,
    val style: TextStitchStyle = TextStitchStyle.RUNNING,
    val satinWidthMm: Float = 2.4f,
    val satinDensityMm: Float = 0.4f,
    val satinPullCompensationMm: Float = 0.2f,
    val satinShortStitches: Boolean = true,
    val satinUnderlayMode: SatinUnderlayMode = SatinUnderlayMode.BOTH,
    val specialStitchMode: SpecialStitchMode? = null,
    val color: Int = 0xE6BE70,
    val font: EmbroideryFontPreset = EmbroideryFontPreset.LINE,
    val outputFormat: String = "DST",
    val hoopProfile: HoopProfile? = null,
    val fabricProfile: FabricProfile? = null,
    val enforceHoop: Boolean = false,
    val rotationDegrees: Float = 0f
)

object TextMatrixGenerator {

    private data class P(
        val x: Float,
        val y: Float
    )

    private data class Glyph(
        val strokes: List<List<P>>,
        val width: Float = 0.72f
    )

    private data class StrokeBuildResult(
        val currentX: Int,
        val currentY: Int,
        val stitchCount: Int,
        val jumpCount: Int
    )

    private val glyphs: Map<Char, Glyph> = mapOf(
        'A' to g(
            s(p(.05f, 0f), p(.5f, 1f), p(.95f, 0f)),
            s(p(.23f, .43f), p(.77f, .43f))
        ),
        'B' to g(
            s(p(.08f, 0f), p(.08f, 1f), p(.58f, 1f), p(.86f, .82f), p(.58f, .55f), p(.08f, .55f)),
            s(p(.58f, .55f), p(.9f, .35f), p(.58f, 0f), p(.08f, 0f))
        ),
        'C' to g(
            s(p(.92f, .86f), p(.7f, 1f), p(.25f, 1f), p(.06f, .75f), p(.06f, .25f), p(.25f, 0f), p(.7f, 0f), p(.92f, .14f))
        ),
        'D' to g(
            s(p(.08f, 0f), p(.08f, 1f), p(.53f, 1f), p(.9f, .72f), p(.9f, .28f), p(.53f, 0f), p(.08f, 0f))
        ),
        'E' to g(
            s(p(.9f, 1f), p(.08f, 1f), p(.08f, 0f), p(.9f, 0f)),
            s(p(.08f, .52f), p(.72f, .52f))
        ),
        'F' to g(
            s(p(.08f, 0f), p(.08f, 1f), p(.9f, 1f)),
            s(p(.08f, .52f), p(.72f, .52f))
        ),
        'G' to g(
            s(p(.92f, .82f), p(.72f, 1f), p(.25f, 1f), p(.06f, .74f), p(.06f, .25f), p(.25f, 0f), p(.72f, 0f), p(.92f, .2f), p(.92f, .48f), p(.55f, .48f))
        ),
        'H' to g(
            s(p(.08f, 0f), p(.08f, 1f)),
            s(p(.92f, 0f), p(.92f, 1f)),
            s(p(.08f, .5f), p(.92f, .5f))
        ),
        'I' to g(
            s(p(.16f, 1f), p(.84f, 1f)),
            s(p(.5f, 1f), p(.5f, 0f)),
            s(p(.16f, 0f), p(.84f, 0f)),
            width = .55f
        ),
        'J' to g(
            s(p(.12f, 1f), p(.88f, 1f)),
            s(p(.68f, 1f), p(.68f, .22f), p(.5f, 0f), p(.2f, 0f), p(.06f, .2f))
        ),
        'K' to g(
            s(p(.08f, 0f), p(.08f, 1f)),
            s(p(.9f, 1f), p(.08f, .42f)),
            s(p(.35f, .62f), p(.92f, 0f))
        ),
        'L' to g(
            s(p(.08f, 1f), p(.08f, 0f), p(.92f, 0f))
        ),
        'M' to g(
            s(p(.06f, 0f), p(.06f, 1f), p(.5f, .48f), p(.94f, 1f), p(.94f, 0f)),
            width = .86f
        ),
        'N' to g(
            s(p(.08f, 0f), p(.08f, 1f), p(.92f, 0f), p(.92f, 1f))
        ),
        'O' to g(
            s(p(.28f, 0f), p(.08f, .22f), p(.08f, .78f), p(.28f, 1f), p(.72f, 1f), p(.92f, .78f), p(.92f, .22f), p(.72f, 0f), p(.28f, 0f))
        ),
        'P' to g(
            s(p(.08f, 0f), p(.08f, 1f), p(.6f, 1f), p(.9f, .78f), p(.6f, .53f), p(.08f, .53f))
        ),
        'Q' to g(
            s(p(.28f, 0f), p(.08f, .22f), p(.08f, .78f), p(.28f, 1f), p(.72f, 1f), p(.92f, .78f), p(.92f, .22f), p(.72f, 0f), p(.28f, 0f)),
            s(p(.57f, .25f), p(.98f, -.08f))
        ),
        'R' to g(
            s(p(.08f, 0f), p(.08f, 1f), p(.6f, 1f), p(.9f, .78f), p(.6f, .53f), p(.08f, .53f)),
            s(p(.48f, .53f), p(.94f, 0f))
        ),
        'S' to g(
            s(p(.9f, .84f), p(.7f, 1f), p(.25f, 1f), p(.06f, .78f), p(.23f, .56f), p(.72f, .45f), p(.92f, .22f), p(.72f, 0f), p(.22f, 0f), p(.06f, .16f))
        ),
        'T' to g(
            s(p(.06f, 1f), p(.94f, 1f)),
            s(p(.5f, 1f), p(.5f, 0f))
        ),
        'U' to g(
            s(p(.08f, 1f), p(.08f, .22f), p(.3f, 0f), p(.7f, 0f), p(.92f, .22f), p(.92f, 1f))
        ),
        'V' to g(
            s(p(.06f, 1f), p(.5f, 0f), p(.94f, 1f))
        ),
        'W' to g(
            s(p(.04f, 1f), p(.24f, 0f), p(.5f, .56f), p(.76f, 0f), p(.96f, 1f)),
            width = .9f
        ),
        'X' to g(
            s(p(.08f, 1f), p(.92f, 0f)),
            s(p(.92f, 1f), p(.08f, 0f))
        ),
        'Y' to g(
            s(p(.06f, 1f), p(.5f, .52f), p(.94f, 1f)),
            s(p(.5f, .52f), p(.5f, 0f))
        ),
        'Z' to g(
            s(p(.08f, 1f), p(.92f, 1f), p(.08f, 0f), p(.92f, 0f))
        ),
        '0' to g(
            s(p(.28f, 0f), p(.08f, .22f), p(.08f, .78f), p(.28f, 1f), p(.72f, 1f), p(.92f, .78f), p(.92f, .22f), p(.72f, 0f), p(.28f, 0f)),
            s(p(.22f, .14f), p(.78f, .86f))
        ),
        '1' to g(
            s(p(.28f, .76f), p(.5f, 1f), p(.5f, 0f)),
            s(p(.22f, 0f), p(.78f, 0f)),
            width = .52f
        ),
        '2' to g(
            s(p(.1f, .76f), p(.28f, 1f), p(.72f, 1f), p(.9f, .76f), p(.1f, 0f), p(.92f, 0f))
        ),
        '3' to g(
            s(p(.12f, .86f), p(.3f, 1f), p(.72f, 1f), p(.9f, .78f), p(.58f, .52f), p(.9f, .24f), p(.72f, 0f), p(.28f, 0f), p(.1f, .14f))
        ),
        '4' to g(
            s(p(.72f, 0f), p(.72f, 1f), p(.08f, .32f), p(.94f, .32f))
        ),
        '5' to g(
            s(p(.88f, 1f), p(.14f, 1f), p(.1f, .55f), p(.7f, .55f), p(.9f, .32f), p(.72f, 0f), p(.24f, 0f), p(.08f, .16f))
        ),
        '6' to g(
            s(p(.84f, .86f), p(.68f, 1f), p(.3f, 1f), p(.08f, .72f), p(.08f, .22f), p(.28f, 0f), p(.7f, 0f), p(.9f, .24f), p(.7f, .5f), p(.12f, .5f))
        ),
        '7' to g(
            s(p(.08f, 1f), p(.94f, 1f), p(.36f, 0f))
        ),
        '8' to g(
            s(p(.28f, .52f), p(.1f, .7f), p(.22f, 1f), p(.72f, 1f), p(.9f, .7f), p(.72f, .52f), p(.28f, .52f), p(.08f, .28f), p(.26f, 0f), p(.74f, 0f), p(.92f, .28f), p(.72f, .52f))
        ),
        '9' to g(
            s(p(.16f, .14f), p(.32f, 0f), p(.7f, 0f), p(.92f, .28f), p(.92f, .78f), p(.72f, 1f), p(.3f, 1f), p(.1f, .76f), p(.3f, .5f), p(.88f, .5f))
        ),
        '-' to g(
            s(p(.18f, .48f), p(.82f, .48f)),
            width = .5f
        ),
        '.' to g(
            s(p(.47f, .02f), p(.53f, .02f)),
            width = .3f
        ),
        '\'' to g(
            s(p(.48f, 1f), p(.42f, .78f)),
            width = .28f
        )
    )

    fun generate(
        options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        runCatching {
            val cleanText =
                options.text
                    .trim()
                    .take(24)

            require(cleanText.isNotBlank()) {
                "Digite um nome."
            }

            require(options.heightMm in 4f..60f) {
                "A altura deve ficar entre 4 e 60 mm."
            }

            require(options.spacingMm in 0f..8f) {
                "O espaçamento deve ficar entre 0 e 8 mm."
            }

            require(options.stitchLengthMm in 1f..5f) {
                "O comprimento de ponto deve ficar entre 1 e 5 mm."
            }

            require(options.satinWidthMm in 1f..6f) {
                "A largura Satin deve ficar entre 1 e 6 mm."
            }

            require(options.satinDensityMm in 0.3f..1.2f) {
                "A densidade Satin deve ficar entre 0,3 e 1,2 mm."
            }

            require(options.satinPullCompensationMm in 0f..1f) {
                "A compensação de repuxo deve ficar entre 0 e 1 mm."
            }

            val outputFormat =
                options.outputFormat
                    .uppercase(Locale.ROOT)

            require(
                outputFormat in
                    MatrixConverter.supportedFormats
            ) {
                "Formato de saída inválido."
            }

            val heightUnits =
                options.heightMm * 10f

            val spacingUnits =
                options.spacingMm *
                    10f *
                    options.font.spacingScale

            val stitchUnits =
                options.stitchLengthMm * 10f

            val satinWidthUnits =
                options.satinWidthMm * 10f

            val satinStepUnits =
                options.satinDensityMm * 10f

            val points =
                mutableListOf<EmbroideryPoint>()

            var cursorX = 0f
            var currentX = 0
            var currentY = 0
            var stitchCount = 0
            var jumpCount = 0

            val normalized =
                cleanText
                    .uppercase(
                        Locale.forLanguageTag(
                            "pt-BR"
                        )
                    )

            normalized.forEach {
                    originalChar ->
                if (originalChar == ' ') {
                    cursorX +=
                        heightUnits * .38f +
                            spacingUnits

                    return@forEach
                }

                val (
                    baseChar,
                    accent
                ) = decompose(
                    originalChar
                )

                val glyph =
                    glyphs[baseChar]
                        ?: glyphs['-']!!

                val glyphWidth =
                    heightUnits *
                        glyph.width *
                        options.font.xScale

                val strokes =
                    styledStrokes(
                        glyph =
                            glyph,
                        accent =
                            accent,
                        font =
                            options.font
                    )

                strokes.forEach {
                        stroke ->
                    if (
                        stroke.size < 2
                    ) {
                        return@forEach
                    }

                    val unitStroke =
                        stroke.map {
                            point ->
                            toUnits(
                                point,
                                cursorX,
                                glyphWidth,
                                heightUnits
                            )
                        }

                    val effectiveStyle =
                        if (
                            options.specialStitchMode !=
                                null
                        ) {
                            TextStitchStyle.RUNNING
                        } else {
                            options.style
                        }

                    val built =
                        when (
                            effectiveStyle
                        ) {
                            TextStitchStyle.RUNNING ->
                                appendRunningStroke(
                                    points =
                                        points,
                                    stroke =
                                        unitStroke,
                                    currentX =
                                        currentX,
                                    currentY =
                                        currentY,
                                    stitchUnits =
                                        stitchUnits
                                )

                            TextStitchStyle.SATIN ->
                                appendSatinStroke(
                                    points =
                                        points,
                                    stroke =
                                        unitStroke,
                                    currentX =
                                        currentX,
                                    currentY =
                                        currentY,
                                    satinWidthUnits =
                                        satinWidthUnits,
                                    satinStepUnits =
                                        satinStepUnits,
                                    pullCompensationUnits =
                                        options
                                            .satinPullCompensationMm *
                                            10f,
                                    shortStitches =
                                        options
                                            .satinShortStitches,
                                    underlayMode =
                                        options
                                            .satinUnderlayMode
                                )
                        }

                    currentX =
                        built.currentX

                    currentY =
                        built.currentY

                    stitchCount +=
                        built.stitchCount

                    jumpCount +=
                        built.jumpCount
                }

                cursorX +=
                    glyphWidth +
                        spacingUnits
            }

            require(stitchCount > 0) {
                "O texto não gerou pontadas."
            }

            require(
                points.size <=
                    if (
                        options.specialStitchMode !=
                            null
                    ) {
                        EmbroideryStressPolicy
                            .MAX_GENERATED_COMMANDS /
                            16
                    } else {
                        EmbroideryStressPolicy
                            .MAX_GENERATED_COMMANDS
                    }
            ) {
                "O texto ficou complexo demais para gerar com segurança no celular."
            }

            val transformedPoints =
                SpecialStitchProcessor
                    .apply(
                        points =
                            points,
                        mode =
                            options
                                .specialStitchMode
                    )

            val lastPoint =
                transformedPoints
                    .lastOrNull()
                    ?: EmbroideryPoint(
                        currentX,
                        currentY,
                        StitchCommand.JUMP,
                        0
                    )

            transformedPoints +=
                EmbroideryPoint(
                    lastPoint.xUnits,
                    lastPoint.yUnits,
                    StitchCommand.END,
                    lastPoint.colorIndex
                )

            val coordinates =
                transformedPoints.filter {
                    it.command !=
                        StitchCommand.END
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

            val safeName =
                Normalizer
                    .normalize(
                        cleanText,
                        Normalizer.Form.NFD
                    )
                    .replace(
                        Regex("\\p{M}+"),
                        ""
                    )
                    .replace(
                        Regex("[^A-Za-z0-9_-]+"),
                        "_"
                    )
                    .trim('_')
                    .take(32)
                    .ifBlank {
                        "nome"
                    }

            val design =
                EmbroideryDesign(
                    fileName =
                        "nome-" +
                            safeName +
                            "." +
                            outputFormat.lowercase(
                                Locale.ROOT
                            ),
                    format =
                        outputFormat,
                    label =
                        cleanText,
                    points =
                        transformedPoints,
                    bounds =
                        EmbroideryBounds(
                            minXUnits =
                                minX,
                            maxXUnits =
                                maxX,
                            minYUnits =
                                minY,
                            maxYUnits =
                                maxY
                        ),
                    stitchCount =
                        transformedPoints.count {
                            it.command ==
                                StitchCommand.STITCH
                        },
                    jumpCount =
                        transformedPoints.count {
                            it.command ==
                                StitchCommand.JUMP
                        },
                    colorChanges = 0,
                    endFound = true,
                    sourceBytes =
                        ByteArray(0),
                    threadColors =
                        listOf(
                            options.color
                        ),
                    isModified = true,
                    hoopProfile =
                        options.hoopProfile,
                    fabricProfile =
                        options.fabricProfile
                )

            EmbroideryStressPolicy
                .requireGeneratedSafe(
                    design
                )

            if (
                options.enforceHoop &&
                options.hoopProfile != null
            ) {
                val fit =
                    HoopValidator.validate(
                        design,
                        options.hoopProfile
                    )

                require(fit.fits) {
                    "A matriz ultrapassa a área segura do bastidor " +
                        options.hoopProfile.displayName +
                        "."
                }
            }

            design
        }

    private fun styledStrokes(
        glyph: Glyph,
        accent: Char?,
        font: EmbroideryFontPreset
    ): List<List<P>> {
        val base =
            buildList {
                addAll(
                    glyph.strokes
                )

                addAll(
                    accentStrokes(
                        accent
                    )
                )
            }

        val transformed =
            base.map {
                    stroke ->
                stroke.map {
                        point ->
                    transformPoint(
                        point =
                            point,
                        font =
                            font
                    )
                }
            }.toMutableList()

        if (
            font.serifAmount >
                0f
        ) {
            val serif =
                font.serifAmount

            glyph.strokes
                .forEach {
                        stroke ->
                    val endpoints =
                        listOf(
                            stroke.first(),
                            stroke.last()
                        )

                    endpoints.forEach {
                            point ->
                        if (
                            point.y <=
                                0.10f ||
                            point.y >=
                                0.90f
                        ) {
                            val center =
                                transformPoint(
                                    point,
                                    font
                                )

                            transformed +=
                                listOf(
                                    P(
                                        center.x -
                                            serif,
                                        center.y
                                    ),
                                    P(
                                        center.x +
                                            serif,
                                        center.y
                                    )
                                )
                        }
                    }
                }
        }

        if (
            font.swashAmount >
                0f
        ) {
            val swash =
                font.swashAmount

            transformed +=
                listOf(
                    transformPoint(
                        P(
                            -swash,
                            0.04f
                        ),
                        font
                    ),
                    transformPoint(
                        P(
                            0.18f,
                            0f
                        ),
                        font
                    )
                )

            transformed +=
                listOf(
                    transformPoint(
                        P(
                            0.82f,
                            1f
                        ),
                        font
                    ),
                    transformPoint(
                        P(
                            1f +
                                swash,
                            0.92f
                        ),
                        font
                    )
                )
        }

        return transformed
    }

    private fun transformPoint(
        point: P,
        font: EmbroideryFontPreset
    ): P {
        val y =
            point.y

        val slantedX =
            point.x +
                font.slant *
                    (
                        y -
                            0.5f
                        )

        val centerAdjustedX =
            slantedX +
                font.centerShift *
                    (
                        1f -
                            kotlin.math.abs(
                                y -
                                    0.5f
                            ) *
                            2f
                        )

        return P(
            x =
                centerAdjustedX,
            y =
                y
        )
    }

    private fun appendRunningStroke(
        points: MutableList<EmbroideryPoint>,
        stroke: List<Pair<Int, Int>>,
        currentX: Int,
        currentY: Int,
        stitchUnits: Float
    ): StrokeBuildResult {
        var x =
            currentX

        var y =
            currentY

        var stitches = 0
        var jumps = 0

        val first =
            stroke.first()

        if (
            x != first.first ||
            y != first.second ||
            points.isEmpty()
        ) {
            points +=
                EmbroideryPoint(
                    first.first,
                    first.second,
                    StitchCommand.JUMP,
                    0
                )

            x = first.first
            y = first.second
            jumps++
        }

        for (
            index in
                1 until stroke.size
        ) {
            val target =
                stroke[index]

            val dx =
                target.first -
                    x

            val dy =
                target.second -
                    y

            val distance =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )

            val segments =
                maxOf(
                    1,
                    ceil(
                        distance /
                            stitchUnits
                    ).toInt()
                )

            val startX = x
            val startY = y

            for (
                part in
                    1..segments
            ) {
                val ratio =
                    part.toDouble() /
                        segments

                val px =
                    (
                        startX +
                            dx * ratio
                        ).roundToInt()

                val py =
                    (
                        startY +
                            dy * ratio
                        ).roundToInt()

                points +=
                    EmbroideryPoint(
                        px,
                        py,
                        StitchCommand.STITCH,
                        0
                    )

                stitches++
            }

            x = target.first
            y = target.second
        }

        return StrokeBuildResult(
            currentX = x,
            currentY = y,
            stitchCount = stitches,
            jumpCount = jumps
        )
    }

    private fun appendSatinStroke(
        points: MutableList<EmbroideryPoint>,
        stroke: List<Pair<Int, Int>>,
        currentX: Int,
        currentY: Int,
        satinWidthUnits: Float,
        satinStepUnits: Float,
        pullCompensationUnits: Float,
        shortStitches: Boolean,
        underlayMode: SatinUnderlayMode
    ): StrokeBuildResult {
        val result =
            SatinGenerator.append(
                points = points,
                stroke = stroke,
                currentX = currentX,
                currentY = currentY,
                widthUnits = satinWidthUnits,
                stepUnits = satinStepUnits,
                pullCompensationUnits =
                    pullCompensationUnits,
                shortStitches =
                    shortStitches,
                underlayMode =
                    underlayMode
            )

        return StrokeBuildResult(
            currentX =
                result.currentX,
            currentY =
                result.currentY,
            stitchCount =
                result.stitchCount,
            jumpCount =
                result.jumpCount
        )
    }

    private fun toUnits(
        point: P,
        cursorX: Float,
        width: Float,
        height: Float
    ): Pair<Int, Int> =
        Pair(
            (
                cursorX +
                    point.x * width
                ).roundToInt(),
            (
                point.y *
                    height
                ).roundToInt()
        )

    private fun decompose(
        char: Char
    ): Pair<Char, Char?> =
        when (char) {
            'Á', 'É', 'Í', 'Ó', 'Ú' ->
                Pair(
                    stripAccent(char),
                    '´'
                )

            'À', 'È', 'Ì', 'Ò', 'Ù' ->
                Pair(
                    stripAccent(char),
                    '`'
                )

            'Â', 'Ê', 'Î', 'Ô', 'Û' ->
                Pair(
                    stripAccent(char),
                    '^'
                )

            'Ã', 'Õ', 'Ñ' ->
                Pair(
                    stripAccent(char),
                    '~'
                )

            'Ä', 'Ë', 'Ï', 'Ö', 'Ü' ->
                Pair(
                    stripAccent(char),
                    '¨'
                )

            'Ç' ->
                Pair(
                    'C',
                    ','
                )

            else ->
                Pair(
                    char,
                    null
                )
        }

    private fun stripAccent(
        char: Char
    ): Char =
        Normalizer
            .normalize(
                char.toString(),
                Normalizer.Form.NFD
            )
            .first()

    private fun accentStrokes(
        accent: Char?
    ): List<List<P>> =
        when (accent) {
            '´' ->
                listOf(
                    s(
                        p(.48f, 1.04f),
                        p(.67f, 1.18f)
                    )
                )

            '`' ->
                listOf(
                    s(
                        p(.52f, 1.04f),
                        p(.33f, 1.18f)
                    )
                )

            '^' ->
                listOf(
                    s(
                        p(.32f, 1.04f),
                        p(.5f, 1.18f),
                        p(.68f, 1.04f)
                    )
                )

            '~' ->
                listOf(
                    s(
                        p(.27f, 1.08f),
                        p(.4f, 1.16f),
                        p(.58f, 1.06f),
                        p(.72f, 1.14f)
                    )
                )

            '¨' ->
                listOf(
                    s(
                        p(.36f, 1.1f),
                        p(.39f, 1.1f)
                    ),
                    s(
                        p(.61f, 1.1f),
                        p(.64f, 1.1f)
                    )
                )

            ',' ->
                listOf(
                    s(
                        p(.54f, -.04f),
                        p(.45f, -.18f)
                    )
                )

            else ->
                emptyList()
        }

    private fun p(
        x: Float,
        y: Float
    ) = P(x, y)

    private fun s(
        vararg points: P
    ): List<P> =
        points.toList()

    private fun g(
        vararg strokes: List<P>,
        width: Float = .72f
    ) = Glyph(
        strokes =
            strokes.toList(),
        width =
            width
    )
}
