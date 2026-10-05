package com.timachado.brothermatrizes.font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.SpecialStitchProcessor
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import com.timachado.brothermatrizes.core.embroidery.TextStitchStyle
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImportedFontMatrixGenerator {

    fun generateGlyph(
        font: ImportedFont,
        char: Char,
        options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        generateWithReferenceSatin(
            font = font,
            sourceText =
                char.toString(),
            options =
                options,
            filePrefix =
                "fonte"
        )

    fun generateText(
        font: ImportedFont,
        text: String,
        options: TextMatrixOptions
    ): Result<EmbroideryDesign> =
        generateWithReferenceSatin(
            font = font,
            sourceText =
                text,
            options =
                options,
            filePrefix =
                "nome"
        )

    /**
     * O caminho Satin TTF/OTF deve ter uma única fonte de verdade.
     *
     * ReferenceImportedFontEngine contém o emissor validado contra o vídeo
     * de referência: entrada pelo centro quando há underlay, passada central
     * única até o extremo, cobertura Satin no retorno, locks e viagens reais.
     *
     * Manter uma segunda implementação aqui fazia "Criar Nome" divergir da
     * sequência já validada mesmo usando o mesmo SimulatorScreen depois.
     */
    private fun generateWithReferenceSatin(
        font: ImportedFont,
        sourceText: String,
        options: TextMatrixOptions,
        filePrefix: String
    ): Result<EmbroideryDesign> =
        generateTextInternal(
            font = font,
            sourceText =
                sourceText,
            options =
                options,
            filePrefix =
                filePrefix
        )

    private fun generateTextInternal(
        font: ImportedFont,
        sourceText: String,
        options: TextMatrixOptions,
        filePrefix: String
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                options.heightMm in 4f..60f
            ) {
                "A altura deve ficar entre 4 e 60 mm."
            }

            require(
                options.stitchLengthMm in 1f..5f
            ) {
                "O comprimento de ponto deve ficar entre 1 e 5 mm."
            }

            require(
                options.satinWidthMm in 1f..6f
            ) {
                "A largura Satin deve ficar entre 1 e 6 mm."
            }

            require(
                options.satinDensityMm in 0.3f..1.2f
            ) {
                "A densidade Satin deve ficar entre 0,3 e 1,2 mm."
            }

            require(
                options.satinPullCompensationMm in 0f..1f
            ) {
                "A compensação de repuxo deve ficar entre 0 e 1 mm."
            }

            val text =
                sourceText
                    .trim()
                    .take(24)

            require(
                text.isNotBlank()
            ) {
                "Digite um nome."
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

            val typeface =
                ImportedFontStore
                    .loadTypeface(font)
                    .getOrThrow()

            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    this.typeface =
                        typeface
                    textSize =
                        1000f
                    style =
                        Paint.Style.FILL
                }

            val renderableText =
                text.map {
                        char ->
                    resolveCharacter(
                        paint = paint,
                        font = font,
                        char = char
                    )
                }.joinToString("")

            val path =
                Path()

            paint.getTextPath(
                renderableText,
                0,
                renderableText.length,
                0f,
                0f,
                path
            )

            require(!path.isEmpty) {
                "A fonte não gerou um contorno utilizável para este texto."
            }

            val pathBounds =
                RectF().also {
                    path.computeBounds(
                        it,
                        true
                    )
                }

            require(
                pathBounds.width() >
                    0.5f &&
                    pathBounds.height() >
                    0.5f
            ) {
                "A fonte não gerou uma área utilizável para este texto."
            }

            val targetHeightUnits =
                options.heightMm *
                    10f

            val effectiveStyle =
                if (
                    options.specialStitchMode !=
                        null
                ) {
                    TextStitchStyle.RUNNING
                } else {
                    options.style
                }

            val guideContours:
                List<SampledContour>

            val points:
                MutableList<EmbroideryPoint>

            if (
                effectiveStyle ==
                    TextStitchStyle.SATIN
            ) {
                /*
                 * Caminho de referência do Mão Design 18.1.2.
                 *
                 * A geometria permanece em ponto flutuante até a emissão
                 * das pontadas. Cada glifo é medido/posicionado primeiro,
                 * todos são centralizados como conjunto e somente então
                 * cada contorno fechado é amostrado pelo PathMeasure.
                 */
                val reference =
                    buildReferenceGlyphGeometry(
                        paint = paint,
                        text = renderableText,
                        targetHeightUnits =
                            targetHeightUnits,
                        spacingUnits =
                            targetHeightUnits *
                                0.04f +
                                options.spacingMm *
                                    10f
                    )

                guideContours =
                    reference.guideContours

                points =
                    buildStrokeFlowSatinText(
                        glyphPolygons =
                            reference.glyphPolygons,
                        options =
                            options
                    )
            } else {
                val capPath =
                    Path()

                val capSample =
                    if (
                        paint.hasGlyph(
                            "H"
                        )
                    ) {
                        "H"
                    } else {
                        renderableText
                            .first()
                            .toString()
                    }

                paint.getTextPath(
                    capSample,
                    0,
                    capSample.length,
                    0f,
                    0f,
                    capPath
                )

                val capBounds =
                    RectF().also {
                        capPath.computeBounds(
                            it,
                            true
                        )
                    }

                val fallbackCapHeight =
                    kotlin.math.abs(
                        paint.fontMetrics
                            .ascent
                    ) *
                        0.72f

                val capHeightRaw =
                    if (
                        capBounds.height() >
                            0.5f
                    ) {
                        capBounds.height()
                    } else {
                        fallbackCapHeight
                    }
                        .coerceAtLeast(
                            1f
                        )

                val scale =
                    targetHeightUnits /
                        capHeightRaw

                guideContours =
                    samplePath(
                        path
                    )
                        .map {
                                contour ->
                            SampledContour(
                                points =
                                    contour.points
                                        .map {
                                            point ->
                                            Pair(
                                                (
                                                    (
                                                        point.first -
                                                            pathBounds
                                                                .centerX()
                                                        ) *
                                                        scale
                                                    ).roundToInt(),
                                                (
                                                    (
                                                        pathBounds
                                                            .centerY() -
                                                            point.second
                                                        ) *
                                                        scale
                                                    ).roundToInt()
                                            )
                                        }
                                        .fold(
                                            mutableListOf<
                                                Pair<Int, Int>
                                            >()
                                        ) {
                                            acc,
                                            point ->
                                            if (
                                                acc.lastOrNull() !=
                                                    point
                                            ) {
                                                acc +=
                                                    point
                                            }

                                            acc
                                        },
                                closed =
                                    contour.closed
                            )
                        }
                        .filter {
                            it.points.size >=
                                2
                        }

                val glyphGroups =
                    sampleGlyphGroups(
                        paint =
                            paint,
                        text =
                            renderableText,
                        pathBounds =
                            pathBounds,
                        scale =
                            scale,
                        spacingUnits =
                            targetHeightUnits *
                                0.04f +
                                options.spacingMm *
                                    10f
                    )

                require(
                    glyphGroups
                        .isNotEmpty()
                ) {
                    "A fonte não gerou contornos válidos."
                }

                points =
                    buildTextInReadingOrder(
                        glyphGroups =
                            glyphGroups,
                        options =
                            options
                    )
            }

            val processedPoints =
                SpecialStitchProcessor
                    .apply(
                        points =
                            points,
                        mode =
                            options
                                .specialStitchMode
                    )

            require(
                processedPoints.any {
                    it.command ==
                        StitchCommand.STITCH
                }
            ) {
                "A fonte não gerou pontadas."
            }

            val coordinates =
                processedPoints.filter {
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
                processedPoints.map {
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

            val centeredGuide =
                buildGuidePoints(
                    guideContours
                )
                    .map {
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
                    }

            val endPoint =
                centered
                    .lastOrNull()
                    ?: error(
                        "A fonte não gerou pontadas."
                    )

            centered +=
                EmbroideryPoint(
                    endPoint.xUnits,
                    endPoint.yUnits,
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

            val design =
                EmbroideryDesign(
                    fileName =
                        filePrefix +
                            "-" +
                            safeName(
                                text
                            ) +
                            "." +
                            outputFormat
                                .lowercase(
                                    Locale.ROOT
                                ),
                    format =
                        outputFormat,
                    label =
                        text,
                    points =
                        centered,
                    bounds =
                        bounds,
                    stitchCount =
                        centered.count {
                            it.command ==
                                StitchCommand.STITCH
                        },
                    jumpCount =
                        centered.count {
                            it.command ==
                                StitchCommand.JUMP
                        },
                    colorChanges =
                        0,
                    endFound =
                        true,
                    sourceBytes =
                        ByteArray(0),
                    guidePoints =
                        centeredGuide,
                    threadColors =
                        listOf(
                            options.color
                        ),
                    isModified =
                        true,
                    hoopProfile =
                        options.hoopProfile,
                    fabricProfile =
                        options.fabricProfile
                )

            if (
                options.enforceHoop &&
                options.hoopProfile !=
                    null
            ) {
                val fit =
                    HoopValidator
                        .validate(
                            design,
                            options
                                .hoopProfile
                        )

                require(fit.fits) {
                    "A matriz ultrapassa a área segura do bastidor " +
                        options
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            design
        }

    private data class ReferenceGlyphGeometry(
        val glyphPolygons:
            List<
                List<
                    List<
                        Pair<Float, Float>
                    >
                >
            >,
        val guideContours:
            List<SampledContour>
    )

    private fun buildReferenceGlyphGeometry(
        paint: Paint,
        text: String,
        targetHeightUnits: Float,
        spacingUnits: Float
    ): ReferenceGlyphGeometry {
        /*
         * Mão Design:
         * SKFont(typeface, 100) -> Metrics.CapHeight.
         * Na API Android não há CapHeight exposto; o contorno de "H"
         * é usado como equivalente e o mesmo fallback ascent * 0,72
         * é preservado.
         */
        paint.textSize =
            100f

        val capPath =
            Path()

        val capSample =
            if (
                paint.hasGlyph(
                    "H"
                )
            ) {
                "H"
            } else {
                text.first()
                    .toString()
            }

        paint.getTextPath(
            capSample,
            0,
            capSample.length,
            0f,
            0f,
            capPath
        )

        val capBounds =
            RectF().also {
                capPath.computeBounds(
                    it,
                    true
                )
            }

        val capHeightAt100 =
            if (
                capBounds.height() >
                    0.01f
            ) {
                capBounds.height()
            } else {
                kotlin.math.abs(
                    paint.fontMetrics
                        .ascent
                ) *
                    0.72f
            }
                .coerceAtLeast(
                    0.01f
                )

        val resolvedTextSize =
            100f *
                targetHeightUnits /
                capHeightAt100

        paint.textSize =
            resolvedTextSize

        val glyphPaths =
            mutableListOf<Path>()

        var penX =
            0f

        text.forEach {
                char ->
            val glyphText =
                char.toString()

            val advance =
                paint.measureText(
                    glyphText
                )

            if (
                !char.isWhitespace()
            ) {
                val glyphPath =
                    Path()

                paint.getTextPath(
                    glyphText,
                    0,
                    glyphText.length,
                    penX,
                    0f,
                    glyphPath
                )

                if (
                    !glyphPath.isEmpty
                ) {
                    glyphPaths +=
                        glyphPath
                }
            }

            penX +=
                advance +
                    spacingUnits
        }

        require(
            glyphPaths.isNotEmpty()
        ) {
            "A fonte não gerou contornos válidos."
        }

        val union =
            RectF()

        var hasBounds =
            false

        glyphPaths.forEach {
                glyphPath ->
            val bounds =
                RectF().also {
                    glyphPath.computeBounds(
                        it,
                        true
                    )
                }

            if (
                !hasBounds
            ) {
                union.set(
                    bounds
                )

                hasBounds =
                    true
            } else {
                union.union(
                    bounds
                )
            }
        }

        require(
            hasBounds &&
                union.width() >
                0.01f &&
                union.height() >
                0.01f
        ) {
            "A fonte não gerou uma área utilizável para este texto."
        }

        val centerTransform =
            Matrix().apply {
                setTranslate(
                    -union.centerX(),
                    -union.centerY()
                )
            }

        val glyphPolygons =
            mutableListOf<
                List<
                    List<
                        Pair<Float, Float>
                    >
                >
            >()

        val guideContours =
            mutableListOf<
                SampledContour
            >()

        glyphPaths.forEach {
                source ->
            val centeredPath =
                Path(
                    source
                )

            centeredPath.transform(
                centerTransform
            )

            /*
             * Android entrega o Path com Y positivo para baixo. O restante do
             * motor de bordado trabalha com Y positivo para cima. Normalize o
             * glifo aqui, uma única vez, para que pontadas e guia usem a mesma
             * orientação e a prévia não fique espelhada verticalmente.
             */
            val polygons =
                polygonizeReferencePath(
                    centeredPath
                )
                    .map {
                            polygon ->
                        polygon.map {
                                point ->
                            Pair(
                                point.first,
                                -point.second
                            )
                        }
                    }

            if (
                polygons.isEmpty()
            ) {
                return@forEach
            }

            glyphPolygons +=
                polygons

            polygons.forEach {
                    polygon ->
                val outputPoints =
                    polygon
                        .map {
                            point ->
                            Pair(
                                point.first
                                    .roundToInt(),
                                point.second
                                    .roundToInt()
                            )
                        }
                        .fold(
                            mutableListOf<
                                Pair<Int, Int>
                            >()
                        ) {
                            acc,
                            point ->
                            if (
                                acc.lastOrNull() !=
                                    point
                            ) {
                                acc +=
                                    point
                            }

                            acc
                        }

                if (
                    outputPoints.size >=
                        2
                ) {
                    guideContours +=
                        SampledContour(
                            points =
                                outputPoints,
                            closed =
                                true
                        )
                }
            }
        }

        require(
            glyphPolygons.isNotEmpty()
        ) {
            "A fonte não gerou contornos Satin utilizáveis."
        }

        return ReferenceGlyphGeometry(
            glyphPolygons =
                glyphPolygons,
            guideContours =
                guideContours
        )
    }

    private fun polygonizeReferencePath(
        path: Path
    ): List<
        List<
            Pair<Float, Float>
        >
    > {
        val measure =
            PathMeasure(
                path,
                true
            )

        val result =
            mutableListOf<
                List<
                    Pair<Float, Float>
                >
            >()

        val position =
            FloatArray(
                2
            )

        do {
            val length =
                measure.length

            if (
                length <=
                    0f
            ) {
                continue
            }

            val sampleCount =
                max(
                    8,
                    ceil(
                        length /
                            2f
                    ).toInt()
                )

            val polygon =
                mutableListOf<
                    Pair<Float, Float>
                >()

            for (
                index in
                    0 until
                        sampleCount
            ) {
                val distance =
                    length *
                        index.toFloat() /
                        sampleCount.toFloat()

                if (
                    measure.getPosTan(
                        distance,
                        position,
                        null
                    )
                ) {
                    polygon +=
                        Pair(
                            position[0],
                            position[1]
                        )
                }
            }

            if (
                polygon.size >=
                    3
            ) {
                result +=
                    polygon
            }
        } while (
            measure.nextContour()
        )

        return result
    }

    private fun buildReferenceSatinText(
        glyphPolygons:
            List<
                List<
                    List<
                        Pair<Float, Float>
                    >
                >
            >,
        options: TextMatrixOptions
    ): MutableList<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val state =
            ReferenceSatinState()

        glyphPolygons.forEach {
                polygons ->
            appendReferenceSatinGlyph(
                output = output,
                polygons = polygons,
                options = options,
                state = state
            )
        }

        return output
    }

    private fun sampleGlyphGroups(
        paint: Paint,
        text: String,
        pathBounds: RectF,
        scale: Float,
        spacingUnits: Float
    ): List<List<SampledContour>> {
        val groups =
            mutableListOf<
                List<SampledContour>
            >()

        val extraSpacingRaw =
            if (
                scale >
                    0.0001f
            ) {
                spacingUnits /
                    scale
            } else {
                0f
            }

        var penXRaw =
            0f

        text.forEach {
                char ->
            val advanceRaw =
                paint.measureText(
                    char.toString()
                )

            if (
                char.isWhitespace()
            ) {
                penXRaw +=
                    advanceRaw +
                        extraSpacingRaw

                return@forEach
            }

            val glyphPath =
                Path()

            paint.getTextPath(
                char.toString(),
                0,
                1,
                penXRaw,
                0f,
                glyphPath
            )

            if (
                glyphPath.isEmpty
            ) {
                return@forEach
            }

            val transformed =
                samplePath(
                    glyphPath
                )
                    .map {
                            contour ->
                        SampledContour(
                            points =
                                contour.points
                                    .map {
                                        point ->
                                        Pair(
                                            (
                                                (
                                                    point.first -
                                                        pathBounds
                                                            .centerX()
                                                    ) *
                                                    scale
                                                ).roundToInt(),
                                            (
                                                (
                                                    pathBounds
                                                        .centerY() -
                                                        point.second
                                                    ) *
                                                    scale
                                                ).roundToInt()
                                        )
                                    }
                                    .fold(
                                        mutableListOf<
                                            Pair<Int, Int>
                                        >()
                                    ) {
                                        acc,
                                        point ->
                                        if (
                                            acc.lastOrNull() !=
                                                point
                                        ) {
                                            acc +=
                                                point
                                        }

                                        acc
                                    },
                            closed =
                                contour.closed
                        )
                    }
                    .filter {
                        it.points.size >=
                            2
                    }

            if (
                transformed.isNotEmpty()
            ) {
                groups +=
                    transformed
            }

            penXRaw +=
                advanceRaw +
                    extraSpacingRaw
        }

        return groups
    }

    private fun buildTextInReadingOrder(
        glyphGroups:
            List<List<SampledContour>>,
        options: TextMatrixOptions
    ): MutableList<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val effectiveStyle =
            if (
                options.specialStitchMode !=
                    null
            ) {
                TextStitchStyle.RUNNING
            } else {
                options.style
            }

        if (
            effectiveStyle ==
                TextStitchStyle.SATIN
        ) {
            /*
             * Referência comportamental Mão Design 18.1.2:
             * a posição atual é compartilhada entre colunas e glifos.
             * Não existe TRIM obrigatório entre letras; o TRIM só entra
             * quando o deslocamento real ultrapassa o limiar de viagem.
             */
            val state =
                ReferenceSatinState()

            glyphGroups.forEach {
                    contours ->
                appendReferenceSatinGlyph(
                    output = output,
                    polygons =
                        densifyReferenceContours(
                            contours = contours,
                            maxStepUnits = 2f
                        ),
                    options = options,
                    state = state
                )
            }

            return output
        }

        glyphGroups.forEach {
                contours ->
            val glyphPoints =
                buildCenterlineRunning(
                    contours =
                        contours,
                    stitchLengthUnits =
                        options
                            .stitchLengthMm *
                            10f
                )

            if (
                glyphPoints.isEmpty()
            ) {
                return@forEach
            }

            if (
                output.isNotEmpty()
            ) {
                val last =
                    output.last()

                if (
                    last.command !=
                        StitchCommand.TRIM
                ) {
                    output +=
                        EmbroideryPoint(
                            last.xUnits,
                            last.yUnits,
                            StitchCommand.TRIM,
                            0
                        )
                }
            }

            output +=
                glyphPoints
        }

        return output
    }

    /*
     * Implementação independente baseada no comportamento observável do
     * Mão Design 18.1.2 para texto Satin:
     *
     * - contorno do glifo -> varreduras nos dois eixos;
     * - escolhe o eixo cuja largura média dos vãos é menor;
     * - agrupa vãos sobrepostos em colunas;
     * - divide colunas acima de 7 mm;
     * - underlay central de ida e volta;
     * - trava de 0,6 mm no início e no fim;
     * - cada linha Satin é bordada A -> B;
     * - viagem > 5 mm gera TRIM;
     * - saltos e pontos longos são quebrados em trechos de até 7 mm.
     *
     * Não há camada de contorno posterior nem "fixação" separada.
     */
    private data class ReferenceSatinState(
        var currentX: Float = 0f,
        var currentY: Float = 0f,
        var started: Boolean = false
    )

    private data class ReferenceSpan(
        val position: Float,
        val start: Float,
        val end: Float,
        val transposed: Boolean
    )

    private data class ReferenceRow(
        val ax: Float,
        val ay: Float,
        val bx: Float,
        val by: Float
    )

    private data class ReferenceColumn(
        val rows:
            MutableList<ReferenceRow> =
            mutableListOf()
    )

    private fun appendReferenceSatinGlyph(
        output: MutableList<EmbroideryPoint>,
        polygons:
            List<
                List<
                    Pair<Float, Float>
                >
            >,
        options: TextMatrixOptions,
        state: ReferenceSatinState
    ) {
        if (
            polygons.isEmpty()
        ) {
            return
        }

        val horizontal =
            scanReferenceSpans(
                polygons = polygons,
                pitchUnits =
                    options.satinDensityMm *
                        10f,
                transposed = false
            )

        val vertical =
            scanReferenceSpans(
                polygons = polygons,
                pitchUnits =
                    options.satinDensityMm *
                        10f,
                transposed = true
            )

        val chosen =
            if (
                meanReferenceSpanWidth(
                    horizontal
                ) >
                meanReferenceSpanWidth(
                    vertical
                )
            ) {
                vertical
            } else {
                horizontal
            }

        val columns =
            buildReferenceColumns(
                layers = chosen,
                pullCompensationUnits =
                    options
                        .satinPullCompensationMm *
                        10f,
                maxSatinWidthUnits =
                    70f
            )

        columns.forEach {
                column ->
            emitReferenceColumn(
                output = output,
                column = column,
                densityUnits =
                    options.satinDensityMm *
                        10f,
                includeUnderlay =
                    options.satinUnderlayMode !=
                        com.timachado
                            .brothermatrizes
                            .core
                            .embroidery
                            .SatinUnderlayMode
                            .NONE,
                state = state
            )
        }
    }

    private fun densifyReferenceContours(
        contours: List<SampledContour>,
        maxStepUnits: Float
    ): List<List<Pair<Float, Float>>> {
        val safeStep =
            maxStepUnits
                .coerceAtLeast(
                    0.5f
                )

        return contours
            .mapNotNull {
                    contour ->
                val source =
                    contour.points

                if (
                    source.size <
                        2
                ) {
                    return@mapNotNull null
                }

                val polygon =
                    mutableListOf<
                        Pair<Float, Float>
                    >()

                val segmentCount =
                    if (
                        contour.closed
                    ) {
                        source.size
                    } else {
                        source.size -
                            1
                    }

                for (
                    index in
                        0 until
                            segmentCount
                ) {
                    val first =
                        source[index]

                    val second =
                        source[
                            (
                                index +
                                    1
                                ) %
                                source.size
                        ]

                    val dx =
                        second.first -
                            first.first

                    val dy =
                        second.second -
                            first.second

                    val distance =
                        hypot(
                            dx.toDouble(),
                            dy.toDouble()
                        )
                            .toFloat()

                    val parts =
                        max(
                            1,
                            ceil(
                                distance /
                                    safeStep
                            ).toInt()
                        )

                    for (
                        part in
                            0 until
                                parts
                    ) {
                        val ratio =
                            part.toFloat() /
                                parts

                        val point =
                            Pair(
                                first.first +
                                    dx *
                                        ratio,
                                first.second +
                                    dy *
                                        ratio
                            )

                        if (
                            polygon.lastOrNull() !=
                                point
                        ) {
                            polygon +=
                                point
                        }
                    }
                }

                if (
                    !contour.closed
                ) {
                    val last =
                        source.last()

                    polygon +=
                        Pair(
                            last.first.toFloat(),
                            last.second.toFloat()
                        )
                }

                if (
                    polygon.size >=
                        3
                ) {
                    polygon
                } else {
                    null
                }
            }
    }

    private fun scanReferenceSpans(
        polygons:
            List<List<Pair<Float, Float>>>,
        pitchUnits: Float,
        transposed: Boolean
    ): List<List<ReferenceSpan>> {
        val all =
            polygons.flatten()

        if (
            all.isEmpty()
        ) {
            return emptyList()
        }

        fun coord(
            point: Pair<Float, Float>
        ): Float =
            if (
                transposed
            ) {
                point.first
            } else {
                point.second
            }

        fun cross(
            point: Pair<Float, Float>
        ): Float =
            if (
                transposed
            ) {
                point.second
            } else {
                point.first
            }

        val minCoord =
            all.minOf {
                coord(
                    it
                )
            }

        val maxCoord =
            all.maxOf {
                coord(
                    it
                )
            }

        val pitch =
            pitchUnits
                .coerceAtLeast(
                    0.5f
                )

        val layers =
            mutableListOf<
                List<ReferenceSpan>
            >()

        var position =
            minCoord +
                pitch /
                    2f

        while (
            position <=
                maxCoord +
                    0.001f
        ) {
            val intersections =
                mutableListOf<Float>()

            polygons.forEach {
                    polygon ->
                for (
                    index in
                        polygon.indices
                ) {
                    val first =
                        polygon[index]

                    val second =
                        polygon[
                            (
                                index +
                                    1
                                ) %
                                polygon.size
                        ]

                    val firstCoord =
                        coord(
                            first
                        )

                    val secondCoord =
                        coord(
                            second
                        )

                    val crosses =
                        (
                            firstCoord <=
                                position &&
                                secondCoord >
                                    position
                            ) ||
                            (
                                secondCoord <=
                                    position &&
                                    firstCoord >
                                        position
                                )

                    if (
                        !crosses
                    ) {
                        continue
                    }

                    val ratio =
                        (
                            position -
                                firstCoord
                            ) /
                            (
                                secondCoord -
                                    firstCoord
                                )

                    intersections +=
                        cross(
                            first
                        ) +
                            ratio *
                            (
                                cross(
                                    second
                                ) -
                                    cross(
                                        first
                                    )
                                )
                }
            }

            intersections.sort()

            val spans =
                mutableListOf<
                    ReferenceSpan
                >()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val start =
                    intersections[index]

                val end =
                    intersections[
                        index +
                            1
                    ]

                if (
                    end -
                        start >=
                        1f
                ) {
                    spans +=
                        ReferenceSpan(
                            position =
                                position,
                            start =
                                start,
                            end =
                                end,
                            transposed =
                                transposed
                        )
                }

                index +=
                    2
            }

            layers +=
                spans

            position +=
                pitch
        }

        return layers
    }

    private fun meanReferenceSpanWidth(
        layers:
            List<List<ReferenceSpan>>
    ): Double {
        var sum =
            0.0

        var count =
            0

        layers.forEach {
                layer ->
            layer.forEach {
                    span ->
                sum +=
                    (
                        span.end -
                            span.start
                        ).toDouble()

                count++
            }
        }

        return if (
            count >
                0
        ) {
            sum /
                count
        } else {
            Double.MAX_VALUE
        }
    }

    private fun ReferenceSpan.toRow(
        pullCompensationUnits: Float
    ): ReferenceRow {
        val first =
            start -
                pullCompensationUnits

        val second =
            end +
                pullCompensationUnits

        return if (
            transposed
        ) {
            ReferenceRow(
                ax =
                    position,
                ay =
                    first,
                bx =
                    position,
                by =
                    second
            )
        } else {
            ReferenceRow(
                ax =
                    first,
                ay =
                    position,
                bx =
                    second,
                by =
                    position
            )
        }
    }

    private fun buildReferenceColumns(
        layers:
            List<List<ReferenceSpan>>,
        pullCompensationUnits: Float,
        maxSatinWidthUnits: Float
    ): List<ReferenceColumn> {
        val completed =
            mutableListOf<
                ReferenceColumn
            >()

        var active =
            mutableListOf<
                Pair<
                    ReferenceSpan,
                    ReferenceColumn
                >
            >()

        layers.forEach {
                layer ->
            val matched =
                BooleanArray(
                    layer.size
                )

            val next =
                mutableListOf<
                    Pair<
                        ReferenceSpan,
                        ReferenceColumn
                    >
                >()

            active.forEach {
                    activeEntry ->
                val previousSpan =
                    activeEntry.first

                val column =
                    activeEntry.second

                var found =
                    false

                for (
                    index in
                        layer.indices
                ) {
                    if (
                        matched[index]
                    ) {
                        continue
                    }

                    val current =
                        layer[index]

                    val overlaps =
                        current.start <=
                            previousSpan.end &&
                            current.end >=
                                previousSpan.start

                    if (
                        overlaps
                    ) {
                        column.rows +=
                            current.toRow(
                                pullCompensationUnits
                            )

                        next +=
                            Pair(
                                current,
                                column
                            )

                        matched[index] =
                            true

                        found =
                            true

                        break
                    }
                }

                if (
                    !found
                ) {
                    completed +=
                        column
                }
            }

            layer.forEachIndexed {
                    index,
                    span ->
                if (
                    matched[index]
                ) {
                    return@forEachIndexed
                }

                val column =
                    ReferenceColumn()

                column.rows +=
                    span.toRow(
                        pullCompensationUnits
                    )

                next +=
                    Pair(
                        span,
                        column
                    )
            }

            active =
                next
        }

        completed +=
            active.map {
                it.second
            }

        val split =
            mutableListOf<
                ReferenceColumn
            >()

        completed
            .filter {
                it.rows.isNotEmpty()
            }
            .forEach {
                    column ->
                split +=
                    splitReferenceWideColumn(
                        column = column,
                        maxWidthUnits =
                            maxSatinWidthUnits
                    )
            }

        return split.sortedWith(
            compareBy<ReferenceColumn> {
                it.rows
                    .first()
                    .ax
            }.thenBy {
                it.rows
                    .first()
                    .ay
            }
        )
    }

    private fun splitReferenceWideColumn(
        column: ReferenceColumn,
        maxWidthUnits: Float
    ): List<ReferenceColumn> {
        val maximum =
            column.rows
                .maxOfOrNull {
                    row ->
                    hypot(
                        (
                            row.bx -
                                row.ax
                            ).toDouble(),
                        (
                            row.by -
                                row.ay
                            ).toDouble()
                    )
                        .toFloat()
                }
                ?: 0f

        if (
            maximum <=
                maxWidthUnits
        ) {
            return listOf(
                column
            )
        }

        val parts =
            ceil(
                maximum /
                    maxWidthUnits
            )
                .toInt()
                .coerceAtLeast(
                    1
                )

        return List(
            parts
        ) {
                part ->
            val startRatio =
                part.toFloat() /
                    parts

            val endRatio =
                (
                    part +
                        1
                    ).toFloat() /
                    parts

            ReferenceColumn(
                rows =
                    column.rows
                        .map {
                                row ->
                            ReferenceRow(
                                ax =
                                    row.ax +
                                        (
                                            row.bx -
                                                row.ax
                                            ) *
                                            startRatio,
                                ay =
                                    row.ay +
                                        (
                                            row.by -
                                                row.ay
                                            ) *
                                            startRatio,
                                bx =
                                    row.ax +
                                        (
                                            row.bx -
                                                row.ax
                                            ) *
                                            endRatio,
                                by =
                                    row.ay +
                                        (
                                            row.by -
                                                row.ay
                                            ) *
                                            endRatio
                            )
                        }
                        .toMutableList()
            )
        }
    }

    private fun emitReferenceColumn(
        output: MutableList<EmbroideryPoint>,
        column: ReferenceColumn,
        densityUnits: Float,
        includeUnderlay: Boolean,
        state: ReferenceSatinState
    ) {
        if (
            column.rows.isEmpty()
        ) {
            return
        }

        val first =
            column.rows.first()

        emitReferenceTravel(
            output = output,
            targetX = first.ax,
            targetY = first.ay,
            state = state
        )

        if (
            includeUnderlay &&
            column.rows.size >=
                4
        ) {
            emitReferenceCenterUnderlay(
                output = output,
                column = column,
                densityUnits = densityUnits,
                state = state
            )
        }

        emitReferenceLock(
            output = output,
            row = first,
            state = state
        )

        column.rows.forEach {
                row ->
            emitReferenceStitchTo(
                output = output,
                targetX = row.ax,
                targetY = row.ay,
                state = state
            )

            emitReferenceStitchTo(
                output = output,
                targetX = row.bx,
                targetY = row.by,
                state = state
            )
        }

        emitReferenceLock(
            output = output,
            row =
                column.rows.last(),
            state = state
        )
    }

    private fun emitReferenceCenterUnderlay(
        output: MutableList<EmbroideryPoint>,
        column: ReferenceColumn,
        densityUnits: Float,
        state: ReferenceSatinState
    ) {
        val safeDensity =
            densityUnits
                .coerceAtLeast(
                    0.5f
                )

        val rowStep =
            max(
                1,
                (
                    20f /
                        safeDensity
                    ).roundToInt()
            )

        val centers =
            mutableListOf<
                Pair<Float, Float>
            >()

        var index =
            0

        while (
            index <
                column.rows.size
        ) {
            val row =
                column.rows[index]

            centers +=
                Pair(
                    (
                        row.ax +
                            row.bx
                        ) /
                        2f,
                    (
                        row.ay +
                            row.by
                        ) /
                        2f
                )

            index +=
                rowStep
        }

        val last =
            column.rows.last()

        centers +=
            Pair(
                (
                    last.ax +
                        last.bx
                    ) /
                    2f,
                (
                    last.ay +
                        last.by
                    ) /
                    2f
            )

        centers.forEach {
                center ->
            emitReferenceStitchTo(
                output = output,
                targetX =
                    center.first,
                targetY =
                    center.second,
                state = state
            )
        }

        for (
            reverseIndex in
                centers.size -
                    2 downTo
                    0
        ) {
            val center =
                centers[
                    reverseIndex
                ]

            emitReferenceStitchTo(
                output = output,
                targetX =
                    center.first,
                targetY =
                    center.second,
                state = state
            )
        }
    }

    private fun emitReferenceLock(
        output: MutableList<EmbroideryPoint>,
        row: ReferenceRow,
        state: ReferenceSatinState
    ) {
        val dx =
            row.bx -
                row.ax

        val dy =
            row.by -
                row.ay

        val length =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )
                .toFloat()

        val normalX =
            if (
                length >=
                    0.001f
            ) {
                dx /
                    length
            } else {
                1f
            }

        val normalY =
            if (
                length >=
                    0.001f
            ) {
                dy /
                    length
            } else {
                0f
            }

        emitReferenceStitchTo(
            output = output,
            targetX =
                row.ax,
            targetY =
                row.ay,
            state = state
        )

        emitReferenceStitchTo(
            output = output,
            targetX =
                row.ax +
                    normalX *
                        6f,
            targetY =
                row.ay +
                    normalY *
                        6f,
            state = state
        )

        emitReferenceStitchTo(
            output = output,
            targetX =
                row.ax,
            targetY =
                row.ay,
            state = state
        )
    }

    private fun emitReferenceTravel(
        output: MutableList<EmbroideryPoint>,
        targetX: Float,
        targetY: Float,
        state: ReferenceSatinState
    ) {
        if (
            !state.started
        ) {
            output +=
                EmbroideryPoint(
                    targetX
                        .roundToInt(),
                    (
                        -targetY
                        ).roundToInt(),
                    StitchCommand.JUMP,
                    0
                )

            state.currentX =
                targetX

            state.currentY =
                targetY

            state.started =
                true

            return
        }

        val dx =
            targetX -
                state.currentX

        val dy =
            targetY -
                state.currentY

        val distance =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )
                .toFloat()

        if (
            distance <
                0.5f
        ) {
            return
        }

        if (
            distance >
                50f
        ) {
            output +=
                EmbroideryPoint(
                    state.currentX
                        .roundToInt(),
                    (
                        -state.currentY
                        ).roundToInt(),
                    StitchCommand.TRIM,
                    0
                )
        }

        val segments =
            max(
                1,
                ceil(
                    distance /
                        70f
                ).toInt()
            )

        val startX =
            state.currentX

        val startY =
            state.currentY

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toFloat() /
                    segments

            output +=
                EmbroideryPoint(
                    (
                        startX +
                            dx *
                                ratio
                        ).roundToInt(),
                    (
                        -(
                            startY +
                                dy *
                                    ratio
                            )
                        ).roundToInt(),
                    StitchCommand.JUMP,
                    0
                )
        }

        state.currentX =
            targetX

        state.currentY =
            targetY
    }

    private fun emitReferenceStitchTo(
        output: MutableList<EmbroideryPoint>,
        targetX: Float,
        targetY: Float,
        state: ReferenceSatinState
    ) {
        val dx =
            targetX -
                state.currentX

        val dy =
            targetY -
                state.currentY

        val distance =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )
                .toFloat()

        val segments =
            max(
                1,
                ceil(
                    distance /
                        70f
                ).toInt()
            )

        val startX =
            state.currentX

        val startY =
            state.currentY

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toFloat() /
                    segments

            output +=
                EmbroideryPoint(
                    (
                        startX +
                            dx *
                                ratio
                        ).roundToInt(),
                    (
                        -(
                            startY +
                                dy *
                                    ratio
                            )
                        ).roundToInt(),
                    StitchCommand.STITCH,
                    0
                )
        }

        state.currentX =
            targetX

        state.currentY =
            targetY
    }

    private fun buildCenterlineRunning(
        contours: List<SampledContour>,
        stitchLengthUnits: Float
    ): MutableList<EmbroideryPoint> {
        val allPoints =
            contours.flatMap {
                it.points
            }

        if (
            allPoints.isEmpty()
        ) {
            return mutableListOf()
        }

        val minX =
            allPoints.minOf {
                it.first
            }

        val maxX =
            allPoints.maxOf {
                it.first
            }

        val minY =
            allPoints.minOf {
                it.second
            }

        val maxY =
            allPoints.maxOf {
                it.second
            }

        val spanUnits =
            maxOf(
                maxX -
                    minX,
                maxY -
                    minY
            )
                .coerceAtLeast(
                    1
                )

        val unitsPerPixel =
            maxOf(
                1.25f,
                spanUnits /
                    1200f
            )

        val padding =
            6

        val width =
            (
                (
                    maxX -
                        minX
                    ) /
                    unitsPerPixel
                ).roundToInt()
                .coerceAtLeast(
                    1
                ) +
                padding *
                    2 +
                1

        val height =
            (
                (
                    maxY -
                        minY
                    ) /
                    unitsPerPixel
                ).roundToInt()
                .coerceAtLeast(
                    1
                ) +
                padding *
                    2 +
                1

        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(
                bitmap
            )

        val paint =
            Paint().apply {
                isAntiAlias =
                    true

                style =
                    Paint.Style.FILL

                color =
                    android.graphics
                        .Color.WHITE
            }

        val rasterPath =
            Path().apply {
                fillType =
                    Path.FillType.EVEN_ODD
            }

        contours.forEach {
                contour ->
            val first =
                contour.points
                    .firstOrNull()
                    ?: return@forEach

            rasterPath.moveTo(
                (
                    first.first -
                        minX
                    ) /
                    unitsPerPixel +
                    padding,
                (
                    maxY -
                        first.second
                    ) /
                    unitsPerPixel +
                    padding
            )

            contour.points
                .drop(1)
                .forEach {
                        point ->
                    rasterPath.lineTo(
                        (
                            point.first -
                                minX
                            ) /
                                unitsPerPixel +
                                padding,
                        (
                            maxY -
                                point.second
                            ) /
                                unitsPerPixel +
                                padding
                    )
                }

            if (
                contour.closed
            ) {
                rasterPath.close()
            }
        }

        canvas.drawPath(
            rasterPath,
            paint
        )

        val pixels =
            IntArray(
                width *
                    height
            )

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        bitmap.recycle()

        val mask =
            BooleanArray(
                pixels.size
            ) {
                    index ->
                (
                    pixels[index]
                        ushr
                        24
                    ) >=
                    96
            }

        val skeleton =
            thinMask(
                mask =
                    mask,
                width =
                    width,
                height =
                    height
            )

        val lines =
            orderSkeletonLinesByProximity(
                traceSkeleton(
                    skeleton =
                        skeleton,
                    width =
                        width,
                    height =
                        height
                )
                    .filter {
                        it.size >=
                            3 &&
                            skeletonLineLength(
                                it
                            ) >=
                                3f
                    }
            )

        if (
            lines.isEmpty()
        ) {
            return buildRunningOutline(
                contours =
                    contours,
                stitchLengthUnits =
                    stitchLengthUnits
            )
        }

        val stepPixels =
            (
                stitchLengthUnits /
                    unitsPerPixel
                ).coerceAtLeast(
                1.5f
            )

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        /*
         * CAMADA 1 — alinhamento/fixação.
         *
         * Antes de qualquer underlay ou Satin visível, percorremos o
         * esqueleto com pontos longos e leves. O percurso é feito em
         * ordem inversa para terminar próximo do início da primeira
         * região que receberá underlay, reduzindo saltos de retorno.
         */
        appendFixationLayer(
            output = output,
            lines = lines,
            minX = minX,
            maxY = maxY,
            padding = padding,
            unitsPerPixel = unitsPerPixel
        )

        lines.forEach {
                rawLine ->
            val samples =
                smoothAndResampleSkeleton(
                    source =
                        rawLine,
                    radius =
                        3,
                    passes =
                        2,
                    stepPixels =
                        stepPixels
                )

            if (
                samples.size <
                    2
            ) {
                return@forEach
            }

            if (
                output.isNotEmpty()
            ) {
                val last =
                    output.last()

                if (
                    last.command !=
                        StitchCommand.TRIM
                ) {
                    output +=
                        EmbroideryPoint(
                            last.xUnits,
                            last.yUnits,
                            StitchCommand.TRIM,
                            0
                        )
                }
            }

            val first =
                samples.first()

            output +=
                EmbroideryPoint(
                    skeletonXToUnits(
                        first.x,
                        minX,
                        padding,
                        unitsPerPixel
                    ),
                    skeletonYToUnits(
                        first.y,
                        maxY,
                        padding,
                        unitsPerPixel
                    ),
                    StitchCommand.JUMP,
                    0
                )

            samples.drop(1)
                .forEach {
                        sample ->
                    val x =
                        skeletonXToUnits(
                            sample.x,
                            minX,
                            padding,
                            unitsPerPixel
                        )

                    val y =
                        skeletonYToUnits(
                            sample.y,
                            maxY,
                            padding,
                            unitsPerPixel
                        )

                    val last =
                        output.last()

                    if (
                        last.xUnits !=
                            x ||
                        last.yUnits !=
                            y
                    ) {
                        output +=
                            EmbroideryPoint(
                                x,
                                y,
                                StitchCommand.STITCH,
                                0
                            )
                    }
                }
        }

        return output
    }

    private fun buildRunningOutline(
        contours: List<SampledContour>,
        stitchLengthUnits: Float
    ): MutableList<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        var currentX = 0
        var currentY = 0

        contours.forEachIndexed {
                index,
                contour ->
            val stroke =
                contour.points
                    .toMutableList()

            if (
                contour.closed &&
                stroke.first() !=
                    stroke.last()
            ) {
                stroke +=
                    stroke.first()
            }

            if (
                index > 0 &&
                output.isNotEmpty()
            ) {
                output +=
                    EmbroideryPoint(
                        currentX,
                        currentY,
                        StitchCommand.TRIM,
                        0
                    )
            }

            val first =
                stroke.first()

            output +=
                EmbroideryPoint(
                    first.first,
                    first.second,
                    StitchCommand.JUMP,
                    0
                )

            currentX =
                first.first

            currentY =
                first.second

            stroke
                .drop(1)
                .forEach {
                        target ->
                    val result =
                        appendRunning(
                            output =
                                output,
                            fromX =
                                currentX,
                            fromY =
                                currentY,
                            toX =
                                target.first,
                            toY =
                                target.second,
                            maxLengthUnits =
                                stitchLengthUnits
                        )

                    currentX =
                        result.first

                    currentY =
                        result.second
                }
        }

        return output
    }

    private data class SkeletonPoint(
        val x: Int,
        val y: Int
    )

    private data class FloatSkeletonPoint(
        val x: Float,
        val y: Float
    )

    private data class SatinSampleRow(
        val yUnits: Int,
        val leftUnits: Int,
        val rightUnits: Int
    ) {
        val centerX: Float
            get() =
                (
                    leftUnits +
                        rightUnits
                    ) /
                    2f

        val widthUnits: Int
            get() =
                rightUnits -
                    leftUnits
    }

    private data class SatinSampleColumn(
        val rows:
            MutableList<SatinSampleRow>
    )

    private data class StrokeFlowSatinRow(
        val centerXUnits: Int,
        val centerYUnits: Int,
        val aXUnits: Int,
        val aYUnits: Int,
        val bXUnits: Int,
        val bYUnits: Int
    )

    private data class StrokeFlowSegment(
        val id: Int,
        val points: List<SkeletonPoint>,
        val startNode: Long,
        val endNode: Long
    )

    private enum class StrokeFlowPhase {
        CENTER_RUN,
        SATIN_RETURN
    }

    private data class StrokeFlowTraversalStep(
        val segmentId: Int,
        val fromNode: Long,
        val toNode: Long,
        val phase: StrokeFlowPhase
    )

    private fun buildStrokeFlowSatinText(
        glyphPolygons:
            List<
                List<
                    List<
                        Pair<Float, Float>
                    >
                >
            >,
        options: TextMatrixOptions
    ): MutableList<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        glyphPolygons.forEach {
                polygons ->
            val contours =
                polygons
                    .map {
                            polygon ->
                        SampledContour(
                            points =
                                polygon
                                    .map {
                                            point ->
                                        Pair(
                                            point.first
                                                .roundToInt(),
                                            point.second
                                                .roundToInt()
                                        )
                                    }
                                    .fold(
                                        mutableListOf<
                                            Pair<Int, Int>
                                        >()
                                    ) {
                                        acc,
                                        point ->
                                        if (
                                            acc.lastOrNull() !=
                                                point
                                        ) {
                                            acc +=
                                                point
                                        }

                                        acc
                                    },
                            closed =
                                true
                        )
                    }
                    .filter {
                        it.points.size >=
                            3
                    }

            if (
                contours.isEmpty()
            ) {
                return@forEach
            }

            val glyphPoints =
                buildSatinByAxis(
                    contours =
                        contours,
                    options =
                        options
                )

            if (
                glyphPoints.isEmpty()
            ) {
                return@forEach
            }

            if (
                output.isNotEmpty()
            ) {
                val previous =
                    output.last()

                val first =
                    glyphPoints.first()

                val distance =
                    hypot(
                        (
                            first.xUnits -
                                previous.xUnits
                            ).toDouble(),
                        (
                            first.yUnits -
                                previous.yUnits
                            ).toDouble()
                    )

                if (
                    distance >
                        50.0 &&
                    previous.command !=
                        StitchCommand.TRIM
                ) {
                    output +=
                        EmbroideryPoint(
                            previous.xUnits,
                            previous.yUnits,
                            StitchCommand.TRIM,
                            previous.colorIndex
                        )
                }
            }

            output +=
                glyphPoints
        }

        if (
            output.none {
                it.command ==
                    StitchCommand.STITCH
            }
        ) {
            return buildReferenceSatinText(
                glyphPolygons =
                    glyphPolygons,
                options =
                    options
            )
        }

        return output
    }

    private fun buildSatinByAxis(
        contours: List<SampledContour>,
        options: TextMatrixOptions
    ): MutableList<EmbroideryPoint> {
        val allPoints =
            contours.flatMap {
                it.points
            }

        if (
            allPoints.isEmpty()
        ) {
            return mutableListOf()
        }

        val minX =
            allPoints.minOf {
                it.first
            }

        val maxX =
            allPoints.maxOf {
                it.first
            }

        val minY =
            allPoints.minOf {
                it.second
            }

        val maxY =
            allPoints.maxOf {
                it.second
            }

        val spanUnits =
            maxOf(
                maxX -
                    minX,
                maxY -
                    minY
            ).coerceAtLeast(
                1
            )

        /*
         * A máscara serve apenas para obter topologia/direção local.
         * O contorno vetorial continua definindo as bordas Satin.
         */
        val unitsPerPixel =
            maxOf(
                0.75f,
                spanUnits /
                    1500f
            )

        val padding =
            8

        val width =
            (
                (
                    maxX -
                        minX
                    ) /
                    unitsPerPixel
                ).roundToInt()
                .coerceAtLeast(
                    1
                ) +
                padding *
                    2 +
                1

        val height =
            (
                (
                    maxY -
                        minY
                    ) /
                    unitsPerPixel
                ).roundToInt()
                .coerceAtLeast(
                    1
                ) +
                padding *
                    2 +
                1

        if (
            width >
                1800 ||
            height >
                1800
        ) {
            return mutableListOf()
        }

        val bitmap =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(
                bitmap
            )

        val fillPaint =
            Paint().apply {
                isAntiAlias =
                    true
                style =
                    Paint.Style.FILL
                color =
                    android.graphics
                        .Color.WHITE
            }

        val rasterPath =
            Path().apply {
                fillType =
                    Path.FillType.EVEN_ODD
            }

        contours.forEach {
                contour ->
            val first =
                contour.points
                    .firstOrNull()
                    ?: return@forEach

            rasterPath.moveTo(
                (
                    first.first -
                        minX
                    ) /
                    unitsPerPixel +
                    padding,
                (
                    maxY -
                        first.second
                    ) /
                    unitsPerPixel +
                    padding
            )

            contour.points
                .drop(
                    1
                )
                .forEach {
                        point ->
                    rasterPath.lineTo(
                        (
                            point.first -
                                minX
                            ) /
                            unitsPerPixel +
                            padding,
                        (
                            maxY -
                                point.second
                            ) /
                            unitsPerPixel +
                            padding
                    )
                }

            if (
                contour.closed
            ) {
                rasterPath.close()
            }
        }

        canvas.drawPath(
            rasterPath,
            fillPaint
        )

        val pixels =
            IntArray(
                width *
                    height
            )

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        bitmap.recycle()

        val mask =
            BooleanArray(
                pixels.size
            ) {
                    index ->
                (
                    pixels[index]
                        ushr
                        24
                    ) >=
                    96
            }

        val skeleton =
            thinMask(
                mask =
                    mask,
                width =
                    width,
                height =
                    height
            )

        val rawLines =
            traceSkeleton(
                skeleton =
                    skeleton,
                width =
                    width,
                height =
                    height
            )
                .filter {
                    it.size >=
                        5 &&
                        skeletonLineLength(
                            it
                        ) >=
                        8f
                }

        if (
            rawLines.isEmpty()
        ) {
            return mutableListOf()
        }

        val rootGuide =
            orderStrokeFlowSkeletonLines(
                rawLines
            )
                .firstOrNull()

        val densityUnits =
            (
                options.satinDensityMm *
                    10f
                ).coerceIn(
                2.5f,
                12f
            )

        val stepPixels =
            (
                densityUnits /
                    unitsPerPixel
                ).coerceIn(
                2.0f,
                8.0f
            )

        val pullPixels =
            (
                options.satinPullCompensationMm *
                    10f /
                    unitsPerPixel
                ).coerceAtLeast(
                0f
            )

        val splitSegments =
            splitStrokeFlowSegments(
                rawLines
            )

        if (
            splitSegments.isEmpty()
        ) {
            return mutableListOf()
        }

        val rowsBySegment =
            splitSegments.associate {
                    segment ->
                segment.id to
                    buildStrokeFlowRows(
                        rawLine =
                            segment.points,
                        mask =
                            mask,
                        width =
                            width,
                        height =
                            height,
                        stepPixels =
                            stepPixels,
                        pullPixels =
                            pullPixels,
                        minX =
                            minX,
                        maxY =
                            maxY,
                        padding =
                            padding,
                        unitsPerPixel =
                            unitsPerPixel
                    )
            }

        val usableSegments =
            splitSegments.filter {
                    segment ->
                (
                    rowsBySegment[
                        segment.id
                    ]?.size
                        ?: 0
                    ) >=
                    2
            }

        if (
            usableSegments.isEmpty()
        ) {
            return mutableListOf()
        }

        val traversal =
            planStrokeFlowTraversal(
                segments =
                    usableSegments,
                rootHint =
                    rootGuide
                        ?.firstOrNull()
            )

        if (
            traversal.isEmpty()
        ) {
            return mutableListOf()
        }

        val segmentById =
            usableSegments.associateBy {
                it.id
            }

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val includeUnderlay =
            options.satinUnderlayMode !=
                com.timachado
                    .brothermatrizes
                    .core
                    .embroidery
                    .SatinUnderlayMode
                    .NONE

        traversal.forEach {
                step ->
            val segment =
                segmentById[
                    step.segmentId
                ]
                    ?: return@forEach

            val baseRows =
                rowsBySegment[
                    step.segmentId
                ]
                    ?: return@forEach

            val rows =
                orientStrokeFlowRows(
                    segment =
                        segment,
                    rows =
                        baseRows,
                    fromNode =
                        step.fromNode
                )

            if (
                rows.size <
                    2
            ) {
                return@forEach
            }

            when (
                step.phase
            ) {
                StrokeFlowPhase.CENTER_RUN ->
                    emitStrokeFlowCenterRun(
                        output =
                            output,
                        rows =
                            rows,
                        includeUnderlay =
                            includeUnderlay,
                        densityUnits =
                            densityUnits
                    )

                StrokeFlowPhase.SATIN_RETURN ->
                    emitStrokeFlowCoverage(
                        output =
                            output,
                        rows =
                            rows
                    )
            }
        }

        return if (
            output.any {
                it.command ==
                    StitchCommand.STITCH
            }
        ) {
            output
        } else {
            mutableListOf()
        }
    }

    private fun strokeFlowNodeKey(
        point: SkeletonPoint
    ): Long =
        (
            point.x.toLong()
                shl
                32
            ) or
            (
                point.y.toLong() and
                    0xffffffffL
                )

    private fun strokeFlowNodePoint(
        key: Long
    ): SkeletonPoint =
        SkeletonPoint(
            x =
                (
                    key shr
                        32
                    ).toInt(),
            y =
                key.toInt()
        )

    /**
     * traceSkeleton() preserva a continuidade direcional ao atravessar uma
     * bifurcação. Para reproduzir a referência, precisamos separar essa linha
     * exatamente nos nós compartilhados: o center-run pode atravessar o nó,
     * mas o Satin do ramo-pai só é executado depois dos ramos-filhos.
     */
    private fun splitStrokeFlowSegments(
        source:
            List<List<SkeletonPoint>>
    ): List<StrokeFlowSegment> {
        if (
            source.isEmpty()
        ) {
            return emptyList()
        }

        val endpointNodes =
            source.flatMap {
                    line ->
                if (
                    line.isEmpty()
                ) {
                    emptyList()
                } else {
                    listOf(
                        strokeFlowNodeKey(
                            line.first()
                        ),
                        strokeFlowNodeKey(
                            line.last()
                        )
                    )
                }
            }
                .toSet()

        val pieces =
            mutableListOf<
                List<SkeletonPoint>
            >()

        source.forEach {
                line ->
            if (
                line.size <
                    2
            ) {
                return@forEach
            }

            var startIndex =
                0

            for (
                index in
                    1 until
                        line.lastIndex
            ) {
                if (
                    strokeFlowNodeKey(
                        line[index]
                    ) in
                    endpointNodes
                ) {
                    val piece =
                        line.subList(
                            startIndex,
                            index +
                                1
                        )
                            .toList()

                    if (
                        piece.size >=
                            2
                    ) {
                        pieces +=
                            piece
                    }

                    startIndex =
                        index
                }
            }

            val tail =
                line.subList(
                    startIndex,
                    line.size
                )
                    .toList()

            if (
                tail.size >=
                    2
            ) {
                pieces +=
                    tail
            }
        }

        return pieces
            .filter {
                skeletonLineLength(
                    it
                ) >=
                    1.25f
            }
            .mapIndexed {
                    index,
                    line ->
                StrokeFlowSegment(
                    id =
                        index,
                    points =
                        line,
                    startNode =
                        strokeFlowNodeKey(
                            line.first()
                        ),
                    endNode =
                        strokeFlowNodeKey(
                            line.last()
                        )
                )
            }
    }

    private fun orientedStrokeFlowPoints(
        segment: StrokeFlowSegment,
        fromNode: Long
    ): List<SkeletonPoint> =
        if (
            segment.startNode ==
                fromNode ||
            segment.startNode ==
                segment.endNode
        ) {
            segment.points
        } else {
            segment.points
                .asReversed()
        }

    private fun strokeFlowOutgoingDirection(
        segment: StrokeFlowSegment,
        fromNode: Long
    ): Pair<Double, Double>? {
        val points =
            orientedStrokeFlowPoints(
                segment,
                fromNode
            )

        if (
            points.size <
                2
        ) {
            return null
        }

        val sampleIndex =
            minOf(
                4,
                points.lastIndex
            )

        val first =
            points.first()

        val next =
            points[
                sampleIndex
            ]

        val dx =
            (
                next.x -
                    first.x
                ).toDouble()

        val dy =
            (
                next.y -
                    first.y
                ).toDouble()

        val length =
            hypot(
                dx,
                dy
            )

        return if (
            length <
                0.001
        ) {
            null
        } else {
            dx /
                length to
                dy /
                    length
        }
    }

    private fun strokeFlowArrivalDirection(
        segment: StrokeFlowSegment,
        fromNode: Long
    ): Pair<Double, Double>? {
        val points =
            orientedStrokeFlowPoints(
                segment,
                fromNode
            )

        if (
            points.size <
                2
        ) {
            return null
        }

        val beforeIndex =
            (
                points.lastIndex -
                    4
                ).coerceAtLeast(
                0
            )

        val before =
            points[
                beforeIndex
            ]

        val last =
            points.last()

        val dx =
            (
                last.x -
                    before.x
                ).toDouble()

        val dy =
            (
                last.y -
                    before.y
                ).toDouble()

        val length =
            hypot(
                dx,
                dy
            )

        return if (
            length <
                0.001
        ) {
            null
        } else {
            dx /
                length to
                dy /
                    length
        }
    }

    private fun strokeFlowContinuationScore(
        incoming:
            Pair<Double, Double>?,
        outgoing:
            Pair<Double, Double>?
    ): Double {
        if (
            incoming ==
                null ||
            outgoing ==
                null
        ) {
            return 0.0
        }

        return incoming.first *
            outgoing.first +
            incoming.second *
                outgoing.second
    }

    /**
     * DFS pós-ordem do grafo de traços.
     *
     * CENTER_RUN é emitido ao descer na aresta.
     * SATIN_RETURN é emitido somente no backtracking. Assim, numa bifurcação,
     * os ramos-filhos são concluídos antes que o Satin feche o ramo-pai —
     * exatamente o padrão observado em 1000338102.mp4.
     */
    private fun planStrokeFlowTraversal(
        segments:
            List<StrokeFlowSegment>,
        rootHint:
            SkeletonPoint?
    ): List<StrokeFlowTraversalStep> {
        if (
            segments.isEmpty()
        ) {
            return emptyList()
        }

        val byId =
            segments.associateBy {
                it.id
            }

        val adjacency =
            linkedMapOf<
                Long,
                MutableList<Int>
            >()

        segments.forEach {
                segment ->
            adjacency
                .getOrPut(
                    segment.startNode
                ) {
                    mutableListOf()
                }
                .add(
                    segment.id
                )

            if (
                segment.endNode !=
                    segment.startNode
            ) {
                adjacency
                    .getOrPut(
                        segment.endNode
                    ) {
                        mutableListOf()
                    }
                    .add(
                        segment.id
                    )
            }
        }

        val visited =
            mutableSetOf<Int>()

        val result =
            mutableListOf<
                StrokeFlowTraversalStep
            >()

        fun otherNode(
            segment: StrokeFlowSegment,
            node: Long
        ): Long =
            if (
                segment.startNode ==
                    node
            ) {
                segment.endNode
            } else {
                segment.startNode
            }

        fun visit(
            node: Long,
            incoming:
                Pair<Double, Double>?
        ) {
            val candidates =
                adjacency[
                    node
                ]
                    .orEmpty()
                    .filter {
                        it !in
                            visited
                    }
                    .sortedWith(
                        compareByDescending<Int> {
                                edgeId ->
                            val edge =
                                byId[
                                    edgeId
                                ]
                                    ?: return@compareByDescending 0.0

                            strokeFlowContinuationScore(
                                incoming =
                                    incoming,
                                outgoing =
                                    strokeFlowOutgoingDirection(
                                        segment =
                                            edge,
                                        fromNode =
                                            node
                                    )
                            )
                        }.thenByDescending {
                                edgeId ->
                            byId[
                                edgeId
                            ]?.let {
                                skeletonLineLength(
                                    it.points
                                )
                            } ?: 0f
                        }
                    )

            candidates.forEach {
                    edgeId ->
                if (
                    edgeId in
                        visited
                ) {
                    return@forEach
                }

                val edge =
                    byId[
                        edgeId
                    ]
                        ?: return@forEach

                val nextNode =
                    otherNode(
                        edge,
                        node
                    )

                visited +=
                    edgeId

                result +=
                    StrokeFlowTraversalStep(
                        segmentId =
                            edgeId,
                        fromNode =
                            node,
                        toNode =
                            nextNode,
                        phase =
                            StrokeFlowPhase
                                .CENTER_RUN
                    )

                visit(
                    node =
                        nextNode,
                    incoming =
                        strokeFlowArrivalDirection(
                            segment =
                                edge,
                            fromNode =
                                node
                        )
                )

                result +=
                    StrokeFlowTraversalStep(
                        segmentId =
                            edgeId,
                        fromNode =
                            nextNode,
                        toNode =
                            node,
                        phase =
                            StrokeFlowPhase
                                .SATIN_RETURN
                    )
            }
        }

        fun distanceToHint(
            node: Long,
            hint: SkeletonPoint?
        ): Double {
            if (
                hint ==
                    null
            ) {
                return 0.0
            }

            val point =
                strokeFlowNodePoint(
                    node
                )

            return pointDistance(
                point,
                hint
            )
        }

        var nextRoot =
            adjacency.keys
                .minByOrNull {
                    distanceToHint(
                        it,
                        rootHint
                    )
                }
                ?: return emptyList()

        var lastRoot =
            nextRoot

        while (
            visited.size <
                segments.size
        ) {
            visit(
                node =
                    nextRoot,
                incoming =
                    null
            )

            lastRoot =
                nextRoot

            val remainingNodes =
                adjacency.keys.filter {
                        node ->
                    adjacency[
                        node
                    ]
                        .orEmpty()
                        .any {
                            it !in
                                visited
                        }
                }

            if (
                remainingNodes.isEmpty()
            ) {
                break
            }

            val lastPoint =
                strokeFlowNodePoint(
                    lastRoot
                )

            nextRoot =
                remainingNodes.minByOrNull {
                        node ->
                    pointDistance(
                        strokeFlowNodePoint(
                            node
                        ),
                        lastPoint
                    )
                }
                    ?: break
        }

        return result
    }

    private fun buildStrokeFlowRows(
        rawLine: List<SkeletonPoint>,
        mask: BooleanArray,
        width: Int,
        height: Int,
        stepPixels: Float,
        pullPixels: Float,
        minX: Int,
        maxY: Int,
        padding: Int,
        unitsPerPixel: Float
    ): List<StrokeFlowSatinRow> {
        val samples =
            smoothAndResampleSkeleton(
                source =
                    rawLine,
                radius =
                    3,
                passes =
                    2,
                stepPixels =
                    stepPixels
            )

        if (
            samples.size <
                2
        ) {
            return emptyList()
        }

        val rows =
            mutableListOf<
                StrokeFlowSatinRow
            >()

        var positiveWidth:
            Double? =
            null

        var negativeWidth:
            Double? =
            null

        samples.forEachIndexed {
                index,
                sample ->
            val before =
                samples[
                    (
                        index -
                            3
                        ).coerceAtLeast(
                        0
                    )
                ]

            val after =
                samples[
                    (
                        index +
                            3
                        ).coerceAtMost(
                        samples.lastIndex
                    )
                ]

            val tangentX =
                (
                    after.x -
                        before.x
                    ).toDouble()

            val tangentY =
                (
                    after.y -
                        before.y
                    ).toDouble()

            val tangentLength =
                sqrt(
                    tangentX *
                        tangentX +
                    tangentY *
                        tangentY
                )

            if (
                tangentLength <
                    0.001
            ) {
                return@forEachIndexed
            }

            val normalX =
                -tangentY /
                    tangentLength

            val normalY =
                tangentX /
                    tangentLength

            val rawPositive =
                boundaryDistance(
                    mask =
                        mask,
                    width =
                        width,
                    height =
                        height,
                    centerX =
                        sample.x
                            .toDouble(),
                    centerY =
                        sample.y
                            .toDouble(),
                    normalX =
                        normalX,
                    normalY =
                        normalY,
                    direction =
                        1.0
                )

            val rawNegative =
                boundaryDistance(
                    mask =
                        mask,
                    width =
                        width,
                    height =
                        height,
                    centerX =
                        sample.x
                            .toDouble(),
                    centerY =
                        sample.y
                            .toDouble(),
                    normalX =
                        normalX,
                    normalY =
                        normalY,
                    direction =
                        -1.0
                )

            if (
                rawPositive <
                    0.8 ||
                rawNegative <
                    0.8
            ) {
                return@forEachIndexed
            }

            val positive =
                positiveWidth
                    ?.let {
                        previous ->
                        previous *
                            0.60 +
                        rawPositive *
                            0.40
                    }
                    ?: rawPositive

            val negative =
                negativeWidth
                    ?.let {
                        previous ->
                        previous *
                            0.60 +
                        rawNegative *
                            0.40
                    }
                    ?: rawNegative

            positiveWidth =
                positive

            negativeWidth =
                negative

            val centerXUnits =
                skeletonXToUnits(
                    sample.x,
                    minX,
                    padding,
                    unitsPerPixel
                )

            val centerYUnits =
                skeletonYToUnits(
                    sample.y,
                    maxY,
                    padding,
                    unitsPerPixel
                )

            val aXUnits =
                skeletonXToUnits(
                    (
                        sample.x +
                            normalX *
                                (
                                    positive +
                                        pullPixels
                                    )
                        ).toFloat(),
                    minX,
                    padding,
                    unitsPerPixel
                )

            val aYUnits =
                skeletonYToUnits(
                    (
                        sample.y +
                            normalY *
                                (
                                    positive +
                                        pullPixels
                                    )
                        ).toFloat(),
                    maxY,
                    padding,
                    unitsPerPixel
                )

            val bXUnits =
                skeletonXToUnits(
                    (
                        sample.x -
                            normalX *
                                (
                                    negative +
                                        pullPixels
                                    )
                        ).toFloat(),
                    minX,
                    padding,
                    unitsPerPixel
                )

            val bYUnits =
                skeletonYToUnits(
                    (
                        sample.y -
                            normalY *
                                (
                                    negative +
                                        pullPixels
                                    )
                        ).toFloat(),
                    maxY,
                    padding,
                    unitsPerPixel
                )

            rows +=
                StrokeFlowSatinRow(
                    centerXUnits =
                        centerXUnits,
                    centerYUnits =
                        centerYUnits,
                    aXUnits =
                        aXUnits,
                    aYUnits =
                        aYUnits,
                    bXUnits =
                        bXUnits,
                    bYUnits =
                        bYUnits
                )
        }

        return rows
    }

    private fun orientStrokeFlowRows(
        segment: StrokeFlowSegment,
        rows: List<StrokeFlowSatinRow>,
        fromNode: Long
    ): List<StrokeFlowSatinRow> {
        if (
            segment.startNode ==
                fromNode ||
            segment.startNode ==
                segment.endNode
        ) {
            return rows
        }

        return rows
            .asReversed()
            .map {
                    row ->
                row.copy(
                    aXUnits =
                        row.bXUnits,
                    aYUnits =
                        row.bYUnits,
                    bXUnits =
                        row.aXUnits,
                    bYUnits =
                        row.aYUnits
                )
            }
    }

    private fun moveStrokeFlowTo(
        output:
            MutableList<EmbroideryPoint>,
        targetX: Int,
        targetY: Int,
        allowStitch: Boolean
    ) {
        if (
            output.isEmpty()
        ) {
            output +=
                EmbroideryPoint(
                    targetX,
                    targetY,
                    StitchCommand.JUMP,
                    0
                )

            return
        }

        val previous =
            output.last()

        val distance =
            hypot(
                (
                    targetX -
                        previous.xUnits
                    ).toDouble(),
                (
                    targetY -
                        previous.yUnits
                    ).toDouble()
            )

        if (
            distance <
                0.5
        ) {
            return
        }

        if (
            allowStitch &&
            distance <=
                18.0
        ) {
            appendSplitStitch(
                output =
                    output,
                fromX =
                    previous.xUnits,
                fromY =
                    previous.yUnits,
                toX =
                    targetX,
                toY =
                    targetY,
                maxLengthUnits =
                    70f
            )
        } else {
            if (
                distance >
                    50.0 &&
                previous.command !=
                    StitchCommand.TRIM
            ) {
                output +=
                    EmbroideryPoint(
                        previous.xUnits,
                        previous.yUnits,
                        StitchCommand.TRIM,
                        0
                    )
            }

            output +=
                EmbroideryPoint(
                    targetX,
                    targetY,
                    StitchCommand.JUMP,
                    0
                )
        }
    }

    private fun emitStrokeFlowCenterRun(
        output:
            MutableList<EmbroideryPoint>,
        rows:
            List<StrokeFlowSatinRow>,
        includeUnderlay: Boolean,
        densityUnits: Float
    ) {
        if (
            rows.isEmpty()
        ) {
            return
        }

        if (
            !includeUnderlay
        ) {
            val end =
                rows.last()

            moveStrokeFlowTo(
                output =
                    output,
                targetX =
                    end.centerXUnits,
                targetY =
                    end.centerYUnits,
                allowStitch =
                    false
            )

            return
        }

        val start =
            rows.first()

        moveStrokeFlowTo(
            output =
                output,
            targetX =
                start.centerXUnits,
            targetY =
                start.centerYUnits,
            allowStitch =
                true
        )

        output +=
            EmbroideryPoint(
                start.centerXUnits,
                start.centerYUnits,
                StitchCommand.STITCH,
                0
            )

        val underlayStep =
            maxOf(
                1,
                (
                    14f /
                        densityUnits
                    ).roundToInt()
            )

        var rowIndex =
            0

        while (
            rowIndex <
                rows.lastIndex
        ) {
            rowIndex =
                minOf(
                    rows.lastIndex,
                    rowIndex +
                        underlayStep
                )

            val center =
                rows[
                    rowIndex
                ]

            val previous =
                output.last()

            appendSplitStitch(
                output =
                    output,
                fromX =
                    previous.xUnits,
                fromY =
                    previous.yUnits,
                toX =
                    center.centerXUnits,
                toY =
                    center.centerYUnits,
                maxLengthUnits =
                    70f
            )
        }
    }

    private fun emitStrokeFlowCoverage(
        output:
            MutableList<EmbroideryPoint>,
        rows:
            List<StrokeFlowSatinRow>
    ) {
        if (
            rows.isEmpty()
        ) {
            return
        }

        val first =
            rows.first()

        moveStrokeFlowTo(
            output =
                output,
            targetX =
                first.centerXUnits,
            targetY =
                first.centerYUnits,
            allowStitch =
                true
        )

        rows.forEach {
                row ->
            var previous =
                output.last()

            appendSplitStitch(
                output =
                    output,
                fromX =
                    previous.xUnits,
                fromY =
                    previous.yUnits,
                toX =
                    row.aXUnits,
                toY =
                    row.aYUnits,
                maxLengthUnits =
                    70f
            )

            previous =
                output.last()

            appendSplitStitch(
                output =
                    output,
                fromX =
                    previous.xUnits,
                fromY =
                    previous.yUnits,
                toX =
                    row.bXUnits,
                toY =
                    row.bYUnits,
                maxLengthUnits =
                    70f
            )
        }

        val end =
            rows.last()

        val previous =
            output.last()

        appendSplitStitch(
            output =
                output,
            fromX =
                previous.xUnits,
            fromY =
                previous.yUnits,
            toX =
                end.centerXUnits,
            toY =
                end.centerYUnits,
            maxLengthUnits =
                70f
        )
    }

    internal fun debugStrokeFlowPostOrder():
        List<String> {
        val parent =
            listOf(
                SkeletonPoint(
                    0,
                    0
                ),
                SkeletonPoint(
                    0,
                    10
                ),
                SkeletonPoint(
                    0,
                    20
                )
            )

        val right =
            listOf(
                SkeletonPoint(
                    0,
                    20
                ),
                SkeletonPoint(
                    10,
                    30
                ),
                SkeletonPoint(
                    20,
                    40
                )
            )

        val left =
            listOf(
                SkeletonPoint(
                    0,
                    20
                ),
                SkeletonPoint(
                    -10,
                    30
                ),
                SkeletonPoint(
                    -20,
                    40
                )
            )

        val segments =
            splitStrokeFlowSegments(
                listOf(
                    parent,
                    right,
                    left
                )
            )

        val byId =
            segments.associateBy {
                it.id
            }

        return planStrokeFlowTraversal(
            segments =
                segments,
            rootHint =
                SkeletonPoint(
                    0,
                    0
                )
        ).map {
                step ->
            val segment =
                byId[
                    step.segmentId
                ]!!

            val from =
                strokeFlowNodePoint(
                    step.fromNode
                )

            val to =
                strokeFlowNodePoint(
                    step.toNode
                )

            step.phase.name +
                ":" +
                from.x +
                "," +
                from.y +
                "->" +
                to.x +
                "," +
                to.y
        }
    }

    private fun orderStrokeFlowSkeletonLines(
        source:
            List<List<SkeletonPoint>>
    ): List<List<SkeletonPoint>> {
        if (
            source.isEmpty()
        ) {
            return emptyList()
        }

        val remaining =
            source
                .map {
                    it.toList()
                }
                .toMutableList()

        val longest =
            remaining.maxByOrNull {
                skeletonLineLength(
                    it
                )
            } ?: return emptyList()

        remaining.remove(
            longest
        )

        /*
         * O primeiro fluxo começa pelo pé visual inferior do traço.
         * Como a máscara usa Y para baixo, o endpoint com maior Y é o
         * ponto visual inferior.
         */
        var current =
            if (
                longest.first().y >=
                    longest.last().y
            ) {
                longest
            } else {
                longest.asReversed()
            }

        val ordered =
            mutableListOf<
                List<SkeletonPoint>
            >()

        ordered +=
            current

        var currentEntry =
            current.first()

        while (
            remaining.isNotEmpty()
        ) {
            var bestIndex =
                0

            var bestReverse =
                false

            var bestDistance =
                Double.MAX_VALUE

            remaining.forEachIndexed {
                    index,
                    line ->
                val firstDistance =
                    pointDistance(
                        currentEntry,
                        line.first()
                    )

                val lastDistance =
                    pointDistance(
                        currentEntry,
                        line.last()
                    )

                val reverse =
                    lastDistance <
                        firstDistance

                val distance =
                    minOf(
                        firstDistance,
                        lastDistance
                    )

                if (
                    distance <
                        bestDistance
                ) {
                    bestDistance =
                        distance
                    bestIndex =
                        index
                    bestReverse =
                        reverse
                }
            }

            val selected =
                remaining.removeAt(
                    bestIndex
                )

            current =
                if (
                    bestReverse
                ) {
                    selected.asReversed()
                } else {
                    selected
                }

            ordered +=
                current

            /*
             * Center-run vai até o extremo e o Satin retorna para a entrada,
             * então o próximo segmento parte novamente desse mesmo nó.
             */
            currentEntry =
                current.first()
        }

        return ordered
    }

    private fun appendFixationLayer(
        output: MutableList<EmbroideryPoint>,
        lines: List<List<SkeletonPoint>>,
        minX: Int,
        maxY: Int,
        padding: Int,
        unitsPerPixel: Float
    ) {
        val stitchStepUnits =
            42f

        val firstFillLine =
            lines.firstOrNull()

        val reverseCandidates =
            lines
                .drop(1)
                .asReversed()
                .toMutableList()

        val longestCandidate =
            reverseCandidates
                .maxByOrNull {
                    skeletonLineLength(
                        it
                    )
                }

        val fixationRoute =
            buildList {
                if (
                    longestCandidate !=
                        null
                ) {
                    add(
                        longestCandidate
                    )

                    reverseCandidates.remove(
                        longestCandidate
                    )
                }

                addAll(
                    reverseCandidates
                )

                if (
                    firstFillLine !=
                        null
                ) {
                    add(
                        firstFillLine
                    )
                }
            }

        fixationRoute
            .forEach {
                    sourceLine ->
                val samples =
                    smoothAndResampleSkeleton(
                        source =
                            sourceLine
                                .asReversed(),
                        radius = 4,
                        passes = 2,
                        stepPixels =
                            (
                                stitchStepUnits /
                                    unitsPerPixel
                                ).coerceAtLeast(
                                2f
                            )
                    )

                if (
                    samples.size <
                        2
                ) {
                    return@forEach
                }

                if (
                    output.isNotEmpty() &&
                    output.last().command !=
                        StitchCommand.TRIM
                ) {
                    val last =
                        output.last()

                    output +=
                        EmbroideryPoint(
                            last.xUnits,
                            last.yUnits,
                            StitchCommand.TRIM,
                            0
                        )
                }

                val first =
                    samples.first()

                var currentX =
                    skeletonXToUnits(
                        first.x,
                        minX,
                        padding,
                        unitsPerPixel
                    )

                var currentY =
                    skeletonYToUnits(
                        first.y,
                        maxY,
                        padding,
                        unitsPerPixel
                    )

                output +=
                    EmbroideryPoint(
                        currentX,
                        currentY,
                        StitchCommand.JUMP,
                        0
                    )

                samples
                    .drop(1)
                    .forEach {
                            sample ->
                        val targetX =
                            skeletonXToUnits(
                                sample.x,
                                minX,
                                padding,
                                unitsPerPixel
                            )

                        val targetY =
                            skeletonYToUnits(
                                sample.y,
                                maxY,
                                padding,
                                unitsPerPixel
                            )

                        appendSplitStitch(
                            output = output,
                            fromX = currentX,
                            fromY = currentY,
                            toX = targetX,
                            toY = targetY,
                            maxLengthUnits =
                                stitchStepUnits
                        )

                        currentX =
                            targetX

                        currentY =
                            targetY
                    }
            }

        if (
            output.isNotEmpty() &&
            output.last().command !=
                StitchCommand.TRIM
        ) {
            val last =
                output.last()

            output +=
                EmbroideryPoint(
                    last.xUnits,
                    last.yUnits,
                    StitchCommand.TRIM,
                    0
                )
        }
    }

    private fun appendDynamicZigzagUnderlay(
        output: MutableList<EmbroideryPoint>,
        samples: List<FloatSkeletonPoint>,
        minX: Int,
        maxY: Int,
        padding: Int,
        unitsPerPixel: Float,
        satinWidthUnits: Float
    ) {
        if (
            samples.size <
                2
        ) {
            return
        }

        val halfWidthPixels =
            (
                satinWidthUnits *
                    0.30f /
                    unitsPerPixel
                ).coerceAtLeast(
                0.8f
            )

        var currentX:
            Int? =
            null

        var currentY:
            Int? =
            null

        samples.forEachIndexed {
                index,
                sample ->
            val before =
                samples[
                    (
                        index -
                            3
                        ).coerceAtLeast(
                        0
                    )
                ]

            val after =
                samples[
                    (
                        index +
                            3
                        ).coerceAtMost(
                        samples.lastIndex
                    )
                ]

            val tangentX =
                (
                    after.x -
                        before.x
                    ).toDouble()

            val tangentY =
                (
                    after.y -
                        before.y
                    ).toDouble()

            val tangentLength =
                sqrt(
                    tangentX *
                        tangentX +
                        tangentY *
                            tangentY
                )

            if (
                tangentLength <
                    0.001
            ) {
                return@forEachIndexed
            }

            val normalX =
                -tangentY /
                    tangentLength

            val normalY =
                tangentX /
                    tangentLength

            val side =
                if (
                    index %
                        2 ==
                        0
                ) {
                    1.0
                } else {
                    -1.0
                }

            val targetX =
                skeletonXToUnits(
                    (
                        sample.x +
                            normalX *
                                halfWidthPixels *
                                side
                        ).toFloat(),
                    minX,
                    padding,
                    unitsPerPixel
                )

            val targetY =
                skeletonYToUnits(
                    (
                        sample.y +
                            normalY *
                                halfWidthPixels *
                                side
                        ).toFloat(),
                    maxY,
                    padding,
                    unitsPerPixel
                )

            val fromX =
                currentX

            val fromY =
                currentY

            if (
                fromX ==
                    null ||
                fromY ==
                    null
            ) {
                output +=
                    EmbroideryPoint(
                        targetX,
                        targetY,
                        StitchCommand.JUMP,
                        0
                    )
            } else if (
                fromX !=
                    targetX ||
                fromY !=
                    targetY
            ) {
                appendSplitStitch(
                    output = output,
                    fromX = fromX,
                    fromY = fromY,
                    toX = targetX,
                    toY = targetY,
                    maxLengthUnits =
                        36f
                )
            }

            currentX =
                targetX

            currentY =
                targetY
        }

        if (
            output.isNotEmpty() &&
            output.last().command !=
                StitchCommand.TRIM
        ) {
            val last =
                output.last()

            output +=
                EmbroideryPoint(
                    last.xUnits,
                    last.yUnits,
                    StitchCommand.TRIM,
                    0
                )
        }
    }

    private fun sampleSatinRows(
        contours:
            List<SampledContour>,
        pitchUnits: Float
    ): List<
        Pair<
            Int,
            List<SatinSampleRow>
        >
    > {
        val allPoints =
            contours.flatMap {
                it.points
            }

        if (
            allPoints.isEmpty()
        ) {
            return emptyList()
        }

        val minY =
            allPoints.minOf {
                it.second
            }

        val maxY =
            allPoints.maxOf {
                it.second
            }

        val layers =
            mutableListOf<
                Pair<
                    Int,
                    List<SatinSampleRow>
                >
            >()

        var y =
            minY.toFloat() +
                pitchUnits /
                    2f

        while (
            y <=
                maxY.toFloat() +
                    0.01f
        ) {
            val intersections =
                mutableListOf<Float>()

            contours.forEach {
                    contour ->
                val polygon =
                    if (
                        contour.closed &&
                        contour.points
                            .first() !=
                            contour.points
                                .last()
                    ) {
                        contour.points +
                            contour.points
                                .first()
                    } else {
                        contour.points
                    }

                for (
                    index in
                        1 until
                            polygon.size
                ) {
                    val first =
                        polygon[
                            index -
                                1
                        ]

                    val second =
                        polygon[index]

                    val y1 =
                        first.second
                            .toFloat()

                    val y2 =
                        second.second
                            .toFloat()

                    val crosses =
                        (
                            y1 <= y &&
                                y2 > y
                            ) ||
                            (
                                y2 <= y &&
                                    y1 > y
                                )

                    if (
                        !crosses
                    ) {
                        continue
                    }

                    val ratio =
                        (
                            y -
                                y1
                            ) /
                            (
                                y2 -
                                    y1
                                )

                    intersections +=
                        first.first +
                            (
                                second.first -
                                    first.first
                                ) *
                                ratio
                }
            }

            intersections.sort()

            val rows =
                mutableListOf<
                    SatinSampleRow
                >()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val left =
                    intersections[index]
                        .roundToInt()

                val right =
                    intersections[
                        index +
                            1
                    ]
                        .roundToInt()

                if (
                    right -
                        left >=
                        2
                ) {
                    rows +=
                        SatinSampleRow(
                            yUnits =
                                y.roundToInt(),
                            leftUnits =
                                left,
                            rightUnits =
                                right
                        )
                }

                index +=
                    2
            }

            if (
                rows.isNotEmpty()
            ) {
                layers +=
                    Pair(
                        y.roundToInt(),
                        rows
                    )
            }

            y +=
                pitchUnits
        }

        return layers
    }

    private fun buildSatinColumns(
        rowLayers:
            List<
                Pair<
                    Int,
                    List<SatinSampleRow>
                >
            >,
        pitchUnits: Float
    ): List<SatinSampleColumn> {
        val finished =
            mutableListOf<
                SatinSampleColumn
            >()

        var active =
            mutableListOf<
                SatinSampleColumn
            >()

        rowLayers.forEach {
                layer ->
            val rows =
                layer.second

            val candidates =
                mutableListOf<
                    Triple<
                        Int,
                        Int,
                        Float
                    >
                >()

            active.forEachIndexed {
                    columnIndex,
                    column ->
                val previous =
                    column.rows
                        .last()

                rows.forEachIndexed {
                        rowIndex,
                        row ->
                    val overlap =
                        minOf(
                            previous.rightUnits,
                            row.rightUnits
                        ) -
                            maxOf(
                                previous.leftUnits,
                                row.leftUnits
                            )

                    val centerDistance =
                        kotlin.math.abs(
                            previous.centerX -
                                row.centerX
                        )

                    val widthReference =
                        maxOf(
                            previous.widthUnits,
                            row.widthUnits
                        )
                            .toFloat()

                    val allowedGap =
                        maxOf(
                            pitchUnits *
                                2.2f,
                            widthReference *
                                .42f
                        )

                    if (
                        overlap >=
                            -pitchUnits ||
                        centerDistance <=
                            allowedGap
                    ) {
                        val score =
                            centerDistance -
                                overlap
                                    .coerceAtLeast(
                                        0
                                    ) *
                                    .35f

                        candidates +=
                            Triple(
                                columnIndex,
                                rowIndex,
                                score
                            )
                    }
                }
            }

            val assignedColumns =
                mutableSetOf<Int>()

            val assignedRows =
                mutableSetOf<Int>()

            val nextActive =
                mutableListOf<
                    SatinSampleColumn
                >()

            candidates
                .sortedBy {
                    it.third
                }
                .forEach {
                        candidate ->
                    val columnIndex =
                        candidate.first

                    val rowIndex =
                        candidate.second

                    if (
                        columnIndex in
                            assignedColumns ||
                        rowIndex in
                            assignedRows
                    ) {
                        return@forEach
                    }

                    val column =
                        active[
                            columnIndex
                        ]

                    column.rows +=
                        rows[
                            rowIndex
                        ]

                    assignedColumns +=
                        columnIndex

                    assignedRows +=
                        rowIndex

                    nextActive +=
                        column
                }

            active.forEachIndexed {
                    index,
                    column ->
                if (
                    index !in
                        assignedColumns
                ) {
                    finished +=
                        column
                }
            }

            rows.forEachIndexed {
                    index,
                    row ->
                if (
                    index !in
                        assignedRows
                ) {
                    nextActive +=
                        SatinSampleColumn(
                            rows =
                                mutableListOf(
                                    row
                                )
                        )
                }
            }

            active =
                nextActive
                    .distinct()
                    .toMutableList()
        }

        finished +=
            active

        return finished
    }

    private fun smoothSatinColumn(
        source:
            SatinSampleColumn
    ): SatinSampleColumn {
        val rows =
            source.rows

        if (
            rows.size <
                3
        ) {
            return source
        }

        val smoothed =
            rows.mapIndexed {
                    index,
                    row ->
                val start =
                    (
                        index -
                            1
                        ).coerceAtLeast(
                        0
                    )

                val end =
                    (
                        index +
                            1
                        ).coerceAtMost(
                        rows.lastIndex
                    )

                val window =
                    rows.subList(
                        start,
                        end +
                            1
                    )

                val left =
                    window
                        .map {
                            it.leftUnits
                        }
                        .average()
                        .roundToInt()

                val right =
                    window
                        .map {
                            it.rightUnits
                        }
                        .average()
                        .roundToInt()

                SatinSampleRow(
                    yUnits =
                        row.yUnits,
                    leftUnits =
                        minOf(
                            left,
                            right -
                                1
                        ),
                    rightUnits =
                        maxOf(
                            right,
                            left +
                                1
                        )
                )
            }

        return SatinSampleColumn(
            rows =
                smoothed
                    .toMutableList()
        )
    }

    private fun routeSatinColumns(
        source:
            List<SatinSampleColumn>
    ): List<SatinSampleColumn> {
        if (
            source.size <=
                1
        ) {
            return source
        }

        val remaining =
            source
                .map {
                    SatinSampleColumn(
                        rows =
                            it.rows
                                .toMutableList()
                    )
                }
                .toMutableList()

        val ordered =
            mutableListOf<
                SatinSampleColumn
            >()

        var currentX =
            remaining
                .minOfOrNull {
                    column ->
                    column.rows
                        .minOf {
                            it.leftUnits
                        }
                }
                ?.toFloat()
                ?: 0f

        var currentY =
            remaining
                .minOfOrNull {
                    column ->
                    column.rows
                        .first()
                        .yUnits
                }
                ?.toFloat()
                ?: 0f

        while (
            remaining.isNotEmpty()
        ) {
            var bestIndex =
                0

            var bestReverse =
                false

            var bestDistance =
                Double.MAX_VALUE

            remaining.forEachIndexed {
                    index,
                    column ->
                val first =
                    column.rows
                        .first()

                val last =
                    column.rows
                        .last()

                val firstDistance =
                    hypot(
                        (
                            first.centerX -
                                currentX
                            ).toDouble(),
                        (
                            first.yUnits -
                                currentY
                            ).toDouble()
                    )

                val lastDistance =
                    hypot(
                        (
                            last.centerX -
                                currentX
                            ).toDouble(),
                        (
                            last.yUnits -
                                currentY
                            ).toDouble()
                    )

                val reverse =
                    lastDistance <
                        firstDistance

                val distance =
                    minOf(
                        firstDistance,
                        lastDistance
                    )

                if (
                    distance <
                        bestDistance
                ) {
                    bestDistance =
                        distance

                    bestIndex =
                        index

                    bestReverse =
                        reverse
                }
            }

            val selected =
                remaining.removeAt(
                    bestIndex
                )

            val routed =
                if (
                    bestReverse
                ) {
                    SatinSampleColumn(
                        rows =
                            selected.rows
                                .asReversed()
                                .toMutableList()
                    )
                } else {
                    selected
                }

            ordered +=
                routed

            val end =
                routed.rows
                    .last()

            currentX =
                end.centerX

            currentY =
                end.yUnits
                    .toFloat()
        }

        return ordered
    }

    private fun emitSatinColumn(
        output:
            MutableList<EmbroideryPoint>,
        column:
            SatinSampleColumn,
        options:
            TextMatrixOptions
    ) {
        val rows =
            column.rows

        if (
            rows.size <
                2
        ) {
            return
        }

        if (
            output.isNotEmpty()
        ) {
            val last =
                output.last()

            if (
                last.command !=
                    StitchCommand.TRIM
            ) {
                output +=
                    EmbroideryPoint(
                        last.xUnits,
                        last.yUnits,
                        StitchCommand.TRIM,
                        0
                    )
            }
        }

        if (
            options
                .satinUnderlayMode !=
                com.timachado
                    .brothermatrizes
                    .core
                    .embroidery
                    .SatinUnderlayMode
                    .NONE
        ) {
            val first =
                rows.first()

            output +=
                EmbroideryPoint(
                    first.centerX
                        .roundToInt(),
                    first.yUnits,
                    StitchCommand.JUMP,
                    0
                )

            var lastX =
                first.centerX

            var lastY =
                first.yUnits
                    .toFloat()

            rows.drop(1)
                .forEach {
                        row ->
                    val distance =
                        hypot(
                            (
                                row.centerX -
                                    lastX
                                ).toDouble(),
                            (
                                row.yUnits -
                                    lastY
                                ).toDouble()
                        )

                    if (
                        distance >=
                            16f
                    ) {
                        output +=
                            EmbroideryPoint(
                                row.centerX
                                    .roundToInt(),
                                row.yUnits,
                                StitchCommand.STITCH,
                                0
                            )

                        lastX =
                            row.centerX

                        lastY =
                            row.yUnits
                                .toFloat()
                    }
                }

            val last =
                output.last()

            output +=
                EmbroideryPoint(
                    last.xUnits,
                    last.yUnits,
                    StitchCommand.TRIM,
                    0
                )
        }

        val pull =
            options
                .satinPullCompensationMm *
                10f

        val first =
            rows.first()

        val firstLeft =
            (
                first.leftUnits -
                    pull
                ).roundToInt()

        val firstRight =
            (
                first.rightUnits +
                    pull
                ).roundToInt()

        output +=
            EmbroideryPoint(
                firstLeft,
                first.yUnits,
                StitchCommand.JUMP,
                0
            )

        appendSplitStitch(
            output =
                output,
            fromX =
                firstLeft,
            fromY =
                first.yUnits,
            toX =
                firstRight,
            toY =
                first.yUnits,
            maxLengthUnits =
                80f
        )

        var currentX =
            firstRight

        var currentY =
            first.yUnits

        rows.drop(1)
            .forEachIndexed {
                    index,
                    row ->
                val left =
                    (
                        row.leftUnits -
                            pull
                        ).roundToInt()

                val right =
                    (
                        row.rightUnits +
                            pull
                        ).roundToInt()

                val targetX =
                    if (
                        index %
                            2 ==
                            0
                    ) {
                        left
                    } else {
                        right
                    }

                appendSplitStitch(
                    output =
                        output,
                    fromX =
                        currentX,
                    fromY =
                        currentY,
                    toX =
                        targetX,
                    toY =
                        row.yUnits,
                    maxLengthUnits =
                        80f
                )

                currentX =
                    targetX

                currentY =
                    row.yUnits
            }
    }

    private fun thinMask(
        mask: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {
        val result =
            mask.copyOf()

        var changed =
            true

        var pass =
            0

        while (
            changed &&
            pass <
                120
        ) {
            changed =
                false

            repeat(2) {
                    phase ->
                val remove =
                    mutableListOf<Int>()

                for (
                    y in
                        1 until
                            height -
                                1
                ) {
                    for (
                        x in
                            1 until
                                width -
                                    1
                    ) {
                        val index =
                            y *
                                width +
                                x

                        if (
                            !result[index]
                        ) {
                            continue
                        }

                        val neighbors =
                            booleanArrayOf(
                                result[
                                    index -
                                        width
                                ],
                                result[
                                    index -
                                        width +
                                        1
                                ],
                                result[
                                    index +
                                        1
                                ],
                                result[
                                    index +
                                        width +
                                        1
                                ],
                                result[
                                    index +
                                        width
                                ],
                                result[
                                    index +
                                        width -
                                        1
                                ],
                                result[
                                    index -
                                        1
                                ],
                                result[
                                    index -
                                        width -
                                        1
                                ]
                            )

                        val count =
                            neighbors.count {
                                it
                            }

                        if (
                            count !in
                                2..6
                        ) {
                            continue
                        }

                        var transitions =
                            0

                        for (
                            neighborIndex in
                                neighbors.indices
                        ) {
                            val current =
                                neighbors[
                                    neighborIndex
                                ]

                            val next =
                                neighbors[
                                    (
                                        neighborIndex +
                                            1
                                        ) %
                                        neighbors.size
                                ]

                            if (
                                !current &&
                                next
                            ) {
                                transitions++
                            }
                        }

                        if (
                            transitions !=
                                1
                        ) {
                            continue
                        }

                        val p2 =
                            neighbors[0]
                        val p4 =
                            neighbors[2]
                        val p6 =
                            neighbors[4]
                        val p8 =
                            neighbors[6]

                        val blocked =
                            if (
                                phase ==
                                    0
                            ) {
                                (
                                    p2 &&
                                        p4 &&
                                        p6
                                    ) ||
                                    (
                                        p4 &&
                                            p6 &&
                                            p8
                                        )
                            } else {
                                (
                                    p2 &&
                                        p4 &&
                                        p8
                                    ) ||
                                    (
                                        p2 &&
                                            p6 &&
                                            p8
                                        )
                            }

                        if (
                            !blocked
                        ) {
                            remove +=
                                index
                        }
                    }
                }

                if (
                    remove.isNotEmpty()
                ) {
                    changed =
                        true

                    remove.forEach {
                        result[it] =
                            false
                    }
                }
            }

            pass++
        }

        return result
    }

    private fun traceSkeleton(
        skeleton: BooleanArray,
        width: Int,
        height: Int
    ): List<List<SkeletonPoint>> {
        fun neighbors(
            index: Int
        ): List<Int> {
            val x =
                index %
                    width

            val y =
                index /
                    width

            val result =
                mutableListOf<Int>()

            for (
                dy in
                    -1..1
            ) {
                for (
                    dx in
                        -1..1
                ) {
                    if (
                        dx ==
                            0 &&
                        dy ==
                            0
                    ) {
                        continue
                    }

                    val nx =
                        x +
                            dx

                    val ny =
                        y +
                            dy

                    if (
                        nx in
                            0 until
                                width &&
                        ny in
                            0 until
                                height
                    ) {
                        val candidate =
                            ny *
                                width +
                                nx

                        if (
                            skeleton[
                                candidate
                            ]
                        ) {
                            result +=
                                candidate
                        }
                    }
                }
            }

            return result
        }

        fun edgeKey(
            first: Int,
            second: Int
        ): Long {
            val low =
                minOf(
                    first,
                    second
                )

            val high =
                maxOf(
                    first,
                    second
                )

            return (
                low.toLong()
                    shl
                    32
                ) or
                (
                    high.toLong() and
                        0xffffffffL
                    )
        }

        val active =
            skeleton.indices
                .filter {
                    skeleton[it]
                }

        if (
            active.isEmpty()
        ) {
            return emptyList()
        }

        val degree =
            HashMap<
                Int,
                Int
            >()

        active.forEach {
            degree[it] =
                neighbors(it)
                    .size
        }

        val starts =
            buildList {
                addAll(
                    active
                        .filter {
                            degree[it] ==
                                1
                        }
                        .sortedBy {
                            it %
                                width
                        }
                )

                addAll(
                    active
                        .filter {
                            (
                                degree[it]
                                    ?: 0
                                ) !=
                                2 &&
                                (
                                    degree[it]
                                        ?: 0
                                    ) >
                                    1
                        }
                        .sortedBy {
                            it %
                                width
                        }
                )

                addAll(
                    active
                )
            }
                .distinct()

        val usedEdges =
            hashSetOf<Long>()

        val lines =
            mutableListOf<
                List<SkeletonPoint>
            >()

        fun directionalScore(
            previous: Int,
            current: Int,
            candidate: Int
        ): Double {
            val px =
                previous %
                    width

            val py =
                previous /
                    width

            val cx =
                current %
                    width

            val cy =
                current /
                    width

            val nx =
                candidate %
                    width

            val ny =
                candidate /
                    width

            val inX =
                (
                    cx -
                        px
                    ).toDouble()

            val inY =
                (
                    cy -
                        py
                    ).toDouble()

            val outX =
                (
                    nx -
                        cx
                    ).toDouble()

            val outY =
                (
                    ny -
                        cy
                    ).toDouble()

            val inLength =
                hypot(
                    inX,
                    inY
                )

            val outLength =
                hypot(
                    outX,
                    outY
                )

            if (
                inLength <
                    0.001 ||
                outLength <
                    0.001
            ) {
                return -2.0
            }

            return (
                inX *
                    outX +
                    inY *
                        outY
                ) /
                (
                    inLength *
                        outLength
                    )
        }

        starts.forEach {
                start ->
            while (
                true
            ) {
                val firstNext =
                    neighbors(
                        start
                    )
                        .firstOrNull {
                            edgeKey(
                                start,
                                it
                            ) !in
                                usedEdges
                        }
                    ?: break

                val line =
                    mutableListOf<Int>()

                line +=
                    start

                var previous =
                    start

                var current =
                    firstNext

                usedEdges +=
                    edgeKey(
                        previous,
                        current
                    )

                line +=
                    current

                var guard =
                    0

                while (
                    guard <
                        active.size *
                            2
                ) {
                    guard++

                    val candidates =
                        neighbors(
                            current
                        )
                            .filter {
                                it !=
                                    previous &&
                                    edgeKey(
                                        current,
                                        it
                                    ) !in
                                        usedEdges
                            }

                    if (
                        candidates
                            .isEmpty()
                    ) {
                        break
                    }

                    val next =
                        candidates
                            .maxByOrNull {
                                directionalScore(
                                    previous,
                                    current,
                                    it
                                )
                            }
                            ?: break

                    usedEdges +=
                        edgeKey(
                            current,
                            next
                        )

                    previous =
                        current

                    current =
                        next

                    line +=
                        current
                }

                if (
                    line.size >=
                        2
                ) {
                    lines +=
                        line.map {
                                index ->
                            SkeletonPoint(
                                x =
                                    index %
                                        width,
                                y =
                                    index /
                                        width
                            )
                        }
                }
            }
        }

        return lines
    }

    private fun orientSkeletonLine(
        line: List<SkeletonPoint>
    ): List<SkeletonPoint> {
        if (
            line.size <
                2
        ) {
            return line
        }

        val first =
            line.first()
        val last =
            line.last()

        return if (
            first.x >
                last.x ||
            (
                first.x ==
                    last.x &&
                first.y >
                    last.y
                )
        ) {
            line.asReversed()
        } else {
            line
        }
    }

    private fun orderSkeletonLinesByProximity(
        source:
            List<List<SkeletonPoint>>
    ): List<List<SkeletonPoint>> {
        if (
            source.size <=
                1
        ) {
            return source
        }

        val remaining =
            source
                .map {
                    orientSkeletonLine(
                        it
                    )
                }
                .toMutableList()

        val ordered =
            mutableListOf<
                List<SkeletonPoint>
            >()

        var current =
            remaining
                .minByOrNull {
                    line ->
                    line.firstOrNull()
                        ?.x
                        ?: Int.MAX_VALUE
                }
                ?: return emptyList()

        remaining.remove(
            current
        )

        ordered +=
            current

        while (
            remaining.isNotEmpty()
        ) {
            val end =
                current.last()

            val next =
                remaining
                    .minByOrNull {
                        line ->
                        minOf(
                            pointDistance(
                                end,
                                line.first()
                            ),
                            pointDistance(
                                end,
                                line.last()
                            )
                        )
                    }
                    ?: break

            remaining.remove(
                next
            )

            current =
                if (
                    pointDistance(
                        end,
                        next.last()
                    ) <
                    pointDistance(
                        end,
                        next.first()
                    )
                ) {
                    next.asReversed()
                } else {
                    next
                }

            ordered +=
                current
        }

        return ordered
    }

    private fun pointDistance(
        first: SkeletonPoint,
        second: SkeletonPoint
    ): Double =
        hypot(
            (
                second.x -
                    first.x
                ).toDouble(),
            (
                second.y -
                    first.y
                ).toDouble()
        )

    private fun appendSplitStitch(
        output:
            MutableList<EmbroideryPoint>,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        maxLengthUnits: Float
    ) {
        val dx =
            toX -
                fromX

        val dy =
            toY -
                fromY

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
                        maxLengthUnits
                ).toInt()
            )

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toFloat() /
                    segments

            val x =
                (
                    fromX +
                        dx *
                            ratio
                    ).roundToInt()

            val y =
                (
                    fromY +
                        dy *
                            ratio
                    ).roundToInt()

            val last =
                output.lastOrNull()

            if (
                last?.xUnits ==
                    x &&
                last.yUnits ==
                    y
            ) {
                continue
            }

            output +=
                EmbroideryPoint(
                    xUnits =
                        x,
                    yUnits =
                        y,
                    command =
                        StitchCommand.STITCH,
                    colorIndex =
                        0
                )
        }
    }

    private fun repairSatinSequence(
        output:
            List<EmbroideryPoint>,
        maxLengthUnits:
            Float
    ): MutableList<EmbroideryPoint> {
        val repaired =
            mutableListOf<
                EmbroideryPoint
            >()

        var previous:
            EmbroideryPoint? =
            null

        output.forEach {
                point ->
            when (
                point.command
            ) {
                StitchCommand.STITCH -> {
                    val before =
                        previous

                    if (
                        before ==
                            null ||
                        (
                            before.xUnits ==
                                point.xUnits &&
                            before.yUnits ==
                                point.yUnits
                            )
                    ) {
                        previous =
                            point

                        return@forEach
                    }

                    appendSplitStitch(
                        output =
                            repaired,
                        fromX =
                            before.xUnits,
                        fromY =
                            before.yUnits,
                        toX =
                            point.xUnits,
                        toY =
                            point.yUnits,
                        maxLengthUnits =
                            maxLengthUnits
                    )

                    previous =
                        repaired.lastOrNull()
                            ?: point
                }

                StitchCommand.JUMP -> {
                    val last =
                        repaired.lastOrNull()

                    if (
                        last?.xUnits !=
                            point.xUnits ||
                        last.yUnits !=
                            point.yUnits ||
                        last.command !=
                            StitchCommand.JUMP
                    ) {
                        repaired +=
                            point
                    }

                    previous =
                        point
                }

                StitchCommand.TRIM,
                StitchCommand.STOP,
                StitchCommand.COLOR_CHANGE,
                StitchCommand.SEQUIN,
                StitchCommand.END -> {
                    if (
                        repaired
                            .lastOrNull()
                            ?.command !=
                            point.command
                    ) {
                        repaired +=
                            point
                    }

                    previous =
                        point
                }
            }
        }

        return repaired
    }

    private fun skeletonLineLength(
        line: List<SkeletonPoint>
    ): Float {
        var length =
            0.0

        for (
            index in
                1 until
                    line.size
        ) {
            val first =
                line[
                    index -
                        1
                ]

            val second =
                line[index]

            length +=
                hypot(
                    (
                        second.x -
                            first.x
                        ).toDouble(),
                    (
                        second.y -
                            first.y
                        ).toDouble()
                )
        }

        return length.toFloat()
    }

    private fun smoothAndResampleSkeleton(
        source: List<SkeletonPoint>,
        radius: Int,
        passes: Int,
        stepPixels: Float
    ): List<FloatSkeletonPoint> {
        if (
            source.size <
                2
        ) {
            return source.map {
                FloatSkeletonPoint(
                    it.x.toFloat(),
                    it.y.toFloat()
                )
            }
        }

        var current =
            source.map {
                FloatSkeletonPoint(
                    it.x.toFloat(),
                    it.y.toFloat()
                )
            }

        repeat(
            passes.coerceAtLeast(
                0
            )
        ) {
            val previous =
                current

            current =
                previous.mapIndexed {
                        index,
                        point ->
                    if (
                        index ==
                            0 ||
                        index ==
                            previous.lastIndex
                    ) {
                        point
                    } else {
                        val start =
                            (
                                index -
                                    radius
                                ).coerceAtLeast(
                                0
                            )

                        val end =
                            (
                                index +
                                    radius
                                ).coerceAtMost(
                                previous.lastIndex
                            )

                        var sumX =
                            0f

                        var sumY =
                            0f

                        var count =
                            0

                        for (
                            neighborIndex in
                                start..end
                        ) {
                            sumX +=
                                previous[
                                    neighborIndex
                                ].x

                            sumY +=
                                previous[
                                    neighborIndex
                                ].y

                            count++
                        }

                        FloatSkeletonPoint(
                            x =
                                sumX /
                                    count,
                            y =
                                sumY /
                                    count
                        )
                    }
                }
        }

        current =
            current.fold(
                mutableListOf<
                    FloatSkeletonPoint
                >()
            ) {
                acc,
                point ->
                val last =
                    acc.lastOrNull()

                if (
                    last ==
                        null ||
                    hypot(
                        (
                            point.x -
                                last.x
                            ).toDouble(),
                        (
                            point.y -
                                last.y
                            ).toDouble()
                    ) >
                        0.15
                ) {
                    acc +=
                        point
                }

                acc
            }

        if (
            current.size <
                2
        ) {
            return current
        }

        val cumulative =
            FloatArray(
                current.size
            )

        for (
            index in
                1 until
                    current.size
        ) {
            cumulative[index] =
                cumulative[
                    index -
                        1
                ] +
                    hypot(
                        (
                            current[index].x -
                                current[
                                    index -
                                        1
                                ].x
                            ).toDouble(),
                        (
                            current[index].y -
                                current[
                                    index -
                                        1
                                ].y
                            ).toDouble()
                    ).toFloat()
        }

        val total =
            cumulative.last()

        val safeStep =
            stepPixels
                .coerceAtLeast(
                    1f
                )

        if (
            total <=
                safeStep
        ) {
            return listOf(
                current.first(),
                current.last()
            )
        }

        val result =
            mutableListOf<
                FloatSkeletonPoint
            >()

        var target =
            0f

        var segment =
            1

        while (
            target <=
                total
        ) {
            while (
                segment <
                    cumulative.size -
                        1 &&
                cumulative[segment] <
                    target
            ) {
                segment++
            }

            val beforeIndex =
                (
                    segment -
                        1
                    ).coerceAtLeast(
                    0
                )

            val afterIndex =
                segment.coerceAtMost(
                    current.lastIndex
                )

            val beforeDistance =
                cumulative[
                    beforeIndex
                ]

            val afterDistance =
                cumulative[
                    afterIndex
                ]

            val span =
                (
                    afterDistance -
                        beforeDistance
                    ).coerceAtLeast(
                    0.0001f
                )

            val ratio =
                (
                    (
                        target -
                            beforeDistance
                        ) /
                        span
                    ).coerceIn(
                    0f,
                    1f
                )

            val before =
                current[
                    beforeIndex
                ]

            val after =
                current[
                    afterIndex
                ]

            result +=
                FloatSkeletonPoint(
                    x =
                        before.x +
                            (
                                after.x -
                                    before.x
                                ) *
                                ratio,
                    y =
                        before.y +
                            (
                                after.y -
                                    before.y
                                ) *
                                ratio
                )

            target +=
                safeStep
        }

        val last =
            current.last()

        if (
            result.isEmpty() ||
            hypot(
                (
                    last.x -
                        result.last().x
                    ).toDouble(),
                (
                    last.y -
                        result.last().y
                    ).toDouble()
            ) >
                safeStep *
                    0.35f
        ) {
            result +=
                last
        }

        return result
    }

    private fun smoothSkeletonLine(
        source: List<SkeletonPoint>,
        radius: Int,
        passes: Int
    ): List<SkeletonPoint> {
        if (
            source.size <
                5 ||
            radius <=
                0 ||
            passes <=
                0
        ) {
            return source
        }

        var current =
            source

        repeat(
            passes
        ) {
            current =
                current.mapIndexed {
                        index,
                        point ->
                    if (
                        index ==
                            0 ||
                        index ==
                            current.lastIndex
                    ) {
                        point
                    } else {
                        val start =
                            (
                                index -
                                    radius
                                ).coerceAtLeast(
                                0
                            )

                        val end =
                            (
                                index +
                                    radius
                                ).coerceAtMost(
                                current.lastIndex
                            )

                        var sumX =
                            0

                        var sumY =
                            0

                        var count =
                            0

                        for (
                            neighborIndex in
                                start..end
                        ) {
                            sumX +=
                                current[
                                    neighborIndex
                                ].x

                            sumY +=
                                current[
                                    neighborIndex
                                ].y

                            count++
                        }

                        SkeletonPoint(
                            x =
                                (
                                    sumX.toFloat() /
                                        count
                                    ).roundToInt(),
                            y =
                                (
                                    sumY.toFloat() /
                                        count
                                    ).roundToInt()
                        )
                    }
                }
        }

        return current
            .fold(
                mutableListOf<
                    SkeletonPoint
                >()
            ) {
                acc,
                point ->
                if (
                    acc.lastOrNull() !=
                        point
                ) {
                    acc +=
                        point
                }

                acc
            }
    }

    private fun sampleSkeletonLine(
        line: List<SkeletonPoint>,
        stepPixels: Float
    ): List<SkeletonPoint> {
        if (
            line.size <=
                2
        ) {
            return line
        }

        val result =
            mutableListOf<
                SkeletonPoint
            >()

        result +=
            line.first()

        var accumulated =
            0.0

        var previous =
            line.first()

        for (
            index in
                1 until
                    line.size
        ) {
            val current =
                line[index]

            accumulated +=
                hypot(
                    (
                        current.x -
                            previous.x
                        ).toDouble(),
                    (
                        current.y -
                            previous.y
                        ).toDouble()
                )

            if (
                accumulated >=
                    stepPixels
            ) {
                result +=
                    current
                accumulated =
                    0.0
            }

            previous =
                current
        }

        if (
            result.last() !=
                line.last()
        ) {
            result +=
                line.last()
        }

        return result
    }

    private fun boundaryDistance(
        mask: BooleanArray,
        width: Int,
        height: Int,
        centerX: Double,
        centerY: Double,
        normalX: Double,
        normalY: Double,
        direction: Double
    ): Double {
        var distance =
            0.5

        val limit =
            90.0

        while (
            distance <=
                limit
        ) {
            val x =
                (
                    centerX +
                        normalX *
                            distance *
                            direction
                    ).roundToInt()

            val y =
                (
                    centerY +
                        normalY *
                            distance *
                            direction
                    ).roundToInt()

            if (
                x !in
                    0 until
                        width ||
                y !in
                    0 until
                        height ||
                !mask[
                    y *
                        width +
                        x
                ]
            ) {
                return max(
                    0.0,
                    distance -
                        0.5
                )
            }

            distance +=
                0.5
        }

        return limit
    }

    private fun skeletonXToUnits(
        x: Float,
        minX: Int,
        padding: Int,
        unitsPerPixel: Float
    ): Int =
        (
            minX +
                (
                    x -
                        padding
                    ) *
                    unitsPerPixel
            ).roundToInt()

    private fun skeletonYToUnits(
        y: Float,
        maxY: Int,
        padding: Int,
        unitsPerPixel: Float
    ): Int =
        (
            maxY -
                (
                    y -
                        padding
                    ) *
                    unitsPerPixel
            ).roundToInt()

    private fun buildFilledText(
        contours: List<SampledContour>,
        rowStepUnits: Float,
        pullCompensationUnits: Float,
        maxStitchUnits: Float
    ): MutableList<EmbroideryPoint> {
        val allPoints =
            contours.flatMap {
                it.points
            }

        val minY =
            allPoints.minOf {
                it.second
            }

        val maxY =
            allPoints.maxOf {
                it.second
            }

        val step =
            rowStepUnits
                .coerceIn(
                    3f,
                    12f
                )

        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        var currentX = 0
        var currentY = 0
        var hasCurrent = false
        var rowIndex = 0
        var previousSingleSegment =
            false

        var scanY =
            minY.toFloat() +
                step /
                    2f

        while (
            scanY <=
                maxY.toFloat() +
                    0.01f
        ) {
            val intersections =
                mutableListOf<Float>()

            contours.forEach {
                    contour ->
                val polygon =
                    if (
                        contour.closed &&
                        contour.points.first() !=
                            contour.points.last()
                    ) {
                        contour.points +
                            contour.points.first()
                    } else {
                        contour.points
                    }

                for (
                    index in
                        1 until polygon.size
                ) {
                    val first =
                        polygon[
                            index -
                                1
                        ]

                    val second =
                        polygon[index]

                    val y1 =
                        first.second
                            .toFloat()

                    val y2 =
                        second.second
                            .toFloat()

                    val crosses =
                        (
                            y1 <= scanY &&
                                y2 > scanY
                            ) ||
                            (
                                y2 <= scanY &&
                                    y1 > scanY
                                )

                    if (!crosses) {
                        continue
                    }

                    val ratio =
                        (
                            scanY -
                                y1
                            ) /
                            (
                                y2 -
                                    y1
                                )

                    intersections +=
                        first.first +
                            (
                                second.first -
                                    first.first
                                ) *
                                ratio
                }
            }

            intersections.sort()

            val segments =
                mutableListOf<
                    Pair<Int, Int>
                >()

            var index = 0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val left =
                    (
                        intersections[index] -
                            pullCompensationUnits
                        ).roundToInt()

                val right =
                    (
                        intersections[
                            index +
                                1
                        ] +
                            pullCompensationUnits
                        ).roundToInt()

                if (
                    right -
                        left >=
                        2
                ) {
                    segments +=
                        Pair(
                            left,
                            right
                        )
                }

                index +=
                    2
            }

            if (
                segments.isNotEmpty()
            ) {
                val reversed =
                    rowIndex %
                        2 ==
                        1

                val ordered =
                    if (reversed) {
                        segments.asReversed()
                    } else {
                        segments
                    }

                ordered.forEachIndexed {
                        segmentIndex,
                        segment ->
                    val startX =
                        if (reversed) {
                            segment.second
                        } else {
                            segment.first
                        }

                    val endX =
                        if (reversed) {
                            segment.first
                        } else {
                            segment.second
                        }

                    val y =
                        scanY
                            .roundToInt()

                    val canConnect =
                        hasCurrent &&
                            previousSingleSegment &&
                            segments.size ==
                                1 &&
                            segmentIndex ==
                                0 &&
                            hypot(
                                (
                                    startX -
                                        currentX
                                    ).toDouble(),
                                (
                                    y -
                                        currentY
                                    ).toDouble()
                            ) <=
                                max(
                                    24f,
                                    step *
                                        4f
                                ).toDouble()

                    if (canConnect) {
                        val connector =
                            appendRunning(
                                output =
                                    output,
                                fromX =
                                    currentX,
                                fromY =
                                    currentY,
                                toX =
                                    startX,
                                toY =
                                    y,
                                maxLengthUnits =
                                    24f
                            )

                        currentX =
                            connector.first

                        currentY =
                            connector.second
                    } else {
                        if (hasCurrent) {
                            val distance =
                                hypot(
                                    (
                                        startX -
                                            currentX
                                        ).toDouble(),
                                    (
                                        y -
                                            currentY
                                        ).toDouble()
                                )

                            if (
                                distance >
                                    40.0
                            ) {
                                output +=
                                    EmbroideryPoint(
                                        currentX,
                                        currentY,
                                        StitchCommand.TRIM,
                                        0
                                    )
                            }
                        }

                        output +=
                            EmbroideryPoint(
                                startX,
                                y,
                                StitchCommand.JUMP,
                                0
                            )

                        currentX =
                            startX

                        currentY =
                            y

                        hasCurrent =
                            true
                    }

                    val filled =
                        appendRunning(
                            output =
                                output,
                            fromX =
                                currentX,
                            fromY =
                                currentY,
                            toX =
                                endX,
                            toY =
                                y,
                            maxLengthUnits =
                                maxStitchUnits
                        )

                    currentX =
                        filled.first

                    currentY =
                        filled.second

                    hasCurrent =
                        true
                }

                previousSingleSegment =
                    segments.size ==
                        1
            } else {
                previousSingleSegment =
                    false
            }

            rowIndex++
            scanY +=
                step
        }

        return output
    }

    private fun resolveCharacter(
        paint: Paint,
        font: ImportedFont,
        char: Char
    ): String {
        if (
            char.isWhitespace()
        ) {
            return char.toString()
        }

        val original =
            char.toString()

        if (
            paint.hasGlyph(
                original
            )
        ) {
            return original
        }

        val normalized =
            Normalizer
                .normalize(
                    original,
                    Normalizer.Form.NFD
                )
                .replace(
                    Regex("\\p{M}+"),
                    ""
                )

        if (
            normalized.isNotBlank() &&
            paint.hasGlyph(
                normalized
            )
        ) {
            return normalized
        }

        error(
            "A fonte " +
                font.displayName +
                " não possui o caractere \"" +
                char +
                "\"."
        )
    }

    private data class SampledContour(
        val points:
            List<Pair<Int, Int>>,
        val closed: Boolean
    )

    private data class RawSampledContour(
        val points:
            List<Pair<Float, Float>>,
        val closed: Boolean
    )

    private fun buildGuidePoints(
        contours:
            List<SampledContour>
    ): List<EmbroideryPoint> {
        val result =
            mutableListOf<
                EmbroideryPoint
            >()

        contours.forEach {
                contour ->
            val first =
                contour.points
                    .firstOrNull()
                    ?: return@forEach

            if (
                result.isNotEmpty()
            ) {
                val last =
                    result.last()

                result +=
                    EmbroideryPoint(
                        last.xUnits,
                        last.yUnits,
                        StitchCommand.TRIM,
                        0
                    )
            }

            result +=
                EmbroideryPoint(
                    first.first,
                    first.second,
                    StitchCommand.JUMP,
                    0
                )

            contour.points
                .drop(1)
                .forEach {
                        point ->
                    result +=
                        EmbroideryPoint(
                            point.first,
                            point.second,
                            StitchCommand.STITCH,
                            0
                        )
                }

            if (
                contour.closed &&
                contour.points
                    .lastOrNull() !=
                    first
            ) {
                result +=
                    EmbroideryPoint(
                        first.first,
                        first.second,
                        StitchCommand.STITCH,
                        0
                    )
            }
        }

        return result
    }

    private fun samplePath(
        path: Path
    ): List<RawSampledContour> {
        val measure =
            PathMeasure(
                path,
                false
            )

        val result =
            mutableListOf<
                RawSampledContour
            >()

        val position =
            FloatArray(2)

        do {
            val length =
                measure.length

            if (length <= 0f) {
                continue
            }

            val step =
                (
                    length /
                        180f
                    ).coerceIn(
                    1.2f,
                    10f
                )

            val contour =
                mutableListOf<
                    Pair<Float, Float>
                >()

            var distance = 0f

            while (
                distance <
                    length
            ) {
                if (
                    measure.getPosTan(
                        distance,
                        position,
                        null
                    )
                ) {
                    contour +=
                        Pair(
                            position[0],
                            position[1]
                        )
                }

                distance +=
                    step
            }

            if (
                measure.getPosTan(
                    length,
                    position,
                    null
                )
            ) {
                contour +=
                    Pair(
                        position[0],
                        position[1]
                    )
            }

            if (
                contour.size >=
                    2
            ) {
                result +=
                    RawSampledContour(
                        points =
                            contour,
                        closed =
                            measure.isClosed
                    )
            }
        } while (
            measure.nextContour()
        )

        return result
    }

    private fun appendRunning(
        output:
            MutableList<EmbroideryPoint>,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        maxLengthUnits: Float
    ): Pair<Int, Int> {
        val dx =
            toX -
                fromX

        val dy =
            toY -
                fromY

        val distance =
            hypot(
                dx.toDouble(),
                dy.toDouble()
            )

        val safeMaxLength =
            maxLengthUnits
                .coerceAtLeast(
                    1f
                )

        val segments =
            max(
                1,
                ceil(
                    distance /
                        safeMaxLength
                ).toInt()
            )

        for (
            part in
                1..segments
        ) {
            val ratio =
                part.toFloat() /
                    segments

            output +=
                EmbroideryPoint(
                    xUnits =
                        (
                            fromX +
                                dx *
                                    ratio
                            ).roundToInt(),
                    yUnits =
                        (
                            fromY +
                                dy *
                                    ratio
                            ).roundToInt(),
                    command =
                        StitchCommand.STITCH,
                    colorIndex =
                        0
                )
        }

        return Pair(
            toX,
            toY
        )
    }

    private fun safeName(
        value: String
    ): String =
        Normalizer
            .normalize(
                value,
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
            .take(24)
            .ifBlank {
                "texto"
            }
}
