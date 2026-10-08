package com.timachado.brothermatrizes.font

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import com.timachado.brothermatrizes.core.embroidery.EmbroideryBounds
import com.timachado.brothermatrizes.core.embroidery.EmbroideryDesign
import com.timachado.brothermatrizes.core.embroidery.EmbroideryPoint
import com.timachado.brothermatrizes.core.embroidery.EmbroideryStressPolicy
import com.timachado.brothermatrizes.core.embroidery.GuidedSatinRoute
import com.timachado.brothermatrizes.core.embroidery.GuidedSatinStep
import com.timachado.brothermatrizes.core.embroidery.GuidedSatinPoint
import com.timachado.brothermatrizes.core.embroidery.HoopValidator
import com.timachado.brothermatrizes.core.embroidery.MatrixConverter
import com.timachado.brothermatrizes.core.embroidery.SatinUnderlayMode
import com.timachado.brothermatrizes.core.embroidery.StitchCommand
import com.timachado.brothermatrizes.core.embroidery.TextMatrixOptions
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor clean-room de texto TTF/OTF inspirado no comportamento observável
 * do PE-DESIGN 11, sem reutilizar código proprietário:
 *
 * contorno vetorial -> medial flow/topologia -> colunas Satin orientadas
 * pela normal local -> underlay -> cobertura do mesmo objeto -> conexão
 * segura -> próximo objeto.
 *
 * A geometria do contorno continua sendo a autoridade. A simulação e a
 * exportação consomem a mesma sequência de EmbroideryPoint.
 */
internal object PeDesignImportedFontEngine {

    private val capHeightRatioCache =
        mutableMapOf<String, Float?>()

    private const val DEFAULT_SATIN_DENSITY_MM =
        0.4f

    private const val DEFAULT_MAX_SATIN_WIDTH_MM =
        7f

    private const val DEFAULT_PULL_MM =
        0.2f

    private const val LETTER_SPACING_FACTOR =
        0.04f

    /*
     * A referência real (Great Vibes / "Maria") inicia a primeira coluna
     * Satin já dentro do center-run, e não no extremo inferior da coluna.
     * 25% da passada central corresponde ao ponto visual marcado pelo usuário
     * e evita começar no floreio de entrada sem remover sua cobertura Satin.
     */
    private const val FIRST_COLUMN_UNDERLAY_ENTRY_FRACTION =
        0.25f

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

            val targetHeightUnits =
                options.heightMm *
                    10f

            val spacingUnits =
                targetHeightUnits *
                    LETTER_SPACING_FACTOR +
                    options.spacingMm *
                        10f

            val useNativeOutlineBackend =
                Build.SUPPORTED_ABIS
                    .any {
                            abi ->
                        abi ==
                            "arm64-v8a" ||
                            abi ==
                                "x86_64"
                    }

            val polygonsByGlyph =
                if (
                    useNativeOutlineBackend
                ) {
                    /*
                     * Adamya/Ademya usa o mesmo backend vetorial do
                     * PE-DESIGN: Skia Font -> glyph width -> glyph path ->
                     * TightBounds -> transformação global -> PathMeasure.
                     *
                     * O sampler e o emissor abaixo continuam sendo código
                     * próprio, mas recebem agora a mesma geometria-base da
                     * referência.
                     */
                    AndroidSkiaOutlineExtractor
                        .extract(
                            font =
                                font,
                            text =
                                text,
                            targetCapHeightUnits =
                                targetHeightUnits,
                            spacingUnits =
                                spacingUnits,
                            rotationDegrees =
                                options.rotationDegrees
                        )
                        .glyphPolygons
                        .map {
                                glyph ->
                            glyph.map {
                                contour ->
                                Polygon(
                                    contour.map {
                                            point ->
                                        FPoint(
                                            x =
                                                point.x,
                                            y =
                                                point.y
                                        )
                                    }
                                )
                            }
                        }
                } else {
                    val typeface =
                        ImportedFontStore
                            .loadTypeface(
                                font
                            )
                            .getOrThrow()

                    val paint =
                        Paint(
                            Paint.ANTI_ALIAS_FLAG or
                                Paint.LINEAR_TEXT_FLAG or
                                Paint.SUBPIXEL_TEXT_FLAG
                        ).apply {
                            this.typeface =
                                typeface
                            style =
                                Paint.Style.FILL
                            hinting =
                                Paint.HINTING_OFF
                            textScaleX =
                                1f
                            textSkewX =
                                0f

                            textSize =
                                resolveFontSizeForCapHeight(
                                    paint =
                                        this,
                                    targetCapHeightUnits =
                                        targetHeightUnits,
                                    font =
                                        font
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
                        sampledTightBounds(
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

                    glyphPaths.map {
                            glyph ->
                        polygonize(
                            path =
                                glyph.path,
                            centerX =
                                centerX,
                            centerY =
                                centerY,
                            rotationDegrees =
                                options.rotationDegrees
                        )
                    }
                }

            require(
                polygonsByGlyph.isNotEmpty() &&
                    polygonsByGlyph.any {
                        it.isNotEmpty()
                    }
            ) {
                "A fonte não gerou contornos bordáveis."
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

            if (options.guidedSatinRoute != null) {
                require(text.equals("Maria", ignoreCase = true)) {
                    "O percurso guiado foi desenhado para Maria. Desative-o para outro nome."
                }
                emitUserGuidedRoute(
                    emitter = emitter,
                    polygonsByGlyph = polygonsByGlyph,
                    route = options.guidedSatinRoute,
                    densityMm = densityMm,
                    maxSatinWidthMm = maxSatinWidthMm,
                    pullMm = pullMm,
                    underlayMode = options.satinUnderlayMode
                )
            } else {
            polygonsByGlyph
                .forEachIndexed {
                        glyphIndex,
                        polygons ->
                    /*
                     * Fonte cursiva/importada precisa seguir o fluxo real do
                     * traço. A antiga rota por scanline/eixo quebrava um mesmo
                     * glifo em faixas independentes: terminava uma faixa,
                     * saltava para outra e depois voltava ao trecho anterior.
                     *
                     * A rota ativa agora separa geometria e sequência:
                     * contorno vetorial -> colunas Satin compactas por eixo ->
                     * planejador topológico emitGlyph() -> underlay/cobertura
                     * completa do objeto -> próximo ramo/objeto.
                     *
                     * Assim preservamos a contagem de pontos enxuta sem voltar
                     * à antiga emissão em ordem bruta que causava saltos.
                     */
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
                            if (glyphIndex == 0) {
                                satinStructuralStartPoint(columns)
                            } else {
                                null
                            },
                        underlayMode =
                            options
                                .satinUnderlayMode,
                        densityMm =
                            densityMm
                    )
                }
            }

            require(
                points.any {
                    it.command ==
                        StitchCommand.STITCH
                }
            ) {
                "O texto não produziu pontos — conteúdo sem área bordável."
            }

            val centeredGeometry =
                centerGeneratedGeometryOnStitches(
                    points =
                        points,
                    guidePoints =
                        guidePoints
                )

            points.clear()
            points +=
                centeredGeometry
                    .first

            val centeredGuidePoints =
                centeredGeometry
                    .second

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
                        centeredGuidePoints,
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

    /**
     * Generated names are physical machine designs, not just preview paths.
     * Center them on the visible sewn geometry (STITCH), never on travel
     * commands. JUMP/TRIM may sit outside the sewn bounds and must not move
     * the name away from the hoop center.
     */
    private fun centerGeneratedGeometryOnStitches(
        points: List<EmbroideryPoint>,
        guidePoints: List<EmbroideryPoint>
    ): Pair<List<EmbroideryPoint>, List<EmbroideryPoint>> {
        val stitches =
            points.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        if (
            stitches.isEmpty()
        ) {
            return points to
                guidePoints
        }

        val minX =
            stitches.minOf {
                it.xUnits
            }

        val maxX =
            stitches.maxOf {
                it.xUnits
            }

        val minY =
            stitches.minOf {
                it.yUnits
            }

        val maxY =
            stitches.maxOf {
                it.yUnits
            }

        val offsetX =
            -(
                (
                    minX +
                        maxX
                    ) /
                    2f
                ).roundToInt()

        val offsetY =
            -(
                (
                    minY +
                        maxY
                    ) /
                    2f
                ).roundToInt()

        fun move(
            point: EmbroideryPoint
        ): EmbroideryPoint =
            point.copy(
                xUnits =
                    point.xUnits +
                        offsetX,
                yUnits =
                    point.yUnits +
                        offsetY
            )

        return points.map(
            ::move
        ) to
            guidePoints.map(
                ::move
            )
    }

    internal fun debugCenteredVisibleGeometry():
        Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val source =
            listOf(
                EmbroideryPoint(
                    -100,
                    0,
                    StitchCommand.JUMP,
                    0
                ),
                EmbroideryPoint(
                    20,
                    10,
                    StitchCommand.STITCH,
                    0
                ),
                EmbroideryPoint(
                    80,
                    50,
                    StitchCommand.STITCH,
                    0
                )
            )

        val centered =
            centerGeneratedGeometryOnStitches(
                points =
                    source,
                guidePoints =
                    emptyList()
            ).first

        val stitches =
            centered.filter {
                it.command ==
                    StitchCommand.STITCH
            }

        val center =
            (
                stitches.minOf {
                    it.xUnits
                } +
                    stitches.maxOf {
                        it.xUnits
                    }
                ) /
                2 to
            (
                stitches.minOf {
                    it.yUnits
                } +
                    stitches.maxOf {
                        it.yUnits
                    }
                ) /
                2

        val jump =
            centered.first {
                it.command ==
                    StitchCommand.JUMP
            }

        return center to
            (
                jump.xUnits to
                    jump.yUnits
                )
    }

    private fun resolveFontSizeForCapHeight(
        paint: Paint,
        targetCapHeightUnits: Float,
        font: ImportedFont
    ): Float {
        val sfntRatio =
            capHeightRatio(
                font
            )

        if (
            sfntRatio !=
                null &&
            sfntRatio >
                0.001f
        ) {
            return targetCapHeightUnits /
                sfntRatio
        }

        /*
         * Fallback idêntico ao motor de referência: mede o ascent do font
         * a 100 unidades e usa 72% quando CapHeight não existe.
         * Não mede H/X, pois isso muda a escala em fontes cursivas.
         */
        paint.textSize =
            100f

        val capHeight =
            kotlin.math.abs(
                paint
                    .fontMetrics
                    .ascent
            ) *
                0.72f

        return 100f *
            targetCapHeightUnits /
            capHeight
                .coerceAtLeast(
                    1f
                )
    }

    private fun capHeightRatio(
        font: ImportedFont
    ): Float? =
        synchronized(
            capHeightRatioCache
        ) {
            if (
                capHeightRatioCache
                    .containsKey(
                        font.absolutePath
                    )
            ) {
                return@synchronized capHeightRatioCache[
                    font.absolutePath
                ]
            }

            val parsed =
                runCatching {
                    parseCapHeightRatio(
                        File(
                            font.absolutePath
                        )
                    )
                }
                    .getOrNull()

            capHeightRatioCache[
                font.absolutePath
            ] =
                parsed

            parsed
        }

    private fun parseCapHeightRatio(
        file: File
    ): Float? {
        if (
            !file.isFile
        ) {
            return null
        }

        val bytes =
            file.readBytes()

        if (
            bytes.size <
                12
        ) {
            return null
        }

        fun u16(
            offset: Int
        ): Int {
            if (
                offset < 0 ||
                offset +
                    2 >
                bytes.size
            ) {
                return -1
            }

            return (
                (
                    bytes[offset]
                        .toInt() and
                        0xFF
                    ) shl
                    8
                ) or
                (
                    bytes[
                        offset +
                            1
                    ].toInt() and
                        0xFF
                    )
        }

        fun s16(
            offset: Int
        ): Int {
            val raw =
                u16(
                    offset
                )

            if (
                raw <
                    0
            ) {
                return raw
            }

            return if (
                raw and
                    0x8000 !=
                    0
            ) {
                raw -
                    0x10000
            } else {
                raw
            }
        }

        fun u32(
            offset: Int
        ): Long {
            if (
                offset < 0 ||
                offset +
                    4 >
                bytes.size
            ) {
                return -1L
            }

            return (
                (
                    bytes[offset]
                        .toLong() and
                        0xFFL
                    ) shl
                    24
                ) or
                (
                    (
                        bytes[
                            offset +
                                1
                        ].toLong() and
                            0xFFL
                        ) shl
                        16
                    ) or
                (
                    (
                        bytes[
                            offset +
                                2
                        ].toLong() and
                            0xFFL
                        ) shl
                        8
                    ) or
                (
                    bytes[
                        offset +
                            3
                    ].toLong() and
                        0xFFL
                    )
        }

        val numTables =
            u16(
                4
            )

        if (
            numTables <=
                0 ||
            12 +
                numTables *
                    16 >
                bytes.size
        ) {
            return null
        }

        var headOffset =
            -1

        var os2Offset =
            -1

        var os2Length =
            0

        repeat(
            numTables
        ) {
                tableIndex ->
            val record =
                12 +
                    tableIndex *
                        16

            val tag =
                String(
                    bytes,
                    record,
                    4,
                    Charsets.US_ASCII
                )

            val offset =
                u32(
                    record +
                        8
                )

            val length =
                u32(
                    record +
                        12
                )

            if (
                offset <
                    0L ||
                length <
                    0L ||
                offset +
                    length >
                bytes.size
                    .toLong()
            ) {
                return@repeat
            }

            when (
                tag
            ) {
                "head" ->
                    headOffset =
                        offset.toInt()

                "OS/2" -> {
                    os2Offset =
                        offset.toInt()

                    os2Length =
                        length.toInt()
                }
            }
        }

        if (
            headOffset <
                0 ||
            os2Offset <
                0 ||
            os2Length <
                90
        ) {
            return null
        }

        val unitsPerEm =
            u16(
                headOffset +
                    18
            )

        val os2Version =
            u16(
                os2Offset
            )

        if (
            unitsPerEm <=
                0 ||
            os2Version <
                2
        ) {
            return null
        }

        val capHeight =
            s16(
                os2Offset +
                    88
            )

        if (
            capHeight <=
                0
        ) {
            return null
        }

        return capHeight
            .toFloat() /
            unitsPerEm
                .toFloat()
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

    private fun sampledTightBounds(
        glyphs: List<GlyphPath>
    ): RectF {
        var minX =
            Float.POSITIVE_INFINITY

        var minY =
            Float.POSITIVE_INFINITY

        var maxX =
            Float.NEGATIVE_INFINITY

        var maxY =
            Float.NEGATIVE_INFINITY

        val position =
            FloatArray(
                2
            )

        glyphs.forEach {
                glyph ->
            val measure =
                PathMeasure(
                    glyph.path,
                    true
                )

            do {
                val length =
                    measure.length

                if (
                    length <=
                        0f ||
                    !length.isFinite()
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
                        minX =
                            minOf(
                                minX,
                                position[0]
                            )

                        minY =
                            minOf(
                                minY,
                                position[1]
                            )

                        maxX =
                            maxOf(
                                maxX,
                                position[0]
                            )

                        maxY =
                            maxOf(
                                maxY,
                                position[1]
                            )
                    }
                }
            } while (
                measure.nextContour()
            )
        }

        require(
            minX.isFinite() &&
                minY.isFinite() &&
                maxX.isFinite() &&
                maxY.isFinite()
        ) {
            "A fonte não gerou limites vetoriais válidos."
        }

        return RectF(
            minX,
            minY,
            maxX,
            maxY
        )
    }

    private fun polygonize(
        path: Path,
        centerX: Float,
        centerY: Float,
        rotationDegrees: Float =
            0f
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

        val radians =
            Math.toRadians(
                rotationDegrees
                    .toDouble()
            )

        val rotationCos =
            cos(
                radians
            )
                .toFloat()

        val rotationSin =
            sin(
                radians
            )
                .toFloat()

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
                    val centeredX =
                        position[0] -
                            centerX

                    val centeredY =
                        position[1] -
                            centerY

                    points +=
                        FPoint(
                            x =
                                centeredX *
                                    rotationCos -
                                    centeredY *
                                        rotationSin,
                            y =
                                centeredX *
                                    rotationSin +
                                    centeredY *
                                        rotationCos
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
        /*
         * IMPORTANTE: polygonize() converte Android/TTF para a tela interna
         * usando y = centerY - sourceY. Portanto a BASE visual da letra fica
         * em maxY, não em minY. As versões anteriores procuravam mínimos de Y
         * e, na prática, analisavam a parte superior do glifo.
         */
        val valleyLimitY =
            maxY -
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
                    point.y <
                        valleyLimitY
                ) {
                    return@forEach
                }

                var leftFloor =
                    Float.POSITIVE_INFINITY

                var rightFloor =
                    Float.POSITIVE_INFINITY

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

                    leftFloor =
                        minOf(
                            leftFloor,
                            left.y
                        )

                    rightFloor =
                        minOf(
                            rightFloor,
                            right.y
                        )
                }

                if (
                    point.y -
                        leftFloor >=
                        prominence &&
                    point.y -
                        rightFloor >=
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
                group.maxWithOrNull(
                    compareBy<FPoint> {
                        it.y
                    }.thenByDescending {
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
                it.y >=
                    maxY -
                        lowerBand
            }
            .ifEmpty {
                points
            }
            .minWithOrNull(
                compareBy<FPoint> {
                    it.x
                }.thenByDescending {
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


    private data class SatinColumnProfile(
        val column: SatinColumn,
        val minX: Float,
        val directSpan: Float,
        val pathLength: Float,
        val tortuosity: Float,
        val bottomCenter: FPoint
    )

    private fun satinStructuralStartPoint(
        columns: List<SatinColumn>
    ): FPoint? {
        val usable =
            columns.filter {
                it.rows.size >=
                    2
            }

        if (
            usable.isEmpty()
        ) {
            return null
        }

        val allXs =
            usable.flatMap {
                    column ->
                column.rows.flatMap {
                        row ->
                    listOf(
                        row.a.x,
                        row.b.x
                    )
                }
            }

        val glyphMinX =
            allXs.minOrNull()
                ?: return null

        val glyphMaxX =
            allXs.maxOrNull()
                ?: return null

        val glyphWidth =
            (
                glyphMaxX -
                    glyphMinX
                ).coerceAtLeast(
                1f
            )

        val profiles =
            usable.map {
                    column ->
                val centers =
                    column.rows.map {
                        center(
                            it
                        )
                    }

                val pathLength =
                    centers
                        .zipWithNext()
                        .sumOf {
                                pair ->
                            distance(
                                pair.first,
                                pair.second
                            )
                                .toDouble()
                        }
                        .toFloat()

                val directSpan =
                    if (
                        centers.size >=
                            2
                    ) {
                        distance(
                            centers.first(),
                            centers.last()
                        )
                    } else {
                        0f
                    }

                val tortuosity =
                    if (
                        directSpan <
                            0.5f
                    ) {
                        Float.MAX_VALUE
                    } else {
                        pathLength /
                            directSpan
                    }

                SatinColumnProfile(
                    column =
                        column,
                    minX =
                        columnLeftEdgeX(
                            column
                        ),
                    directSpan =
                        directSpan,
                    pathLength =
                        pathLength,
                    tortuosity =
                        tortuosity,
                    bottomCenter =
                        // sourceYAxisDown=true: maior Y e a base visual.
                        centers.maxBy {
                            it.y
                        }
                )
            }

        val maxDirectSpan =
            profiles.maxOf {
                it.directSpan
            }

        val substantial =
            profiles
                .filter {
                    it.directSpan >=
                        max(
                            6f,
                            maxDirectSpan *
                                0.42f
                        ) &&
                        it.column.rows.size >=
                            3
                }
                .ifEmpty {
                    profiles
                }
                .sortedBy {
                    it.minX
                }

        val first =
            substantial.first()

        val second =
            substantial.getOrNull(
                1
            )

        val selected =
            if (
                second !=
                    null
            ) {
                val firstPosition =
                    (
                        first.minX -
                            glyphMinX
                        ) /
                        glyphWidth

                val secondPosition =
                    (
                        second.minX -
                            glyphMinX
                        ) /
                        glyphWidth

                val gap =
                    (
                        second.minX -
                            first.minX
                        ) /
                        glyphWidth

                val leadingFlourish =
                    firstPosition <=
                        0.14f &&
                    secondPosition in
                        0.14f..0.48f &&
                    gap >=
                        0.08f &&
                    first.tortuosity >=
                        1.18f &&
                    first.tortuosity >=
                        second.tortuosity *
                            1.12f &&
                    second.directSpan >=
                        first.directSpan *
                            0.55f

                if (
                    leadingFlourish
                ) {
                    second
                } else {
                    first
                }
            } else {
                first
            }

        return selected.bottomCenter
    }

    /**
     * A forma da fonte e a unica autoridade para COBERTURA Satin. Os caminhos
     * anotados pelo usuario controlam apenas a sequencia dos tracos.
     *
     * O gerador anterior projetava a linha desenhada sobre o contorno e
     * descartava as amostras ligeiramente fora da letra. Isso deixava
     * lacunas visiveis e concentrava pontos em poucos trechos.
     *
     * O medial-flow gerado a partir da mascara da fonte fornece os eixos
     * completos, inclusive as curvas que a anotacao nao alcançou.
     */
    private fun emitUserGuidedRoute(
        emitter: SatinEmitter,
        polygonsByGlyph: List<List<Polygon>>,
        route: GuidedSatinRoute,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullMm: Float,
        underlayMode: SatinUnderlayMode
    ) {
        val outlines = polygonsByGlyph.flatten().flatMap { it.points }
        require(outlines.size >= 3) { "Fonte sem contornos bordaveis." }
        val minX = outlines.minOf { it.x }
        val minY = outlines.minOf { it.y }
        val width = (outlines.maxOf { it.x } - minX).coerceAtLeast(1f)
        val height = (outlines.maxOf { it.y } - minY).coerceAtLeast(1f)
        fun map(p: GuidedSatinPoint) =
            FPoint(minX + p.x * width, minY + p.y * height)

        val guides = route.steps
            .filter { !it.jumpWithoutStitch && it.points.size >= 2 }
            .map { step -> step.points.map(::map) }
        require(guides.isNotEmpty()) { "A referencia nao tem tracos Satin." }

        fun projection(point: FPoint, path: List<FPoint>): Pair<Float, Float> {
            var progress = 0f
            var walked = 0f
            var minDistance = Float.POSITIVE_INFINITY
            path.zipWithNext().forEach { (a, b) ->
                val dx = b.x - a.x
                val dy = b.y - a.y
                val squared = dx * dx + dy * dy
                val t = if (squared < 0.0001f) 0f else
                    (((point.x - a.x) * dx + (point.y - a.y) * dy) / squared)
                        .coerceIn(0f, 1f)
                val projected = FPoint(a.x + t * dx, a.y + t * dy)
                val d = distance(point, projected)
                if (d < minDistance) {
                    minDistance = d
                    progress = walked + t * distance(a, b)
                }
                walked += distance(a, b)
            }
            return minDistance to progress
        }

        val start = map(route.start)
        var firstGlyph = true
        polygonsByGlyph.forEachIndexed { glyphIndex, polygons ->
            if (polygons.isEmpty()) return@forEachIndexed

            // Criar TODOS os eixos pelo contorno da fonte. Nao filtrar por
            // proximidade com a anotacao: ela e aproximada e pode estar
            // deslocada em relacao ao glifo real importado.
            val medialColumns = sampleAdaptiveFlowColumns(
                polygons = polygons,
                densityMm = densityMm,
                maxSatinWidthMm = maxSatinWidthMm,
                pullCompensationMm = pullMm
            ).filter { it.rows.size >= 2 }

            require(medialColumns.isNotEmpty()) {
                "A fonte nao produziu eixos Satin completos na letra " +
                    (glyphIndex + 1) + ". Tente outra fonte ou altura."
            }

            val ordered = medialColumns.map { column ->
                val centers = column.rows.map(::center)
                val probes = listOf(
                    centers.first(),
                    centers[centers.lastIndex / 2],
                    centers.last()
                )
                val stage = guides.indices.minByOrNull { index ->
                    probes.sumOf { projection(it, guides[index]).first.toDouble() }
                } ?: 0
                val position = projection(centers[centers.lastIndex / 2], guides[stage]).second
                Triple(column, stage, position)
            }.sortedWith(
                compareBy<Triple<SatinColumn, Int, Float>> { it.second }
                    .thenBy { it.third }
            ).map { it.first }.toMutableList()

            if (firstGlyph && ordered.isNotEmpty()) {
                // A primeira regiao parte do inicio marcado, nao de um
                // segmento aleatorio do esqueleto.
                val chosen = ordered.minBy { column ->
                    column.rows.minOf { row ->
                        minOf(
                            distance(start, center(row)),
                            distance(start, row.a),
                            distance(start, row.b)
                        )
                    }
                }
                ordered.remove(chosen)
                ordered.add(0, chosen)
            }

            if (firstGlyph) {
                // Nao perfurar uma regiao vazia fora da tipografia por
                // causa da imprecisao da marca sobre uma imagem de tela.
                val firstNeedle = if (pointInsidePolygons(start, polygons)) {
                    start
                } else {
                    ordered.first().rows.minBy { distance(start, center(it)) }
                        .let(::center)
                }
                emitter.guidedFirstStitch(firstNeedle)
                firstGlyph = false
            }

            emitter.emitGlyph(
                columns = ordered,
                polygons = polygons,
                startHint = if (glyphIndex == 0) start else null,
                underlayMode = underlayMode,
                densityMm = densityMm,
                guidedOrder = true
            )
        }
    }

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

    /*
     * Geometria Satin ativa para TTF/OTF:
     *
     * O sampler por eixos produz a cobertura compacta já comprovada no app
     * (contagem próxima da matriz de referência) e mantém a densidade física
     * solicitada. A ordem NÃO é mais a ordem bruta desse sampler: as colunas
     * resultantes são entregues ao SatinEmitter.emitGlyph(), que decide
     * continuidade, entrada/saída e próximo ramo pela topologia do contorno.
     *
     * O sampler medial adaptativo continua abaixo como ferramenta de
     * diagnóstico/experimentação, mas não é usado na produção porque o
     * skeleton pode multiplicar ramos em glifos cursivos complexos e inflar
     * a matriz para milhares de pontos redundantes.
     */
    private fun sampleColumns(
        polygons: List<Polygon>,
        densityMm: Float,
        maxSatinWidthMm: Float,
        pullCompensationMm: Float
    ): List<SatinColumn> =
        sampleAxisColumns(
            polygons =
                polygons,
            densityMm =
                densityMm,
            maxSatinWidthMm =
                maxSatinWidthMm,
            pullCompensationMm =
                pullCompensationMm
        )

    private const val MAX_FLOW_GRID_CELLS =
        250_000

    private const val MAX_FLOW_THIN_ITERATIONS =
        300

    private data class FlowGrid(
        val originX: Float,
        val originY: Float,
        val step: Float,
        val mask: Array<BooleanArray>
    ) {
        val rows: Int
            get() =
                mask.size

        val columns: Int
            get() =
                mask.firstOrNull()
                    ?.size
                    ?: 0

        fun point(
            row: Int,
            column: Int
        ): FPoint =
            FPoint(
                x =
                    originX +
                        column *
                            step,
                y =
                    originY +
                        row *
                            step
            )
    }

    private data class GridPoint(
        val row: Int,
        val column: Int
    )

    private fun sampleAdaptiveFlowColumns(
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

        val grid =
            buildFlowGrid(
                polygons =
                    polygons,
                densityMm =
                    densityMm
            )
                ?: return emptyList()

        val skeleton =
            thinFlowMask(
                grid.mask
            )

        val paths =
            extractFlowPaths(
                skeleton =
                    skeleton,
                columns =
                    grid.columns
            )

        if (
            paths.isEmpty()
        ) {
            return emptyList()
        }

        val pitchUnits =
            (
                densityMm *
                    10f
                ).coerceAtLeast(
                1.5f
            )

        val pullUnits =
            pullCompensationMm *
                10f

        val maxWidthUnits =
            maxSatinWidthMm *
                10f

        val columns =
            mutableListOf<
                SatinColumn
            >()

        paths.forEach {
                gridPath ->
            val guide =
                gridPath.map {
                        pixel ->
                    grid.point(
                        row =
                            pixel.row,
                        column =
                            pixel.column
                    )
                }

            val extendedGuide =
                extendFlowGuideToCaps(
                    guide =
                        guide,
                    polygons =
                        polygons
                )

            val sampled =
                resampleFlowPath(
                    extendedGuide,
                    pitchUnits
                )

            if (
                sampled.isEmpty()
            ) {
                return@forEach
            }

            val rows =
                mutableListOf<
                    SatinRow
                >()

            sampled.forEachIndexed {
                    index,
                    centerPoint ->
                val tangent =
                    localFlowTangent(
                        points =
                            sampled,
                        index =
                            index
                    )
                        ?: return@forEachIndexed

                val row =
                    satinRowFromFlow(
                        center =
                            centerPoint,
                        tangent =
                            tangent,
                        polygons =
                            polygons,
                        pullUnits =
                            pullUnits,
                        maxWidthUnits =
                            maxWidthUnits
                    )

                if (
                    row !=
                        null
                ) {
                    val previous =
                        rows.lastOrNull()

                    if (
                        previous ==
                            null ||
                        distance(
                            center(
                                previous
                            ),
                            center(
                                row
                            )
                        ) >=
                            pitchUnits *
                                0.45f
                    ) {
                        rows +=
                            row
                    }
                }
            }

            if (
                rows.isNotEmpty()
            ) {
                splitWideColumn(
                    column =
                        SatinColumn(
                            rows
                        ),
                    maxWidthUnits =
                        maxWidthUnits
                ).forEach {
                        piece ->
                    if (
                        piece.rows
                            .isNotEmpty()
                    ) {
                        columns +=
                            piece
                    }
                }
            }
        }

        val usefulRows =
            columns.sumOf {
                it.rows.size
            }

        return if (
            usefulRows >=
                3
        ) {
            columns
        } else {
            emptyList()
        }
    }

    private fun buildFlowGrid(
        polygons: List<Polygon>,
        densityMm: Float
    ): FlowGrid? {
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

        var step =
            max(
                2.5f,
                densityMm *
                    10f *
                    0.7f
            )

        fun dimensions(
            candidateStep: Float
        ): Pair<Int, Int> {
            val columns =
                ceil(
                    (
                        maxX -
                            minX
                        ) /
                        candidateStep
                )
                    .toInt() +
                    3

            val rows =
                ceil(
                    (
                        maxY -
                            minY
                        ) /
                        candidateStep
                )
                    .toInt() +
                    3

            return rows to
                columns
        }

        var dims =
            dimensions(
                step
            )

        while (
            dims.first.toLong() *
                dims.second.toLong() >
                MAX_FLOW_GRID_CELLS
        ) {
            step *=
                1.2f

            dims =
                dimensions(
                    step
                )
        }

        val rows =
            dims.first

        val columns =
            dims.second

        if (
            rows <
                3 ||
            columns <
                3
        ) {
            return null
        }

        val originX =
            minX -
                step

        val originY =
            minY -
                step

        val mask =
            Array(
                rows
            ) {
                BooleanArray(
                    columns
                )
            }

        var filled =
            0

        /*
         * Raster por scanline: calcula as interseções do contorno uma vez
         * por linha e preenche os pares internos. Evita testar cada célula
         * contra todos os segmentos do glifo.
         */
        for (
            row in
                1 until
                    rows -
                    1
        ) {
            val y =
                originY +
                    row *
                        step

            val intersections =
                mutableListOf<
                    Float
                >()

            polygons.forEach {
                    polygon ->
                val contour =
                    polygon.points

                if (
                    contour.size <
                        3
                ) {
                    return@forEach
                }

                for (
                    index in
                        contour.indices
                ) {
                    val a =
                        contour[index]

                    val b =
                        contour[
                            (
                                index +
                                    1
                                ) %
                                contour.size
                        ]

                    val crosses =
                        (
                            a.y <=
                                y &&
                            b.y >
                                y
                            ) ||
                            (
                                b.y <=
                                    y &&
                                a.y >
                                    y
                                )

                    if (
                        !crosses
                    ) {
                        continue
                    }

                    val ratio =
                        (
                            y -
                                a.y
                            ) /
                            (
                                b.y -
                                    a.y
                                )

                    intersections +=
                        a.x +
                            (
                                b.x -
                                    a.x
                                ) *
                                ratio
                }
            }

            intersections.sort()

            var index =
                0

            while (
                index +
                    1 <
                    intersections.size
            ) {
                val startX =
                    intersections[index]

                val endX =
                    intersections[
                        index +
                            1
                    ]

                val startColumn =
                    ceil(
                        (
                            startX -
                                originX
                            ) /
                            step
                    )
                        .toInt()
                        .coerceIn(
                            1,
                            columns -
                                2
                        )

                val endColumn =
                    kotlin.math.floor(
                        (
                            endX -
                                originX
                            ) /
                            step
                    )
                        .toInt()
                        .coerceIn(
                            1,
                            columns -
                                2
                        )

                if (
                    endColumn >=
                        startColumn
                ) {
                    for (
                        column in
                            startColumn..endColumn
                    ) {
                        if (
                            !mask[row][column]
                        ) {
                            mask[row][column] =
                                true

                            filled++
                        }
                    }
                }

                index +=
                    2
            }
        }

        return if (
            filled >=
                3
        ) {
            FlowGrid(
                originX =
                    originX,
                originY =
                    originY,
                step =
                    step,
                mask =
                    mask
            )
        } else {
            null
        }
    }

    private fun thinFlowMask(
        source: Array<BooleanArray>
    ): Array<BooleanArray> {
        val mask =
            Array(
                source.size
            ) {
                    row ->
                source[row]
                    .copyOf()
            }

        if (
            mask.size <
                3 ||
            mask[0].size <
                3
        ) {
            return mask
        }

        val rows =
            mask.size

        val columns =
            mask[0].size

        fun neighbors(
            row: Int,
            column: Int
        ): BooleanArray =
            booleanArrayOf(
                mask[row - 1][column],
                mask[row - 1][column + 1],
                mask[row][column + 1],
                mask[row + 1][column + 1],
                mask[row + 1][column],
                mask[row + 1][column - 1],
                mask[row][column - 1],
                mask[row - 1][column - 1]
            )

        var iteration =
            0

        var changed =
            true

        while (
            changed &&
            iteration <
                MAX_FLOW_THIN_ITERATIONS
        ) {
            changed =
                false

            for (
                phase in
                    0..1
            ) {
                val remove =
                    mutableListOf<
                        GridPoint
                    >()

                for (
                    row in
                        1 until
                            rows -
                            1
                ) {
                    for (
                        column in
                            1 until
                                columns -
                                1
                    ) {
                        if (
                            !mask[row][column]
                        ) {
                            continue
                        }

                        val n =
                            neighbors(
                                row,
                                column
                            )

                        val count =
                            n.count {
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
                            index in
                                n.indices
                        ) {
                            if (
                                !n[index] &&
                                n[
                                    (
                                        index +
                                            1
                                        ) %
                                        n.size
                                ]
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
                            n[0]

                        val p4 =
                            n[2]

                        val p6 =
                            n[4]

                        val p8 =
                            n[6]

                        val firstRule =
                            if (
                                phase ==
                                    0
                            ) {
                                !(p2 &&
                                    p4 &&
                                    p6) &&
                                    !(p4 &&
                                        p6 &&
                                        p8)
                            } else {
                                !(p2 &&
                                    p4 &&
                                    p8) &&
                                    !(p2 &&
                                        p6 &&
                                        p8)
                            }

                        if (
                            firstRule
                        ) {
                            remove +=
                                GridPoint(
                                    row,
                                    column
                                )
                        }
                    }
                }

                if (
                    remove.isNotEmpty()
                ) {
                    changed =
                        true

                    remove.forEach {
                            pixel ->
                        mask[pixel.row][pixel.column] =
                            false
                    }
                }
            }

            iteration++
        }

        return mask
    }

    private fun extractFlowPaths(
        skeleton: Array<BooleanArray>,
        columns: Int
    ): List<List<GridPoint>> {
        if (
            skeleton.isEmpty() ||
            columns <=
                0
        ) {
            return emptyList()
        }

        val rows =
            skeleton.size

        fun id(
            point: GridPoint
        ): Int =
            point.row *
                columns +
                point.column

        fun fromId(
            value: Int
        ): GridPoint =
            GridPoint(
                row =
                    value /
                        columns,
                column =
                    value %
                        columns
            )

        fun neighbors(
            point: GridPoint
        ): List<GridPoint> {
            val result =
                mutableListOf<
                    GridPoint
                >()

            for (
                dr in
                    -1..1
            ) {
                for (
                    dc in
                        -1..1
                ) {
                    if (
                        dr ==
                            0 &&
                        dc ==
                            0
                    ) {
                        continue
                    }

                    val row =
                        point.row +
                            dr

                    val column =
                        point.column +
                            dc

                    if (
                        row in
                            0 until
                                rows &&
                        column in
                            0 until
                                columns &&
                        skeleton[row][column]
                    ) {
                        /*
                         * Em um skeleton 8-conectado, uma diagonal não deve
                         * virar uma segunda aresta quando os mesmos pixels já
                         * estão ligados por um vizinho ortogonal. Aceitar as
                         * três ligações cria triângulos artificiais no grafo;
                         * cada triângulo era extraído como mais um flow path e
                         * acabava bordado como coluna Satin redundante.
                         */
                        val diagonal =
                            dr !=
                                0 &&
                            dc !=
                                0

                        val hasOrthogonalBridge =
                            diagonal &&
                            (
                                skeleton[
                                    point.row
                                ][
                                    point.column +
                                        dc
                                ] ||
                                skeleton[
                                    point.row +
                                        dr
                                ][
                                    point.column
                                ]
                                )

                        if (
                            !hasOrthogonalBridge
                        ) {
                            result +=
                                GridPoint(
                                    row,
                                    column
                                )
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
            val a =
                minOf(
                    first,
                    second
                )

            val b =
                maxOf(
                    first,
                    second
                )

            return (
                a.toLong()
                    .shl(
                        32
                    )
                ) xor
                (
                    b.toLong() and
                        0xffffffffL
                    )
        }

        val nodes =
            mutableListOf<
                GridPoint
            >()

        for (
            row in
                0 until
                    rows
        ) {
            for (
                column in
                    0 until
                        columns
            ) {
                if (
                    skeleton[row][column]
                ) {
                    nodes +=
                        GridPoint(
                            row,
                            column
                        )
                }
            }
        }

        if (
            nodes.isEmpty()
        ) {
            return emptyList()
        }

        val neighborMap =
            nodes.associate {
                    point ->
                id(
                    point
                ) to
                    neighbors(
                        point
                    )
                        .map {
                            id(
                                it
                            )
                        }
            }

        val visited =
            mutableSetOf<
                Long
            >()

        val paths =
            mutableListOf<
                List<GridPoint>
            >()

        fun follow(
            start: Int,
            next: Int
        ): List<GridPoint> {
            val result =
                mutableListOf(
                    fromId(
                        start
                    )
                )

            var previous =
                start

            var current =
                next

            var guard =
                0

            while (
                guard <
                    nodes.size *
                        2
            ) {
                visited +=
                    edgeKey(
                        previous,
                        current
                    )

                result +=
                    fromId(
                        current
                    )

                val available =
                    neighborMap[current]
                        .orEmpty()
                        .filter {
                            it !=
                                previous
                        }

                if (
                    neighborMap[current]
                        .orEmpty()
                        .size !=
                        2 ||
                    available.isEmpty()
                ) {
                    break
                }

                val candidate =
                    available.first()

                if (
                    edgeKey(
                        current,
                        candidate
                    ) in
                    visited
                ) {
                    break
                }

                previous =
                    current

                current =
                    candidate

                guard++
            }

            return result
        }

        val terminalIds =
            neighborMap
                .filterValues {
                    it.size !=
                        2
                }
                .keys
                .sorted()

        terminalIds.forEach {
                start ->
            val adjacent =
                neighborMap[start]
                    .orEmpty()

            if (
                adjacent.isEmpty()
            ) {
                paths +=
                    listOf(
                        fromId(
                            start
                        )
                    )
            } else {
                adjacent.forEach {
                        next ->
                    val key =
                        edgeKey(
                            start,
                            next
                        )

                    if (
                        key !in
                            visited
                    ) {
                        paths +=
                            follow(
                                start,
                                next
                            )
                    }
                }
            }
        }

        neighborMap.forEach {
                (
                    start,
                    adjacent
                ) ->
            adjacent.forEach {
                    next ->
                val key =
                    edgeKey(
                        start,
                        next
                    )

                if (
                    key !in
                        visited
                ) {
                    paths +=
                        follow(
                            start,
                            next
                        )
                }
            }
        }

        val cleaned =
            pruneShortTerminalFlowSpurs(
                paths.filter {
                    it.isNotEmpty()
                }
            )

        return mergeFlowPathsByTangentContinuity(
            cleaned
        )
    }

    /**
     * Remove apenas ramos terminais muito curtos que nascem em um
     * entroncamento do skeleton. Componentes isolados são preservados
     * (por exemplo o ponto do "i").
     *
     * O limite é medido em células do grid e foi mantido conservador:
     * ruído de 1-3 pixels sai; um traço tipográfico real permanece.
     */
    private fun pruneShortTerminalFlowSpurs(
        source: List<List<GridPoint>>
    ): List<List<GridPoint>> {
        if (
            source.size <
                2
        ) {
            return source
        }

        val endpointUse =
            mutableMapOf<
                GridPoint,
                Int
            >()

        source.forEach {
                path ->
            path.firstOrNull()
                ?.let {
                    endpointUse[it] =
                        (
                            endpointUse[it]
                                ?: 0
                            ) +
                            1
                }

            path.lastOrNull()
                ?.let {
                    endpointUse[it] =
                        (
                            endpointUse[it]
                                ?: 0
                            ) +
                            1
                }
        }

        return source.filter {
                path ->
            if (
                path.size >=
                    5
            ) {
                return@filter true
            }

            val firstShared =
                (
                    endpointUse[
                        path.first()
                    ] ?: 0
                    ) >
                    1

            val lastShared =
                (
                    endpointUse[
                        path.last()
                    ] ?: 0
                    ) >
                    1

            /*
             * Só é spur quando exatamente uma ponta nasce num nó compartilhado.
             * Caminho curto isolado/fechado não é descartado.
             */
            !(firstShared xor
                lastShared)
        }
    }

    /**
     * O skeleton divide um traço em cada nó com grau != 2. Isso é útil para
     * detectar ramos, mas não significa que toda aresta seja uma nova coluna
     * de bordado.
     *
     * No PE-DESIGN-style, quando dois segmentos atravessam o mesmo
     * entroncamento mantendo a direção do traço, eles pertencem à mesma
     * coluna Satin. A ramificação lateral permanece separada e só será
     * trabalhada depois que a coluna contínua terminar.
     *
     * Regressão protegida: não saltar para um ramo lateral no meio da
     * cobertura da mesma coluna visual.
     */
    private fun mergeFlowPathsByTangentContinuity(
        source: List<List<GridPoint>>
    ): List<List<GridPoint>> {
        val paths =
            source
                .filter {
                    it.isNotEmpty()
                }
                .map {
                    it.toList()
                }
                .toMutableList()

        if (
            paths.size <
                2
        ) {
            return paths
        }

        data class Endpoint(
            val pathIndex: Int,
            val atStart: Boolean,
            val point: GridPoint,
            val directionRow: Double,
            val directionColumn: Double
        )

        fun endpoint(
            path: List<GridPoint>,
            pathIndex: Int,
            atStart: Boolean
        ): Endpoint? {
            if (
                path.size <
                    2
            ) {
                return null
            }

            val anchor =
                if (
                    atStart
                ) {
                    path.first()
                } else {
                    path.last()
                }

            val sampleIndex =
                minOf(
                    4,
                    path.lastIndex
                )

            val inner =
                if (
                    atStart
                ) {
                    path[
                        sampleIndex
                    ]
                } else {
                    path[
                        path.lastIndex -
                            sampleIndex
                    ]
                }

            val dr =
                (
                    inner.row -
                        anchor.row
                    ).toDouble()

            val dc =
                (
                    inner.column -
                        anchor.column
                    ).toDouble()

            val length =
                hypot(
                    dr,
                    dc
                )

            if (
                length <
                    0.001
            ) {
                return null
            }

            return Endpoint(
                pathIndex =
                    pathIndex,
                atStart =
                    atStart,
                point =
                    anchor,
                directionRow =
                    dr /
                        length,
                directionColumn =
                    dc /
                        length
            )
        }

        fun continuationScore(
            first: Endpoint,
            second: Endpoint
        ): Double {
            /*
             * Os dois vetores apontam do nó para dentro de cada segmento.
             * Para uma continuação reta eles apontam em sentidos opostos,
             * então o produto escalar vale aproximadamente -1. Invertemos
             * o sinal para que 1 represente a melhor continuação.
             */
            return -(
                first.directionRow *
                    second.directionRow +
                first.directionColumn *
                    second.directionColumn
                )
        }

        fun merge(
            firstPath: List<GridPoint>,
            firstAtStart: Boolean,
            secondPath: List<GridPoint>,
            secondAtStart: Boolean
        ): List<GridPoint> {
            val first =
                if (
                    firstAtStart
                ) {
                    firstPath.asReversed()
                } else {
                    firstPath
                }

            val second =
                if (
                    secondAtStart
                ) {
                    secondPath
                } else {
                    secondPath.asReversed()
                }

            return first +
                second.drop(
                    1
                )
        }

        while (
            true
        ) {
            val endpoints =
                paths.flatMapIndexed {
                        index,
                        path ->
                    listOfNotNull(
                        endpoint(
                            path =
                                path,
                            pathIndex =
                                index,
                            atStart =
                                true
                        ),
                        endpoint(
                            path =
                                path,
                            pathIndex =
                                index,
                            atStart =
                                false
                        )
                    )
                }

            var bestFirst:
                Endpoint? =
                null

            var bestSecond:
                Endpoint? =
                null

            /*
             * Letras cursivas frequentemente atravessam um nó em Y com
             * ângulo de ~120°. Como os vetores saem do nó em direções
             * opostas ao percurso, isso produz score de continuação ~0.5.
             * Exigir 0.55 quebrava uma mesma coluna em duas e fazia o motor
             * sair para outro lado antes de voltar.
             */
            var bestScore =
                0.05

            endpoints
                .groupBy {
                    it.point
                }
                .values
                .forEach {
                        group ->
                    if (
                        group.size <
                            2
                    ) {
                        return@forEach
                    }

                    for (
                        firstIndex in
                            0 until
                                group.lastIndex
                    ) {
                        for (
                            secondIndex in
                                firstIndex +
                                    1 until
                                    group.size
                        ) {
                            val first =
                                group[
                                    firstIndex
                                ]

                            val second =
                                group[
                                    secondIndex
                                ]

                            if (
                                first.pathIndex ==
                                    second.pathIndex
                            ) {
                                continue
                            }

                            val score =
                                continuationScore(
                                    first,
                                    second
                                )

                            if (
                                score >
                                    bestScore +
                                        0.0001
                            ) {
                                bestScore =
                                    score
                                bestFirst =
                                    first
                                bestSecond =
                                    second
                            } else if (
                                abs(
                                    score -
                                        bestScore
                                ) <=
                                    0.0001 &&
                                bestFirst !=
                                    null &&
                                bestSecond !=
                                    null
                            ) {
                                val candidateMin =
                                    minOf(
                                        first.pathIndex,
                                        second.pathIndex
                                    )

                                val currentMin =
                                    minOf(
                                        bestFirst!!.pathIndex,
                                        bestSecond!!.pathIndex
                                    )

                                if (
                                    candidateMin <
                                        currentMin
                                ) {
                                    bestFirst =
                                        first
                                    bestSecond =
                                        second
                                }
                            }
                        }
                    }
                }

            val first =
                bestFirst
                    ?: break

            val second =
                bestSecond
                    ?: break

            val firstIndex =
                first.pathIndex

            val secondIndex =
                second.pathIndex

            val merged =
                merge(
                    firstPath =
                        paths[
                            firstIndex
                        ],
                    firstAtStart =
                        first.atStart,
                    secondPath =
                        paths[
                            secondIndex
                        ],
                    secondAtStart =
                        second.atStart
                )

            val keepIndex =
                minOf(
                    firstIndex,
                    secondIndex
                )

            val removeIndex =
                maxOf(
                    firstIndex,
                    secondIndex
                )

            paths[
                keepIndex
            ] =
                merged

            paths.removeAt(
                removeIndex
            )
        }

        return paths
    }

    private fun extendFlowGuideToCaps(
        guide: List<FPoint>,
        polygons: List<Polygon>
    ): List<FPoint> {
        if (
            guide.size <
                2
        ) {
            return guide
        }

        fun normalized(
            from: FPoint,
            to: FPoint
        ): FPoint? {
            val dx =
                to.x -
                    from.x

            val dy =
                to.y -
                    from.y

            val length =
                hypot(
                    dx,
                    dy
                )

            if (
                length <
                    0.001f
            ) {
                return null
            }

            return FPoint(
                dx /
                    length,
                dy /
                    length
            )
        }

        val result =
            guide.toMutableList()

        val firstDirection =
            normalized(
                guide[0],
                guide[1]
            )

        if (
            firstDirection !=
                null
        ) {
            val distances =
                lineBoundaryDistances(
                    center =
                        guide.first(),
                    direction =
                        firstDirection,
                    polygons =
                        polygons
                )

            val outward =
                distances?.first

            if (
                outward !=
                    null &&
                outward >
                    0.75f
            ) {
                val travel =
                    (
                        outward -
                            0.5f
                        ).coerceAtLeast(
                        0f
                    )

                result[0] =
                    FPoint(
                        x =
                            guide.first().x -
                                firstDirection.x *
                                    travel,
                        y =
                            guide.first().y -
                                firstDirection.y *
                                    travel
                    )
            }
        }

        val lastDirection =
            normalized(
                guide[
                    guide.lastIndex -
                        1
                ],
                guide.last()
            )

        if (
            lastDirection !=
                null
        ) {
            val distances =
                lineBoundaryDistances(
                    center =
                        guide.last(),
                    direction =
                        lastDirection,
                    polygons =
                        polygons
                )

            val outward =
                distances?.second

            if (
                outward !=
                    null &&
                outward >
                    0.75f
            ) {
                val travel =
                    (
                        outward -
                            0.5f
                        ).coerceAtLeast(
                        0f
                    )

                result[
                    result.lastIndex
                ] =
                    FPoint(
                        x =
                            guide.last().x +
                                lastDirection.x *
                                    travel,
                        y =
                            guide.last().y +
                                lastDirection.y *
                                    travel
                    )
            }
        }

        return result
    }

    private fun resampleFlowPath(
        source: List<FPoint>,
        pitchUnits: Float
    ): List<FPoint> {
        if (
            source.size <=
                1
        ) {
            return source
        }

        val pitch =
            pitchUnits.coerceAtLeast(
                1f
            )

        val result =
            mutableListOf(
                source.first()
            )

        var remaining =
            pitch

        var segmentStart =
            source.first()

        for (
            index in
                1 until
                    source.size
        ) {
            val segmentEnd =
                source[index]

            var dx =
                segmentEnd.x -
                    segmentStart.x

            var dy =
                segmentEnd.y -
                    segmentStart.y

            var length =
                hypot(
                    dx,
                    dy
                )

            if (
                length <
                    0.0001f
            ) {
                segmentStart =
                    segmentEnd

                continue
            }

            var localStart =
                segmentStart

            while (
                length >=
                    remaining
            ) {
                val ratio =
                    remaining /
                        length

                val point =
                    FPoint(
                        x =
                            localStart.x +
                                (
                                    segmentEnd.x -
                                        localStart.x
                                    ) *
                                    ratio,
                        y =
                            localStart.y +
                                (
                                    segmentEnd.y -
                                        localStart.y
                                    ) *
                                    ratio
                    )

                result +=
                    point

                localStart =
                    point

                dx =
                    segmentEnd.x -
                        localStart.x

                dy =
                    segmentEnd.y -
                        localStart.y

                length =
                    hypot(
                        dx,
                        dy
                    )

                remaining =
                    pitch
            }

            remaining -=
                length

            segmentStart =
                segmentEnd
        }

        val last =
            source.last()

        if (
            distance(
                result.last(),
                last
            ) >=
                pitch *
                    0.35f
        ) {
            result +=
                last
        }

        return result
    }

    private fun localFlowTangent(
        points: List<FPoint>,
        index: Int
    ): FPoint? {
        if (
            points.isEmpty()
        ) {
            return null
        }

        val before =
            points[
                max(
                    0,
                    index -
                        2
                )
            ]

        val after =
            points[
                minOf(
                    points.lastIndex,
                    index +
                        2
                )
            ]

        val dx =
            after.x -
                before.x

        val dy =
            after.y -
                before.y

        val length =
            hypot(
                dx,
                dy
            )

        if (
            length <
                0.001f
        ) {
            return null
        }

        return FPoint(
            dx /
                length,
            dy /
                length
        )
    }

    private fun satinRowFromFlow(
        center: FPoint,
        tangent: FPoint,
        polygons: List<Polygon>,
        pullUnits: Float,
        maxWidthUnits: Float
    ): SatinRow? {
        if (
            !pointInsidePolygons(
                center,
                polygons
            )
        ) {
            return null
        }

        val normal =
            FPoint(
                x =
                    -tangent.y,
                y =
                    tangent.x
            )

        val boundaryDistances =
            lineBoundaryDistances(
                center =
                    center,
                direction =
                    normal,
                polygons =
                    polygons
            )
                ?: return null

        val negative =
            boundaryDistances.first

        val positive =
            boundaryDistances.second

        val width =
            negative +
                positive

        if (
            width <
                1f
        ) {
            return null
        }

        return SatinRow(
            a =
                FPoint(
                    x =
                        center.x -
                            normal.x *
                                (
                                    negative +
                                        pullUnits
                                    ),
                    y =
                        center.y -
                            normal.y *
                                (
                                    negative +
                                        pullUnits
                                    )
                ),
            b =
                FPoint(
                    x =
                        center.x +
                            normal.x *
                                (
                                    positive +
                                        pullUnits
                                    ),
                    y =
                        center.y +
                            normal.y *
                                (
                                    positive +
                                        pullUnits
                                    )
                )
        )
    }

    private fun lineBoundaryDistances(
        center: FPoint,
        direction: FPoint,
        polygons: List<Polygon>
    ): Pair<Float, Float>? {
        var nearestNegative =
            Float.NEGATIVE_INFINITY

        var nearestPositive =
            Float.POSITIVE_INFINITY

        var negativeFound =
            false

        var positiveFound =
            false

        fun cross(
            ax: Float,
            ay: Float,
            bx: Float,
            by: Float
        ): Float =
            ax *
                by -
                ay *
                    bx

        polygons.forEach {
                polygon ->
            val points =
                polygon.points

            if (
                points.size <
                    2
            ) {
                return@forEach
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

                val sx =
                    b.x -
                        a.x

                val sy =
                    b.y -
                        a.y

                val denominator =
                    cross(
                        direction.x,
                        direction.y,
                        sx,
                        sy
                    )

                if (
                    abs(
                        denominator
                    ) <
                        0.00001f
                ) {
                    continue
                }

                val qx =
                    a.x -
                        center.x

                val qy =
                    a.y -
                        center.y

                val t =
                    cross(
                        qx,
                        qy,
                        sx,
                        sy
                    ) /
                        denominator

                val u =
                    cross(
                        qx,
                        qy,
                        direction.x,
                        direction.y
                    ) /
                        denominator

                if (
                    u <
                        -0.0001f ||
                    u >
                        1.0001f
                ) {
                    continue
                }

                if (
                    t <
                        -0.0001f
                ) {
                    if (
                        !negativeFound ||
                        t >
                            nearestNegative
                    ) {
                        nearestNegative =
                            t

                        negativeFound =
                            true
                    }
                } else if (
                    t >
                        0.0001f
                ) {
                    if (
                        !positiveFound ||
                        t <
                            nearestPositive
                    ) {
                        nearestPositive =
                            t

                        positiveFound =
                            true
                    }
                }
            }
        }

        if (
            !negativeFound ||
            !positiveFound
        ) {
            return null
        }

        return (
            -nearestNegative
            ) to
            nearestPositive
    }

    private fun rayBoundaryDistance(
        center: FPoint,
        direction: FPoint,
        polygons: List<Polygon>,
        maxDistance: Float
    ): Float? {
        var insideDistance =
            0f

        var outsideDistance =
            0f

        val step =
            0.75f

        var distanceValue =
            step

        while (
            distanceValue <=
                maxDistance
        ) {
            val point =
                FPoint(
                    x =
                        center.x +
                            direction.x *
                                distanceValue,
                    y =
                        center.y +
                            direction.y *
                                distanceValue
                )

            if (
                pointInsidePolygons(
                    point,
                    polygons
                )
            ) {
                insideDistance =
                    distanceValue
            } else {
                outsideDistance =
                    distanceValue

                break
            }

            distanceValue +=
                step
        }

        if (
            outsideDistance <=
                0f
        ) {
            return null
        }

        repeat(
            8
        ) {
            val middle =
                (
                    insideDistance +
                        outsideDistance
                    ) /
                    2f

            val point =
                FPoint(
                    x =
                        center.x +
                            direction.x *
                                middle,
                    y =
                        center.y +
                            direction.y *
                                middle
                )

            if (
                pointInsidePolygons(
                    point,
                    polygons
                )
            ) {
                insideDistance =
                    middle
            } else {
                outsideDistance =
                    middle
            }
        }

        return (
            insideDistance +
                outsideDistance
            ) /
            2f
    }

    private fun pointInsidePolygons(
        point: FPoint,
        polygons: List<Polygon>
    ): Boolean {
        var inside =
            false

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

            var polygonInside =
                false

            var previous =
                points.last()

            points.forEach {
                    current ->
                val crosses =
                    (
                        current.y >
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
                            current.y

                    if (
                        abs(
                            denominator
                        ) >
                            0.00001f
                    ) {
                        val crossingX =
                            (
                                previous.x -
                                    current.x
                                ) *
                                (
                                    point.y -
                                        current.y
                                    ) /
                                denominator +
                                current.x

                        if (
                            point.x <
                                crossingX
                        ) {
                            polygonInside =
                                !polygonInside
                        }
                    }
                }

                previous =
                    current
            }

            if (
                polygonInside
            ) {
                inside =
                    !inside
            }
        }

        return inside
    }

    private fun sampleAxisColumns(
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

        /**
         * Emissão por colunas reproduzindo o modelo observado na referência:
         *
         * 1. entra sempre em rows[0].a;
         * 2. se habilitado, percorre o centro até o fim e volta ao início;
         * 3. trava sempre no rail A;
         * 4. emite A -> B para cada row, em ordem direta;
         * 5. trava no rail A da última row.
         *
         * A ordem das colunas vem exclusivamente de buildColumns().
         */
        fun emitAxisReferenceColumn(
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

            val entry =
                rows.first()
                    .a

            travelTo(
                entry
            )

            if (
                includeUnderlay &&
                rows.size >=
                    4
            ) {
                emitAxisCenterRunRoundTrip(
                    rows =
                        rows,
                    densityMm =
                        densityMm
                )
            }

            emitAxisLock(
                rows.first()
            )

            rows.forEach {
                    row ->
                emitStitchTo(
                    row.a
                )

                emitStitchTo(
                    row.b
                )
            }

            emitAxisLock(
                rows.last()
            )
        }

        private fun emitAxisCenterRunRoundTrip(
            rows: List<SatinRow>,
            densityMm: Float
        ) {
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

            val last =
                center(
                    rows.last()
                )

            /*
             * O gerador de referência adiciona sempre o centro da última row,
             * mesmo quando ela já caiu exatamente no passo amostrado.
             * Isso preserva inclusive a pontada zero no extremo antes da volta.
             */
            centers +=
                last

            centers.forEach {
                    point ->
                emitStitchTo(
                    point
                )
            }

            for (
                reverseIndex in
                    centers.lastIndex -
                        1 downTo
                        0
            ) {
                emitStitchTo(
                    centers[
                        reverseIndex
                    ]
                )
            }
        }

        private fun emitAxisLock(
            row: SatinRow
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

            emitStitchTo(
                anchor
            )

            emitStitchTo(
                FPoint(
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

            emitStitchTo(
                anchor
            )
        }

        fun emitGlyph(
            columns: List<SatinColumn>,
            polygons: List<Polygon>,
            startHint: FPoint?,
            underlayMode: SatinUnderlayMode,
            densityMm: Float,
            guidedOrder: Boolean = false
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
                    (
                        if (guidedOrder) {
                            remaining.firstOrNull()
                        } else if (
                            firstColumn &&
                            startsDesignAtVisualHint &&
                            startHint !=
                                null
                        ) {
                            nextColumnFromAnchorGeometry(
                                columns =
                                    remaining,
                                anchor =
                                    startHint
                            )
                        } else if (
                            selectionAnchor !=
                                null
                        ) {
                            nextColumnFollowingObject(
                                columns =
                                    remaining,
                                anchor =
                                    selectionAnchor,
                                underlayMode =
                                    underlayMode,
                                polygons =
                                    polygons
                            )
                        } else {
                            nextColumnInReadingOrder(
                                remaining
                            )
                        }
                    ) ?: break

                val continuesCurrentObject =
                    current?.let {
                            anchor ->
                        columnConnectsToAnchor(
                            column =
                                selected,
                            anchor =
                                anchor,
                            polygons =
                                polygons
                        )
                    } ==
                        true

                val includeUnderlay =
                    underlayMode !=
                        SatinUnderlayMode.NONE &&
                    selected.rows.size >=
                        4 &&
                    (
                        guidedOrder && firstColumn ||
                        !continuesCurrentObject
                    )

                val anchor =
                    if (
                        firstColumn &&
                        startsDesignAtVisualHint
                    ) {
                        visualLowerEnd(
                            selected
                        )
                    } else {
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

                val insetFirstUnderlay =
                    firstColumn &&
                    startsDesignAtVisualHint &&
                    includeUnderlay

                val entry =
                    if (
                        includeUnderlay
                    ) {
                        underlayEntryPoint(
                            column =
                                column,
                            densityMm =
                                densityMm,
                            insetFirstColumn =
                                insetFirstUnderlay,
                            visualAnchor =
                                if (
                                    insetFirstUnderlay
                                ) {
                                    startHint
                                } else {
                                    null
                                }
                        )
                            ?: center(
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

                val connectionDistance =
                    currentPoint?.let {
                            point ->
                        distance(
                            point,
                            entry
                        )
                    }

                val continuousInsideJoin =
                    currentPoint !=
                        null &&
                    connectionDistance !=
                        null &&
                    connectionDistance in
                        0.5f..NEAR_COLUMN_JOIN_UNITS &&
                    segmentInsideGlyph(
                        from =
                            currentPoint,
                        to =
                            entry,
                        polygons =
                            polygons
                    )

                if (
                    guidedOrder &&
                    currentPoint != null &&
                    segmentInsideGlyph(
                        from = currentPoint,
                        to = entry,
                        polygons = polygons
                    )
                ) {
                    // Tracos realmente conectados dentro da letra devem
                    // ser COSTURADOS, nao transformados em saltos.
                    emitSegmented(
                        from = currentPoint,
                        to = entry,
                        command = StitchCommand.STITCH,
                        maxSegmentUnits = CONTINUOUS_CONNECTOR_STITCH_UNITS
                    )
                } else if (
                    continuousInsideJoin
                ) {
                    /*
                     * Trechos adjacentes do mesmo traço não devem virar
                     * JUMP só porque a medial-line foi dividida em um
                     * entroncamento. A referência continua costurando pela
                     * própria área preenchida da letra.
                     */
                    emitSegmented(
                        from =
                            currentPoint!!,
                        to =
                            entry,
                        command =
                            StitchCommand.STITCH,
                        maxSegmentUnits =
                            CONTINUOUS_CONNECTOR_STITCH_UNITS
                    )
                } else if (
                    firstColumn &&
                    startsDesignAtVisualHint &&
                    currentPoint !=
                        null &&
                    connectionDistance !=
                        null &&
                    connectionDistance >=
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
                        densityMm,
                    insetFirstUnderlay =
                        insetFirstUnderlay,
                    visualAnchor =
                        if (
                            insetFirstUnderlay
                        ) {
                            startHint
                        } else {
                            null
                        }
                )

                remaining.remove(
                    selected
                )

                firstColumn =
                    false
            }
        }

        private fun nextColumnFromAnchorGeometry(
            columns: List<SatinColumn>,
            anchor: FPoint
        ): SatinColumn? =
            columns.minByOrNull {
                    column ->
                column.rows
                    .minOfOrNull {
                            row ->
                        pointToSegmentDistance(
                            point =
                                anchor,
                            a =
                                row.a,
                            b =
                                row.b
                        )
                    }
                    ?: Float.MAX_VALUE
            }

        fun debugColumnFromAnchor(
            columns: List<SatinColumn>,
            anchor: FPoint
        ): SatinColumn? =
            nextColumnFromAnchorGeometry(
                columns =
                    columns,
                anchor =
                    anchor
            )

        /**
         * PE-DESIGN-style object traversal:
         * 1) while there is a column that can be reached through the filled
         *    area of the current glyph, stay on that same embroidery object;
         * 2) only when the object is exhausted, move to the next column in
         *    stable reading order.
         *
         * Distance may choose the entry end of a connected column, but it may
         * never pull an unrelated leg forward merely because it is closer.
         */
        private fun columnConnectsToAnchor(
            column: SatinColumn,
            anchor: FPoint,
            polygons: List<Polygon>
        ): Boolean {
            val rows =
                column.rows

            if (
                rows.isEmpty()
            ) {
                return false
            }

            val edgeRows =
                listOf(
                    rows.first(),
                    rows.last()
                )

            val candidates =
                edgeRows.flatMap {
                        row ->
                    listOf(
                        row.a,
                        center(
                            row
                        ),
                        row.b
                    )
                }

            return candidates.any {
                    candidate ->
                distance(
                    anchor,
                    candidate
                ) <=
                    NEAR_COLUMN_JOIN_UNITS &&
                segmentInsideGlyph(
                    from =
                        anchor,
                    to =
                        candidate,
                    polygons =
                        polygons
                )
            }
        }

        private fun nextColumnFollowingObject(
            columns: List<SatinColumn>,
            anchor: FPoint,
            underlayMode: SatinUnderlayMode,
            polygons: List<Polygon>
        ): SatinColumn? {
            val connected =
                columns.mapNotNull {
                        column ->
                    val rows =
                        column.rows

                    if (
                        rows.isEmpty()
                    ) {
                        return@mapNotNull null
                    }

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

                    val first =
                        entry(
                            rows.first()
                        )

                    val last =
                        entry(
                            rows.last()
                        )

                    val firstDistance =
                        distance(
                            anchor,
                            first
                        )

                    val lastDistance =
                        distance(
                            anchor,
                            last
                        )

                    val candidateEntry =
                        if (
                            lastDistance <
                                firstDistance
                        ) {
                            last
                        } else {
                            first
                        }

                    val candidateDistance =
                        minOf(
                            firstDistance,
                            lastDistance
                        )

                    if (
                        candidateDistance <=
                            NEAR_COLUMN_JOIN_UNITS &&
                        segmentInsideGlyph(
                            from =
                                anchor,
                            to =
                                candidateEntry,
                            polygons =
                                polygons
                        )
                    ) {
                        Pair(
                            column,
                            candidateDistance
                        )
                    } else {
                        null
                    }
                }

            // Esgota o ramo à esquerda antes de avançar para outro
            // ramo conectado à direita. A distância só desempata a
            // mesma posição espacial; não deve reordenar regiões do M.
            return connected
                .minWithOrNull(
                    compareBy<Pair<SatinColumn, Float>> {
                        columnLeftEdgeX(it.first)
                    }.thenBy {
                        it.second
                    }
                )
                ?.first
                ?: nextColumnInReadingOrder(
                    columns
                )
        }

        private fun visualLowerEnd(
            column: SatinColumn
        ): FPoint {
            val first =
                center(
                    column.rows
                        .first()
                )

            val last =
                center(
                    column.rows
                        .last()
                )

            /*
             * O design gerado usa sourceYAxisDown=true.
             * Y maior significa ponto visualmente mais baixo.
             */
            return if (
                first.y >=
                    last.y
            ) {
                first
            } else {
                last
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
            densityMm: Float,
            insetFirstUnderlay: Boolean =
                false,
            visualAnchor: FPoint? =
                null
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
                            densityMm,
                        insetFirstColumn =
                            insetFirstUnderlay,
                        visualAnchor =
                            visualAnchor
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

        private fun centerRunUnderlayPoints(
            column: SatinColumn,
            densityMm: Float
        ): List<FPoint> {
            val rows =
                column.rows

            if (
                rows.isEmpty()
            ) {
                return emptyList()
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

            return centers
        }

        private fun firstColumnUnderlayStartIndex(
            centers: List<FPoint>
        ): Int {
            if (
                centers.size <
                    5
            ) {
                return 0
            }

            return (
                centers.lastIndex *
                    FIRST_COLUMN_UNDERLAY_ENTRY_FRACTION
                ).roundToInt()
                .coerceIn(
                    1,
                    centers.lastIndex -
                        1
                )
        }

        private fun underlayEntryPoint(
            column: SatinColumn,
            densityMm: Float,
            insetFirstColumn: Boolean,
            visualAnchor: FPoint? =
                null
        ): FPoint? {
            val centers =
                centerRunUnderlayPoints(
                    column =
                        column,
                    densityMm =
                        densityMm
                )

            if (
                centers.isEmpty()
            ) {
                return null
            }

            val index =
                if (insetFirstColumn) {
                    firstColumnUnderlayStartIndex(centers)
                } else {
                    0
                }

            return centers[
                index
            ]
        }

        private fun emitCenterRunUnderlay(
            column: SatinColumn,
            densityMm: Float,
            insetFirstColumn: Boolean =
                false,
            visualAnchor: FPoint? =
                null
        ) {
            val centers =
                centerRunUnderlayPoints(
                    column =
                        column,
                    densityMm =
                        densityMm
                )

            if (
                centers.isEmpty()
            ) {
                return
            }

            val startIndex =
                if (insetFirstColumn) {
                    firstColumnUnderlayStartIndex(centers)
                } else {
                    0
                }

            // Uma única passada central do ponto de entrada até o extremo.
            centers
                .drop(
                    startIndex
                )
                .forEach {
                        point ->
                    emitStitchTo(
                        point
                    )
                }
        }

        private fun emitLock(
            row: SatinRow
        ) {
            val before =
                current

            val distanceToA =
                before?.let {
                        point ->
                    distance(
                        point,
                        row.a
                    )
                }
                    ?: 0f

            val distanceToB =
                before?.let {
                        point ->
                    distance(
                        point,
                        row.b
                    )
                }
                    ?: Float.MAX_VALUE

            val anchor =
                if (
                    distanceToB <
                        distanceToA
                ) {
                    row.b
                } else {
                    row.a
                }

            val other =
                if (
                    anchor ==
                        row.a
                ) {
                    row.b
                } else {
                    row.a
                }

            val dx =
                other.x -
                    anchor.x

            val dy =
                other.y -
                    anchor.y

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
                anchor
            )

            emitStitchTo(
                FPoint(
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

            emitStitchTo(
                anchor
            )
        }

        fun guidedJump(target: FPoint) {
            travelTo(target)
        }

        fun guidedFirstStitch(target: FPoint) {
            require(current == null) {
                "O inicio marcado deve ser a primeira pontada do desenho."
            }
            emit(target, StitchCommand.JUMP)
            // Uma penetracao zero-deslocamento ancora o inicio definido
            // pelo usuario; nao cria costura entre o marcador e o bordado.
            emit(target, StitchCommand.STITCH)
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

            val xUnits =
                point.x
                    .roundToInt()

            val yUnits =
                point.y
                    .roundToInt()

            val previous =
                output.lastOrNull()

            if (
                command ==
                    StitchCommand.STITCH &&
                previous?.command ==
                    StitchCommand.STITCH &&
                previous.xUnits ==
                    xUnits &&
                previous.yUnits ==
                    yUnits
            ) {
                /*
                 * Não martela a mesma perfuração duas vezes por simples
                 * coincidência de underlay/lock/row. A trava real de 0,6 mm
                 * continua preservada porque muda de coordenada.
                 */
                current =
                    point
                return
            }

            output +=
                EmbroideryPoint(
                    xUnits =
                        xUnits,
                    yUnits =
                        yUnits,
                    command =
                        command,
                    colorIndex =
                        0
                )

            current =
                point
        }

        fun debugConnectedObjectWinsOverCloserUnrelatedLeg():
            Float {
            fun columnAt(
                x: Float,
                y: Float
            ): SatinColumn =
                SatinColumn(
                    mutableListOf(
                        SatinRow(
                            FPoint(
                                x,
                                y
                            ),
                            FPoint(
                                x +
                                    2f,
                                y
                            )
                        ),
                        SatinRow(
                            FPoint(
                                x,
                                y +
                                    2f
                            ),
                            FPoint(
                                x +
                                    2f,
                                y +
                                    2f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                x,
                                y +
                                    4f
                            ),
                            FPoint(
                                x +
                                    2f,
                                y +
                                    4f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                x,
                                y +
                                    6f
                            ),
                            FPoint(
                                x +
                                    2f,
                                y +
                                    6f
                            )
                        )
                    )
                )

            val sameObject =
                columnAt(
                    20f,
                    0f
                )

            val closerButSeparated =
                columnAt(
                    0f,
                    12f
                )

            val polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                -3f,
                                -3f
                            ),
                            FPoint(
                                30f,
                                -3f
                            ),
                            FPoint(
                                30f,
                                6f
                            ),
                            FPoint(
                                -3f,
                                6f
                            )
                        )
                    ),
                    Polygon(
                        listOf(
                            FPoint(
                                -3f,
                                12f
                            ),
                            FPoint(
                                3f,
                                12f
                            ),
                            FPoint(
                                3f,
                                22f
                            ),
                            FPoint(
                                -3f,
                                22f
                            )
                        )
                    )
                )

            val selected =
                nextColumnFollowingObject(
                    columns =
                        listOf(
                            closerButSeparated,
                            sameObject
                        ),
                    anchor =
                        FPoint(
                            0f,
                            0f
                        ),
                    underlayMode =
                        SatinUnderlayMode.CENTER,
                    polygons =
                        polygons
                )
                    ?: error(
                        "Nenhuma coluna selecionada."
                    )

            return columnLeftEdgeX(
                selected
            )
        }

        fun debugConnectedLeftBranchPriority(): Float {
            fun column(x: Float) = SatinColumn(
                (0..6 step 2).map { y ->
                    SatinRow(
                        FPoint(x, y.toFloat()),
                        FPoint(x + 2f, y.toFloat())
                    )
                }.toMutableList()
            )

            val selected = nextColumnFollowingObject(
                columns = listOf(column(50f), column(20f)),
                anchor = FPoint(44f, 0f),
                underlayMode = SatinUnderlayMode.NONE,
                polygons = listOf(
                    Polygon(
                        listOf(
                            FPoint(15f, -5f),
                            FPoint(60f, -5f),
                            FPoint(60f, 15f),
                            FPoint(15f, 15f)
                        )
                    )
                )
            ) ?: error("Nenhum ramo conectado encontrado")

            return columnLeftEdgeX(selected)
        }
    }

    internal fun debugAxisReferenceEmissionPath(
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

        val column =
            SatinColumn(
                rows =
                    mutableListOf(
                        SatinRow(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                20f,
                                0f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                0f,
                                10f
                            ),
                            FPoint(
                                20f,
                                10f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                0f,
                                20f
                            ),
                            FPoint(
                                20f,
                                20f
                            )
                        ),
                        SatinRow(
                            FPoint(
                                0f,
                                30f
                            ),
                            FPoint(
                                20f,
                                30f
                            )
                        )
                    )
            )

        emitter.emitAxisReferenceColumn(
            column =
                column,
            includeUnderlay =
                includeUnderlay,
            densityMm =
                0.4f
        )

        return output
    }

    internal fun debugAdaptiveFlowEnvelope():
        Pair<Float, Float> {
        val polygon =
            Polygon(
                listOf(
                    FPoint(
                        0f,
                        0f
                    ),
                    FPoint(
                        20f,
                        0f
                    ),
                    FPoint(
                        20f,
                        100f
                    ),
                    FPoint(
                        0f,
                        100f
                    )
                )
            )

        val columns =
            sampleAdaptiveFlowColumns(
                polygons =
                    listOf(
                        polygon
                    ),
                densityMm =
                    0.4f,
                maxSatinWidthMm =
                    7f,
                pullCompensationMm =
                    0f
            )

        val points =
            columns.flatMap {
                    column ->
                column.rows.flatMap {
                        row ->
                    listOf(
                        row.a,
                        row.b
                    )
                }
            }

        return (
            points.minOfOrNull {
                it.y
            } ?: Float.NaN
            ) to
            (
                points.maxOfOrNull {
                    it.y
                } ?: Float.NaN
                )
    }

    internal fun debugActiveSamplerOrientationCounts():
        Pair<Int, Int> {
        val polygon =
            Polygon(
                listOf(
                    FPoint(0f, 0f),
                    FPoint(22f, 0f),
                    FPoint(22f, 58f),
                    FPoint(70f, 58f),
                    FPoint(70f, 80f),
                    FPoint(0f, 80f)
                )
            )

        val columns =
            sampleColumns(
                polygons =
                    listOf(
                        polygon
                    ),
                densityMm =
                    0.4f,
                maxSatinWidthMm =
                    7f,
                pullCompensationMm =
                    0f
            )

        val rows =
            columns.flatMap {
                it.rows
            }

        val horizontal =
            rows.count {
                    row ->
                abs(
                    row.b.x -
                        row.a.x
                ) >
                    abs(
                        row.b.y -
                            row.a.y
                    ) *
                        1.5f
            }

        val vertical =
            rows.count {
                    row ->
                abs(
                    row.b.y -
                        row.a.y
                ) >
                    abs(
                        row.b.x -
                            row.a.x
                    ) *
                        1.5f
            }

        return horizontal to
            vertical
    }

    internal fun debugCompactVsAdaptiveRowCounts():
        Pair<Int, Int> {
        val polygon =
            Polygon(
                listOf(
                    FPoint(0f, 0f),
                    FPoint(22f, 0f),
                    FPoint(22f, 58f),
                    FPoint(70f, 58f),
                    FPoint(70f, 80f),
                    FPoint(0f, 80f)
                )
            )

        val compact =
            sampleColumns(
                polygons =
                    listOf(
                        polygon
                    ),
                densityMm =
                    0.4f,
                maxSatinWidthMm =
                    7f,
                pullCompensationMm =
                    0f
            ).sumOf {
                it.rows.size
            }

        val adaptive =
            sampleAdaptiveFlowColumns(
                polygons =
                    listOf(
                        polygon
                    ),
                densityMm =
                    0.4f,
                maxSatinWidthMm =
                    7f,
                pullCompensationMm =
                    0f
            ).sumOf {
                it.rows.size
            }

        return compact to
            adaptive
    }

    internal fun debugAdaptiveFlowOrientationCounts():
        Pair<Int, Int> {
        val polygon =
            Polygon(
                listOf(
                    FPoint(
                        0f,
                        0f
                    ),
                    FPoint(
                        22f,
                        0f
                    ),
                    FPoint(
                        22f,
                        58f
                    ),
                    FPoint(
                        70f,
                        58f
                    ),
                    FPoint(
                        70f,
                        80f
                    ),
                    FPoint(
                        0f,
                        80f
                    )
                )
            )

        val columns =
            sampleAdaptiveFlowColumns(
                polygons =
                    listOf(
                        polygon
                    ),
                densityMm =
                    0.4f,
                maxSatinWidthMm =
                    7f,
                pullCompensationMm =
                    0f
            )

        val rows =
            columns.flatMap {
                it.rows
            }

        val horizontal =
            rows.count {
                    row ->
                abs(
                    row.b.x -
                        row.a.x
                ) >
                    abs(
                        row.b.y -
                            row.a.y
                    ) *
                        1.5f
            }

        val vertical =
            rows.count {
                    row ->
                abs(
                    row.b.y -
                        row.a.y
                ) >
                    abs(
                        row.b.x -
                            row.a.x
                    ) *
                        1.5f
            }

        return horizontal to
            vertical
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

    internal fun debugRealStartGeometry(
        font: ImportedFont,
        sourceText: String,
        options: TextMatrixOptions
    ): String =
        runCatching {
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

                    /*
                     * O PE-DESIGN obtém o contorno vetorial pelo Skia sem
                     * hinting de tela. Desabilitar hinting/forçar métricas
                     * lineares evita que o Android altere discretamente o
                     * contorno e a largura do glifo antes do sampler Satin.
                     */
                    hinting =
                        Paint.HINTING_OFF
                    isSubpixelText =
                        true
                    @Suppress("DEPRECATION")
                    isLinearText =
                        true

                    style =
                        Paint.Style.FILL
                    textSize =
                        resolveFontSizeForCapHeight(
                            paint =
                                this,
                            targetCapHeightUnits =
                                targetHeightUnits,
                            font =
                                font
                        )
                }

            val renderableText =
                resolveText(
                    paint =
                        paint,
                    font =
                        font,
                    text =
                        sourceText
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

            val union =
                sampledTightBounds(
                    glyphPaths
                )

            val centerX =
                union.centerX()

            val centerY =
                union.centerY()

            val firstPolygons =
                polygonize(
                    path =
                        glyphPaths.first()
                            .path,
                    centerX =
                        centerX,
                    centerY =
                        centerY,
                    rotationDegrees =
                        options.rotationDegrees
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

            val columns =
                sampleColumns(
                    polygons =
                        firstPolygons,
                    densityMm =
                        densityMm,
                    maxSatinWidthMm =
                        DEFAULT_MAX_SATIN_WIDTH_MM,
                    pullCompensationMm =
                        pullMm
                )

            val start =
                satinStructuralStartPoint(
                    columns
                )

            val selected =
                start?.let {
                        anchor ->
                    columns.minByOrNull {
                            column ->
                        column.rows
                            .minOfOrNull {
                                    row ->
                                distance(
                                    anchor,
                                    center(
                                        row
                                    )
                                )
                            }
                            ?: Float.MAX_VALUE
                    }
                }

            buildString {
                appendLine(
                    "union=" +
                        union.left +
                        "," +
                        union.top +
                        ".." +
                        union.right +
                        "," +
                        union.bottom
                )
                appendLine(
                    "center=" +
                        centerX +
                        "," +
                        centerY
                )
                appendLine(
                    "baselineCenteredY=" +
                        centerY
                )
                appendLine(
                    "structuralStart=" +
                        start
                )
                appendLine(
                    "columns=" +
                        columns.size
                )

                columns
                    .take(
                        12
                    )
                    .forEachIndexed {
                            index,
                            column ->
                        val centers =
                            column.rows.map {
                                center(
                                    it
                                )
                            }

                        val pathLength =
                            centers
                                .zipWithNext()
                                .sumOf {
                                        pair ->
                                    distance(
                                        pair.first,
                                        pair.second
                                    )
                                        .toDouble()
                                }

                        val direct =
                            if (
                                centers.size >=
                                    2
                            ) {
                                distance(
                                    centers.first(),
                                    centers.last()
                                )
                            } else {
                                0f
                            }

                        appendLine(
                            "column[" +
                                index +
                                "] rows=" +
                                column.rows.size +
                                " minX=" +
                                columnLeftEdgeX(
                                    column
                                ) +
                                " start=" +
                                centers.firstOrNull() +
                                " end=" +
                                centers.lastOrNull() +
                                " direct=" +
                                direct +
                                " path=" +
                                pathLength
                        )
                    }

                if (
                    selected !=
                        null
                ) {
                    appendLine(
                        "selectedRows:"
                    )

                    selected.rows
                        .take(
                            32
                        )
                        .forEachIndexed {
                                index,
                                row ->
                            val mid =
                                center(
                                    row
                                )

                            appendLine(
                                index.toString() +
                                    " center=" +
                                    mid.x +
                                    "," +
                                    mid.y +
                                    " width=" +
                                    distance(
                                        row.a,
                                        row.b
                                    )
                            )
                        }
                }
            }
        }.getOrElse {
                error ->
            "debugRealStartGeometry error=" +
                error.message
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

    internal fun debugStructuralSatinStartX():
        Float? {
        fun column(
            centers:
                List<Pair<Float, Float>>
        ): SatinColumn =
            SatinColumn(
                centers.map {
                        point ->
                    SatinRow(
                        FPoint(
                            point.first -
                                4f,
                            point.second
                        ),
                        FPoint(
                            point.first +
                                4f,
                            point.second
                        )
                    )
                }
                    .toMutableList()
            )

        val flourish =
            column(
                listOf(
                    4f to 0f,
                    16f to 7f,
                    5f to 14f,
                    16f to 21f,
                    4f to 30f
                )
            )

        val mainStroke =
            column(
                listOf(
                    24f to 1f,
                    24f to 9f,
                    25f to 17f,
                    26f to 25f,
                    27f to 33f
                )
            )

        val nextStroke =
            column(
                listOf(
                    55f to 1f,
                    56f to 10f,
                    57f to 19f,
                    58f to 28f
                )
            )

        return satinStructuralStartPoint(
            listOf(
                flourish,
                mainStroke,
                nextStroke
            )
        )?.x
    }

    internal fun debugIncompleteHandTraceStillSewsWholeStroke(): List<EmbroideryPoint> {
        val glyph = Polygon(listOf(
            FPoint(0f, 0f), FPoint(120f, 0f),
            FPoint(120f, 55f), FPoint(0f, 55f)
        ))
        // O usuario desenhou apenas os primeiros 15% da faixa. O motor
        // precisa cobrir o RESTO da tipografia por seu contorno real.
        val route = GuidedSatinRoute(
            start = GuidedSatinPoint(0.04f, 0.5f),
            steps = listOf(
                GuidedSatinStep(1, false, listOf(
                    GuidedSatinPoint(0.04f, 0.5f),
                    GuidedSatinPoint(0.15f, 0.5f)
                ))
            )
        )
        val output = mutableListOf<EmbroideryPoint>()
        emitUserGuidedRoute(
            emitter = SatinEmitter(output),
            polygonsByGlyph = listOf(listOf(glyph)),
            route = route,
            densityMm = 0.4f,
            maxSatinWidthMm = 7f,
            pullMm = 0.2f,
            underlayMode = SatinUnderlayMode.CENTER
        )
        return output
    }

    internal fun debugGuidedConnectedLetterHasNoForcedJump(): List<EmbroideryPoint> {
        val outline = Polygon(
            listOf(
                FPoint(0f, 0f), FPoint(90f, 0f),
                FPoint(90f, 50f), FPoint(0f, 50f)
            )
        )
        val route = GuidedSatinRoute(
            start = GuidedSatinPoint(0.3f, 0.5f),
            steps = listOf(
                GuidedSatinStep(
                    1, false,
                    listOf(GuidedSatinPoint(0.1f, 0.5f),
                        GuidedSatinPoint(0.5f, 0.5f))
                ),
                GuidedSatinStep(
                    2, true,
                    listOf(GuidedSatinPoint(0.5f, 0.5f),
                        GuidedSatinPoint(0.7f, 0.5f))
                ),
                GuidedSatinStep(
                    3, false,
                    listOf(GuidedSatinPoint(0.7f, 0.5f),
                        GuidedSatinPoint(0.95f, 0.5f))
                )
            )
        )
        val result = mutableListOf<EmbroideryPoint>()
        emitUserGuidedRoute(
            emitter = SatinEmitter(result),
            polygonsByGlyph = listOf(listOf(outline)),
            route = route,
            densityMm = 0.4f,
            maxSatinWidthMm = 7f,
            pullMm = 0.2f,
            underlayMode = SatinUnderlayMode.CENTER
        )
        return result
    }

    internal fun debugGuidedRouteStageOrder(): List<EmbroideryPoint> {
        fun rect(left: Float, top: Float): Polygon =
            Polygon(listOf(
                FPoint(left, top),
                FPoint(left + 20f, top),
                FPoint(left + 20f, top + 50f),
                FPoint(left, top + 50f)
            ))
        val route = GuidedSatinRoute(
            start = GuidedSatinPoint(0.85f, 0.5f),
            steps = listOf(
                GuidedSatinStep(1, false, listOf(
                    GuidedSatinPoint(0.87f, 0.9f),
                    GuidedSatinPoint(0.87f, 0.1f)
                )),
                GuidedSatinStep(2, true, listOf(
                    GuidedSatinPoint(0.55f, 0.5f),
                    GuidedSatinPoint(0.45f, 0.5f)
                )),
                GuidedSatinStep(3, false, listOf(
                    GuidedSatinPoint(0.12f, 0.9f),
                    GuidedSatinPoint(0.12f, 0.1f)
                ))
            )
        )
        val output = mutableListOf<EmbroideryPoint>()
        emitUserGuidedRoute(
            emitter = SatinEmitter(output),
            polygonsByGlyph = listOf(listOf(rect(80f, 0f)), listOf(rect(0f, 0f))),
            route = route,
            densityMm = 0.4f,
            maxSatinWidthMm = 7f,
            pullMm = 0.2f,
            underlayMode = SatinUnderlayMode.CENTER
        )
        return output
    }

    internal fun debugStructuralFootY(): Float? {
        val column = SatinColumn(
            listOf(0f, 40f, 80f, 120f, 140f).map { y ->
                SatinRow(
                    FPoint(24f, y),
                    FPoint(38f, y)
                )
            }.toMutableList()
        )
        return satinStructuralStartPoint(listOf(column))?.y
    }

    internal fun debugFirstColumnChosenByAnchor():
        Float? {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val loopColumn =
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
                    ),
                    SatinRow(
                        FPoint(
                            0f,
                            30f
                        ),
                        FPoint(
                            10f,
                            30f
                        )
                    )
                )
            )

        val mainStroke =
            SatinColumn(
                mutableListOf(
                    SatinRow(
                        FPoint(
                            24f,
                            0f
                        ),
                        FPoint(
                            38f,
                            0f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            24f,
                            10f
                        ),
                        FPoint(
                            38f,
                            10f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            24f,
                            20f
                        ),
                        FPoint(
                            38f,
                            20f
                        )
                    ),
                    SatinRow(
                        FPoint(
                            24f,
                            30f
                        ),
                        FPoint(
                            38f,
                            30f
                        )
                    )
                )
            )

        val anchor =
            FPoint(
                25f,
                4f
            )

        return emitter
            .debugColumnFromAnchor(
                columns =
                    listOf(
                        loopColumn,
                        mainStroke
                    ),
                anchor =
                    anchor
            )
            ?.rows
            ?.flatMap {
                    row ->
                listOf(
                    row.a.x,
                    row.b.x
                )
            }
            ?.minOrNull()
    }

    internal fun debugFirstColumnInsetPath():
        List<EmbroideryPoint> {
        val output =
            mutableListOf<
                EmbroideryPoint
            >()

        val emitter =
            SatinEmitter(
                output
            )

        val rows =
            (0..140 step 4)
                .map {
                        y ->
                    SatinRow(
                        FPoint(
                            0f,
                            y.toFloat()
                        ),
                        FPoint(
                            20f,
                            y.toFloat()
                        )
                    )
                }
                .toMutableList()

        emitter.emitGlyph(
            columns =
                listOf(
                    SatinColumn(
                        rows
                    )
                ),
            polygons =
                listOf(
                    Polygon(
                        listOf(
                            FPoint(
                                0f,
                                0f
                            ),
                            FPoint(
                                20f,
                                0f
                            ),
                            FPoint(
                                20f,
                                140f
                            ),
                            FPoint(
                                0f,
                                140f
                            )
                        )
                    )
                ),
            startHint =
                FPoint(
                    10f,
                    37f
                ),
            underlayMode =
                SatinUnderlayMode.CENTER,
            densityMm =
                0.4f
        )

        return output
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

    internal fun debugSerpentineSecondColumnEntry():
        Pair<Int, Int>? {
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
            .firstOrNull {
                it.command ==
                    StitchCommand.STITCH &&
                    it.xUnits >=
                        30
            }
            ?.let {
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

    internal fun debugConnectedObjectWinsOverCloserUnrelatedLeg():
        Float =
        SatinEmitter(
            mutableListOf()
        )
            .debugConnectedObjectWinsOverCloserUnrelatedLeg()

    internal fun debugConnectedLeftBranchPriority(): Float =
        SatinEmitter(mutableListOf()).debugConnectedLeftBranchPriority()

    internal fun debugFlowJunctionContinuity():
        List<List<Pair<Int, Int>>> {
        val left =
            listOf(
                GridPoint(
                    10,
                    0
                ),
                GridPoint(
                    10,
                    5
                ),
                GridPoint(
                    10,
                    10
                )
            )

        val right =
            listOf(
                GridPoint(
                    10,
                    10
                ),
                GridPoint(
                    10,
                    15
                ),
                GridPoint(
                    10,
                    20
                )
            )

        val branch =
            listOf(
                GridPoint(
                    10,
                    10
                ),
                GridPoint(
                    5,
                    10
                ),
                GridPoint(
                    0,
                    10
                )
            )

        return mergeFlowPathsByTangentContinuity(
            listOf(
                left,
                branch,
                right
            )
        ).map {
                path ->
            path.map {
                    point ->
                point.row to
                    point.column
            }
        }
    }

    internal fun debugCurvedYJunctionContinuity():
        List<List<Pair<Int, Int>>> {
        /*
         * Três braços em Y. Os dois braços inferiores formam a continuação
         * mais suave do mesmo traço (aprox. 120 graus no nó); o braço
         * superior deve permanecer como ramo lateral.
         */
        val left =
            listOf(
                GridPoint(
                    20,
                    0
                ),
                GridPoint(
                    16,
                    5
                ),
                GridPoint(
                    10,
                    10
                )
            )

        val right =
            listOf(
                GridPoint(
                    10,
                    10
                ),
                GridPoint(
                    5,
                    15
                ),
                GridPoint(
                    0,
                    20
                )
            )

        val branch =
            listOf(
                GridPoint(
                    10,
                    10
                ),
                GridPoint(
                    5,
                    10
                ),
                GridPoint(
                    0,
                    10
                )
            )

        return mergeFlowPathsByTangentContinuity(
            listOf(
                left,
                branch,
                right
            )
        ).map {
                path ->
            path.map {
                    point ->
                point.row to
                    point.column
            }
        }
    }

    internal fun debugSkeletonCornerPathCount():
        Int {
        val mask =
            Array(
                7
            ) {
                BooleanArray(
                    7
                )
            }

        /*
         * Uma quina espessa/staircase. Sem supressão da diagonal redundante,
         * o trio (3,2)-(3,3)-(2,3) forma um triângulo de arestas no grafo.
         */
        val pixels =
            listOf(
                GridPoint(3, 1),
                GridPoint(3, 2),
                GridPoint(3, 3),
                GridPoint(2, 3),
                GridPoint(1, 3)
            )

        pixels.forEach {
                point ->
            mask[
                point.row
            ][
                point.column
            ] =
                true
        }

        return extractFlowPaths(
            skeleton =
                mask,
            columns =
                7
        ).size
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
