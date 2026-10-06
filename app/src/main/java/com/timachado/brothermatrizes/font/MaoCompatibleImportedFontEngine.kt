package com.timachado.brothermatrizes.font

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
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reimplementação clean-room do pipeline TTF/OTF Satin observado no
 * MãoDesign 18.1.2.
 *
 * Deliberadamente não contém as heurísticas históricas do Brother:
 * - sem skeleton/DFS;
 * - sem seleção pela coluna mais próxima;
 * - sem reversão automática A/B;
 * - sem conectores artificiais entre colunas;
 * - sem ponto inicial especial por floreio.
 *
 * Fluxo único:
 * Skia glyphs -> scan spans X/Y -> menor largura média -> BuildColumns ->
 * SplitWideColumn -> ordem Rows[0].A(X,Y) -> travel -> underlay -> lock ->
 * A/B por row -> lock.
 */
internal object MaoCompatibleImportedFontEngine {

    private const val MAX_SATIN_WIDTH_UNITS =
        70f

    private const val MAX_COMMAND_SEGMENT_UNITS =
        70f

    private const val TRIM_DISTANCE_UNITS =
        50f

    private const val LETTER_SPACING_FACTOR =
        0.04f

    private const val MAX_SCAN_LINES =
        20_000

    private data class P(
        val x: Float,
        val y: Float
    )

    private data class Polygon(
        val points: List<P>
    )

    private data class Span(
        val position: Float,
        val start: Float,
        val end: Float,
        val transposed: Boolean
    ) {
        fun toRow(
            pull: Float
        ): Row =
            if (
                transposed
            ) {
                Row(
                    a =
                        P(
                            position,
                            start -
                                pull
                        ),
                    b =
                        P(
                            position,
                            end +
                                pull
                        )
                )
            } else {
                Row(
                    a =
                        P(
                            start -
                                pull,
                            position
                        ),
                    b =
                        P(
                            end +
                                pull,
                            position
                        )
                )
            }
    }

    private data class Row(
        val a: P,
        val b: P
    )

    private data class Column(
        val rows:
            MutableList<Row> =
            mutableListOf()
    )

    private data class Active(
        val lastSpan: Span,
        val column: Column
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
                    .take(
                        24
                    )

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

            val heightUnits =
                options.heightMm *
                    10f

            val spacingUnits =
                heightUnits *
                    LETTER_SPACING_FACTOR +
                    options.spacingMm *
                        10f

            val outline =
                MaoSkiaOutlineExtractor
                    .extract(
                        font =
                            font,
                        text =
                            text,
                        targetCapHeightUnits =
                            heightUnits,
                        spacingUnits =
                            spacingUnits,
                        rotationDegrees =
                            options.rotationDegrees
                    )

            val glyphs =
                outline.glyphPolygons
                    .map {
                            glyph ->
                        glyph.map {
                            contour ->
                            Polygon(
                                contour.map {
                                        point ->
                                    P(
                                        point.x,
                                        point.y
                                    )
                                }
                            )
                        }
                    }
                    .filter {
                        it.isNotEmpty()
                    }

            require(
                glyphs.isNotEmpty()
            ) {
                "A fonte não gerou contornos bordáveis."
            }

            val densityMm =
                options.satinDensityMm
                    .coerceIn(
                        0.06f,
                        2f
                    )

            val pullUnits =
                options.satinPullCompensationMm
                    .coerceIn(
                        0f,
                        1f
                    ) *
                    10f

            val output =
                mutableListOf<
                    EmbroideryPoint
                >()

            val emitter =
                Emitter(
                    output
                )

            glyphs.forEach {
                    polygons ->
                val columns =
                    sampleColumns(
                        polygons =
                            polygons,
                        densityMm =
                            densityMm,
                        pullUnits =
                            pullUnits
                    )

                columns.forEach {
                        column ->
                    emitter.emitColumn(
                        column =
                            column,
                        includeUnderlay =
                            options
                                .satinUnderlayMode !=
                                SatinUnderlayMode.NONE,
                        densityMm =
                            densityMm
                    )
                }
            }

            require(
                output.any {
                    it.command ==
                        StitchCommand.STITCH
                }
            ) {
                "A fonte não gerou pontadas."
            }

            val last =
                output.last()

            output +=
                EmbroideryPoint(
                    xUnits =
                        last.xUnits,
                    yUnits =
                        last.yUnits,
                    command =
                        StitchCommand.END,
                    colorIndex =
                        0
                )

            val visible =
                output.filter {
                    it.command !=
                        StitchCommand.END
                }

            val bounds =
                EmbroideryBounds(
                    minXUnits =
                        visible.minOf {
                            it.xUnits
                        },
                    maxXUnits =
                        visible.maxOf {
                            it.xUnits
                        },
                    minYUnits =
                        visible.minOf {
                            it.yUnits
                        },
                    maxYUnits =
                        visible.maxOf {
                            it.yUnits
                        }
                )

            val guide =
                buildGuide(
                    glyphs
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
                        output,
                    bounds =
                        bounds,
                    stitchCount =
                        output.count {
                            it.command ==
                                StitchCommand.STITCH
                        },
                    jumpCount =
                        output.count {
                            it.command ==
                                StitchCommand.JUMP
                        },
                    colorChanges =
                        0,
                    endFound =
                        true,
                    sourceBytes =
                        ByteArray(
                            0
                        ),
                    guidePoints =
                        guide,
                    threadColors =
                        listOf(
                            options.color
                        ),
                    sourceYAxisDown =
                        true,
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
                            design =
                                design,
                            hoop =
                                options.hoopProfile
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

    private fun sampleColumns(
        polygons: List<Polygon>,
        densityMm: Float,
        pullUnits: Float
    ): List<Column> {
        val pitch =
            (
                densityMm *
                    10f
                ).coerceAtLeast(
                0.5f
            )

        val normal =
            scanSpans(
                polygons =
                    polygons,
                pitch =
                    pitch,
                transposed =
                    false
            )

        val transposed =
            scanSpans(
                polygons =
                    polygons,
                pitch =
                    pitch,
                transposed =
                    true
            )

        val selected =
            if (
                meanWidth(
                    transposed
                ) <
                meanWidth(
                    normal
                )
            ) {
                transposed
            } else {
                normal
            }

        return buildColumns(
            scanLines =
                selected,
            maxWidth =
                MAX_SATIN_WIDTH_UNITS,
            pull =
                pullUnits
        )
    }

    private fun scanSpans(
        polygons: List<Polygon>,
        pitch: Float,
        transposed: Boolean
    ): List<List<Span>> {
        val all =
            polygons.flatMap {
                it.points
            }

        if (
            all.isEmpty()
        ) {
            return emptyList()
        }

        fun scan(
            p: P
        ): Float =
            if (
                transposed
            ) {
                p.x
            } else {
                p.y
            }

        fun cross(
            p: P
        ): Float =
            if (
                transposed
            ) {
                p.y
            } else {
                p.x
            }

        val minimum =
            all.minOf(
                ::scan
            )

        val maximum =
            all.maxOf(
                ::scan
            )

        val lineCount =
            ceil(
                (
                    maximum -
                        minimum
                    ) /
                    pitch
            )
                .toInt()

        require(
            lineCount in
                0..MAX_SCAN_LINES
        ) {
            "A geometria da fonte exige varredura complexa demais."
        }

        val result =
            mutableListOf<
                List<Span>
            >()

        var position =
            minimum +
                pitch /
                    2f

        while (
            position <=
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
                    val a =
                        points[
                            index
                        ]

                    val b =
                        points[
                            (
                                index +
                                    1
                                ) %
                                points.size
                        ]

                    val aScan =
                        scan(
                            a
                        )

                    val bScan =
                        scan(
                            b
                        )

                    val crosses =
                        (
                            aScan <=
                                position &&
                            bScan >
                                position
                            ) ||
                            (
                                bScan <=
                                    position &&
                                aScan >
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
                                aScan
                            ) /
                            (
                                bScan -
                                    aScan
                                )

                    intersections +=
                        cross(
                            a
                        ) +
                            (
                                cross(
                                    b
                                ) -
                                    cross(
                                        a
                                    )
                                ) *
                                ratio
                }
            }

            intersections.sort()

            val spans =
                mutableListOf<
                    Span
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
                        Span(
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

            result +=
                spans

            position +=
                pitch
        }

        return result
    }

    private fun meanWidth(
        lines: List<List<Span>>
    ): Double {
        var count =
            0

        var total =
            0.0

        lines.forEach {
                line ->
            line.forEach {
                    span ->
                count++
                total +=
                    (
                        span.end -
                            span.start
                        )
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
        scanLines: List<List<Span>>,
        maxWidth: Float,
        pull: Float
    ): List<Column> {
        val finished =
            mutableListOf<
                Column
            >()

        var active =
            mutableListOf<
                Active
            >()

        scanLines.forEach {
                spans ->
            val used =
                BooleanArray(
                    spans.size
                )

            val next =
                mutableListOf<
                    Active
                >()

            active.forEach {
                    item ->
                var match =
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

                    val span =
                        spans[
                            index
                        ]

                    if (
                        span.start <=
                            item.lastSpan.end &&
                        span.end >=
                            item.lastSpan.start
                    ) {
                        match =
                            index

                        break
                    }
                }

                if (
                    match >=
                        0
                ) {
                    val span =
                        spans[
                            match
                        ]

                    item.column.rows +=
                        span.toRow(
                            pull
                        )

                    used[
                        match
                    ] =
                        true

                    next +=
                        Active(
                            lastSpan =
                                span,
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
                        Column()

                    column.rows +=
                        span.toRow(
                            pull
                        )

                    next +=
                        Active(
                            lastSpan =
                                span,
                            column =
                                column
                        )
                }
            }

            active =
                next
        }

        active.forEach {
                item ->
            finished +=
                item.column
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
                    maxWidth =
                        maxWidth
                )
            }
            .sortedWith(
                compareBy<Column> {
                    it.rows
                        .first()
                        .a
                        .x
                }.thenBy {
                    it.rows
                        .first()
                        .a
                        .y
                }
            )
    }

    private fun splitWideColumn(
        column: Column,
        maxWidth: Float
    ): List<Column> {
        val widest =
            column.rows
                .maxOfOrNull {
                    distance(
                        it.a,
                        it.b
                    )
                }
                ?: 0f

        if (
            widest <=
                maxWidth ||
            maxWidth <=
                0f
        ) {
            return listOf(
                column
            )
        }

        val pieces =
            ceil(
                widest /
                    maxWidth
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

                Column(
                    rows =
                        column.rows
                            .map {
                                    row ->
                                Row(
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

    private class Emitter(
        private val output:
            MutableList<EmbroideryPoint>
    ) {
        private var current:
            P? =
            null

        fun emitColumn(
            column: Column,
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

            travelTo(
                rows.first()
                    .a
            )

            if (
                includeUnderlay &&
                rows.size >=
                    4
            ) {
                emitCenterRun(
                    rows =
                        rows,
                    densityMm =
                        densityMm
                )
            }

            emitLock(
                rows.first()
            )

            rows.forEach {
                    row ->
                stitchTo(
                    row.a
                )

                stitchTo(
                    row.b
                )
            }

            emitLock(
                rows.last()
            )
        }

        private fun emitCenterRun(
            rows: List<Row>,
            densityMm: Float
        ) {
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
                    P
                >()

            var index =
                0

            while (
                index <
                    rows.size
            ) {
                centers +=
                    center(
                        rows[
                            index
                        ]
                    )

                index +=
                    step
            }

            /*
             * A implementação observada sempre adiciona o centro da última
             * row, inclusive se ele já coincide com a amostragem anterior.
             */
            centers +=
                center(
                    rows.last()
                )

            centers.forEach(
                ::stitchTo
            )

            for (
                reverse in
                    centers.lastIndex -
                        1 downTo
                        0
            ) {
                stitchTo(
                    centers[
                        reverse
                    ]
                )
            }
        }

        private fun emitLock(
            row: Row
        ) {
            val anchor =
                row.a

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

            stitchTo(
                anchor
            )

            stitchTo(
                P(
                    x =
                        anchor.x +
                            ux *
                                6f,
                    y =
                        anchor.y +
                            uy *
                                6f
                )
            )

            stitchTo(
                anchor
            )
        }

        private fun travelTo(
            target: P
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

            val distance =
                distance(
                    before,
                    target
                )

            if (
                distance <
                    0.5f
            ) {
                current =
                    target
                return
            }

            if (
                distance >
                    TRIM_DISTANCE_UNITS
            ) {
                emit(
                    before,
                    StitchCommand.TRIM
                )
            }

            segmented(
                from =
                    before,
                to =
                    target,
                command =
                    StitchCommand.JUMP
            )
        }

        private fun stitchTo(
            target: P
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

            segmented(
                from =
                    before,
                to =
                    target,
                command =
                    StitchCommand.STITCH
            )
        }

        private fun segmented(
            from: P,
            to: P,
            command: StitchCommand
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
                            MAX_COMMAND_SEGMENT_UNITS
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
            p: P,
            command: StitchCommand
        ) {
            output +=
                EmbroideryPoint(
                    xUnits =
                        p.x
                            .roundToInt(),
                    yUnits =
                        p.y
                            .roundToInt(),
                    command =
                        command,
                    colorIndex =
                        0
                )

            current =
                p
        }
    }

    private fun buildGuide(
        glyphs:
            List<List<Polygon>>
    ): List<EmbroideryPoint> {
        val result =
            mutableListOf<
                EmbroideryPoint
            >()

        glyphs
            .flatten()
            .forEach {
                    polygon ->
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
                            xUnits =
                                last.xUnits,
                            yUnits =
                                last.yUnits,
                            command =
                                StitchCommand.TRIM,
                            colorIndex =
                                0
                        )
                }

                result +=
                    EmbroideryPoint(
                        xUnits =
                            first.x
                                .roundToInt(),
                        yUnits =
                            first.y
                                .roundToInt(),
                        command =
                            StitchCommand.JUMP,
                        colorIndex =
                            0
                    )

                polygon.points
                    .drop(
                        1
                    )
                    .forEach {
                            point ->
                        result +=
                            EmbroideryPoint(
                                xUnits =
                                    point.x
                                        .roundToInt(),
                                yUnits =
                                    point.y
                                        .roundToInt(),
                                command =
                                    StitchCommand.STITCH,
                                colorIndex =
                                    0
                            )
                    }

                result +=
                    EmbroideryPoint(
                        xUnits =
                            first.x
                                .roundToInt(),
                        yUnits =
                            first.y
                                .roundToInt(),
                        command =
                            StitchCommand.STITCH,
                        colorIndex =
                            0
                    )
            }

        return result
    }

    private fun center(
        row: Row
    ): P =
        P(
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

    private fun lerp(
        a: P,
        b: P,
        ratio: Float
    ): P =
        P(
            x =
                a.x +
                    (
                        b.x -
                            a.x
                        ) *
                        ratio,
            y =
                a.y +
                    (
                        b.y -
                            a.y
                        ) *
                        ratio
        )

    private fun distance(
        a: P,
        b: P
    ): Float =
        hypot(
            (
                b.x -
                    a.x
                ).toDouble(),
            (
                b.y -
                    a.y
                ).toDouble()
        )
            .toFloat()

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
