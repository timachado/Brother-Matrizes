package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.EmbroideryStressPolicy
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Digitalizador de texto TTF/OTF baseado no mesmo pipeline funcional
 * observado no app de referência:
 * contorno do glifo -> polygonize -> scan spans em dois eixos ->
 * escolha da menor largura média -> colunas Satin -> divisão de
 * colunas largas -> underlay/locks -> pontos.
 *
 * A implementação é própria e independente do código do app de referência.
 */
internal object ReferenceImportedFontEngine {

    private const val DEFAULT_SATIN_DENSITY_MM =
        0.4f

    private const val DEFAULT_MAX_SATIN_WIDTH_MM =
        7f

    private const val DEFAULT_PULL_MM =
        0.2f

    private const val LETTER_SPACING_FACTOR =
        0.04f

    private const val MAX_STITCH_UNITS =
        70f

    private const val TRIM_DISTANCE_UNITS =
        50f

    private const val CONTINUOUS_CONNECTOR_STITCH_UNITS =
        15f

    /*
     * Colunas do mesmo traço podem terminar alguns milímetros separadas
     * pela discretização do contorno. Até 2,5 mm a referência continua
     * costurando em vez de transformar a transição em salto.
     */
    private const val NEAR_COLUMN_JOIN_UNITS =
        25f

    private const val GLYPH_JOIN_UNITS =
        12f

    private const val CONNECTOR_SAMPLE_UNITS =
        2f

    private const val CONNECTOR_EDGE_MARGIN_UNITS =
        1f

    private const val MAX_CONTOUR_SAMPLES =
        50_000

    private const val MAX_POLYGON_SAMPLES_PER_GLYPH =
        200_000

    private const val MAX_SCAN_LINES =
        20_000

    private const val MAX_FONT_GEOMETRY_UNITS =
        100_000f

    private data class FPoint(
        val x: Float,
        val y: Float
    )

    private data class Polygon(
        val points: List<FPoint>
    )

    private data class SpanSegment(
        val position: Float,
        val start: Float,
        val end: Float,
        val transposed: Boolean
    ) {
        fun withPull(
            pullUnits: Float
        ): SatinRow =
            if (
                transposed
            ) {
                SatinRow(
                    a =
                        FPoint(
                            position,
                            start -
                                pullUnits
                        ),
                    b =
                        FPoint(
                            position,
                            end +
                                pullUnits
                        )
                )
            } else {
                SatinRow(
                    a =
                        FPoint(
                            start -
                                pullUnits,
                            position
                        ),
                    b =
                        FPoint(
                            end +
                                pullUnits,
                            position
                        )
                )
            }
    }

    private data class SatinRow(
        val a: FPoint,
        val b: FPoint
    )

    private data class SatinColumn(
        val rows:
            MutableList<SatinRow> =
            mutableListOf()
    )

    private data class ActiveColumn(
        val lastSpan: SpanSegment,
        val column: SatinColumn
    )

    private data class GlyphPath(
        val path: Path
    )

    fun generate(
        font: ImportedFont,
        sourceText: String,
        options: TextMatrixOptions,
        filePrefix: String
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                options.heightMm in
                    4f..60f
            ) {
                "A altura deve ficar entre 4 e 60 mm."
            }

            require(
                options.satinDensityMm >
                    0.05f
            ) {
                "Densidade Satin inválida."
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

            val typeface =
                ImportedFontStore
                    .loadTypeface(
                        font
                    )
                    .getOrThrow()

            val targetHeightUnits =
                options.heightMm *
                    10f

            val paint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    this.typeface =
                        typeface
                    style =
                        Paint.Style.FILL
                    textSize =
                        resolveFontSizeForCapHeight(
                            this,
                            targetHeightUnits
                        )
                }

            val renderableText =
                resolveText(
                    paint =
                        paint,
                    font =
                        font,
                    text =
                        text
                )

            val spacingUnits =
                targetHeightUnits *
                    LETTER_SPACING_FACTOR +
                    options.spacingMm *
                        10f

            val glyphPaths =
                extractGlyphPaths(
                    paint =
                        paint,
                    text =
                        renderableText,
                    spacingUnits =
                        spacingUnits
                )

            require(
                glyphPaths.isNotEmpty()
            ) {
                "A fonte não gerou glifos bordáveis."
            }

            val unionBounds =
                unionBounds(
                    glyphPaths
                )

            require(
                unionBounds.width() >
                    0.5f &&
                unionBounds.height() >
                    0.5f
            ) {
                "A fonte não gerou uma área bordável."
            }

            require(
                unionBounds.left.isFinite() &&
                    unionBounds.top.isFinite() &&
                    unionBounds.right.isFinite() &&
                    unionBounds.bottom.isFinite() &&
                    unionBounds.width() <=
                        MAX_FONT_GEOMETRY_UNITS &&
                    unionBounds.height() <=
                        MAX_FONT_GEOMETRY_UNITS
            ) {
                "A geometria da fonte é extrema demais para processar com segurança."
            }

            val centerX =
                unionBounds.centerX()

            val centerY =
                unionBounds.centerY()

            val polygonsByGlyph =
                glyphPaths.map {
                        glyph ->
                    polygonize(
                        path =
                            glyph.path,
                        centerX =
                            centerX,
                        centerY =
                            centerY
                    )
                }

            val guidePoints =
                buildGuidePoints(
                    polygonsByGlyph
                        .flatten()
                )

            val densityMm =
                if (
                    options.satinDensityMm in
                        0.06f..2f
                ) {
                    options.satinDensityMm
                } else {
                    DEFAULT_SATIN_DENSITY_MM
                }

            val pullMm =
                if (
                    options.satinPullCompensationMm in
                        0f..1f
                ) {
                    options.satinPullCompensationMm
                } else {
                    DEFAULT_PULL_MM
                }

            /*
             * O app de referência usa 7 mm como largura máxima Satin.
             * O Brother Matrizes antigo usava 2,4 mm como "largura" fixa, o que
             * fragmentava demais letras largas. Aqui a forma da fonte
             * define a largura e este valor atua somente como limite para
             * dividir colunas fisicamente largas.
             */
            val maxSatinWidthMm =
                DEFAULT_MAX_SATIN_WIDTH_MM

            val points =
                mutableListOf<
                    EmbroideryPoint
                >()

            val emitter =
                SatinEmitter(
                    output =
                        points
                )

            polygonsByGlyph
                .forEach {
                        polygons ->
                    val columns =
                        sampleColumns(
                            polygons =
                                polygons,
                            densityMm =
                                densityMm,
                            maxSatinWidthMm =
                                maxSatinWidthMm,
                            pullCompensationMm =
                                pullMm
                        )

                    emitter.emitGlyph(
                        columns =
                            columns,
                        polygons =
                            polygons,
                        startHint =
                            glyphVisualStartPoint(
                                polygons
                            ),
                        underlayMode =
                            options
                                .satinUnderlayMode,
                        densityMm =
                            densityMm
                    )
                }

            require(
                points.any {
                    it.command ==
                        StitchCommand.STITCH
                }
            ) {
                "O texto não produziu pontos — conteúdo sem área bordável."
            }

            val end =
                points.last()

            points +=
                EmbroideryPoint(
                    end.xUnits,
                    end.yUnits,
                    StitchCommand.END,
                    0
                )

            val coordinates =
                points.filter {
                    it.command !=
                        StitchCommand.END
                }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        coordinates.minOf {
                            it.xUnits
                        },
                    maxXUnits =
                        coordinates.maxOf {
                            it.xUnits
                        },
                    minYUnits =
                        coordinates.minOf {
                            it.yUnits
                        },
                    maxYUnits =
                        coordinates.maxOf {
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
                        points,
                    bounds =
                        bounds,
                    stitchCount =
                        points.count {
                            it.command ==
                                StitchCommand.STITCH
                        },
                    jumpCount =
                        points.count {
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
                        guidePoints,
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

            EmbroideryStressPolicy
                .requireGeneratedSafe(
                    design
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

                require(
                    fit.fits
                ) {
                    "A matriz ultrapassa a área segura do bastidor " +
                        options
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            design
        }

    private fun resolveFontSizeForCapHeight(
        paint: Paint,
        targetCapHeightUnits: Float
    ): Float {
        paint.textSize =
            100f

        val capPath =
            Path()

        val capChar =
            when {
                paint.hasGlyph(
                    "H"
                ) ->
                    "H"

                paint.hasGlyph(
                    "X"
                ) ->
                    "X"

                else ->
                    null
            }

        val capHeight =
            if (
                capChar !=
                    null
            ) {
                paint.getTextPath(
                    capChar,
                    0,
                    capChar.length,
                    0f,
                    0f,
                    capPath
                )

                val bounds =
                    RectF()

                capPath.computeBounds(
                    bounds,
                    true
                )

                bounds.height()
            } else {
                0f
            }

        val fallback =
            abs(
                paint
                    .fontMetrics
                    .ascent
            ) *
                0.72f

        val resolvedCapHeight =
            if (
                capHeight >
                    0.001f
            ) {
                capHeight
            } else {
                fallback
            }
                .coerceAtLeast(
                    1f
                )

        return 100f *
            targetCapHeightUnits /
            resolvedCapHeight
    }

    private fun resolveText(
        paint: Paint,
        font: ImportedFont,
        text: String
    ): String =
        text.map {
                char ->
            if (
                char.isWhitespace()
            ) {
                char.toString()
            } else {
                val original =
                    char.toString()

                if (
                    paint.hasGlyph(
                        original
                    )
                ) {
                    original
                } else {
                    val normalized =
                        Normalizer
                            .normalize(
                                original,
                                Normalizer.Form.NFD
                            )
                            .replace(
                                Regex(
                                    "\\p{M}+"
                                ),
                                ""
                            )

                    if (
                        normalized.isNotBlank() &&
                        paint.hasGlyph(
                            normalized
                        )
                    ) {
                        normalized
                    } else {
                        error(
                            "A fonte " +
                                font.displayName +
                                " não possui o caractere \"" +
                                char +
                                "\"."
                        )
                    }
                }
            }
        }.joinToString(
            ""
        )

    private fun extractGlyphPaths(
        paint: Paint,
        text: String,
        spacingUnits: Float
    ): List<GlyphPath> {
        val paths =
            mutableListOf<
                GlyphPath
            >()

        var penX =
            0f

        text.forEachIndexed {
                index,
                char ->
            val value =
                char.toString()

            if (
                !char.isWhitespace()
            ) {
                val path =
                    Path()

                paint.getTextPath(
                    value,
                    0,
                    value.length,
                    penX,
                    0f,
                    path
                )

                if (
                    !path.isEmpty
                ) {
                    paths +=
                        GlyphPath(
                            path
                        )
                }
            }

            penX +=
                paint.measureText(
                    value
                )

            if (
                index <
                    text.lastIndex
            ) {
                penX +=
                    spacingUnits
            }
        }

        return paths
    }

    private fun unionBounds(
        glyphs: List<GlyphPath>
    ): RectF {
        val union =
            RectF()

        var initialized =
            false

        glyphs.forEach {
                glyph ->
            val bounds =
                RectF()

            glyph.path
                .computeBounds(
                    bounds,
                    true
                )

            if (
                !initialized
            ) {
                union.set(
                    bounds
                )

                initialized =
                    true
            } else {
                union.union(
                    bounds
                )
            }
        }

        return union
    }

    private fun polygonize(
        path: Path,
        centerX: Float,
        centerY: Float
    ): List<Polygon> {
        val measure =
            PathMeasure(
                path,
                true
            )

        val polygons =
            mutableListOf<
                Polygon
            >()

        val position =
            FloatArray(
                2
            )

        var totalSamples =
            0

        do {
            val length =
                measure.length

            if (
                length <=
                    0f
            ) {
                continue
            }

            require(
                length.isFinite()
            ) {
                "A fonte contém um contorno inválido."
            }

            val sampleCount =
                max(
                    8,
                    ceil(
                        length /
                            2f
                    ).toInt()
                )

            require(
                sampleCount <=
                    MAX_CONTOUR_SAMPLES
            ) {
                "Um glifo da fonte possui contorno complexo demais."
            }

            totalSamples +=
                sampleCount

            require(
                totalSamples <=
                    MAX_POLYGON_SAMPLES_PER_GLYPH
            ) {
                "Um glifo da fonte possui detalhes demais para digitalizar com segurança."
            }

            val points =
                mutableListOf<
                    FPoint
                >()

            for (
                index in
                    0 until
                        sampleCount
            ) {
                val distance =
                    length *
                        index /
                        sampleCount

                if (
                    measure.getPosTan(
                        distance,
                        position,
                        null
                    )
                ) {
                    points +=
                        FPoint(
                            x =
                                position[0] -
                                    centerX,
                            y =
                                centerY -
                                    position[1]
                        )
                }
            }

            if (
                points.size >=
                    3
            ) {
                polygons +=
                    Polygon(
                        points
                    )
            }
        } while (
            measure.nextContour()
        )

        return polygons
    }

    private fun glyphVisualStartPoint(
        polygons: List<Polygon>
    ): FPoint? {
        val points =
            polygons.flatMap {
                it.points
            }

        if (
            points.isEmpty()
        ) {
            return null
        }

        val minX =
            points.minOf {
                it.x
            }

        val maxX =
            points.maxOf {
                it.x
            }

        val minY =
            points.minOf {
                it.y
            }

        val maxY =
            points.maxOf {
                it.y
            }

        val glyphWidth =
            (
                maxX -
                    minX
                ).coerceAtLeast(
                1f
            )

        val glyphHeight =
            (
                maxY -
                    minY
                ).coerceAtLeast(
                1f
            )

        /*
         * O ponto inicial de uma capital cursiva não é necessariamente o
         * ponto mais baixo nem a extremidade mais à esquerda do contorno.
         * No "M" da referência existe um laço ornamental à esquerda cujo
         * fundo fica abaixo do pé do primeiro traço principal.
         *
         * Procuramos mínimos LOCAIS do contorno na metade inferior do glifo.
         * Se o primeiro mínimo fica colado à borda esquerda e existe outro
         * mínimo bem definido logo depois, tratamos o primeiro como floreio
         * de entrada e começamos no segundo vale — o pé do traço principal.
         */
        val valleyLimitY =
            minY +
                glyphHeight *
                    0.42f

        val prominence =
            max(
                2f,
                glyphHeight *
                    0.025f
            )

        val valleys =
            mutableListOf<
                FPoint
            >()

        polygons.forEach {
                polygon ->
            val contour =
                polygon.points

            if (
                contour.size <
                    7
            ) {
                return@forEach
            }

            val window =
                minOf(
                    6,
                    max(
                        2,
                        contour.size /
                            24
                    )
                )

            contour.indices.forEach {
                    index ->
                val point =
                    contour[index]

                if (
                    point.y >
                        valleyLimitY
                ) {
                    return@forEach
                }

                var leftPeak =
                    Float.NEGATIVE_INFINITY

                var rightPeak =
                    Float.NEGATIVE_INFINITY

                for (
                    offset in
                        1..window
                ) {
                    val left =
                        contour[
                            (
                                index -
                                    offset +
                                    contour.size
                                ) %
                                contour.size
                        ]

                    val right =
                        contour[
                            (
                                index +
                                    offset
                                ) %
                                contour.size
                        ]

                    leftPeak =
                        max(
                            leftPeak,
                            left.y
                        )

                    rightPeak =
                        max(
                            rightPeak,
                            right.y
                        )
                }

                if (
                    leftPeak -
                        point.y >=
                        prominence &&
                    rightPeak -
                        point.y >=
                        prominence
                ) {
                    valleys +=
                        point
                }
            }
        }

        val valleyMergeDistance =
            max(
                4f,
                glyphWidth *
                    0.045f
            )

        val valleyGroups =
            mutableListOf<
                MutableList<FPoint>
            >()

        valleys
            .sortedBy {
                it.x
            }
            .forEach {
                    point ->
                val group =
                    valleyGroups.lastOrNull()

                if (
                    group ==
                        null ||
                    point.x -
                        group.last().x >
                        valleyMergeDistance
                ) {
                    valleyGroups +=
                        mutableListOf(
                            point
                        )
                } else {
                    group +=
                        point
                }
            }

        val valleyRepresentatives =
            valleyGroups.map {
                    group ->
                group.minWithOrNull(
                    compareBy<FPoint> {
                        it.y
                    }.thenBy {
                        it.x
                    }
                )!!
            }

        if (
            valleyRepresentatives.size >=
                2
        ) {
            val first =
                valleyRepresentatives[0]

            val second =
                valleyRepresentatives[1]

            val firstPosition =
                (
                    first.x -
                        minX
                    ) /
                    glyphWidth

            val secondPosition =
                (
                    second.x -
                        minX
                    ) /
                    glyphWidth

            val gap =
                (
                    second.x -
                        first.x
                    ) /
                    glyphWidth

            if (
                firstPosition <=
                    0.12f &&
                secondPosition in
                    0.14f..0.45f &&
                gap >=
                    0.10f
            ) {
                return second
            }

            return first
        }

        /*
         * Fallback para glifos sem dois vales bem definidos: usa a faixa
         * inferior, mas sem tentar inventar uma estrutura de traço que a
         * geometria não demonstrou.
         */
        val lowerBand =
            max(
                2f,
                glyphHeight *
                    0.14f
            )

        return points
            .filter {
                it.y <=
                    minY +
                        lowerBand
            }
            .ifEmpty {
                points
            }
            .minWithOrNull(
                compareBy<FPoint> {
                    it.x
                }.thenBy {
                    it.y
                }
            )
    }

    private fun columnLeftEdgeX(
        column: SatinColumn
    ): Float =
        column.rows
            .flatMap {
                    row ->
                listOf(
                    row.a.x,
                    row.b.x
                )
            }
            .minOrNull()
            ?: Float.MAX_VALUE


    private fun columnTopEdgeY(
        column: SatinColumn
    ): Float =
        column.rows
            .flatMap {
                    row ->
                listOf(
                    row.a.y,
                    row.b.y
                )
            }
            .minOrNull()
            ?: Float.MAX_VALUE


    private fun nextColumnInReadingOrder(
        columns: List<SatinColumn>
    ): SatinColumn? =
        columns.minWithOrNull(
            compareBy<SatinColumn> {
                columnLeftEdgeX(
                    it
                )
            }.thenBy {
                columnTopEdgeY(
                    it
                )
            }
        )

    private fun sampleColumns(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float
    ): List<SatinColumn> {
        if (
            polygons.isEmpty()
        ) {
            return emptyList()
        }

        val pitchUnits =
            densityMm *
                10f

        val normal =
            scanSpans(
                polygons =
                    polygons,
                pitchUnits =
                    pitchUnits,
                transposed =
                    false
            )

        val transposed =
            scanSpans(
                polygons =
                    polygons,
                pitchUnits =
                    pitchUnits,
                transposed =
                    true
            )

        val normalWidth =
            meanSpanWidth(
                normal
            )

        val transposedWidth =
            meanSpanWidth(
                transposed
            )

        val selected =
            if (
                transposedWidth <
                    normalWidth
            ) {
                transposed
            } else {
                normal
            }

        return buildColumns(
            scanLines =
                selected,
            maxWidthUnits =
                maxSatinWidthMm *
                    10f,
            pullUnits =
                pullCompensationMm *
                    10f
        )
    }

    private fun scanSpans(
        polygons: List<Polygon>,
        pitchUnits: Float,
        transposed: Boolean
    ): List<List<SpanSegment>> {
        val all =
            polygons.flatMap {
                it.points
            }

        if (
            all.isEmpty()
        ) {
            return emptyList()
        }

        fun scanCoordinate(
            point: FPoint
        ): Float =
            if (
                transposed
            ) {
                point.x
            } else {
                point.y
            }

        fun crossCoordinate(
            point: FPoint
        ): Float =
            if (
                transposed
            ) {
                point.y
            } else {
                point.x
            }

        val minimum =
            all.minOf {
                scanCoordinate(
                    it
                )
            }

        val maximum =
            all.maxOf {
                scanCoordinate(
                    it
                )
            }

        val safePitch =
            pitchUnits
                .coerceAtLeast(
                    0.5f
                )

        val result =
            mutableListOf<
                List<SpanSegment>
            >()

        val estimatedLines =
            ceil(
                (
                    maximum -
                        minimum
                    ).toDouble() /
                    safePitch
            ).toLong()

        require(
            estimatedLines in
                0..MAX_SCAN_LINES.toLong()
        ) {
            "A geometria da fonte exige varredura complexa demais."
        }

        var scan =
            minimum +
                safePitch /
                    2f

        while (
            scan <=
                maximum
        ) {
            val intersections =
                mutableListOf<
                    Float
                >()

            polygons.forEach {
                    polygon ->
                val points =
                    polygon.points

                if (
                    points.size <
                        3
                ) {
                    return@forEach
                }

                for (
                    index in
                        points.indices
                ) {
                    val first =
                        points[
                            index
                        ]

                    val second =
                        points[
                            (
                                index +
                                    1
                                ) %
                                points.size
                        ]

                    val firstScan =
                        scanCoordinate(
                            first
                        )

                    val secondScan =
                        scanCoordinate(
                            second
                        )

                    val crosses =
                        (
                            firstScan <=
                                scan &&
                            secondScan >
                                scan
                            ) ||
                            (
                                secondScan <=
                                    scan &&
                                firstScan >
                                    scan
                                )

                    if (
                        !crosses
                    ) {
                        continue
                    }

                    val ratio =
                        (
                            scan -
                                firstScan
                            ) /
                            (
                                secondScan -
                                    firstScan
                                )

                    intersections +=
                        crossCoordinate(
                            first
                        ) +
                            (
                                crossCoordinate(
                                    second
                                ) -
                                    crossCoordinate(
                                        first
                                    )
                                ) *
                                ratio
                }
            }

            intersections.sort()

            val spans =
                mutableListOf<
                    SpanSegment
                >()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val start =
                    intersections[
                        index
                    ]

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
                        SpanSegment(
                            position =
                                scan,
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

            result +=
                spans

            scan +=
                safePitch
        }

        return result
    }

    private fun meanSpanWidth(
        lines:
            List<List<SpanSegment>>
    ): Double {
        var count =
            0

        var total =
            0.0

        lines.forEach {
                spans ->
            spans.forEach {
                    span ->
                total +=
                    (
                        span.end -
                            span.start
                        ).toDouble()

                count++
            }
        }

        return if (
            count ==
                0
        ) {
            Double.MAX_VALUE
        } else {
            total /
                count
        }
    }

    private fun buildColumns(
        scanLines:
            List<List<SpanSegment>>,
        maxWidthUnits: Float,
        pullUnits: Float
    ): List<SatinColumn> {
        val finished =
            mutableListOf<
                SatinColumn
            >()

        var active =
            mutableListOf<
                ActiveColumn
            >()

        scanLines.forEach {
                spans ->
            val used =
                BooleanArray(
                    spans.size
                )

            val nextActive =
                mutableListOf<
                    ActiveColumn
                >()

            active.forEach {
                    item ->
                var matchIndex =
                    -1

                for (
                    index in
                        spans.indices
                ) {
                    if (
                        used[
                            index
                        ]
                    ) {
                        continue
                    }

                    val current =
                        spans[
                            index
                        ]

                    if (
                        current.start <=
                            item.lastSpan
                                .end &&
                        current.end >=
                            item.lastSpan
                                .start
                    ) {
                        matchIndex =
                            index

                        break
                    }
                }

                if (
                    matchIndex >=
                        0
                ) {
                    val current =
                        spans[
                            matchIndex
                        ]

                    item.column
                        .rows +=
                        current.withPull(
                            pullUnits
                        )

                    used[
                        matchIndex
                    ] =
                        true

                    nextActive +=
                        ActiveColumn(
                            lastSpan =
                                current,
                            column =
                                item.column
                        )
                } else {
                    finished +=
                        item.column
                }
            }

            spans.forEachIndexed {
                    index,
                    span ->
                if (
                    !used[
                        index
                    ]
                ) {
                    val column =
                        SatinColumn()

                    column.rows +=
                        span.withPull(
                            pullUnits
                        )

                    nextActive +=
                        ActiveColumn(
                            lastSpan =
                                span,
                            column =
                                column
                        )
                }
            }

            active =
                nextActive
        }

        active.forEach {
            finished +=
                it.column
        }

        return finished
            .filter {
                it.rows
                    .isNotEmpty()
            }
            .flatMap {
                    column ->
                splitWideColumn(
                    column =
                        column,
                    maxWidthUnits =
                        maxWidthUnits
                )
            }
            .sortedWith(
                compareBy<SatinColumn> {
                    column ->
                    column.rows
                        .first()
                        .a
                        .x
                }.thenBy {
                        column ->
                    column.rows
                        .first()
                        .a
                        .y
                }
            )
    }

    private fun splitWideColumn(
        column: SatinColumn,
        maxWidthUnits: Float
    ): List<SatinColumn> {
        val maxFound =
            column.rows
                .maxOfOrNull {
                    distance(
                        it.a,
                        it.b
                    )
                }
                ?: 0f

        if (
            maxFound <=
                maxWidthUnits ||
            maxWidthUnits <=
                0f
        ) {
            return listOf(
                column
            )
        }

        val pieces =
            ceil(
                maxFound /
                    maxWidthUnits
            )
                .toInt()
                .coerceAtLeast(
                    1
                )

        return (
            0 until
                pieces
            ).map {
                    piece ->
                val start =
                    piece.toFloat() /
                        pieces

                val end =
                    (
                        piece +
                            1
                        ).toFloat() /
                        pieces

                SatinColumn(
                    rows =
                        column.rows
                            .map {
                                    row ->
                                SatinRow(
                                    a =
                                        lerp(
                                            row.a,
                                            row.b,
                                            start
                                        ),
                                    b =
                                        lerp(
                                            row.a,
                                            row.b,
                                            end
                                        )
                                )
                            }
                            .toMutableList()
                )
            }
    }

    private class SatinEmitter(
        private val output:
            MutableList<EmbroideryPoint>
    ) {
        private var current:
            FPoint? =
            null

        fun emitGlyph(
            columns: List<SatinColumn>,
            polygons: List<Polygon>,
            startHint: FPoint?,
            underlayMode: SatinUnderlayMode,
            densityMm: Float
        ) {
            val remaining =
                columns
                    .filter {
                        it.rows
                            .isNotEmpty()
                    }
                    .toMutableList()

            if (
                remaining.isEmpty()
            ) {
                return
            }

            /*
             * No primeiro glifo do desenho, a posição inicial da agulha
             * precisa coincidir com o começo visual do traço. O underlay
             * continua sendo interno à coluna, mas não pode esconder esse
             * ponto inicial levando a agulha diretamente para o centro.
             */
            val startsDesignAtVisualHint =
                current ==
                    null &&
                startHint !=
                    null

            if (
                startsDesignAtVisualHint
            ) {
                travelTo(
                    startHint!!
                )
            }

            var firstColumn =
                true

            while (
                remaining.isNotEmpty()
            ) {
                /*
                 * A comparação quadro a quadro com o vídeo de referência
                 * confirma duas regras independentes:
                 *
                 * 1) a ORDEM dos blocos/colunas é espacial e estável;
                 * 2) somente o SENTIDO da coluna pode inverter para entrar
                 *    pelo extremo mais próximo da agulha atual.
                 *
                 * O erro anterior deixava a distância escolher qualquer
                 * coluna remanescente e ainda trocava A/B, alterando o
                 * percurso mesmo quando o desenho final parecia correto.
                 */
                val selectionAnchor =
                    current
                        ?: startHint

                val selected =
                    if (
                        selectionAnchor !=
                            null
                    ) {
                        nextColumnFromNearestEntry(
                            columns =
                                remaining,
                            anchor =
                                selectionAnchor,
                            underlayMode =
                                underlayMode
                        )
                    } else {
                        nextColumnInReadingOrder(
                            remaining
                        )
                    }
                        ?: break

                val includeUnderlay =
                    underlayMode !=
                        SatinUnderlayMode.NONE &&
                    selected.rows.size >=
                        4

                val anchor =
                    current
                        ?: startHint
                        ?: if (
                            includeUnderlay
                        ) {
                            center(
                                selected.rows
                                    .first()
                            )
                        } else {
                            selected.rows
                                .first()
                                .a
                        }

                val column =
                    orientColumnFromNearestEnd(
                        column =
                            selected,
                        anchor =
                            anchor,
                        includeUnderlay =
                            includeUnderlay
                    )

                val entry =
                    if (
                        includeUnderlay
                    ) {
                        center(
                            column.rows
                                .first()
                        )
                    } else {
                        column.rows
                            .first()
                            .a
                    }

                val currentPoint =
                    current

                if (
                    firstColumn &&
                    startsDesignAtVisualHint &&
                    currentPoint !=
                        null &&
                    distance(
                        currentPoint,
                        entry
                    ) >=
                        0.5f &&
                    segmentInsideGlyph(
                        from =
                            currentPoint,
                        to =
                            entry,
                        polygons =
                            polygons
                    )
                ) {
                    /*
                     * A referência começa no pé do traço e segue costurando
                     * para dentro da letra. Quando a ligação até o centro do
                     * underlay permanece inteiramente na área do glifo, ela
                     * é STITCH, não um JUMP artificial.
                     */
                    emitStitchTo(
                        entry
                    )
                } else {
                    travelTo(
                        target =
                            entry
                    )
                }

                emitReferenceColumn(
                    column =
                        column,
                    includeUnderlay =
                        includeUnderlay,
                    densityMm =
                        densityMm
                )

                remaining.remove(
                    selected
                )

                firstColumn =
                    false
            }
        }

        private fun nextColumnFromNearestEntry(
            columns: List<SatinColumn>,
            anchor: FPoint,
            underlayMode: SatinUnderlayMode
        ): SatinColumn? =
            columns.minByOrNull {
                    column ->
                val rows =
                    column.rows

                if (
                    rows.isEmpty()
                ) {
                    Float.MAX_VALUE
                } else {
                    val includeUnderlay =
                        underlayMode !=
                            SatinUnderlayMode.NONE &&
                        rows.size >=
                            4

                    fun entry(
                        row: SatinRow
                    ): FPoint =
                        if (
                            includeUnderlay
                        ) {
                            center(
                                row
                            )
                        } else {
                            row.a
                        }

                    minOf(
                        distance(
                            anchor,
                            entry(
                                rows.first()
                            )
                        ),
                        distance(
                            anchor,
                            entry(
                                rows.last()
                            )
                        )
                    )
                }
            }

        private fun orientColumnFromNearestEnd(
            column: SatinColumn,
            anchor: FPoint,
            includeUnderlay: Boolean
        ): SatinColumn {
            val normalRows =
                column.rows
                    .toList()

            val reversedRows =
                normalRows
                    .asReversed()

            fun entryOf(
                rows: List<SatinRow>
            ): FPoint =
                if (
                    includeUnderlay
                ) {
                    center(
                        rows.first()
                    )
                } else {
                    rows.first()
                        .a
                }

            val normalDistance =
                distance(
                    anchor,
                    entryOf(
                        normalRows
                    )
                )

            val reversedDistance =
                distance(
                    anchor,
                    entryOf(
                        reversedRows
                    )
                )

            /*
             * Ao inverter a coluna, invertemos apenas a ordem das linhas.
             * A e B permanecem A -> B dentro de cada linha Satin.
             */
            return SatinColumn(
                rows =
                    (
                        if (
                            reversedDistance <
                                normalDistance
                        ) {
                            reversedRows
                        } else {
                            normalRows
                        }
                        )
                        .toMutableList()
            )
        }

        private fun underlayIndices(
            rows: List<SatinRow>,
            densityMm: Float
        ): Set<Int> {
            if (
                rows.isEmpty()
            ) {
                return emptySet()
            }

            val pitchUnits =
                (
                    densityMm *
                        10f
                    ).coerceAtLeast(
                    0.5f
                )

            val step =
                max(
                    1,
                    (
                        20f /
                            pitchUnits
                        ).roundToInt()
                )

            val indices =
                mutableSetOf<Int>()

            var index =
                0

            while (
                index <
                    rows.size
            ) {
                indices +=
                    index

                index +=
                    step
            }

            indices +=
                rows.lastIndex

            return indices
        }

        private fun emitReferenceColumn(
            column: SatinColumn,
            includeUnderlay: Boolean,
            densityMm: Float
        ) {
            val rows =
                column.rows

            if (
                rows.isEmpty()
            ) {
                return
            }

            /*
             * Vídeo de referência oficial:
             *
             * 1) percorre o centro da coluna uma única vez até o extremo;
             * 2) sem retornar pelo centro, trava nesse extremo;
             * 3) o Satin começa dali e percorre as linhas no sentido inverso,
             *    preenchendo de volta até a entrada da coluna.
             */
            val coverageRows =
                if (
                    includeUnderlay
                ) {
                    emitCenterRunUnderlay(
                        column =
                            column,
                        densityMm =
                            densityMm
                    )

                    rows.asReversed()
                } else {
                    rows
                }

            emitLock(
                coverageRows.first()
            )

            coverageRows.forEach {
                    row ->
                emitStitchTo(
                    row.a
                )

                emitStitchTo(
                    row.b
                )
            }

            emitLock(
                coverageRows.last()
            )
        }

        private fun emitCenterRunUnderlay(
            column: SatinColumn,
            densityMm: Float
        ) {
            val rows =
                column.rows

            if (
                rows.isEmpty()
            ) {
                return
            }

            val pitchUnits =
                densityMm *
                    10f

            val step =
                max(
                    1,
                    (
                        20f /
                            pitchUnits
                                .coerceAtLeast(
                                    0.5f
                                )
                        ).roundToInt()
                )

            val centers =
                mutableListOf<
                    FPoint
                >()

            var index =
                0

            while (
                index <
                    rows.size
            ) {
                centers +=
                    center(
                        rows[index]
                    )

                index +=
                    step
            }

            val lastCenter =
                center(
                    rows.last()
                )

            if (
                centers.lastOrNull() !=
                    lastCenter
            ) {
                centers +=
                    lastCenter
            }

            // Uma única passada central até o extremo.
            centers.forEach {
                    point ->
                emitStitchTo(
                    point
                )
            }
        }

        private fun emitLock(
            row: SatinRow
        ) {
            val dx =
                row.b.x -
                    row.a.x

            val dy =
                row.b.y -
                    row.a.y

            val length =
                hypot(
                    dx.toDouble(),
                    dy.toDouble()
                )
                    .toFloat()

            val ux =
                if (
                    length >
                        0.001f
                ) {
                    dx /
                        length
                } else {
                    1f
                }

            val uy =
                if (
                    length >
                        0.001f
                ) {
                    dy /
                        length
                } else {
                    0f
                }

            emitStitchTo(
                row.a
            )

            emitStitchTo(
                FPoint(
                    x =
                        row.a.x +
                            ux *
                                6f,
                    y =
                        row.a.y +
                            uy *
                                6f
                )
            )

            emitStitchTo(
                row.a
            )
        }

        private fun travelTo(
            target: FPoint
        ) {
            val before =
                current

            if (
                before ==
                    null
            ) {
                emit(
                    target,
                    StitchCommand.JUMP
                )

                return
            }

            val travelDistance =
                distance(
                    before,
                    target
                )

            // 0,05 mm: não emite deslocamento.
            if (
                travelDistance <
                    0.5f
            ) {
                current =
                    target

                return
            }

            // Acima de 5 mm a referência corta antes de viajar.
            if (
                travelDistance >
                    TRIM_DISTANCE_UNITS
            ) {
                emit(
                    before,
                    StitchCommand.TRIM
                )
            }

            emitSegmented(
                from =
                    before,
                to =
                    target,
                command =
                    StitchCommand.JUMP,
                maxSegmentUnits =
                    MAX_STITCH_UNITS
            )
        }

        private fun segmentInsideGlyph(
            from: FPoint,
            to: FPoint,
            polygons: List<Polygon>
        ): Boolean {
            if (
                polygons.isEmpty()
            ) {
                return false
            }

            val total =
                distance(
                    from,
                    to
                )

            val samples =
                max(
                    2,
                    ceil(
                        total /
                            CONNECTOR_SAMPLE_UNITS
                    ).toInt()
                )

            for (
                part in
                    1 until
                        samples
            ) {
                val ratio =
                    part.toFloat() /
                        samples

                val point =
                    lerp(
                        from,
                        to,
                        ratio
                    )

                if (
                    !pointInsideOrNearGlyph(
                        point =
                            point,
                        polygons =
                            polygons
                    )
                ) {
                    return false
                }
            }

            return true
        }

        private fun pointInsideOrNearGlyph(
            point: FPoint,
            polygons: List<Polygon>
        ): Boolean {
            var inside =
                false

            polygons.forEach {
                    polygon ->
                if (
                    pointInsidePolygon(
                        point =
                            point,
                        polygon =
                            polygon
                    )
                ) {
                    inside =
                        !inside
                }
            }

            if (
                inside
            ) {
                return true
            }

            return polygons.any {
                    polygon ->
                pointNearPolygonEdge(
                    point =
                        point,
                    polygon =
                        polygon,
                    margin =
                        CONNECTOR_EDGE_MARGIN_UNITS
                )
            }
        }

        private fun pointInsidePolygon(
            point: FPoint,
            polygon: Polygon
        ): Boolean {
            val points =
                polygon.points

            if (
                points.size <
                    3
            ) {
                return false
            }

            var inside =
                false

            var previous =
                points.last()

            points.forEach {
                    currentPoint ->
                val crosses =
                    (
                        currentPoint.y >
                            point.y
                        ) !=
                        (
                            previous.y >
                                point.y
                            )

                if (
                    crosses
                ) {
                    val denominator =
                        previous.y -
                            currentPoint.y

                    if (
                        abs(
                            denominator
                        ) >
                            0.00001f
                    ) {
                        val crossingX =
                            (
                                previous.x -
                                    currentPoint.x
                                ) *
                                (
                                    point.y -
                                        currentPoint.y
                                    ) /
                                denominator +
                                currentPoint.x

                        if (
                            point.x <
                                crossingX
                        ) {
                            inside =
                                !inside
                        }
                    }
                }

                previous =
                    currentPoint
            }

            return inside
        }

        private fun pointNearPolygonEdge(
            point: FPoint,
            polygon: Polygon,
            margin: Float
        ): Boolean {
            val points =
                polygon.points

            if (
                points.size <
                    2
            ) {
                return false
            }

            for (
                index in
                    points.indices
            ) {
                val a =
                    points[index]

                val b =
                    points[
                        (
                            index +
                                1
                            ) %
                            points.size
                    ]

                if (
                    pointToSegmentDistance(
                        point =
                            point,
                        a =
                            a,
                        b =
                            b
                    ) <=
                    margin
                ) {
                    return true
                }
            }

            return false
        }

        private fun pointToSegmentDistance(
            point: FPoint,
            a: FPoint,
            b: FPoint
        ): Float {
            val dx =
                b.x -
                    a.x

            val dy =
                b.y -
                    a.y

            val lengthSquared =
                dx *
                    dx +
                    dy *
                        dy

            if (
                lengthSquared <=
                    0.00001f
            ) {
                return distance(
                    point,
                    a
                )
            }

            val projection =
                (
                    (
                        point.x -
                            a.x
                        ) *
                        dx +
                        (
                            point.y -
                                a.y
                            ) *
                            dy
                    ) /
                    lengthSquared

            val ratio =
                projection.coerceIn(
                    0f,
                    1f
                )

            val closest =
                FPoint(
                    x =
                        a.x +
                            dx *
                                ratio,
                    y =
                        a.y +
                            dy *
                                ratio
                )

            return distance(
                point,
                closest
            )
        }

        private fun emitStitchTo(
            target: FPoint
        ) {
            val before =
                current

            if (
                before ==
                    null
            ) {
                emit(
                    target,
                    StitchCommand.JUMP
                )

                return
            }

            /*
             * Não descartamos deslocamento zero aqui. O motor analisado
             * sempre emite pelo menos um STITCH, inclusive quando a trava
             * ou o primeiro A coincide com a posição atual.
             */
            emitSegmented(
                from =
                    before,
                to =
                    target,
                command =
                    StitchCommand.STITCH,
                maxSegmentUnits =
                    70f
            )
        }

        private fun emitSegmented(
            from: FPoint,
            to: FPoint,
            command: StitchCommand,
            maxSegmentUnits: Float
        ) {
            val total =
                distance(
                    from,
                    to
                )

            val segments =
                max(
                    1,
                    ceil(
                        total /
                            maxSegmentUnits
                                .coerceAtLeast(
                                    1f
                                )
                    ).toInt()
                )

            for (
                part in
                    1..segments
            ) {
                val ratio =
                    part.toFloat() /
                        segments

                emit(
                    lerp(
                        from,
                        to,
                        ratio
                    ),
                    command
                )
            }
        }

        private fun emit(
            point: FPoint,
            command: StitchCommand
        ) {
            require(
                point.x.isFinite() &&
                    point.y.isFinite()
            ) {
                "A fonte gerou coordenadas inválidas."
            }

            require(
                output.size <
                    EmbroideryStressPolicy
                        .MAX_GENERATED_COMMANDS
            ) {
                "A fonte gerou pontos demais para processar com segurança no celular."
            }

            output +=
                EmbroideryPoint(
                    xUnits =
                        point.x
                            .roundToInt(),
                    yUnits =
                        point.y
                            .roundToInt(),
                    command =
                        command,
                    colorIndex =
                        0
                )

            current =
                point
        }
    }

    internal fun debugProgressiveCenterUnderlayPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val column =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            0f,
                            0f
                        ),
                        FPoint(
                            2f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            10f,
                            0f
                        ),
                        FPoint(
                            12f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            20f,
                            0f
                        ),
                        FPoint(
                            22f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            30f,
                            0f
                        ),
                        FPoint(
                            32f,
                            0f
                        )
                    )
                )
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    column
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                -2f,
                                -2f
                            ),
                            FPoint(
                                34f,
                                -2f
                            ),
                            FPoint(
                                34f,
                                2f
                            ),
                            FPoint(
                                -2f,
                                2f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    0f,
                    0f
                ),
            underlayMode =
                SatinUnderlayMode.CENTER,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugVisualStartPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val left =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            5f,
                            0f
                        ),
                        FPoint(
                            20f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            10f
                        ),
                        FPoint(
                            20f,
                            10f
                        )
                    )
                )
            )

        val right =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            70f,
                            0f
                        ),
                        FPoint(
                            85f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            70f,
                            10f
                        ),
                        FPoint(
                            85f,
                            10f
                        )
                    )
                )
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    right,
                    left
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                -5f
                            ),
                            FPoint(
                                90f,
                                -5f
                            ),
                            FPoint(
                                90f,
                                15f
                            ),
                            FPoint(
                                0f,
                                15f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    90f,
                    10f
                ),
            underlayMode =
                SatinUnderlayMode.NONE,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugGlyphVisualStartPoint(
        points:
            List<Pair<Float, Float>>
    ): Pair<Float, Float>? =
        glyphVisualStartPoint(
            listOf(
                Polygon(
                    points.map {
                            point ->
                        FPoint(
                            point.first,
                            point.second
                        )
                    }
                )
            )
        )?.let {
                point ->
            point.x to
                point.y
        }

    internal fun debugReferencePath(
        connected: Boolean,
        includeUnderlay: Boolean =
            true
    ): List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val first =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            5f,
                            5f
                        ),
                        FPoint(
                            20f,
                            5f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            13f
                        ),
                        FPoint(
                            20f,
                            13f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            21f
                        ),
                        FPoint(
                            20f,
                            21f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            5f,
                            29f
                        ),
                        FPoint(
                            20f,
                            29f
                        )
                    )
                )
            )

        val second =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            34f,
                            5f
                        ),
                        FPoint(
                            49f,
                            5f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            34f,
                            13f
                        ),
                        FPoint(
                            49f,
                            13f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            34f,
                            21f
                        ),
                        FPoint(
                            49f,
                            21f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            34f,
                            29f
                        ),
                        FPoint(
                            49f,
                            29f
                        )
                    )
                )
            )

        val polygons =
            if (
                connected
            ) {
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                54f,
                                0f
                            ),
                            FPoint(
                                54f,
                                34f
                            ),
                            FPoint(
                                0f,
                                34f
                            )
                        )
                    )
                )
            } else {
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                24f,
                                0f
                            ),
                            FPoint(
                                24f,
                                34f
                            ),
                            FPoint(
                                0f,
                                34f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                30f,
                                0f
                            ),
                            FPoint(
                                54f,
                                0f
                            ),
                            FPoint(
                                54f,
                                34f
                            ),
                            FPoint(
                                30f,
                                34f
                            )
                        )
                    )
                )
            }

        emitter.emitGlyph(
            columns =
                listOf(
                    first,
                    second
                ),
            polygons =
                polygons,
            startHint =
                FPoint(
                    5f,
                    5f
                ),
            underlayMode =
                if (
                    includeUnderlay
                ) {
                    SatinUnderlayMode.CENTER
                } else {
                    SatinUnderlayMode.NONE
                },
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugConnectedPriorityPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        fun column(
            left: Float
        ) =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            left,
                            5f
                        ),
                        FPoint(
                            left +
                                10f,
                            5f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            left,
                            15f
                        ),
                        FPoint(
                            left +
                                10f,
                            15f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            left,
                            25f
                        ),
                        FPoint(
                            left +
                                10f,
                            25f
                        )
                    )
                )
            )

        val first =
            column(
                0f
            )

        val far =
            column(
                80f
            )

        val connected =
            column(
                20f
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    first,
                    far,
                    connected
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                -2f,
                                0f
                            ),
                            FPoint(
                                35f,
                                0f
                            ),
                            FPoint(
                                35f,
                                30f
                            ),
                            FPoint(
                                -2f,
                                30f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                78f,
                                0f
                            ),
                            FPoint(
                                95f,
                                0f
                            ),
                            FPoint(
                                95f,
                                30f
                            ),
                            FPoint(
                                78f,
                                30f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    0f,
                    5f
                ),
            underlayMode =
                SatinUnderlayMode.NONE,
            densityMm =
                0.4f
        )

        return output
    }

    private fun buildGuidePoints(
        polygons: List<Polygon>
    ): List<EmbroideryPoint> {
        val result =
            mutableListOf<
                EmbroideryPoint
            >()

        polygons.forEach {
                polygon ->
            require(
                result.size <=
                    EmbroideryStressPolicy
                        .MAX_GUIDE_COMMANDS
            ) {
                "O contorno da fonte ficou complexo demais para processar com segurança no celular."
            }
            val first =
                polygon.points
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
                    first.x
                        .roundToInt(),
                    first.y
                        .roundToInt(),
                    StitchCommand.JUMP,
                    0
                )

            polygon.points
                .drop(1)
                .forEach {
                        point ->
                    result +=
                        EmbroideryPoint(
                            point.x
                                .roundToInt(),
                            point.y
                                .roundToInt(),
                            StitchCommand.STITCH,
                            0
                        )
                }

            result +=
                EmbroideryPoint(
                    first.x
                        .roundToInt(),
                    first.y
                        .roundToInt(),
                    StitchCommand.STITCH,
                    0
                )
        }

        return result
    }

    private fun center(
        row: SatinRow
    ): FPoint =
        FPoint(
            x =
                (
                    row.a.x +
                        row.b.x
                    ) /
                    2f,
            y =
                (
                    row.a.y +
                        row.b.y
                    ) /
                    2f
        )

    private fun normalize(
        point: FPoint
    ): FPoint {
        val length =
            hypot(
                point.x.toDouble(),
                point.y.toDouble()
            )
                .toFloat()

        return if (
            length <
                0.0001f
        ) {
            FPoint(
                0f,
                0f
            )
        } else {
            FPoint(
                point.x /
                    length,
                point.y /
                    length
            )
        }
    }

    private fun distance(
        first: FPoint,
        second: FPoint
    ): Float =
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
            .toFloat()

    private fun lerp(
        first: FPoint,
        second: FPoint,
        ratio: Float
    ): FPoint =
        FPoint(
            x =
                first.x +
                    (
                        second.x -
                            first.x
                        ) *
                        ratio,
            y =
                first.y +
                    (
                        second.y -
                            first.y
                        ) *
                        ratio
        )

    internal fun debugSerpentineTransitionJumpTargets():
        List<Pair<Int, Int>> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val first =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            0f,
                            0f
                        ),
                        FPoint(
                            10f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            0f,
                            10f
                        ),
                        FPoint(
                            10f,
                            10f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            0f,
                            20f
                        ),
                        FPoint(
                            10f,
                            20f
                        )
                    )
                )
            )

        val second =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            30f,
                            0f
                        ),
                        FPoint(
                            40f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            30f,
                            10f
                        ),
                        FPoint(
                            40f,
                            10f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            30f,
                            20f
                        ),
                        FPoint(
                            40f,
                            20f
                        )
                    )
                )
            )

        emitter.emitGlyph(
            columns =
                listOf(
                    first,
                    second
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                -2f,
                                -2f
                            ),
                            FPoint(
                                42f,
                                -2f
                            ),
                            FPoint(
                                42f,
                                22f
                            ),
                            FPoint(
                                -2f,
                                22f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    0f,
                    0f
                ),
            underlayMode =
                SatinUnderlayMode.NONE,
            densityMm =
                0.4f
        )

        return output
            .filter {
                it.command ==
                    StitchCommand.JUMP
            }
            .map {
                it.xUnits to
                    it.yUnits
            }
    }

    internal fun debugReadingOrderColumnLeftEdges():
        List<Float> {
        val left =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            0f,
                            0f
                        ),
                        FPoint(
                            90f,
                            0f
                        )
                    )
                )
            )

        val middle =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            40f,
                            0f
                        ),
                        FPoint(
                            50f,
                            0f
                        )
                    )
                )
            )

        val right =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            70f,
                            0f
                        ),
                        FPoint(
                            80f,
                            0f
                        )
                    )
                )
            )

        val remaining =
            mutableListOf(
                right,
                left,
                middle
            )

        val visited =
            mutableListOf<Float>()

        while (
            remaining.isNotEmpty()
        ) {
            val next =
                nextColumnInReadingOrder(
                    remaining
                )!!

            visited +=
                columnLeftEdgeX(
                    next
                )

            remaining.remove(
                next
            )
        }

        return visited
    }

    internal fun debugColumnWidths(
        polygon:
            List<Pair<Float, Float>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): List<Float> =
        sampleColumns(
            polygons =
                listOf(
                    Polygon(
                        polygon.map {
                            FPoint(
                                it.first,
                                it.second
                            )
                        }
                    )
                ),
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxWidthMm,
            pullCompensationMm =
                0f
        )
            .flatMap {
                it.rows
            }
            .map {
                distance(
                    it.a,
                    it.b
                )
            }

    internal fun debugColumnCount(
        polygons:
            List<List<Pair<Float, Float>>>,
        densityMm: Float =
            0.4f,
        maxWidthMm: Float =
            7f
    ): Int =
        sampleColumns(
            polygons =
                polygons.map {
                    points ->
                    Polygon(
                        points.map {
                            FPoint(
                                it.first,
                                it.second
                            )
                        }
                    )
                },
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxWidthMm,
            pullCompensationMm =
                0f
        )
            .size

    private fun safeName(
        value: String
    ): String =
        Normalizer
            .normalize(
                value,
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
            .trim(
                '_'
            )
            .take(
                24
            )
            .ifBlank {
                "texto"
            }
}
