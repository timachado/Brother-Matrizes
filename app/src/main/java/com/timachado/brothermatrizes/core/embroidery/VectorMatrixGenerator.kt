package com.timachado.brothermatrizes.core.embroidery

import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class VectorPoint(
    val x: Float,
    val y: Float
)

data class VectorPath(
    val points: List<VectorPoint>,
    val closed: Boolean = false
)

data class VectorArtwork(
    val paths: List<VectorPath>,
    val label: String = "Desenho"
)

enum class VectorFillMode { NONE, TATAMI }

data class VectorMatrixOptions(
    val widthMm: Float = 60f,
    val stitchStyle: TextStitchStyle =
        TextStitchStyle.RUNNING,
    val runningStitchLengthMm: Float = 2.5f,
    val satinWidthMm: Float = 2.2f,
    val satinDensityMm: Float = 0.5f,
    val color: Int = 0xE6BE70,
    val outputFormat: String = "DST",
    val hoopProfile: HoopProfile? =
        HoopProfile.H100X100,
    val fabricProfile: FabricProfile? =
        FabricProfile.COTTON,
    val enforceHoop: Boolean = false,
    val fillMode: VectorFillMode = VectorFillMode.NONE,
    val tatamiRowSpacingMm: Float = 0.5f,
    val tatamiStitchLengthMm: Float = 3f
)

object VectorMatrixGenerator {

    fun generate(
        artwork: VectorArtwork,
        options: VectorMatrixOptions
    ): Result<EmbroideryDesign> =
        runCatching {
            require(
                artwork.paths.isNotEmpty()
            ) {
                "O desenho não possui caminhos."
            }

            require(
                options.widthMm in 5f..300f
            ) {
                "A largura deve ficar entre 5 e 300 mm."
            }

            require(
                options.runningStitchLengthMm in
                    1f..5f
            ) {
                "O ponto corrido deve ficar entre 1 e 5 mm."
            }

            require(
                options.satinWidthMm in
                    1f..6f
            ) {
                "A largura Satin deve ficar entre 1 e 6 mm."
            }

            require(
                options.satinDensityMm in
                    0.3f..1.2f
            ) {
                "A densidade Satin deve ficar entre 0,3 e 1,2 mm."
            }

            val format =
                options.outputFormat
                    .uppercase(
                        Locale.ROOT
                    )

            require(
                format in
                    MatrixConverter
                        .supportedFormats
            ) {
                "Formato de saída inválido."
            }

            val usefulPaths =
                artwork.paths
                    .mapNotNull {
                        path ->
                        val compact =
                            path.points
                                .fold(
                                    mutableListOf<
                                        VectorPoint
                                    >()
                                ) {
                                        acc,
                                        point ->
                                    val last =
                                        acc.lastOrNull()

                                    if (
                                        last == null ||
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
                                            0.0001
                                    ) {
                                        acc +=
                                            point
                                    }

                                    acc
                                }

                        if (
                            compact.size <
                                2
                        ) {
                            null
                        } else {
                            VectorPath(
                                points =
                                    compact,
                                closed =
                                    path.closed
                            )
                        }
                    }

            require(
                usefulPaths
                    .isNotEmpty()
            ) {
                "O desenho não possui traços suficientes."
            }

            val allPoints =
                usefulPaths
                    .flatMap {
                        it.points
                    }

            val minX =
                allPoints.minOf {
                    it.x
                }

            val maxX =
                allPoints.maxOf {
                    it.x
                }

            val minY =
                allPoints.minOf {
                    it.y
                }

            val maxY =
                allPoints.maxOf {
                    it.y
                }

            val sourceWidth =
                (
                    maxX -
                        minX
                    ).coerceAtLeast(
                        0.001f
                    )

            val sourceHeight =
                (
                    maxY -
                        minY
                    ).coerceAtLeast(
                        0.001f
                    )

            val widthUnits =
                options.widthMm *
                    10f

            val scale =
                widthUnits /
                    sourceWidth

            val heightUnits =
                sourceHeight *
                    scale

            val targetCenterX =
                0f

            val targetCenterY =
                0f

            val output =
                mutableListOf<
                    EmbroideryPoint
                >()

            var currentX = 0
            var currentY = 0

            if (options.fillMode == VectorFillMode.TATAMI) {
                require(options.tatamiRowSpacingMm in 0.3f..0.9f) {
                    "Espaçamento Tatami inválido."
                }
                require(options.tatamiStitchLengthMm in 1.5f..4f) {
                    "Comprimento máximo Tatami inválido."
                }
                require(usefulPaths.all { it.closed }) {
                    "Para usar Tatami, feche todos os contornos do desenho."
                }
                val contours = usefulPaths.map { path ->
                    path.points.map { point ->
                        val x = ((point.x - minX) * scale - widthUnits / 2f +
                            targetCenterX).roundToInt()
                        val y = (heightUnits / 2f -
                            (point.y - minY) * scale + targetCenterY).roundToInt()
                        x to y
                    }
                }
                val fill = VectorTatamiFillGenerator.generate(
                    contours = contours,
                    rowSpacingUnits = (options.tatamiRowSpacingMm * 10).roundToInt(),
                    maxStitchLengthUnits = (options.tatamiStitchLengthMm * 10).roundToInt()
                ).getOrThrow()
                output.addAll(fill)
                currentX = fill.last().xUnits
                currentY = fill.last().yUnits
            } else {
            usefulPaths.forEach {
                    path ->
                val units =
                    path.points
                        .map {
                                point ->
                            Pair(
                                (
                                    (
                                        point.x -
                                            minX
                                        ) *
                                        scale -
                                        widthUnits /
                                            2f +
                                        targetCenterX
                                    ).roundToInt(),
                                (
                                    heightUnits /
                                        2f -
                                        (
                                            point.y -
                                                minY
                                            ) *
                                            scale +
                                        targetCenterY
                                    ).roundToInt()
                            )
                        }
                        .toMutableList()

                if (
                    path.closed &&
                    units.first() !=
                        units.last()
                ) {
                    units +=
                        units.first()
                }

                if (
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
                    units.first()

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

                when (
                    options.stitchStyle
                ) {
                    TextStitchStyle.RUNNING -> {
                        for (
                            index in
                                1 until
                                    units.size
                        ) {
                            val target =
                                units[index]

                            val segment =
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
                                        options
                                            .runningStitchLengthMm *
                                            10f
                                )

                            currentX =
                                segment.first

                            currentY =
                                segment.second
                        }
                    }

                    TextStitchStyle.SATIN -> {
                        val result =
                            SatinGenerator
                                .append(
                                    points =
                                        output,
                                    stroke =
                                        units,
                                    currentX =
                                        currentX,
                                    currentY =
                                        currentY,
                                    widthUnits =
                                        options
                                            .satinWidthMm *
                                            10f,
                                    stepUnits =
                                        options
                                            .satinDensityMm *
                                            10f,
                                    pullCompensationUnits =
                                        2f,
                                    shortStitches =
                                        true,
                                    underlayMode =
                                        SatinUnderlayMode
                                            .CENTER
                                )

                        currentX =
                            result.currentX

                        currentY =
                            result.currentY
                    }
                }
            }
            }

            output +=
                EmbroideryPoint(
                    currentX,
                    currentY,
                    StitchCommand.END,
                    0
                )

            val coordinates =
                output.filter {
                    it.command !=
                        StitchCommand.END
                }

            val design =
                EmbroideryDesign(
                    fileName =
                        safeName(
                            artwork.label
                        ) +
                            "." +
                            format
                                .lowercase(
                                    Locale.ROOT
                                ),
                    format =
                        format,
                    label =
                        artwork.label,
                    points =
                        output,
                    bounds =
                        EmbroideryBounds(
                            minXUnits =
                                coordinates
                                    .minOf {
                                        it.xUnits
                                    },
                            maxXUnits =
                                coordinates
                                    .maxOf {
                                        it.xUnits
                                    },
                            minYUnits =
                                coordinates
                                    .minOf {
                                        it.yUnits
                                    },
                            maxYUnits =
                                coordinates
                                    .maxOf {
                                        it.yUnits
                                    }
                        ),
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

            val finished =
                MachineFinishing
                    .apply(
                        design
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
                            options.hoopProfile
                        )

                require(
                    fit.fits
                ) {
                    "O desenho ultrapassa a área segura do bastidor " +
                        options
                            .hoopProfile
                            .displayName +
                        "."
                }
            }

            finished
        }

    private fun appendRunning(
        output: MutableList<EmbroideryPoint>,
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

        val segments =
            max(
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
                part.toDouble() /
                    segments

            output +=
                EmbroideryPoint(
                    (
                        fromX +
                            dx *
                                ratio
                        ).roundToInt(),
                    (
                        fromY +
                            dy *
                                ratio
                        ).roundToInt(),
                    StitchCommand.STITCH,
                    0
                )
        }

        return Pair(
            toX,
            toY
        )
    }

    private fun safeName(
        input: String
    ): String =
        input
            .lowercase(
                Locale.ROOT
            )
            .replace(
                Regex(
                    "[^a-z0-9_-]+"
                ),
                "-"
            )
            .trim('-')
            .ifBlank {
                "desenho"
            }
}

private data class SvgAffineTransform(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val e: Double = 0.0,
    val f: Double = 0.0
) {
    // Matriz afim SVG: x'=a*x+c*y+e, y'=b*x+d*y+f.
    // A composição A * B aplica B primeiro e depois A.
    operator fun times(rhs: SvgAffineTransform) = SvgAffineTransform(
        a = a * rhs.a + c * rhs.b,
        b = b * rhs.a + d * rhs.b,
        c = a * rhs.c + c * rhs.d,
        d = b * rhs.c + d * rhs.d,
        e = a * rhs.e + c * rhs.f + e,
        f = b * rhs.e + d * rhs.f + f
    )

    fun map(point: VectorPoint): VectorPoint {
        val x = a * point.x + c * point.y + e
        val y = b * point.x + d * point.y + f
        require(x.isFinite() && y.isFinite() &&
            kotlin.math.abs(x) <= 1_000_000 && kotlin.math.abs(y) <= 1_000_000) {
            "Transformação SVG gerou coordenadas inválidas."
        }
        return VectorPoint(x.toFloat(), y.toFloat())
    }
}

private fun parseSvgTransform(raw: String): SvgAffineTransform {
    if (raw.isBlank()) return SvgAffineTransform()
    val transformPattern = Regex("""([a-zA-Z]+)\s*\(([^()]*)\)""")
    val numberPattern = Regex("""[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?""")
    var result = SvgAffineTransform()
    var cursor = 0
    var count = 0
    for (match in transformPattern.findAll(raw)) {
        require(raw.substring(cursor, match.range.first).all {
            it.isWhitespace() || it == ','
        }) { "Transformação SVG inválida." }
        cursor = match.range.last + 1
        require(++count <= 64) { "Transformações SVG em excesso." }
        val args = match.groupValues[2]
        require(args.replace(numberPattern, "").all {
            it.isWhitespace() || it == ','
        }) { "Parâmetros SVG inválidos." }
        val n = numberPattern.findAll(args).map { it.value.toDouble() }.toList()
        require(n.all { it.isFinite() && kotlin.math.abs(it) <= 1e9 }) {
            "Valores de transformação SVG inválidos."
        }
        val local = when (match.groupValues[1].lowercase(Locale.ROOT)) {
            "translate" -> {
                require(n.size in 1..2)
                SvgAffineTransform(e = n[0], f = n.getOrElse(1) { 0.0 })
            }
            "scale" -> {
                require(n.size in 1..2)
                SvgAffineTransform(a = n[0], d = n.getOrElse(1) { n[0] })
            }
            "matrix" -> {
                require(n.size == 6)
                SvgAffineTransform(n[0], n[1], n[2], n[3], n[4], n[5])
            }
            "rotate" -> {
                require(n.size == 1 || n.size == 3)
                val angle = Math.toRadians(n[0])
                val rotation = SvgAffineTransform(
                    a = kotlin.math.cos(angle), b = kotlin.math.sin(angle),
                    c = -kotlin.math.sin(angle), d = kotlin.math.cos(angle)
                )
                if (n.size == 1) rotation else {
                    SvgAffineTransform(e = n[1], f = n[2]) *
                        rotation * SvgAffineTransform(e = -n[1], f = -n[2])
                }
            }
            "skewx" -> {
                require(n.size == 1)
                SvgAffineTransform(c = kotlin.math.tan(Math.toRadians(n[0])))
            }
            "skewy" -> {
                require(n.size == 1)
                SvgAffineTransform(b = kotlin.math.tan(Math.toRadians(n[0])))
            }
            else -> error("Tipo de transformação SVG não suportado.")
        }
        result = result * local
    }
    require(count > 0 && raw.substring(cursor).all {
        it.isWhitespace() || it == ','
    }) { "Transformação SVG inválida." }
    return result
}

object SimpleSvgParser {

    fun parse(
        name: String,
        bytes: ByteArray
    ): Result<VectorArtwork> =
        runCatching {
            require(
                bytes.size <=
                    2_000_000
            ) {
                "SVG muito grande para esta versão."
            }

            val factory =
                DocumentBuilderFactory
                    .newInstance()

            runCatching {
                factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
                )
            }

            runCatching {
                factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
                )
            }

            runCatching {
                factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
                )
            }

            factory.isExpandEntityReferences =
                false

            // Alguns editores (ex.: Potrace) emitem um DOCTYPE SVG 1.0
            // público, sem entidades. Removemos somente esse cabeçalho
            // conhecido ANTES da análise XML. Nenhum DTD é resolvido.
            val legacyDoctype = Regex(
                """<!DOCTYPE\s+svg\s+PUBLIC\s+"-//W3C//DTD SVG 20010904//EN"\s+"http://www\.w3\.org/TR/2001/REC-SVG-20010904/DTD/svg10\.dtd"\s*>""",
                RegexOption.IGNORE_CASE
            )
            val xml = bytes.toString(Charsets.UTF_8).replace(legacyDoctype, "")
            require(!Regex("""<!DOCTYPE\b""", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
                "Este SVG contém um DOCTYPE não permitido."
            }
            val document = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ ->
                    throw IllegalArgumentException("Referências XML externas não são permitidas.")
                }
            }.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

            val root =
                document.documentElement

            require(
                root.nodeName
                    .substringAfterLast(':')
                    .equals(
                        "svg",
                        ignoreCase = true
                    )
            ) {
                "O arquivo não é um SVG."
            }

            val paths =
                mutableListOf<
                    VectorPath
                >()

            fun collect(
                node: org.w3c.dom.Node,
                parentTransform: SvgAffineTransform = SvgAffineTransform()
            ) {
                var nodeTransform = parentTransform
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val element = node as org.w3c.dom.Element
                    val tag = element.tagName.substringAfterLast(':')
                        .lowercase(Locale.ROOT)

                    // Elementos de definição não são desenhados diretamente.
                    if (tag in setOf("defs", "clippath", "mask", "symbol", "pattern")) {
                        return
                    }
                    require(tag !in setOf("use", "image", "text", "foreignobject")) {
                        "Este SVG contém elementos ainda não suportados: $tag."
                    }

                    nodeTransform = parentTransform * parseSvgTransform(
                        element.getAttribute("transform")
                    )
                    val firstPathIndex = paths.size
                    when (tag) {
                        "path" ->
                            element
                                .getAttribute(
                                    "d"
                                )
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?.let {
                                    data ->
                                    paths +=
                                        parsePathData(
                                            data
                                        )
                                }

                        "line" ->
                            paths +=
                                VectorPath(
                                    listOf(
                                        VectorPoint(
                                            number(
                                                element,
                                                "x1"
                                            ),
                                            number(
                                                element,
                                                "y1"
                                            )
                                        ),
                                        VectorPoint(
                                            number(
                                                element,
                                                "x2"
                                            ),
                                            number(
                                                element,
                                                "y2"
                                            )
                                        )
                                    )
                                )

                        "polyline",
                        "polygon" -> {
                            val points =
                                numberList(
                                    element
                                        .getAttribute(
                                            "points"
                                        )
                                )
                                .chunked(
                                    2
                                )
                                .mapNotNull {
                                    pair ->
                                    if (
                                        pair.size ==
                                            2
                                    ) {
                                        VectorPoint(
                                            pair[0],
                                            pair[1]
                                        )
                                    } else {
                                        null
                                    }
                                }

                            if (
                                points.size >=
                                    2
                            ) {
                                paths +=
                                    VectorPath(
                                        points =
                                            points,
                                        closed =
                                            element.tagName
                                                .substringAfterLast(':')
                                                .equals(
                                                    "polygon",
                                                    true
                                                )
                                    )
                            }
                        }

                        "rect" -> {
                            val x =
                                number(
                                    element,
                                    "x"
                                )

                            val y =
                                number(
                                    element,
                                    "y"
                                )

                            val width =
                                number(
                                    element,
                                    "width"
                                )

                            val height =
                                number(
                                    element,
                                    "height"
                                )

                            if (
                                width >
                                    0f &&
                                height >
                                    0f
                            ) {
                                paths +=
                                    VectorPath(
                                        listOf(
                                            VectorPoint(
                                                x,
                                                y
                                            ),
                                            VectorPoint(
                                                x +
                                                    width,
                                                y
                                            ),
                                            VectorPoint(
                                                x +
                                                    width,
                                                y +
                                                    height
                                            ),
                                            VectorPoint(
                                                x,
                                                y +
                                                    height
                                            )
                                        ),
                                        closed = true
                                    )
                            }
                        }

                        "circle" -> {
                            val cx =
                                number(
                                    element,
                                    "cx"
                                )

                            val cy =
                                number(
                                    element,
                                    "cy"
                                )

                            val r =
                                number(
                                    element,
                                    "r"
                                )

                            if (
                                r >
                                    0f
                            ) {
                                paths +=
                                    ellipse(
                                        cx,
                                        cy,
                                        r,
                                        r
                                    )
                            }
                        }

                        "ellipse" -> {
                            val cx =
                                number(
                                    element,
                                    "cx"
                                )

                            val cy =
                                number(
                                    element,
                                    "cy"
                                )

                            val rx =
                                number(
                                    element,
                                    "rx"
                                )

                            val ry =
                                number(
                                    element,
                                    "ry"
                                )

                            if (
                                rx >
                                    0f &&
                                ry >
                                    0f
                            ) {
                                paths +=
                                    ellipse(
                                        cx,
                                        cy,
                                        rx,
                                        ry
                                    )
                            }
                        }
                    }
                    // Aplicar o transform uma só vez a cada caminho gerado
                    // neste elemento. Caminhos filhos recebem a matriz herdada.
                    for (index in firstPathIndex until paths.size) {
                        val original = paths[index]
                        paths[index] = original.copy(
                            points = original.points.map(nodeTransform::map)
                        )
                    }
                }

                val children = node.childNodes
                for (index in 0 until children.length) {
                    collect(children.item(index), nodeTransform)
                }
            }

            collect(root)
            require(paths.size <= 2_000 && paths.sumOf { it.points.size } <= 100_000) {
                "SVG com geometria complexa demais para importar."
            }

            require(
                paths.isNotEmpty()
            ) {
                "Nenhum caminho simples foi encontrado no SVG."
            }

            VectorArtwork(
                paths =
                    paths,
                label =
                    name
                        .substringBeforeLast(
                            '.'
                        )
                        .ifBlank {
                            "Logo SVG"
                        }
            )
        }

    private fun number(
        element:
            org.w3c.dom.Element,
        attribute: String
    ): Float =
        element
            .getAttribute(
                attribute
            )
            .trim()
            .removeSuffix(
                "px"
            )
            .toFloatOrNull()
            ?: 0f

    private fun numberList(
        value: String
    ): List<Float> =
        Regex(
            "[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?"
        )
            .findAll(
                value
            )
            .mapNotNull {
                it.value
                    .toFloatOrNull()
            }
            .toList()

    private fun ellipse(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float
    ): VectorPath {
        val points =
            (0 until 48)
                .map {
                    index ->
                    val angle =
                        index /
                            48f *
                            (
                                Math.PI *
                                    2.0
                                )

                    VectorPoint(
                        x =
                            cx +
                                cos(
                                    angle
                                ).toFloat() *
                                rx,
                        y =
                            cy +
                                sin(
                                    angle
                                ).toFloat() *
                                ry
                    )
                }

        return VectorPath(
            points =
                points,
            closed =
                true
        )
    }

    private fun parsePathData(
        data: String
    ): List<VectorPath> {
        val tokens =
            Regex(
                "[A-Za-z]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?"
            )
                .findAll(
                    data
                )
                .map {
                    it.value
                }
                .toList()

        val result =
            mutableListOf<
                VectorPath
            >()

        var index = 0
        var command = ' '
        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f
        var lastControlX = 0f
        var lastControlY = 0f

        var currentPath =
            mutableListOf<
                VectorPoint
            >()

        fun flush(
            closed: Boolean =
                false
        ) {
            if (
                currentPath.size >=
                    2
            ) {
                result +=
                    VectorPath(
                        points =
                            currentPath
                                .toList(),
                        closed =
                            closed
                    )
            }

            currentPath =
                mutableListOf()
        }

        fun isCommand(
            token: String
        ) =
            token.length ==
                1 &&
                token[0].isLetter()

        fun nextNumber():
            Float {
            require(
                index <
                    tokens.size &&
                    !isCommand(
                        tokens[index]
                    )
            ) {
                "SVG path inválido."
            }

            return tokens[
                index++
            ].toFloat()
        }

        fun addPoint(
            x: Float,
            y: Float
        ) {
            currentPath +=
                VectorPoint(
                    x,
                    y
                )

            currentX = x
            currentY = y
        }

        while (
            index <
                tokens.size
        ) {
            if (
                isCommand(
                    tokens[index]
                )
            ) {
                command =
                    tokens[
                        index++
                    ][0]
            }

            require(
                command !=
                    ' '
            ) {
                "SVG path sem comando inicial."
            }

            val relative =
                command.isLowerCase()

            when (
                command.uppercaseChar()
            ) {
                'M' -> {
                    val x =
                        nextNumber()

                    val y =
                        nextNumber()

                    if (
                        currentPath
                            .isNotEmpty()
                    ) {
                        flush()
                    }

                    val targetX =
                        if (
                            relative
                        ) {
                            currentX +
                                x
                        } else {
                            x
                        }

                    val targetY =
                        if (
                            relative
                        ) {
                            currentY +
                                y
                        } else {
                            y
                        }

                    addPoint(
                        targetX,
                        targetY
                    )

                    startX =
                        targetX

                    startY =
                        targetY

                    command =
                        if (
                            relative
                        ) {
                            'l'
                        } else {
                            'L'
                        }
                }

                'L' -> {
                    val x =
                        nextNumber()

                    val y =
                        nextNumber()

                    addPoint(
                        if (
                            relative
                        ) {
                            currentX +
                                x
                        } else {
                            x
                        },
                        if (
                            relative
                        ) {
                            currentY +
                                y
                        } else {
                            y
                        }
                    )
                }

                'H' -> {
                    val x =
                        nextNumber()

                    addPoint(
                        if (
                            relative
                        ) {
                            currentX +
                                x
                        } else {
                            x
                        },
                        currentY
                    )
                }

                'V' -> {
                    val y =
                        nextNumber()

                    addPoint(
                        currentX,
                        if (
                            relative
                        ) {
                            currentY +
                                y
                        } else {
                            y
                        }
                    )
                }

                'C' -> {
                    val x1Raw =
                        nextNumber()

                    val y1Raw =
                        nextNumber()

                    val x2Raw =
                        nextNumber()

                    val y2Raw =
                        nextNumber()

                    val xRaw =
                        nextNumber()

                    val yRaw =
                        nextNumber()

                    val x1 =
                        if (
                            relative
                        ) {
                            currentX +
                                x1Raw
                        } else {
                            x1Raw
                        }

                    val y1 =
                        if (
                            relative
                        ) {
                            currentY +
                                y1Raw
                        } else {
                            y1Raw
                        }

                    val x2 =
                        if (
                            relative
                        ) {
                            currentX +
                                x2Raw
                        } else {
                            x2Raw
                        }

                    val y2 =
                        if (
                            relative
                        ) {
                            currentY +
                                y2Raw
                        } else {
                            y2Raw
                        }

                    val x =
                        if (
                            relative
                        ) {
                            currentX +
                                xRaw
                        } else {
                            xRaw
                        }

                    val y =
                        if (
                            relative
                        ) {
                            currentY +
                                yRaw
                        } else {
                            yRaw
                        }

                    val fromX =
                        currentX

                    val fromY =
                        currentY

                    for (
                        step in
                            1..16
                    ) {
                        val t =
                            step /
                                16f

                        val inv =
                            1f -
                                t

                        addPoint(
                            inv *
                                inv *
                                inv *
                                fromX +
                                3f *
                                inv *
                                inv *
                                t *
                                x1 +
                                3f *
                                inv *
                                t *
                                t *
                                x2 +
                                t *
                                t *
                                t *
                                x,
                            inv *
                                inv *
                                inv *
                                fromY +
                                3f *
                                inv *
                                inv *
                                t *
                                y1 +
                                3f *
                                inv *
                                t *
                                t *
                                y2 +
                                t *
                                t *
                                t *
                                y
                        )
                    }

                    lastControlX =
                        x2

                    lastControlY =
                        y2
                }

                'S' -> {
                    val x2Raw =
                        nextNumber()

                    val y2Raw =
                        nextNumber()

                    val xRaw =
                        nextNumber()

                    val yRaw =
                        nextNumber()

                    val x1 =
                        2f *
                            currentX -
                            lastControlX

                    val y1 =
                        2f *
                            currentY -
                            lastControlY

                    val x2 =
                        if (
                            relative
                        ) {
                            currentX +
                                x2Raw
                        } else {
                            x2Raw
                        }

                    val y2 =
                        if (
                            relative
                        ) {
                            currentY +
                                y2Raw
                        } else {
                            y2Raw
                        }

                    val x =
                        if (
                            relative
                        ) {
                            currentX +
                                xRaw
                        } else {
                            xRaw
                        }

                    val y =
                        if (
                            relative
                        ) {
                            currentY +
                                yRaw
                        } else {
                            yRaw
                        }

                    val fromX =
                        currentX

                    val fromY =
                        currentY

                    for (
                        step in
                            1..16
                    ) {
                        val t =
                            step /
                                16f

                        val inv =
                            1f -
                                t

                        addPoint(
                            inv *
                                inv *
                                inv *
                                fromX +
                                3f *
                                inv *
                                inv *
                                t *
                                x1 +
                                3f *
                                inv *
                                t *
                                t *
                                x2 +
                                t *
                                t *
                                t *
                                x,
                            inv *
                                inv *
                                inv *
                                fromY +
                                3f *
                                inv *
                                inv *
                                t *
                                y1 +
                                3f *
                                inv *
                                t *
                                t *
                                y2 +
                                t *
                                t *
                                t *
                                y
                        )
                    }

                    lastControlX =
                        x2

                    lastControlY =
                        y2
                }

                'Q' -> {
                    val cxRaw =
                        nextNumber()

                    val cyRaw =
                        nextNumber()

                    val xRaw =
                        nextNumber()

                    val yRaw =
                        nextNumber()

                    val cx =
                        if (
                            relative
                        ) {
                            currentX +
                                cxRaw
                        } else {
                            cxRaw
                        }

                    val cy =
                        if (
                            relative
                        ) {
                            currentY +
                                cyRaw
                        } else {
                            cyRaw
                        }

                    val x =
                        if (
                            relative
                        ) {
                            currentX +
                                xRaw
                        } else {
                            xRaw
                        }

                    val y =
                        if (
                            relative
                        ) {
                            currentY +
                                yRaw
                        } else {
                            yRaw
                        }

                    val fromX =
                        currentX

                    val fromY =
                        currentY

                    for (
                        step in
                            1..12
                    ) {
                        val t =
                            step /
                                12f

                        val inv =
                            1f -
                                t

                        addPoint(
                            inv *
                                inv *
                                fromX +
                                2f *
                                inv *
                                t *
                                cx +
                                t *
                                t *
                                x,
                            inv *
                                inv *
                                fromY +
                                2f *
                                inv *
                                t *
                                cy +
                                t *
                                t *
                                y
                        )
                    }

                    lastControlX =
                        cx

                    lastControlY =
                        cy
                }

                'T' -> {
                    val xRaw =
                        nextNumber()

                    val yRaw =
                        nextNumber()

                    val cx =
                        2f *
                            currentX -
                            lastControlX

                    val cy =
                        2f *
                            currentY -
                            lastControlY

                    val x =
                        if (
                            relative
                        ) {
                            currentX +
                                xRaw
                        } else {
                            xRaw
                        }

                    val y =
                        if (
                            relative
                        ) {
                            currentY +
                                yRaw
                        } else {
                            yRaw
                        }

                    val fromX =
                        currentX

                    val fromY =
                        currentY

                    for (
                        step in
                            1..12
                    ) {
                        val t =
                            step /
                                12f

                        val inv =
                            1f -
                                t

                        addPoint(
                            inv *
                                inv *
                                fromX +
                                2f *
                                inv *
                                t *
                                cx +
                                t *
                                t *
                                x,
                            inv *
                                inv *
                                fromY +
                                2f *
                                inv *
                                t *
                                cy +
                                t *
                                t *
                                y
                        )
                    }

                    lastControlX =
                        cx

                    lastControlY =
                        cy
                }

                'Z' -> {
                    if (
                        currentPath
                            .isNotEmpty()
                    ) {
                        addPoint(
                            startX,
                            startY
                        )

                        flush(
                            closed = true
                        )
                    }

                    currentX =
                        startX

                    currentY =
                        startY

                    command =
                        ' '
                }

                else ->
                    error(
                        "Comando SVG ainda não suportado: $command"
                    )
            }
        }

        flush()

        return result
    }
}
